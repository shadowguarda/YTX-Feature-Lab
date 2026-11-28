package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AbRepeatLogic].
 */
class AbRepeatTest {

    @Test
    fun normalize_swapsWhenBIsBeforeA() {
        val range = AbRepeatLogic.normalize(aMs = 30_000L, bMs = 12_000L)
        assertEquals(AbRepeatRange(12_000L, 30_000L), range)
    }

    @Test
    fun normalize_enforcesMinimumOneSecondWhenEqual() {
        val range = AbRepeatLogic.normalize(aMs = 10_000L, bMs = 10_000L)
        assertEquals(AbRepeatRange(10_000L, 11_000L), range)
    }

    @Test
    fun normalize_enforcesMinimumOneSecondWhenTooShort() {
        val range = AbRepeatLogic.normalize(aMs = 10_000L, bMs = 10_400L)
        assertEquals(AbRepeatRange(10_000L, 11_000L), range)
    }

    @Test
    fun normalize_shiftsStartBackWhenNearVideoEnd() {
        val range = AbRepeatLogic.normalize(aMs = 19_600L, bMs = 19_900L, durationMs = 20_000L)
        assertEquals(AbRepeatRange(19_000L, 20_000L), range)
    }

    @Test
    fun normalize_clampsNegativeValuesToZero() {
        val range = AbRepeatLogic.normalize(aMs = -500L, bMs = 2_500L)
        assertEquals(AbRepeatRange(0L, 2_500L), range)
    }

    @Test
    fun shouldLoopBack_falseBeforeB() {
        assertFalse(AbRepeatLogic.shouldLoopBack(positionMs = 19_999L, aMs = 10_000L, bMs = 20_000L))
    }

    @Test
    fun shouldLoopBack_trueAtBAndInsideWindow() {
        assertTrue(AbRepeatLogic.shouldLoopBack(positionMs = 20_000L, aMs = 10_000L, bMs = 20_000L))
        assertTrue(AbRepeatLogic.shouldLoopBack(positionMs = 21_499L, aMs = 10_000L, bMs = 20_000L))
    }

    @Test
    fun shouldLoopBack_falseAtOrPastWindowEnd() {
        assertFalse(AbRepeatLogic.shouldLoopBack(positionMs = 21_500L, aMs = 10_000L, bMs = 20_000L))
        assertFalse(AbRepeatLogic.shouldLoopBack(positionMs = 45_000L, aMs = 10_000L, bMs = 20_000L))
    }

    @Test
    fun shouldLoopBack_falseBeforeA() {
        assertFalse(AbRepeatLogic.shouldLoopBack(positionMs = 5_000L, aMs = 10_000L, bMs = 20_000L))
    }

    @Test
    fun shouldLoopBack_falseForInvalidBounds() {
        assertFalse(AbRepeatLogic.shouldLoopBack(positionMs = 10_000L, aMs = 10_000L, bMs = 10_000L))
        assertFalse(AbRepeatLogic.shouldLoopBack(positionMs = 10_000L, aMs = -1L, bMs = 10_000L))
    }

    @Test
    fun formatTime_formatsMinutesAndHours() {
        assertEquals("0:00", AbRepeatLogic.formatTime(0L))
        assertEquals("0:00", AbRepeatLogic.formatTime(-1_000L))
        assertEquals("1:05", AbRepeatLogic.formatTime(65_000L))
        assertEquals("59:59", AbRepeatLogic.formatTime(3_599_000L))
        assertEquals("1:00:00", AbRepeatLogic.formatTime(3_600_000L))
        assertEquals("1:02:05", AbRepeatLogic.formatTime(3_725_000L))
    }

    @Test
    fun label_reflectsState() {
        assertEquals("Set A", AbRepeatLogic.label(null, null))
        assertEquals("A 1:05 · set B", AbRepeatLogic.label(65_000L, null))
        assertEquals("A 1:05 – B 1:42", AbRepeatLogic.label(65_000L, 102_000L))
    }

    @Test
    fun hasMediaChanged_detectsVideoSwitchOnlyWhenTracked() {
        assertFalse(AbRepeatLogic.hasMediaChanged(null, "vid1"))
        assertFalse(AbRepeatLogic.hasMediaChanged("vid1", "vid1"))
        assertTrue(AbRepeatLogic.hasMediaChanged("vid1", "vid2"))
        assertTrue(AbRepeatLogic.hasMediaChanged("vid1", null))
    }
}
