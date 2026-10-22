package kr.safestop

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaviStopZoneTest {
    @Test fun tollWithinCumulative500m() {
        assertTrue(GuardService.stopZoneAhead(listOf(TBTInfo(TurnType.TURN_LEFT, 80.0, ""), TBTInfo(TurnType.TURN_TOLL, 300.0, ""))))
    }

    @Test fun tollBeyond500mCountsEarlierSegments() {
        assertFalse(GuardService.stopZoneAhead(listOf(TBTInfo(TurnType.TURN_LEFT, 80.0, ""), TBTInfo(TurnType.TURN_RIGHT, 380.0, ""), TBTInfo(TurnType.TURN_TOLL, 100.0, ""))))
    }
}
