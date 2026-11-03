package com.example.ytxfeaturelab.ambient

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Ambient mode: extract the video's dominant / vibrant colours and use them to tint
 * the surrounding page, exactly like YouTube's "ambient mode". Colors are cached
 * per thumbnail URL so scrolling never re-decodes the same image.
 *
 * Integration: call [AmbientPalette.colorsFor] on the thumbnails you care about
 * (playing video, current feed item) and pass the result into [AmbientBackground].
 */
object AmbientPalette {
    private val cache = ConcurrentHashMap<String, Pair<Color, Color>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cached(url: String): Pair<Color, Color>? = cache[url]

    fun colorsFor(context: Context, url: String, onColors: (Pair<Color, Color>) -> Unit) {
        cache[url]?.let { onColors(it); return }
        scope.launch {
            val bitmap = loadBitmap(context, url) ?: return@launch
            val palette = Palette.from(bitmap).generate()
            val vibrant = palette.getVibrantColor(palette.getMutedColor(palette.getDominantColor(0xFF333333.toInt())))
            val dark = palette.getDarkMutedColor(vibrant)
            val pair = Color(vibrant) to Color(dark)
            cache[url] = pair
            onColors(pair)
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        val req = ImageRequest.Builder(context)
            .data(url)
            .size(256, 144)
            .allowHardware(false)
            .build()
        return runCatching {
            (context.imageLoader.execute(req) as? SuccessResult)
                ?.drawable?.toBitmap()
        }.getOrNull()
    }
}

/**
 * Draws a soft two-lobe radial gradient (top + bottom) from [colors].
 * Matches the YouTube ambient mood with a very slow horizontal breathing motion.
 */
@Composable
fun AmbientBackground(
    colors: Pair<Color, Color>?,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    if (colors == null || intensity <= 0f) return
    val (top, bottom) = colors

    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(top.copy(alpha = 0.32f * intensity), Color.Transparent),
                        center = Offset(size.width * (0.2f + 0.6f * drift), 0f),
                        radius = size.width * 0.9f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(bottom.copy(alpha = 0.24f * intensity), Color.Transparent),
                        center = Offset(size.width * (0.85f - 0.5f * drift), size.height),
                        radius = size.width * 0.85f,
                    ),
                )
            },
    )
}

/**
 * Convenience composable that resolves ambient colors from a thumbnail URL and
 * exposes the current pair via state. This is what the sample feed and player
 * use to show the video's colours behind their content.
 */
@Composable
fun rememberAmbientColors(thumbnailUrl: String?): Pair<Color, Color>? {
    val context = LocalContext.current
    var colors by remember(thumbnailUrl) { mutableStateOf(thumbnailUrl?.let(AmbientPalette::cached)) }
    androidx.compose.runtime.LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) AmbientPalette.colorsFor(context, thumbnailUrl) { colors = it }
    }
    return colors
}
