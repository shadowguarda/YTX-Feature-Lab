package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimestampLinkToolsTest {

    @Test
    fun buildLinkAddsTimeOnlyWhenAtLeastOneSecond() {
        assertEquals("https://youtu.be/abc123", TimestampLinkTools.buildLink("abc123", 0))
        assertEquals("https://youtu.be/abc123", TimestampLinkTools.buildLink("abc123", 999))
        assertEquals("https://youtu.be/abc123?t=75", TimestampLinkTools.buildLink("abc123", 75_900))
        assertEquals("https://youtu.be/abc123", TimestampLinkTools.buildLink("abc123", -5_000))
    }

    @Test
    fun parseStartSecondsReadsCommonForms() {
        assertEquals(90L, TimestampLinkTools.parseStartSeconds("https://youtu.be/x?t=90"))
        assertEquals(90L, TimestampLinkTools.parseStartSeconds("https://youtu.be/x?t=90s"))
        assertEquals(3723L, TimestampLinkTools.parseStartSeconds("https://youtu.be/x?t=1h2m3s"))
        assertEquals(300L, TimestampLinkTools.parseStartSeconds("https://www.youtube.com/watch?v=x&t=5m"))
        assertEquals(42L, TimestampLinkTools.parseStartSeconds("https://www.youtube.com/watch?v=x&start=42"))
        assertEquals(15L, TimestampLinkTools.parseStartSeconds("https://www.youtube.com/watch?v=x#t=15"))
    }

    @Test
    fun parseStartSecondsRejectsMissingOrBadValues() {
        assertNull(TimestampLinkTools.parseStartSeconds("https://youtu.be/x"))
        assertNull(TimestampLinkTools.parseStartSeconds("https://youtu.be/x?t=abc"))
        assertNull(TimestampLinkTools.parseStartSeconds("https://youtu.be/x?t=-5"))
    }

    @Test
    fun parseClockHandlesMinutesAndHours() {
        assertEquals(65_000L, TimestampLinkTools.parseClock("1:05"))
        assertEquals(3_723_000L, TimestampLinkTools.parseClock("1:02:03"))
        assertEquals(750_000L, TimestampLinkTools.parseClock(" 12:30 "))
        assertNull(TimestampLinkTools.parseClock("1:75"))
        assertNull(TimestampLinkTools.parseClock("hello"))
        assertNull(TimestampLinkTools.parseClock("12"))
    }

    @Test
    fun findClockTimesReturnsEachMatchWithItsRange() {
        val text = "Intro 0:00, best part at 12:34 and the end 1:02:03!"
        val hits = TimestampLinkTools.findClockTimes(text)
        assertEquals(listOf(0L, 754_000L, 3_723_000L), hits.map { it.ms })
        hits.forEach { assertEquals(true, text.substring(it.start, it.end).contains(':')) }
        assertEquals("12:34", text.substring(hits[1].start, hits[1].end))
        assertEquals("1:02:03", text.substring(hits[2].start, hits[2].end))
    }

    @Test
    fun findClockTimesIgnoresNumbersThatAreNotClockTimes() {
        assertEquals(emptyList<TimestampClockHit>(), TimestampLinkTools.findClockTimes("Score 3-2, ratio 10:100, port 8080"))
    }
}
