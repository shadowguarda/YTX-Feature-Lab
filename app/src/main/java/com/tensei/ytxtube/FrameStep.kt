package com.tensei.ytxtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.exoplayer.SeekParameters

/**
 * Frame-by-frame stepping while paused, for checking a moment closely. Each step moves by one
 * frame of the playing video (its real frame rate when the stream reports it, 30 fps otherwise).
 * Steps are exact seeks, so on some streams a step lands a little early or late.
 */
object FrameStep {
    var enabled by mutableStateOf(false)
        private set

    fun toggle(): Boolean {
        enabled = !enabled
        PlayerBridge.exo?.setSeekParameters(if (enabled) SeekParameters.EXACT else SeekParameters.DEFAULT)
        return enabled
    }

    fun step(forward: Boolean) {
        val player = PlayerBridge.exo ?: return
        player.pause()
        player.setSeekParameters(SeekParameters.EXACT)
        val fps = player.videoFormat?.frameRate?.takeIf { it > 1f } ?: 30f
        val frameMs = (1000f / fps).toLong().coerceAtLeast(1L)
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + if (forward) frameMs else -frameMs).coerceIn(0L, duration))
    }
}
