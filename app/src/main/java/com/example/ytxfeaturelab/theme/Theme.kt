package com.example.ytxfeaturelab.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Compact seed-scheme generator. Mirrors the spirit of YTXTube's `seedScheme`
 * but is intentionally smaller so it can be lifted into the real app directly.
 */
fun schemeFromSeed(seedHex: String, dark: Boolean): ColorScheme {
    val argb = runCatching {
        android.graphics.Color.parseColor(seedHex.removePrefix("#").let { if (it.length == 6) "#$it" else seedHex })
    }.getOrElse { 0xFFB5651D.toInt() }

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    val h = hsl[0]

    fun hslColor(hue: Float, sat: Float, light: Float): Color {
        val arr = floatArrayOf(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 100f), light.coerceIn(0f, 100f))
        return Color(ColorUtils.HSLToColor(arr))
    }

    return if (!dark) lightColorScheme(
        primary = hslColor(h, 70f, 42f),
        onPrimary = Color.White,
        primaryContainer = hslColor(h, 55f, 90f),
        onPrimaryContainer = hslColor(h, 65f, 18f),
        secondary = hslColor(h, 26f, 40f),
        onSecondary = Color.White,
        secondaryContainer = hslColor(h, 22f, 88f),
        onSecondaryContainer = hslColor(h, 26f, 20f),
        tertiary = hslColor(h + 40f, 45f, 45f),
        background = hslColor(h, 12f, 98f),
        onBackground = hslColor(h, 10f, 12f),
        surface = hslColor(h, 12f, 98f),
        onSurface = hslColor(h, 10f, 12f),
        surfaceVariant = hslColor(h, 12f, 90f),
        onSurfaceVariant = hslColor(h, 12f, 36f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hslColor(h, 14f, 96f),
        surfaceContainer = hslColor(h, 14f, 94f),
        surfaceContainerHigh = hslColor(h, 14f, 91f),
        surfaceContainerHighest = hslColor(h, 14f, 87f),
        outline = hslColor(h, 10f, 54f),
        outlineVariant = hslColor(h, 10f, 84f),
    ) else darkColorScheme(
        primary = hslColor(h, 60f, 78f),
        onPrimary = hslColor(h, 65f, 18f),
        primaryContainer = hslColor(h, 55f, 30f),
        onPrimaryContainer = hslColor(h, 60f, 90f),
        secondary = hslColor(h, 18f, 80f),
        onSecondary = hslColor(h, 20f, 20f),
        secondaryContainer = hslColor(h, 16f, 22f),
        onSecondaryContainer = hslColor(h, 18f, 90f),
        tertiary = hslColor(h + 40f, 35f, 80f),
        background = hslColor(h, 10f, 8f),
        onBackground = hslColor(h, 8f, 92f),
        surface = hslColor(h, 10f, 8f),
        onSurface = hslColor(h, 8f, 92f),
        surfaceVariant = hslColor(h, 10f, 30f),
        onSurfaceVariant = hslColor(h, 10f, 80f),
        surfaceContainerLowest = hslColor(h, 10f, 4f),
        surfaceContainerLow = hslColor(h, 10f, 10f),
        surfaceContainer = hslColor(h, 10f, 12f),
        surfaceContainerHigh = hslColor(h, 10f, 17f),
        surfaceContainerHighest = hslColor(h, 10f, 22f),
        outline = hslColor(h, 8f, 60f),
        outlineVariant = hslColor(h, 8f, 32f),
    )
}

/** Amoled override: everything surfaces → pure black, keeps the accent. */
fun amoledize(base: ColorScheme): ColorScheme = base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF141414),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1E1E1E),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    outlineVariant = Color(0xFF1F1F1F),
)

@Composable
fun isDarkTheme(): Boolean = when (ThemeSettings.mode) {
    "dark", "amoled" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun YtxTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val base = schemeFromSeed(ThemeSettings.seed, dark)
    val scheme = if (ThemeSettings.mode == "amoled") amoledize(base) else base
    MaterialTheme(colorScheme = scheme, content = content)
}

// Needed by the seed picker to render a preview of each swatch.
fun previewScheme(seed: String, dark: Boolean): ColorScheme = schemeFromSeed(seed, dark)

// Convenience for background color of system bars.
fun ColorScheme.windowBackgroundArgb(): Int = background.toArgb()
