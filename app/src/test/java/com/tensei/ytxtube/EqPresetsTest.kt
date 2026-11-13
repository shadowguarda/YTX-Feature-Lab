package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests for the equalizer band curve and the sleep timer countdown text. */
class EqPresetsTest {
    private val points = intArrayOf(60, 250, 1_000, 4_000, 16_000)

    @Test
    fun bandCurveHitsEachSliderPoint() {
        val gains = intArrayOf(600, -300, 0, 450, 1_200)
        points.forEachIndexed { i, hz ->
            assertEquals(gains[i], interpolateBandMb(points, gains, hz))
        }
    }

    @Test
    fun bandCurveHoldsEndValuesOutsideTheRange() {
        val gains = intArrayOf(500, 0, 0, 0, -700)
        assertEquals(500, interpolateBandMb(points, gains, 30))
        assertEquals(-700, interpolateBandMb(points, gains, 20_000))
    }

    @Test
    fun bandCurveInterpolatesBetweenPointsOnALogAxis() {
        val gains = intArrayOf(0, 1_000, 1_000, 1_000, 1_000)
        // 125 Hz is the geometric middle of 60 and 250 only approximately; it must land between.
        val mid = interpolateBandMb(points, gains, 125)
        assertTrue("expected 0 < $mid < 1000", mid in 1..999)
        // Exactly half way in log space between 250 and 1000 Hz is 500 Hz.
        val gains2 = intArrayOf(0, 0, 800, 800, 800)
        assertEquals(400, interpolateBandMb(points, gains2, 500))
    }

    @Test
    fun flatPresetIsZeroEverywhere() {
        points.forEach { hz -> assertEquals(0, AudioEffects.previewGainMb(AudioEffects.Preset.Flat, hz)) }
    }

    @Test
    fun sleepCountdownIsFormatted() {
        assertEquals("0:00", formatSleepRemaining(-5))
        assertEquals("5:00", formatSleepRemaining(300))
        assertEquals("12:34", formatSleepRemaining(754))
        assertEquals("1:00:00", formatSleepRemaining(3_600))
    }
}
