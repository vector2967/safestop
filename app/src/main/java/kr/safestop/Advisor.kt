package kr.safestop

enum class Advice { LOW_ENERGY, LONG_DRIVE }

class Advisor(
    private val lowSocPct: Int = 15,
    private val lowFuelPct: Int = 10,
    private val longDriveMs: Long = 2 * 3_600_000L,
    private val restResetMs: Long = 10 * 60_000L,
    private val cooldownMs: Long = 30 * 60_000L,
) {
    private var drivingSince: Long? = null
    private var parkedSince: Long? = null
    private val lastAdvice = mutableMapOf<Advice, Long>()

    fun update(s: CarState, now: Long): Advice? {
        if (s.parkedOrNeutral) {
            parkedSince = parkedSince ?: now
            if (now - parkedSince!! >= restResetMs) drivingSince = null
        } else {
            parkedSince = null
            if (s.speedKmh >= 5f) drivingSince = drivingSince ?: now
        }
        // 고속 주행 중에만 안내한다. 정차 중이면 이미 쉬는 중이거나 비상정차 안내가 우선
        if (s.speedKmh < 60f) return null
        val lowEnergy = lowEnergy(s)
        val longDrive = drivingSince?.let { now - it >= longDriveMs } ?: false
        return listOfNotNull(Advice.LOW_ENERGY.takeIf { lowEnergy }, Advice.LONG_DRIVE.takeIf { longDrive })
            .firstOrNull { now - (lastAdvice[it] ?: Long.MIN_VALUE / 2) >= cooldownMs }
            ?.also { lastAdvice[it] = now }
    }

    fun stillValid(a: Advice, s: CarState) = when (a) {
        Advice.LOW_ENERGY -> lowEnergy(s)
        Advice.LONG_DRIVE -> drivingSince != null
    }

    private fun lowEnergy(s: CarState) = (s.socPct ?: 100) <= lowSocPct || (s.fuelPct ?: 100) <= lowFuelPct
}

// 차박·대기 중 공회전: 내연기관(연료값이 있는 차)이 P단에 사람을 태운 채 시동을 오래 켜 두면 환기를 안내한다
class IdleWatch(private val warnAfterMs: Long = 30 * 60_000L, private val repeatMs: Long = 60 * 60_000L) {
    private var since: Long? = null
    private var lastWarn = Long.MIN_VALUE / 2

    fun update(s: CarState, now: Long): Boolean {
        val idling = s.engineOn && s.parkedOrNeutral && s.occupiedSeats > 0 && s.fuelPct != null
        if (!idling) { since = null; return false }
        val start = since ?: now.also { since = it }
        if (now - start < warnAfterMs || now - lastWarn < repeatMs) return false
        lastWarn = now
        return true
    }
}
