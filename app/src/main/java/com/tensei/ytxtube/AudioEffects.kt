package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * System-level audio effects for every video and song: a preset equalizer and a loudness boost.
 *
 * Effects hook onto the player's audio session and follow the player automatically (see
 * [PlayerBridge.exo]). They are only created while a preset or boost is switched on, so with the
 * default settings this object costs nothing. Every call is wrapped in try/catch because some
 * devices do not ship the effect or throw when a session is torn down.
 */
object AudioEffects {
    enum class Preset(val label: String) {
        Flat("Flat"),
        BassBoost("Bass boost"),
        TrebleBoost("Treble boost"),
        Vocal("Vocal"),
        Cinema("Cinema"),
    }

    /** Loudness boost steps in millibels (100 mB = 1 dB). Index 0 is off. */
    val boostSteps = listOf(0, 300, 600, 900, 1200)

    private const val PREFS = "audio_effects"
    private const val KEY_PRESET = "preset"
    private const val KEY_BOOST = "boost_index"

    private var prefs: SharedPreferences? = null
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var attachedTo: ExoPlayer? = null
    private var sessionId = 0

    private val sessionListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            rebuild(audioSessionId)
        }
    }

    var preset by mutableStateOf(Preset.Flat)
        private set

    var boostIndex by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        preset = Preset.entries.getOrElse(p.getInt(KEY_PRESET, 0)) { Preset.Flat }
        boostIndex = p.getInt(KEY_BOOST, 0).coerceIn(0, boostSteps.lastIndex)
    }

    /** Called by [PlayerBridge] when the live player is created. */
    fun attach(player: ExoPlayer?) {
        if (player == null) return
        attachedTo = player
        player.addListener(sessionListener)
        rebuild(player.audioSessionId)
    }

    /** Called by [PlayerBridge] when the live player is replaced or released. */
    fun detach(player: ExoPlayer?) {
        player?.removeListener(sessionListener)
        if (player == null || player === attachedTo) {
            attachedTo = null
            sessionId = 0
            release()
        }
    }

    fun setPreset(value: Preset) {
        preset = value
        prefs?.edit()?.putInt(KEY_PRESET, value.ordinal)?.apply()
        refresh()
    }

    fun setBoost(index: Int) {
        boostIndex = index.coerceIn(0, boostSteps.lastIndex)
        prefs?.edit()?.putInt(KEY_BOOST, boostIndex)?.apply()
        refresh()
    }

    private fun wanted() = preset != Preset.Flat || boostIndex > 0

    private fun rebuild(id: Int) {
        release()
        sessionId = id
        refresh()
    }

    /** Creates the effects when something is switched on, frees them when everything is off. */
    private fun refresh() {
        if (!wanted()) {
            release()
            return
        }
        // Session id 0 is the global output mix; never attach effects to it.
        if (sessionId <= 0) return
        if (equalizer == null) {
            equalizer = try { Equalizer(0, sessionId) } catch (_: Exception) { null }
        }
        if (loudness == null) {
            loudness = try { LoudnessEnhancer(sessionId) } catch (_: Exception) { null }
        }
        applyEqualizer()
        applyLoudness()
    }

    private fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { loudness?.release() } catch (_: Exception) {}
        equalizer = null
        loudness = null
    }

    private fun applyLoudness() {
        val l = loudness ?: return
        try {
            val gain = boostSteps[boostIndex]
            l.setTargetGain(gain)
            l.enabled = gain > 0
        } catch (_: Exception) {
        }
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return
        try {
            if (preset == Preset.Flat) {
                eq.enabled = false
                return
            }
            val range = eq.bandLevelRange // [min, max] in millibels
            val min = range[0].toInt()
            val max = range[1].toInt()
            for (band in 0 until eq.numberOfBands) {
                // Centre frequency comes back in milliHertz.
                val hz = eq.getCenterFreq(band.toShort()) / 1000
                val level = curve(preset, hz).coerceIn(min, max)
                eq.setBandLevel(band.toShort(), level.toShort())
            }
            eq.enabled = true
        } catch (_: Exception) {
        }
    }

    /** Gain in millibels for a preset at a given frequency. */
    private fun curve(preset: Preset, hz: Int): Int = when (preset) {
        Preset.Flat -> 0
        Preset.BassBoost -> when {
            hz <= 250 -> 600
            hz <= 1000 -> 600 * (1000 - hz) / 750
            else -> 0
        }
        Preset.TrebleBoost -> when {
            hz >= 6000 -> 600
            hz >= 2000 -> 600 * (hz - 2000) / 4000
            else -> 0
        }
        Preset.Vocal -> when {
            hz < 120 -> -300
            hz in 1000..4000 -> 450
            hz in 400 until 1000 -> 200
            else -> 0
        }
        Preset.Cinema -> when {
            hz <= 120 -> 500
            hz in 2000..5000 -> 250
            else -> 0
        }
    }
}
