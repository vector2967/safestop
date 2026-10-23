package kr.safestop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTest {
    // 에뮬레이터 STT가 실제로 돌려준 문장
    @Test fun recognizedPhrases() {
        assertEquals(Cmd.NEXT, Voice.command("네 했어요", Step.HAZARD))
        assertEquals(Cmd.NEXT, Voice.command("그럼 그 열었어", Step.TRUNK))
        assertEquals(Cmd.NEXT, Voice.command("다 나온 거예요", Step.EVACUATE))
        assertEquals(Cmd.NEXT, Voice.command("다음", Step.HAZARD))
        assertEquals(Cmd.REPLAY, Voice.command("다시 말해줘", Step.TRUNK))
        assertEquals(Cmd.CANCEL, Voice.command("정상이에요", Step.CONFIRM))
    }

    @Test fun cancelOnlyWhileConfirming() = assertNull(Voice.command("정상이에요", Step.EVACUATE))

    @Test fun unrelatedSpeechIgnored() {
        assertNull(Voice.command("기억이 있어요", Step.HAZARD))
        assertNull(Voice.command("그러네", Step.HAZARD))
        assertNull(Voice.command("아직 못 했어요", Step.TRUNK))
    }

    @Test fun otherLanguages() {
        assertEquals(Cmd.NEXT, Voice.command("OK done", Step.HAZARD))
        assertEquals(Cmd.REPLAY, Voice.command("请再说一遍", Step.TRUNK))
    }
}
