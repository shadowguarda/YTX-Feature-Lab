package com.tensei.ytxtube

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Normalized inclusive loop bounds in milliseconds. */
data class AbRepeatRange(val aMs: Long, val bMs: Long)

/**
 * Pure Kotlin A-B repeat rules with no Android or Compose dependencies.
 */
object AbRepeatLogic {

    const val MIN_LOOP_MS: Long = 1_000L
    const val LOOP_WINDOW_MS: Long = 1_500L

    /** Normalizes [aMs] and [bMs], swapping if needed and enforcing at least 1 second. */
    fun normalize(aMs: Long, bMs: Long, durationMs: Long = -1L): AbRepeatRange {
        var start = aMs.coerceAtLeast(0L)
        var end = bMs.coerceAtLeast(0L)
        if (end < start) {
            val tmp = start
            start = end
            end = tmp
        }
        if (end - start < MIN_LOOP_MS) {
            end = start + MIN_LOOP_MS
            if (durationMs > 0L && end > durationMs) {
                end = durationMs
                start = (end - MIN_LOOP_MS).coerceAtLeast(0L)
            }
        }
        return AbRepeatRange(start, end)
    }

    /** Returns true when [positionMs] is within the loop-back trigger window at [bMs]. */
    fun shouldLoopBack(positionMs: Long, aMs: Long, bMs: Long): Boolean {
        if (aMs < 0L || bMs <= aMs) return false
        return positionMs >= bMs && positionMs < bMs + LOOP_WINDOW_MS
    }

    /** Returns true when both [aMs] and [bMs] are set with a valid range. */
    fun isLoopActive(aMs: Long?, bMs: Long?): Boolean =
        aMs != null && bMs != null && bMs > aMs

    /** Returns true when the active media ID changed to a different video. */
    fun hasMediaChanged(previousMediaId: String?, currentMediaId: String?): Boolean =
        previousMediaId != null && previousMediaId != currentMediaId

    /** Formats [ms] as m:ss, or h:mm:ss when one hour or longer. */
    fun formatTime(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        val secStr = if (seconds < 10L) "0$seconds" else "$seconds"
        return if (hours > 0L) {
            val minStr = if (minutes < 10L) "0$minutes" else "$minutes"
            "$hours:$minStr:$secStr"
        } else {
            "$minutes:$secStr"
        }
    }

    /** Returns a compact UI label describing the current A-B repeat state. */
    fun label(aMs: Long?, bMs: Long?): String = when {
        aMs == null -> "Set A"
        bMs == null -> "A ${formatTime(aMs)} · set B"
        else -> "A ${formatTime(aMs)} – B ${formatTime(bMs)}"
    }
}

/**
 * Main-thread A-B repeat controller with Compose-observable state and 150 ms polling.
 */
object AbRepeat {

    private const val POLL_INTERVAL_MS: Long = 150L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var trackedMediaId: String? = null
    private var attached: Boolean = false
    private var polling: Boolean = false

    /** Loop start point in milliseconds, or null when unset. */
    var a: Long? by mutableStateOf(null)
        private set

    /** Loop end point in milliseconds, or null when unset. */
    var b: Long? by mutableStateOf(null)
        private set

    /** True only when both A and B are set. */
    val active: Boolean
        get() = a != null && b != null

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            pollOnce()
            if (polling) {
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    /** Attaches the controller and resumes polling if a loop is active. */
    fun attach() {
        runOnMain {
            attached = true
            syncPollingState()
        }
    }

    /** Detaches the controller and stops polling without clearing A-B bounds. */
    fun detach() {
        runOnMain {
            attached = false
            stopPolling()
        }
    }

    /** Marks point A at the current playback position, re-normalizing if B is set. */
    fun setA() {
        runOnMain {
            val exo = safeExo() ?: return@runOnMain
            val mediaId = safeMediaId(exo)
            if (AbRepeatLogic.hasMediaChanged(trackedMediaId, mediaId)) {
                clearInternal(mediaId)
            } else if (trackedMediaId == null) {
                trackedMediaId = mediaId
            }

            val pos = safePosition(exo) ?: return@runOnMain
            val existingB = b
            if (existingB != null) {
                val range = AbRepeatLogic.normalize(pos, existingB, safeDuration(exo))
                a = range.aMs
                b = range.bMs
                startPolling()
            } else {
                a = pos
            }
        }
    }

    /** Marks point B at the current playback position and activates the loop if A is set. */
    fun setB() {
        runOnMain {
            val currentA = a ?: return@runOnMain
            val exo = safeExo() ?: return@runOnMain
            val mediaId = safeMediaId(exo)
            if (AbRepeatLogic.hasMediaChanged(trackedMediaId, mediaId)) {
                clearInternal(mediaId)
                return@runOnMain
            }

            val pos = safePosition(exo) ?: return@runOnMain
            val range = AbRepeatLogic.normalize(currentA, pos, safeDuration(exo))
            a = range.aMs
            b = range.bMs
            startPolling()
        }
    }

    /** Clears the active A-B loop and stops polling. */
    fun clear() {
        runOnMain {
            clearInternal(null)
        }
    }

    /** Clears the loop when the active video changes to a different [mediaId]. */
    fun onMediaChanged(mediaId: String?) {
        runOnMain {
            if (AbRepeatLogic.hasMediaChanged(trackedMediaId, mediaId)) {
                clearInternal(mediaId)
            } else if (trackedMediaId == null) {
                trackedMediaId = mediaId
            }
        }
    }

    private fun pollOnce() {
        val currentA = a
        val currentB = b
        if (currentA == null || currentB == null) {
            stopPolling()
            return
        }

        val exo = safeExo() ?: return
        val mediaId = safeMediaId(exo)
        if (AbRepeatLogic.hasMediaChanged(trackedMediaId, mediaId)) {
            clearInternal(mediaId)
            return
        }

        if (!safeIsPlaying(exo)) return
        val pos = safePosition(exo) ?: return
        if (AbRepeatLogic.shouldLoopBack(pos, currentA, currentB)) {
            safeSeekTo(exo, currentA)
        }
    }

    private fun clearInternal(newMediaId: String?) {
        a = null
        b = null
        trackedMediaId = newMediaId
        stopPolling()
    }

    private fun syncPollingState() {
        if (attached && active) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    private fun startPolling() {
        if (!active || polling) return
        polling = true
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun stopPolling() {
        polling = false
        mainHandler.removeCallbacks(pollRunnable)
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    private fun safeExo(): androidx.media3.exoplayer.ExoPlayer? = try {
        PlayerBridge.exo
    } catch (_: Throwable) {
        null
    }

    private fun safePosition(exo: androidx.media3.exoplayer.ExoPlayer): Long? = try {
        exo.currentPosition.coerceAtLeast(0L)
    } catch (_: Throwable) {
        null
    }

    private fun safeDuration(exo: androidx.media3.exoplayer.ExoPlayer): Long = try {
        exo.duration
    } catch (_: Throwable) {
        -1L
    }

    private fun safeIsPlaying(exo: androidx.media3.exoplayer.ExoPlayer): Boolean = try {
        exo.isPlaying
    } catch (_: Throwable) {
        false
    }

    private fun safeMediaId(exo: androidx.media3.exoplayer.ExoPlayer): String? = try {
        exo.currentMediaItem?.mediaId
    } catch (_: Throwable) {
        null
    }

    private fun safeSeekTo(exo: androidx.media3.exoplayer.ExoPlayer, positionMs: Long) {
        try {
            exo.seekTo(positionMs)
        } catch (_: Throwable) {
            // Player may have been released concurrently; ignore safely.
        }
    }
}
