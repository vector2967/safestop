package kr.safestop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorTest {
    private val a = Advisor()
    private val h = 3_600_000L

    @Test fun lowBatteryOnHighwayAdvisesOnceThenCoolsDown() {
        assertEquals(Advice.LOW_ENERGY, a.update(CarState(speedKmh = 100f, socPct = 12), 0))
        assertNull(a.update(CarState(speedKmh = 100f, socPct = 11), 60_000))
        assertEquals(Advice.LOW_ENERGY, a.update(CarState(speedKmh = 100f, socPct = 10), 31 * 60_000L))
    }

    @Test fun lowFuelCounts() = assertEquals(Advice.LOW_ENERGY, a.update(CarState(speedKmh = 90f, fuelPct = 8), 0))

    @Test fun noAdviceWhenSlowOrEnough() {
        assertNull(a.update(CarState(speedKmh = 30f, socPct = 5), 0))
        assertNull(a.update(CarState(speedKmh = 100f, socPct = 50, fuelPct = 50), 0))
    }

    @Test fun longDriveAfterTwoHours() {
        a.update(CarState(speedKmh = 100f), 0)
        assertNull(a.update(CarState(speedKmh = 100f), h))
        assertEquals(Advice.LONG_DRIVE, a.update(CarState(speedKmh = 100f), 2 * h))
    }

    @Test fun tenMinuteRestResetsDrivingTime() {
        a.update(CarState(speedKmh = 100f), 0)
        a.update(CarState(parkedOrNeutral = true), h)
        a.update(CarState(parkedOrNeutral = true), h + 11 * 60_000L)
        a.update(CarState(speedKmh = 100f), h + 12 * 60_000L)
        assertNull(a.update(CarState(speedKmh = 100f), 2 * h + 30 * 60_000L))
    }

    @Test fun lowEnergyAdviceExpiresAfterCharging() {
        assertTrue(a.stillValid(Advice.LOW_ENERGY, CarState(socPct = 10)))
        assertFalse(a.stillValid(Advice.LOW_ENERGY, CarState(socPct = 80)))
    }
}
