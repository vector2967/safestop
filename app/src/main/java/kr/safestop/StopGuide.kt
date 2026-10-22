package kr.safestop

enum class Step { IDLE, CONFIRM, HAZARD, TRUNK, EVACUATE, REPORT }

data class CarState(
    val speedKmh: Float = 0f,
    val parkedOrNeutral: Boolean = false,
    val driverDoorOpen: Boolean = false,
    val trunkOpen: Boolean = false,
    val occupiedSeats: Int = 1,
    val airbagDeployed: Boolean = false,
    val speedLimitKmh: Int = 0,
    val nearStopZone: Boolean = false,
    val socPct: Int? = null,
    val fuelPct: Int? = null,
    val rightSignal: Boolean = false,
    val steeringDeg: Float = 0f,
    val naviStopZone: Boolean = false,
)

class StopGuide(
    private val highwaySpeedKmh: Float = 80f,
    private val highwayMemoryMs: Long = 120_000,
    private val confirmMs: Long = 5_000,
    private val hazardAutoNextMs: Long = 8_000,
    private val repeatMs: Long = 20_000,
    private val pullOverMs: Long = 30_000,
) {
    var step = Step.IDLE
        private set
    private var lastHighwayAt = Long.MIN_VALUE / 2
    private var stepSince = 0L
    private var lastSpokeAt = 0L
    private var repeats = 0
    private var remindedPassengers = false
    private var armed = true
    private var pullOverCueAt = Long.MIN_VALUE / 2

    fun update(s: CarState, now: Long): String? {
        if (s.speedKmh >= highwaySpeedKmh || s.speedLimitKmh >= 100) lastHighwayAt = now
        val onHighway = now - lastHighwayAt <= highwayMemoryMs
        // 에뮬레이터 실측: Pleos 핸들 각도는 오른쪽이 음수. 고속 차로 변경은 핸들 10도 안팎
        if (s.speedKmh >= 60f && (s.rightSignal || s.steeringDeg <= -10f)) pullOverCueAt = now

        if (step == Step.IDLE) {
            if (s.speedKmh >= 10f && !s.parkedOrNeutral) armed = true
            // 고속에서 우측으로 빠지며 30초 안에 멈추면 D단이어도 갓길 정차로 본다(정체와 구분)
            val pulledOver = now - pullOverCueAt <= pullOverMs
            // 배터리·연료가 바닥난 채 멈췄으면 D단이어도 고장 정차
            val outOfEnergy = (s.socPct ?: 100) <= 3 || (s.fuelPct ?: 100) <= 2
            val stopped = s.speedKmh < 1f && (s.parkedOrNeutral || s.driverDoorOpen || pulledOver || outOfEnergy)
            return when {
                s.airbagDeployed -> go(Step.HAZARD, now)
                // 휴게소·졸음쉼터 정차는 정상 정차
                // 사용자가 끈 정차는 다시 달릴 때까지 재감지하지 않는다
                armed && onHighway && stopped && !s.nearStopZone && !s.naviStopZone -> go(Step.CONFIRM, now)
                else -> null
            }
        }
        // 기어를 넣고 다시 출발하면 오탐으로 보고 해제. P/N 상태의 차속 튐은 무시
        if (s.speedKmh >= 10f && !s.parkedOrNeutral) { reset(); return null }

        return when (step) {
            // 문이나 트렁크를 열면 이미 하차 중이므로 대기 없이 안내
            Step.CONFIRM -> if (s.driverDoorOpen || s.trunkOpen || now - stepSince >= confirmMs)
                go(Step.HAZARD, now) else null
            Step.HAZARD -> when {
                s.trunkOpen -> go(Step.EVACUATE, now)
                now - stepSince >= hazardAutoNextMs -> go(Step.TRUNK, now)
                else -> null
            }
            Step.TRUNK -> if (s.trunkOpen) go(Step.EVACUATE, now) else repeatIfIgnored(now)
            Step.EVACUATE -> when {
                s.occupiedSeats == 0 -> go(Step.REPORT, now)
                s.driverDoorOpen && s.occupiedSeats > 0 && !remindedPassengers -> {
                    remindedPassengers = true
                    lastSpokeAt = now
                    "아직 차 안에 탑승자가 있습니다. 모두 함께 가드레일 밖으로 대피하세요."
                }
                else -> repeatIfIgnored(now)
            }
            else -> null
        }
    }

    // 반응이 없으면 같은 안내를 최대 두 번 더 한다
    private fun repeatIfIgnored(now: Long): String? {
        if (repeats >= 2 || now - lastSpokeAt < repeatMs) return null
        repeats++; lastSpokeAt = now
        return SPEECH.getValue(step)
    }

    fun next(now: Long): String? = when (step) {
        Step.CONFIRM -> go(Step.HAZARD, now)
        Step.HAZARD -> go(Step.TRUNK, now)
        Step.TRUNK -> go(Step.EVACUATE, now)
        Step.EVACUATE -> go(Step.REPORT, now)
        else -> null
    }

    fun startManually(now: Long): String? = if (step == Step.IDLE) go(Step.HAZARD, now) else null

    fun confirmLeftSec(now: Long) = ((confirmMs - (now - stepSince) + 999) / 1000).coerceAtLeast(0)

    // 서비스가 재시작돼도 진행 중이던 단계를 잇는다
    fun restore(saved: Step, since: Long) { if (step == Step.IDLE) { step = saved; stepSince = since; lastSpokeAt = since } }

    fun reset() { step = Step.IDLE; remindedPassengers = false }

    fun dismiss() { reset(); armed = false }

    private fun go(next: Step, now: Long): String {
        step = next; stepSince = now; lastSpokeAt = now; repeats = 0
        return SPEECH.getValue(next)
    }

    companion object {
        val SPEECH = mapOf(
            Step.CONFIRM to "고속도로 정차가 감지되었습니다. 5초 뒤 대피 안내를 시작합니다.",
            Step.HAZARD to "지금 바로 비상등을 켜세요.",
            Step.TRUNK to "트렁크를 열어 뒤차에 정차 사실을 알리세요.",
            Step.EVACUATE to "휴대폰을 챙겨 차 뒤쪽이 아닌, 가드레일 밖 안전한 곳으로 모두 대피하세요.",
            Step.REPORT to "대피 후 112나 한국도로공사 1588-2504로 신고하세요. 무료 긴급견인을 받을 수 있습니다.",
        )
    }
}
