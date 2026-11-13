package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The playback service and the UI live in the same process. This gives small playback tools
 * (sleep timer, skip silence) a way to reach the real ExoPlayer, which the UI otherwise only
 * sees through a MediaController. Always touch the player from the main thread.
 */
object PlayerBridge {
    /**
     * Set by PlaybackService on the main thread. Replacing the player also moves the network
     * recovery listener (see PlaybackRecovery), the stats listener (see PlayerStats) and the audio
     * effects (see AudioEffects) so they always follow the live player.
     */
    @Volatile
    var exo: ExoPlayer? = null
        set(value) {
            val old = field
            field = value
            if (old !== value) {
                PlaybackRecovery.detach(old)
                PlayerStats.detach(old)
                PlaybackRecovery.attach(value)
                PlayerStats.attach(value)
                // Audio effects hook onto the player's audio session; attach(null) releases them.
                AudioEffects.attach(value)
            }
        }
}

/** Trims silent stretches out of audio, handy for podcasts, lectures and talk-heavy videos. */
object SkipSilence {
    private const val PREFS = "playback_tools"
    private const val KEY = "skip_silence"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean(KEY, false)
    }

    /** Flips the setting, applies it to the live player and returns the new value. */
    fun toggle(): Boolean {
        enabled = !enabled
        prefs?.edit()?.putBoolean(KEY, enabled)?.apply()
        applyTo(PlayerBridge.exo)
        return enabled
    }

    fun applyTo(player: ExoPlayer?) {
        player?.setSkipSilenceEnabled(enabled)
    }
}

/**
 * Pauses playback after a chosen number of minutes, or at the end of the current video.
 * The countdown is wall-clock based, so it stays correct while the app is in the background.
 */
@OptIn(UnstableApi::class)
object SleepTimer {
    /** Minute presets offered in the sleep timer sheet. */
    val presets = listOf(5, 15, 30, 45, 60)
    private val steps = listOf(0) + presets
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var armedPlayer: ExoPlayer? = null

    private val endListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                endReached = true
            } else if (playWhenReady && endReached) {
                // The user pressed play again after the video ended: the timer is done.
                set(0)
            }
        }
    }

    private var endReached by mutableStateOf(false)

    /** Minutes the timer was set to, or 0 when it is off or set to the end of the video. */
    var minutes by mutableIntStateOf(0)
        private set

    /** Whole seconds left on a minute timer, or 0 when none is counting down. */
    var remainingSeconds by mutableIntStateOf(0)
        private set

    /** True while playback is set to pause when the current video ends. */
    var endOfVideo by mutableStateOf(false)
        private set

    val active: Boolean get() = minutes > 0 || (endOfVideo && !endReached)

    /** Steps to the next preset (Off, 5, 15, 30, 45, 60, then Off again) and returns it. */
    fun cycle(): Int {
        if (endOfVideo) {
            set(0)
            return 0
        }
        val next = steps[(steps.indexOf(minutes) + 1) % steps.size]
        set(next)
        return next
    }

    fun set(min: Int) {
        clear()
        if (min <= 0) return
        minutes = min
        remainingSeconds = min * 60
        job = scope.launch {
            val endAt = SystemClock.elapsedRealtime() + min * 60_000L
            while (true) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) break
                remainingSeconds = ((left + 999L) / 1000L).toInt()
                delay(minOf(1_000L, left))
            }
            PlayerBridge.exo?.pause()
            minutes = 0
            remainingSeconds = 0
        }
    }

    /**
     * Called when playback ended while [endOfVideo] was set. The timer stays armed (so autoplay
     * keeps seeing it) until the user presses play again.
     */
    fun markEndReached() {
        if (endOfVideo) endReached = true
    }

    /** Pauses at the end of the current video and keeps autoplay from starting the next one. */
    fun setEndOfVideo() {
        clear()
        val player = PlayerBridge.exo ?: return
        endOfVideo = true
        armedPlayer = player
        player.addListener(endListener)
        player.pauseAtEndOfMediaItems = true
    }

    private fun clear() {
        job?.cancel()
        job = null
        armedPlayer?.let {
            it.removeListener(endListener)
            it.pauseAtEndOfMediaItems = false
        }
        armedPlayer = null
        endOfVideo = false
        endReached = false
        minutes = 0
        remainingSeconds = 0
    }

    /** Short text for settings rows: Off, End of video, or the time left. */
    fun statusLabel(): String = when {
        endOfVideo && !endReached -> "End of video"
        minutes > 0 -> formatSleepRemaining(remainingSeconds)
        else -> "Off"
    }
}

/** 754 -> "12:34", 3600 -> "1:00:00". */
internal fun formatSleepRemaining(totalSeconds: Int): String {
    val t = totalSeconds.coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val sec = t % 60
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(java.util.Locale.ROOT, "%d:%02d", m, sec)
}
