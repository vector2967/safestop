package kr.safestop

enum class Step { IDLE, HAZARD, TRUNK, EVACUATE, REPORT }

data class CarState(
    val speedKmh: Float = 0f,
    val parkedOrNeutral: Boolean = false,
    val driverDoorOpen: Boolean = false,
    val trunkOpen: Boolean = false,
    val occupiedSeats: Int = 1,
    val airbagDeployed: Boolean = false,
    val speedLimitKmh: Int = 0,
)

class StopGuide(
    private val highwaySpeedKmh: Float = 80f,
    private val highwayMemoryMs: Long = 120_000,
    private val hazardAutoNextMs: Long = 8_000,
) {
    var step = Step.IDLE
        private set
    private var lastHighwayAt = Long.MIN_VALUE / 2
    private var stepSince = 0L
    private var remindedPassengers = false

    fun update(s: CarState, now: Long): String? {
        if (s.speedKmh >= highwaySpeedKmh || s.speedLimitKmh >= 100) lastHighwayAt = now
        val onHighway = now - lastHighwayAt <= highwayMemoryMs

        if (step == Step.IDLE) {
            val stopped = s.speedKmh < 1f && (s.parkedOrNeutral || s.driverDoorOpen)
            return if (s.airbagDeployed || (onHighway && stopped)) go(Step.HAZARD, now) else null
        }
        // 다시 출발하면 오탐으로 보고 해제
        if (s.speedKmh >= 10f) { reset(); return null }

        return when (step) {
            Step.HAZARD -> when {
                s.trunkOpen -> go(Step.EVACUATE, now)
                now - stepSince >= hazardAutoNextMs -> go(Step.TRUNK, now)
                else -> null
            }
            Step.TRUNK -> if (s.trunkOpen) go(Step.EVACUATE, now) else null
            Step.EVACUATE -> when {
                s.occupiedSeats == 0 -> go(Step.REPORT, now)
                s.driverDoorOpen && s.occupiedSeats > 0 && !remindedPassengers -> {
                    remindedPassengers = true
                    "아직 차 안에 탑승자가 있습니다. 모두 함께 가드레일 밖으로 대피하세요."
                }
                else -> null
            }
            else -> null
        }
    }

    fun next(now: Long): String? = when (step) {
        Step.HAZARD -> go(Step.TRUNK, now)
        Step.TRUNK -> go(Step.EVACUATE, now)
        Step.EVACUATE -> go(Step.REPORT, now)
        else -> null
    }

    fun reset() { step = Step.IDLE; remindedPassengers = false }

    private fun go(next: Step, now: Long): String {
        step = next; stepSince = now
        return SPEECH.getValue(next)
    }

    companion object {
        val SPEECH = mapOf(
            Step.HAZARD to "고속도로에 정차했습니다. 지금 바로 비상등을 켜세요.",
            Step.TRUNK to "트렁크를 열어 뒤차에 정차 사실을 알리세요.",
            Step.EVACUATE to "휴대폰을 챙겨 차 뒤쪽이 아닌, 가드레일 밖 안전한 곳으로 모두 대피하세요.",
            Step.REPORT to "대피 후 112나 한국도로공사 1588-2504로 신고하세요. 무료 긴급견인을 받을 수 있습니다.",
        )
    }
}
