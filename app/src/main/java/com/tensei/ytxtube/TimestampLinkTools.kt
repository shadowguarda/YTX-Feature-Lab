package com.tensei.ytxtube

/** A clock time such as "1:05" found inside some text, with where it sits in that text. */
data class TimestampClockHit(val start: Int, val end: Int, val ms: Long)

/**
 * Helpers for video timestamps: build a share link that starts at a position, read the start
 * time out of a link, and find "1:05"-style times in comments or descriptions so they can be
 * tapped to seek. Pure Kotlin (no android.net.Uri), so it is unit tested directly.
 */
object TimestampLinkTools {
    private val START_PARAM = Regex("[?&#](?:t|start|time_continue)=([^&#]+)")
    private val DURATION = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("(?<![\\d:])(\\d{1,3}):([0-5]\\d)(?::([0-5]\\d))?(?![\\d:])")

    /** `https://youtu.be/ID`, with `?t=SECONDS` when [positionMs] is a second or more. */
    fun buildLink(videoId: String, positionMs: Long): String {
        val seconds = positionMs.coerceAtLeast(0L) / 1000L
        val base = "https://youtu.be/$videoId"
        return if (seconds > 0) "$base?t=$seconds" else base
    }

    /** Start time in seconds from `t=`, `start=` or `time_continue=`. Accepts 90, 90s or 1h2m3s. */
    fun parseStartSeconds(url: String): Long? {
        val value = START_PARAM.find(url)?.groupValues?.get(1) ?: return null
        value.toLongOrNull()?.let { return if (it >= 0) it else null }
        val m = DURATION.matchEntire(value) ?: return null
        val groups = m.groupValues.drop(1)
        if (groups.all { it.isEmpty() }) return null
        val h = groups[0].toLongOrNull() ?: 0L
        val min = groups[1].toLongOrNull() ?: 0L
        val s = groups[2].toLongOrNull() ?: 0L
        return h * 3600 + min * 60 + s
    }

    /** "1:05" or "1:02:03" as milliseconds, or null if [text] is not a clock time. */
    fun parseClock(text: String): Long? {
        val m = CLOCK.matchEntire(text.trim()) ?: return null
        return clockMs(m)
    }

    /** Every clock time in [text], in order, with its character range. */
    fun findClockTimes(text: String): List<TimestampClockHit> =
        CLOCK.findAll(text).map { TimestampClockHit(it.range.first, it.range.last + 1, clockMs(it)) }.toList()

    private fun clockMs(m: MatchResult): Long {
        val a = m.groupValues[1].toLong()
        val b = m.groupValues[2].toLong()
        val c = m.groupValues[3].takeIf { it.isNotEmpty() }?.toLong()
        val seconds = if (c == null) a * 60 + b else a * 3600 + b * 60 + c
        return seconds * 1000L
    }
}
