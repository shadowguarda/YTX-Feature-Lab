package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import java.util.TimeZone

enum class WatchLimitState { Off, Under, Nearing, Reached }

/** Pure rules for a daily watch-time limit. No Android imports, so it is unit tested directly. */
object WatchLimitLogic {
    const val NEARING_FRACTION = 0.9
    const val MAX_LIMIT_MINUTES = 24 * 60
    private const val DAY_MS = 86_400_000L

    /** Longest single amount accepted by [accumulate]; guards against a bad clock or a stuck timer. */
    const val MAX_ADD_MS = 60 * 60 * 1000L

    /** Whole days since the epoch in the viewer's local time, given the zone's UTC offset. */
    fun dayKey(epochMs: Long, utcOffsetMs: Long): Long = Math.floorDiv(epochMs + utcOffsetMs, DAY_MS)

    fun clampLimit(minutes: Int): Int = minutes.coerceIn(0, MAX_LIMIT_MINUTES)

    fun state(watchedMs: Long, limitMinutes: Int): WatchLimitState {
        if (limitMinutes <= 0) return WatchLimitState.Off
        val limitMs = limitMinutes * 60_000L
        return when {
            watchedMs >= limitMs -> WatchLimitState.Reached
            watchedMs >= (limitMs * NEARING_FRACTION).toLong() -> WatchLimitState.Nearing
            else -> WatchLimitState.Under
        }
    }

    /** Time left today, or null when no limit is set. Never negative. */
    fun remainingMs(watchedMs: Long, limitMinutes: Int): Long? =
        if (limitMinutes <= 0) null else (limitMinutes * 60_000L - watchedMs).coerceAtLeast(0L)

    /**
     * Adds [addMs] to the running total. When [today] differs from [day] a new day has started,
     * so the total restarts from [addMs]. Returns the (day, watchedMs) pair to store.
     */
    fun accumulate(day: Long, watchedMs: Long, today: Long, addMs: Long): Pair<Long, Long> {
        val add = addMs.coerceIn(0L, MAX_ADD_MS)
        return if (today != day) today to add else day to (watchedMs + add)
    }

    /** "2h 05m", "45m" or "0m". */
    fun format(ms: Long): String {
        val totalMinutes = ms.coerceAtLeast(0L) / 60_000L
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${if (m < 10) "0$m" else "$m"}m" else "${m}m"
    }
}

/**
 * Today's watch time against a daily limit. Feed it with [addWatched] from a playback tick and
 * show [state] / [remainingMs] in the UI. It only reports; it never stops playback by itself.
 */
object WatchLimit {
    private const val PREFS = "watch_limit"
    private const val KEY_LIMIT = "limit_minutes"
    private const val KEY_DAY = "day"
    private const val KEY_WATCHED = "watched_ms"
    private const val SAVE_EVERY_MS = 10_000L

    private var prefs: SharedPreferences? = null
    private var day = 0L
    private var lastSaved = 0L

    /** Daily limit in minutes, or 0 for no limit. */
    var limitMinutes by mutableIntStateOf(0)
        private set

    /** Watch time recorded for the current day, in milliseconds. */
    var watchedMs by mutableLongStateOf(0L)
        private set

    val state: WatchLimitState get() = WatchLimitLogic.state(watchedMs, limitMinutes)
    val remainingMs: Long? get() = WatchLimitLogic.remainingMs(watchedMs, limitMinutes)

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        limitMinutes = WatchLimitLogic.clampLimit(p.getInt(KEY_LIMIT, 0))
        day = p.getLong(KEY_DAY, today())
        watchedMs = p.getLong(KEY_WATCHED, 0L).coerceAtLeast(0L)
        rollDay()
    }

    fun setLimit(minutes: Int) {
        limitMinutes = WatchLimitLogic.clampLimit(minutes)
        prefs?.edit()?.putInt(KEY_LIMIT, limitMinutes)?.apply()
    }

    /** Records [ms] of playback. Saves to disk at most every 10 seconds. */
    fun addWatched(ms: Long) {
        val (d, total) = WatchLimitLogic.accumulate(day, watchedMs, today(), ms)
        day = d
        watchedMs = total
        val now = System.currentTimeMillis()
        if (now - lastSaved >= SAVE_EVERY_MS) save(now)
    }

    fun resetToday() {
        day = today()
        watchedMs = 0L
        save(System.currentTimeMillis())
    }

    private fun rollDay() {
        val t = today()
        if (t != day) {
            day = t
            watchedMs = 0L
            save(System.currentTimeMillis())
        }
    }

    private fun save(now: Long) {
        lastSaved = now
        prefs?.edit()?.putLong(KEY_DAY, day)?.putLong(KEY_WATCHED, watchedMs)?.apply()
    }

    private fun today(): Long {
        val now = System.currentTimeMillis()
        return WatchLimitLogic.dayKey(now, TimeZone.getDefault().getOffset(now).toLong())
    }
}
