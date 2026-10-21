package kr.safestop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StopGuideTest {
    private val g = StopGuide()

    @Test fun highwayStopRunsAllSteps() {
        g.update(CarState(speedKmh = 100f, occupiedSeats = 2), 0)
        assertNotNull(g.update(CarState(speedKmh = 0f, parkedOrNeutral = true, occupiedSeats = 2), 30_000))
        assertEquals(Step.CONFIRM, g.step)
        g.update(CarState(parkedOrNeutral = true, occupiedSeats = 2), 35_000)
        assertEquals(Step.HAZARD, g.step)
        g.update(CarState(parkedOrNeutral = true, occupiedSeats = 2), 43_000)
        assertEquals(Step.TRUNK, g.step)
        g.update(CarState(parkedOrNeutral = true, trunkOpen = true, occupiedSeats = 2), 44_000)
        assertEquals(Step.EVACUATE, g.step)
        assertNotNull(g.update(CarState(parkedOrNeutral = true, driverDoorOpen = true, occupiedSeats = 1), 45_000))
        assertNull(g.update(CarState(parkedOrNeutral = true, driverDoorOpen = true, occupiedSeats = 1), 46_000))
        g.update(CarState(parkedOrNeutral = true, occupiedSeats = 0), 47_000)
        assertEquals(Step.REPORT, g.step)
    }

    @Test fun repeatsIgnoredStepTwiceAtMost() {
        g.update(CarState(speedKmh = 100f), 0)
        g.update(CarState(parkedOrNeutral = true), 10_000)
        g.update(CarState(parkedOrNeutral = true), 15_000)
        g.update(CarState(parkedOrNeutral = true), 23_000)
        assertEquals(Step.TRUNK, g.step)
        assertNull(g.update(CarState(parkedOrNeutral = true), 30_000))
        assertNotNull(g.update(CarState(parkedOrNeutral = true), 43_000))
        assertNotNull(g.update(CarState(parkedOrNeutral = true), 63_000))
        assertNull(g.update(CarState(parkedOrNeutral = true), 83_000))
    }

    @Test fun speedBlipInParkDoesNotCancel() {
        g.update(CarState(speedKmh = 100f), 0)
        g.update(CarState(parkedOrNeutral = true), 10_000)
        g.update(CarState(speedKmh = 19f, parkedOrNeutral = true), 11_000)
        assertEquals(Step.CONFIRM, g.step)
    }

    @Test fun doorOpenSkipsConfirmWait() {
        g.update(CarState(speedKmh = 100f), 0)
        g.update(CarState(parkedOrNeutral = true), 10_000)
        g.update(CarState(parkedOrNeutral = true, driverDoorOpen = true), 11_000)
        assertEquals(Step.HAZARD, g.step)
    }

    @Test fun stopNearRestAreaDoesNotTrigger() {
        g.update(CarState(speedKmh = 100f), 0)
        assertNull(g.update(CarState(parkedOrNeutral = true, nearStopZone = true), 10_000))
        assertEquals(Step.IDLE, g.step)
    }

    @Test fun cityStopDoesNotTrigger() {
        g.update(CarState(speedKmh = 50f), 0)
        assertNull(g.update(CarState(speedKmh = 0f, parkedOrNeutral = true), 10_000))
        assertEquals(Step.IDLE, g.step)
    }

    @Test fun trafficJamInDriveDoesNotTrigger() {
        g.update(CarState(speedKmh = 100f), 0)
        assertNull(g.update(CarState(speedKmh = 0f), 10_000))
    }

    @Test fun airbagTriggersAnywhere() {
        assertNotNull(g.update(CarState(airbagDeployed = true), 0))
        assertEquals(Step.HAZARD, g.step)
    }

    @Test fun drivingAgainCancels() {
        g.update(CarState(speedKmh = 100f), 0)
        g.update(CarState(parkedOrNeutral = true), 10_000)
        g.update(CarState(speedKmh = 30f), 20_000)
        assertEquals(Step.IDLE, g.step)
    }
}
