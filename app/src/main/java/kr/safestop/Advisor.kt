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
        val lowEnergy = (s.socPct ?: 100) <= lowSocPct || (s.fuelPct ?: 100) <= lowFuelPct
        val longDrive = drivingSince?.let { now - it >= longDriveMs } ?: false
        return listOfNotNull(Advice.LOW_ENERGY.takeIf { lowEnergy }, Advice.LONG_DRIVE.takeIf { longDrive })
            .firstOrNull { now - (lastAdvice[it] ?: Long.MIN_VALUE / 2) >= cooldownMs }
            ?.also { lastAdvice[it] = now }
    }
}
