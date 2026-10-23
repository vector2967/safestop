package kr.safestop

enum class Cmd { NEXT, REPLAY, CANCEL }

object Voice {
    private val CANCEL = listOf("정상", "괜찮", "취소", "cancel", "normal", "fine", "取消", "没事", "正常")
    private val REPLAY = listOf("다시", "뭐라고", "again", "repeat", "再说", "重复")
    // 에뮬레이터 온디바이스 STT 실측: "켰어요"는 자주 틀리고 "했어요·다음·나온·열었"은 잘 잡힌다
    private val NEXT = listOf("켰", "켜졌", "열었", "열렸", "나왔", "나온", "대피", "했", "됐", "다음", "완료", "시작", "확인",
        "done", "next", "ok", "start", "yes", "好了", "下一步", "完成", "开始", "好的")
    private val NEXT_WORDS = setOf("네", "예", "응")
    private val NOT_YET = listOf("아직", "못", "안했", "안켰", "안열", "notyet", "can't", "还没", "没有")

    fun command(text: String, step: Step): Cmd? {
        val t = text.lowercase().replace(" ", "")
        return when {
            // "정상 정차"는 확인 단계에서만 취소로 본다. 대피 중에는 끄지 않는다
            step == Step.CONFIRM && CANCEL.any(t::contains) -> Cmd.CANCEL
            REPLAY.any(t::contains) -> Cmd.REPLAY
            NOT_YET.any(t::contains) -> null
            NEXT.any(t::contains) || text.trim().split(" ").firstOrNull() in NEXT_WORDS -> Cmd.NEXT
            else -> null
        }
    }
}
