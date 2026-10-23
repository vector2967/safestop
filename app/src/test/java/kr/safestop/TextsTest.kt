package kr.safestop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextsTest {
    @Test fun everyLanguageCoversEveryStep() = Lang.entries.forEach { l ->
        val c = l.copy
        assertEquals(l.name, Step.entries.toSet() - Step.IDLE, c.speech.keys)
        assertEquals(l.name, Step.entries.toSet(), c.screen.keys)
        assertEquals(l.name, 4, c.stepNames.size)
        c.peopleLeft.format(2)
        c.lowBattery.format(6.0, "망향휴게소"); c.lowFuel.format(6.0, "망향휴게소"); c.longDrive.format(6.0, "망향휴게소")
        c.month.format(3L, 5L, 1L, 0L)
    }

    @Test fun nightAddsWarningOnlyToEvacuate() {
        val g = StopGuide()
        g.update(CarState(speedKmh = 100f, dark = true), 0)
        g.update(CarState(parkedOrNeutral = true, dark = true), 10_000)
        assertFalse(g.speech(Step.HAZARD)!!.contains(Texts.KO.night))
        assertTrue(g.speech(Step.EVACUATE)!!.endsWith(Texts.KO.night))
        g.lang = Lang.EN
        assertTrue(g.speech(Step.EVACUATE)!!.endsWith(Texts.EN.night))
        g.lang = Lang.ZH; g.voiceLang = Lang.EN
        assertEquals(Texts.EN.speech[Step.TRUNK], g.speech(Step.TRUNK))
    }

    @Test fun idlingWarnsCombustionCarsOnly() {
        val w = IdleWatch()
        val ice = CarState(parkedOrNeutral = true, engineOn = true, fuelPct = 50)
        assertFalse(w.update(ice, 0))
        assertTrue(w.update(ice, 30 * 60_000L))
        assertFalse(w.update(ice, 40 * 60_000L))
        assertTrue(w.update(ice, 90 * 60_000L))
        val ev = IdleWatch()
        val evState = CarState(parkedOrNeutral = true, engineOn = true, socPct = 80)
        ev.update(evState, 0)
        assertFalse(ev.update(evState, 60 * 60_000L))
    }
}
