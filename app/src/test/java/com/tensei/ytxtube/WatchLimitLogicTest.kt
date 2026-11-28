package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchLimitLogicTest {
    private val minute = 60_000L

    @Test
    fun stateFollowsTheLimit() {
        assertEquals(WatchLimitState.Off, WatchLimitLogic.state(10 * minute, 0))
        assertEquals(WatchLimitState.Under, WatchLimitLogic.state(10 * minute, 60))
        assertEquals(WatchLimitState.Nearing, WatchLimitLogic.state(54 * minute, 60))
        assertEquals(WatchLimitState.Reached, WatchLimitLogic.state(60 * minute, 60))
        assertEquals(WatchLimitState.Reached, WatchLimitLogic.state(90 * minute, 60))
    }

    @Test
    fun remainingIsNullWhenOffAndNeverNegative() {
        assertNull(WatchLimitLogic.remainingMs(5 * minute, 0))
        assertEquals(25 * minute, WatchLimitLogic.remainingMs(5 * minute, 30))
        assertEquals(0L, WatchLimitLogic.remainingMs(99 * minute, 30))
    }

    @Test
    fun accumulateAddsWithinADayAndRestartsOnANewDay() {
        assertEquals(10L to 3 * minute, WatchLimitLogic.accumulate(10, 2 * minute, 10, minute))
        assertEquals(11L to minute, WatchLimitLogic.accumulate(10, 50 * minute, 11, minute))
    }

    @Test
    fun accumulateIgnoresNegativeAndHugeAmounts() {
        assertEquals(5L to 100L, WatchLimitLogic.accumulate(5, 100L, 5, -500L))
        assertEquals(5L to WatchLimitLogic.MAX_ADD_MS, WatchLimitLogic.accumulate(5, 0L, 5, Long.MAX_VALUE))
    }

    @Test
    fun dayKeyUsesTheLocalZoneOffset() {
        val utcMidnight = 20_000L * 86_400_000L
        assertEquals(20_000L, WatchLimitLogic.dayKey(utcMidnight, 0L))
        // 23:00 UTC is already the next day in India (UTC+5:30).
        val lateUtc = utcMidnight + 23 * 3_600_000L
        assertEquals(20_000L, WatchLimitLogic.dayKey(lateUtc, 0L))
        assertEquals(20_001L, WatchLimitLogic.dayKey(lateUtc, 19_800_000L))
        // Before the epoch rounds down, not toward zero.
        assertEquals(-1L, WatchLimitLogic.dayKey(-1L, 0L))
    }

    @Test
    fun formatAndClamp() {
        assertEquals("0m", WatchLimitLogic.format(-5L))
        assertEquals("45m", WatchLimitLogic.format(45 * minute))
        assertEquals("1h 05m", WatchLimitLogic.format(65 * minute))
        assertEquals("2h 00m", WatchLimitLogic.format(120 * minute))
        assertEquals(0, WatchLimitLogic.clampLimit(-3))
        assertEquals(WatchLimitLogic.MAX_LIMIT_MINUTES, WatchLimitLogic.clampLimit(99_999))
    }
}
