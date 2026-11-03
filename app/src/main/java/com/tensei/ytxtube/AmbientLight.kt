package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Live ambient light for the watch page.
 *
 * The technique (read a tiny frame off the playing video every couple of seconds, box-blur it,
 * saturate it, cross-fade it in and fade it into the page colour) is adapted from MeeTube's
 * AmbientGlow, https://github.com/yashoncode/meetube, MIT licensed, derived from SmartTube
 * (Copyright (c) 2020-present yuliskov). See THIRD_PARTY_NOTICES.md.
 */

/** On/off switch for the live ambient glow. Stored with the other appearance settings. */
object AmbientSettings {
    private const val PREFS = "appearance"
    private const val KEY = "ambient_light"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean(KEY, true)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY, value)?.apply()
    }
}

private const val FRAME_W = 48
private const val FRAME_H = 27
private const val BLUR_RADIUS = 3
private const val SAMPLE_INTERVAL_MS = 2_000L
private const val FADE_MS = 1_200
private const val GLOW_ALPHA = 0.82f

/** The two most recent blurred video frames and the cross-fade between them. */
class AmbientFrames {
    var current by mutableStateOf<ImageBitmap?>(null)
        private set
    var previous by mutableStateOf<ImageBitmap?>(null)
        private set

    /** 0 = show [previous], 1 = show [current]. */
    val mix = Animatable(1f)
    private var fadeJob: Job? = null

    fun push(frame: ImageBitmap, scope: CoroutineScope) {
        previous = current
        current = frame
        fadeJob?.cancel()
        fadeJob = scope.launch {
            mix.snapTo(0f)
            mix.animateTo(1f, tween(if (previous == null) FADE_MS / 2 else FADE_MS))
        }
    }

    fun clear() {
        fadeJob?.cancel()
        current = null
        previous = null
    }
}

/**
 * Samples the playing video while [active] and exposes the blurred frames. Nothing runs while the
 * video surface is not on screen, so a collapsed player or a background app costs nothing.
 */
@Composable
fun rememberAmbientFrames(active: Boolean): AmbientFrames {
    val frames = remember { AmbientFrames() }
    LaunchedEffect(active) {
        if (!active) {
            frames.clear()
            return@LaunchedEffect
        }
        while (isActive) {
            val view = SharedVideoTexture.view?.get()
            if (view != null && view.isAvailable && view.isShown) {
                val raw = runCatching { view.getBitmap(FRAME_W, FRAME_H) }.getOrNull()
                if (raw != null) {
                    val blurred = withContext(Dispatchers.Default) { blur(raw) }
                    frames.push(blurred.asImageBitmap(), this)
                }
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }
    return frames
}

/** Two separable box-blur passes: at 48x27 this is free and reads as a wide gaussian. */
private fun blur(source: Bitmap): Bitmap {
    val w = source.width
    val h = source.height
    val px = IntArray(w * h)
    val tmp = IntArray(w * h)
    source.getPixels(px, 0, w, 0, 0, w, h)
    repeat(2) {
        boxPass(px, tmp, len = w, lines = h, step = 1, stride = w)
        boxPass(tmp, px, len = h, lines = w, step = w, stride = 1)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun boxPass(src: IntArray, dst: IntArray, len: Int, lines: Int, step: Int, stride: Int) {
    for (line in 0 until lines) {
        val base = line * stride
        for (i in 0 until len) {
            var r = 0
            var g = 0
            var b = 0
            var n = 0
            val from = maxOf(0, i - BLUR_RADIUS)
            val to = minOf(len - 1, i + BLUR_RADIUS)
            for (k in from..to) {
                val c = src[base + k * step]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
            dst[base + i * step] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
}

private val Saturate: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) })

/**
 * Paints the glow from the top of the page: the video's colours spread down and fade into
 * [pageColor], so the title area sits on a soft light cast by the picture.
 */
fun DrawScope.drawAmbientGlow(frames: AmbientFrames, pageColor: Color, strength: Float) {
    val cur = frames.current ?: return
    if (strength <= 0f || size.width < 10f) return
    val pad = 30.dp.roundToPx()
    val height = minOf((size.width * 1.1f).toInt(), 420.dp.roundToPx())
    val offset = IntOffset(-pad, 0)
    val dst = IntSize(size.width.toInt() + pad * 2, height)
    val mix = frames.mix.value

    frames.previous?.takeIf { mix < 1f }?.let {
        drawImage(
            it,
            dstOffset = offset,
            dstSize = dst,
            alpha = (GLOW_ALPHA * (1f - mix) * strength).coerceIn(0f, 1f),
            colorFilter = Saturate,
            filterQuality = FilterQuality.Medium,
        )
    }
    drawImage(
        cur,
        dstOffset = offset,
        dstSize = dst,
        alpha = (GLOW_ALPHA * mix * strength).coerceIn(0f, 1f),
        colorFilter = Saturate,
        filterQuality = FilterQuality.Medium,
    )
    drawRect(
        brush = Brush.verticalGradient(
            0f to pageColor.copy(alpha = 0f),
            0.55f to pageColor.copy(alpha = 0.35f),
            1f to pageColor,
            startY = 0f,
            endY = height.toFloat(),
        ),
        size = Size(size.width, height.toFloat()),
    )
}
