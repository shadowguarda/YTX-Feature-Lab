package com.tensei.ytxtube

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * One place to decide whether the refractive glass shaders can run. Old Android
 * versions and low-RAM TVs keep the same glass surfaces through a cheaper
 * translucent fallback instead of losing the design language or stuttering.
 */
object GlassQuality {
    @Volatile
    private var deviceSupports = isRuntimeShaderSupported()

    /**
     * True only when the device can draw the shaders *and* the viewer has kept
     * Liquid Glass switched on. Compose observes this state, so flipping the
     * setting instantly swaps every surface between glass and solid Material.
     */
    var effectsSupported by mutableStateOf(deviceSupports)
        private set

    fun init(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java)
        deviceSupports = isRuntimeShaderSupported() && am?.isLowRamDevice != true
        effectsSupported = deviceSupports && GlassSettings.enabled
    }

    fun setEnabled(enabled: Boolean) {
        effectsSupported = deviceSupports && enabled
    }
}

/**
 * User-facing Liquid Glass preference. Stored in the same "appearance" file as
 * the theme so it survives restarts, but kept separate from the theme mode.
 */
object GlassSettings {
    private const val PREFS = "appearance"
    private const val KEY = "liquid_glass"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean(KEY, true)
        GlassQuality.setEnabled(enabled)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY, value)?.apply()
        GlassQuality.setEnabled(value)
    }
}









@Composable
fun glassFill(alpha: Float = 1f): Color {
    if (!GlassQuality.effectsSupported) {
        // Solid Material surface: no translucency, no blur, no backdrop.
        return MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val dark = isSystemInDarkTheme()
    // The Liquid Glass Studio material sets how see-through every surface is.
    val a = GlassTuning.fillAlpha(LiquidGlassStudio.materialPreset.surfaceAlpha, dark, alpha)
    return if (dark) Color(0xFF121212).copy(alpha = a) else Color(0xFFFAFAFA).copy(alpha = a)
}

/** Which Studio blur slider a glass surface follows. */
enum class GlassRole { General, Player, Sheet, Music, Header, Nav }

/**
 * Turns the Liquid Glass Studio settings into glass. Each surface keeps the design values it was built
 * with and they are scaled by how far the Studio value has moved from its factory default, so
 * untouched settings change nothing and a slider at zero turns that effect off.
 */
object GlassTuning {
    const val DEFAULT_PLAYER_BLUR = 20f
    const val DEFAULT_SHEET_BLUR = 26f
    const val DEFAULT_MUSIC_BLUR = 24f
    const val DEFAULT_HEADER_BLUR = 16f
    const val DEFAULT_NAV_BLUR = 10f
    const val DEFAULT_REFRACTION_HEIGHT = 14f
    const val DEFAULT_REFRACTION_AMOUNT = 22f
    const val DEFAULT_INNER_SHADOW = 0.5f
    const val DEFAULT_SURFACE_ALPHA = 0.38f
    const val DEFAULT_BORDER_ALPHA = 0.20f

    fun ratio(value: Float, baseline: Float): Float = if (baseline <= 0f) 1f else (value / baseline).coerceAtLeast(0f)

    fun scale(base: Float, value: Float, baseline: Float): Float = base * ratio(value, baseline)

    /** Fill opacity: dark glass uses [surfaceAlpha] as is, light glass is a little clearer. */
    fun fillAlpha(surfaceAlpha: Float, dark: Boolean, alpha: Float): Float =
        ((if (dark) surfaceAlpha else surfaceAlpha * 0.34f / DEFAULT_SURFACE_ALPHA) * alpha).coerceIn(0f, 1f)

    /** Edge line opacity of the fallback glass: 0.16 at the default material. */
    fun borderAlpha(presetBorderAlpha: Float): Float = (presetBorderAlpha * 0.8f).coerceIn(0f, 1f)

    /** (current slider value, factory default) for a role. */
    fun blurFor(role: GlassRole): Pair<Float, Float> = when (role) {
        GlassRole.General, GlassRole.Player -> LiquidGlassStudio.playerBlurDp.toFloat() to DEFAULT_PLAYER_BLUR
        GlassRole.Sheet -> LiquidGlassStudio.sheetBlurDp.toFloat() to DEFAULT_SHEET_BLUR
        GlassRole.Music -> LiquidGlassStudio.musicBlurDp.toFloat() to DEFAULT_MUSIC_BLUR
        GlassRole.Header -> LiquidGlassStudio.headerBlurDp.toFloat() to DEFAULT_HEADER_BLUR
        GlassRole.Nav -> LiquidGlassStudio.navBarBlurDp.toFloat() to DEFAULT_NAV_BLUR
    }
}

/**
 * Opaque fill for controls drawn directly on top of video. Those controls keep
 * white icons in both themes, so with Liquid Glass off they need a dark solid
 * surface rather than the light Material surface used by sheets and dialogs.
 */
@Composable
fun glassOverlayFill(alpha: Float = 1f): Color =
    if (!GlassQuality.effectsSupported) Color(0xFF1C1C1E) else glassFill(alpha)

/** The backdrop captured behind the player, used by in-player liquid glass controls. */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Immutable
data class GlassSpec(
    val shape: Shape,
    val tint: Color,
    val blur: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val shadow: Shadow?,
    val highlight: Highlight?,
    val innerShadow: InnerShadow?,
    val chromaticAberration: Boolean,
)

@Composable
fun glassSpec(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    role: GlassRole = GlassRole.General,
    tint: Color = glassFill(),
    blur: Dp = 18.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 22.dp,
    shadow: Shadow? = Shadow.Default,
    highlight: Highlight? = Highlight.Default,
    innerShadow: InnerShadow? = InnerShadow.Default.copy(radius = 10.dp, alpha = 0.5f),
    chromaticAberration: Boolean = true,
): GlassSpec {
    val (studioBlur, defaultBlur) = GlassTuning.blurFor(role)
    val shadowScale = GlassTuning.ratio(LiquidGlassStudio.innerShadowAlpha, GlassTuning.DEFAULT_INNER_SHADOW)
    return GlassSpec(
    shape = shape,
    // With Liquid Glass off every tint becomes a solid Material fill, even when
    // a caller supplied a translucent colour for the shader path.
    tint = if (GlassQuality.effectsSupported) tint else tint.copy(alpha = 1f),
    blur = GlassTuning.scale(blur.value, studioBlur, defaultBlur).dp,
    refractionHeight = GlassTuning.scale(refractionHeight.value, LiquidGlassStudio.refractionHeightDp.toFloat(), GlassTuning.DEFAULT_REFRACTION_HEIGHT).dp,
    refractionAmount = GlassTuning.scale(refractionAmount.value, LiquidGlassStudio.refractionAmountDp.toFloat(), GlassTuning.DEFAULT_REFRACTION_AMOUNT).dp,
    shadow = shadow,
    highlight = highlight,
    innerShadow = innerShadow?.let { it.copy(alpha = (it.alpha * shadowScale).coerceIn(0f, 1f)) },
    chromaticAberration = chromaticAberration && LiquidGlassStudio.chromaticAberration,
    )
}


fun Modifier.liquidGlass(backdrop: Backdrop, spec: GlassSpec): Modifier =
    if (GlassQuality.effectsSupported) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { spec.shape },
            effects = {
                vibrancy()
                // A Studio slider at zero switches that effect off.
                if (spec.blur.value > 0f) blur(spec.blur.toPx())
                if (spec.refractionHeight.value > 0f && spec.refractionAmount.value > 0f) {
                    lens(
                        spec.refractionHeight.toPx(),
                        spec.refractionAmount.toPx(),
                        chromaticAberration = spec.chromaticAberration,
                    )
                }
            },
            highlight = spec.highlight?.let { h -> { h } },
            shadow = spec.shadow?.let { s -> { s } },
            innerShadow = spec.innerShadow?.let { s -> { s } },
            onDrawSurface = {
                if (spec.tint.isSpecified) drawRect(spec.tint)
            },
        )
    } else {
        // Android 12 and older (plus low-end TVs without runtime shaders) cannot
        // render the refractive glass shader. Keep the same frosted surface look
        // with a translucent fill, soft top sheen and hairline edge instead of
        // removing the glass entirely.
        fallbackGlass(spec)
    }

private fun Modifier.fallbackGlass(spec: GlassSpec): Modifier =
    if (!GlassQuality.effectsSupported) {
        // Liquid Glass is switched off: a plain, fully opaque Material surface.
        this
            .clip(spec.shape)
            .background(spec.tint.copy(alpha = 1f))
    } else {
        // Android 12 and older (plus low-end TVs without runtime shaders) cannot
        // render the refractive glass shader. Keep the same frosted surface look
        // with a translucent fill, soft top sheen and hairline edge instead of
        // removing the glass entirely.
        this
            .clip(spec.shape)
            .background(spec.tint)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.05f),
                    ),
                )
            }
            .border(
                LiquidGlassStudio.borderWidthDp.dp,
                Color.White.copy(alpha = GlassTuning.borderAlpha(LiquidGlassStudio.materialPreset.borderAlpha)),
                spec.shape,
            )
    }
