package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import com.kyant.backdrop.Backdrop

/** The two video players the app offers. The playback engine and gestures are shared by both. */
enum class PlayerEngineMode(val title: String, val tagline: String, val description: String) {
    Classic(
        title = "YTX Classic",
        tagline = "Original, full-featured",
        description = "The player the app shipped with: chapter and SponsorBlock markers on the seekbar, " +
            "frame step, edge-to-edge scrubbing and every Player controls option.",
    ),
    Style(
        title = "YTX Style",
        tagline = "Cute, modern, 10 looks",
        description = "Restyled controls with 10 looks (Kawaii Glass, Cyberpunk HUD, OLED Pure and more), " +
            "5 seekbar styles and 4 button shapes. Same gestures, speed, quality and captions.",
    ),
}

/** What the person cares about most, used to suggest a player. */
enum class PlayerPriority(val label: String) {
    Features("Every feature and marker"),
    Looks("Looks and personality"),
    CalmCinema("Calm, distraction-free"),
    Battery("AMOLED and dark rooms"),
}

data class PlayerRecommendation(val mode: PlayerEngineMode, val style: PlayerStylePreset?, val reason: String)

object PlayerEngineLogic {
    fun parse(name: String?): PlayerEngineMode = PlayerEngineMode.entries.find { it.name == name } ?: PlayerEngineMode.Classic

    fun recommend(p: PlayerPriority): PlayerRecommendation = when (p) {
        PlayerPriority.Features -> PlayerRecommendation(
            PlayerEngineMode.Classic, null,
            "Classic keeps chapter and SponsorBlock markers, frame step and edge-to-edge scrubbing.",
        )
        PlayerPriority.Looks -> PlayerRecommendation(
            PlayerEngineMode.Style, PlayerStylePreset.KawaiiGlass,
            "Style gives you 10 looks; Kawaii Glass is the friendliest starting point.",
        )
        PlayerPriority.CalmCinema -> PlayerRecommendation(
            PlayerEngineMode.Style, PlayerStylePreset.MinimalCinema,
            "Minimal Cinema hides everything except the controls you need.",
        )
        PlayerPriority.Battery -> PlayerRecommendation(
            PlayerEngineMode.Style, PlayerStylePreset.OledPure,
            "OLED Pure uses true black behind the controls, which saves power on AMOLED screens.",
        )
    }

    /** Short label for the chip row, e.g. "YTX Style · Kawaii Glass". */
    fun summary(mode: PlayerEngineMode, style: PlayerStylePreset): String =
        if (mode == PlayerEngineMode.Style) "${mode.title} · ${style.title}" else mode.title
}

/** Which player is active, plus a flag the app uses to open the player switcher from anywhere. */
object PlayerEngine {
    private const val PREFS = "player_engine"
    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(PlayerEngineMode.Classic)
        private set

    /** Not saved: whether the player switcher sheet is open right now. */
    var switcherOpen by mutableStateOf(false)

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        mode = PlayerEngineLogic.parse(p.getString("mode", null))
    }

    fun updateMode(value: PlayerEngineMode) {
        mode = value
        prefs?.edit()?.putString("mode", value.name)?.apply()
    }
}

// ---- Small stand-ins for the helpers the Style player was written against ----

object YtxNeonPalette {
    val VioletCore = Color(0xFF7C3AED)
    val ElectricBlue = Color(0xFF3B82F6)

    fun parseHex(hex: String, fallback: Color): Color {
        val argb = PersonalizationLogic.hexToArgb(hex) ?: return fallback
        return Color(argb)
    }
}

data class YtxDesignTokens(
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val primaryAccent: Color,
    val cardSurface: Color,
    val subtleBorder: Color,
    val isDark: Boolean,
)

object LocalYtxDesignTokens {
    val current: YtxDesignTokens
        @Composable get() {
            val c = MaterialTheme.colorScheme
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            return YtxDesignTokens(
                primaryGlow = studioGlowColor(),
                secondaryGlow = studioGlowColor(secondary = true),
                primaryAccent = c.primary,
                cardSurface = c.surfaceContainerHigh,
                subtleBorder = c.outlineVariant.copy(alpha = 0.6f),
                isDark = dark,
            )
        }
}

/** Glass behind controls when a backdrop exists; a solid Material surface otherwise. */
@Composable
fun Modifier.selectiveGlassSurface(
    backdrop: Backdrop?,
    role: GlassRole,
    shape: Shape,
    useNeonBorder: Boolean = false,
): Modifier {
    val base = if (backdrop != null && GlassQuality.effectsSupported) {
        this.liquidGlass(backdrop, glassSpec(shape = shape, role = role))
    } else {
        this.clip(shape).background(glassOverlayFill(0.6f))
    }
    return if (useNeonBorder) base.border(1.dp, studioGlowColor().copy(alpha = 0.5f), shape) else base
}

/** Press-to-shrink with an optional haptic tick on tap. */
fun Modifier.liquidPressable(pressedScale: Float = 0.94f, haptic: Boolean = false, onClick: () -> Unit): Modifier = composed {
    val feedback = LocalHapticFeedback.current
    this.pressable(pressedScale = pressedScale) {
        if (haptic) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

// ---- Switcher UI ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerSwitcherContent(backdrop: Backdrop?, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    var priority by remember { mutableStateOf<PlayerPriority?>(null) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Video player", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Both players use the same playback, downloads, gestures and Picture-in-picture. You can switch any time, even mid-video.",
            color = c.onSurfaceVariant, fontSize = 12.5.sp,
        )

        PlayerEngineMode.entries.forEach { m ->
            val selected = PlayerEngine.mode == m
            val shape = RoundedCornerShape(20.dp)
            val bg by animateColorAsState(if (selected) c.primary.copy(alpha = 0.14f) else c.surfaceContainerHighest, label = "engineBg")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(bg)
                    .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.primary else c.outlineVariant.copy(alpha = 0.5f)), shape)
                    .clickable { PlayerEngine.updateMode(m) }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(m.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (selected) "Selected" else m.tagline,
                        color = if (selected) c.primary else c.onSurfaceVariant,
                        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(m.description, color = c.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
        }

        Text("Which is best for me?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Pick what matters most and we will suggest a player.", color = c.onSurfaceVariant, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerPriority.entries.forEach { p ->
                YtxChip(label = p.label, selected = priority == p) { priority = p }
            }
        }
        priority?.let { p ->
            val rec = PlayerEngineLogic.recommend(p)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.secondaryContainer.copy(alpha = 0.5f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Suggested: " + PlayerEngineLogic.summary(rec.mode, rec.style ?: CutePlayerPreferences.style), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(rec.reason, fontSize = 12.5.sp, lineHeight = 17.sp)
                Button(onClick = {
                    PlayerEngine.updateMode(rec.mode)
                    rec.style?.let { CutePlayerPreferences.updateStyle(it) }
                }) { Text("Use this") }
            }
        }

        if (PlayerEngine.mode == PlayerEngineMode.Style) {
            PlayerStyleAndCustomizerSheet(backdrop = backdrop, modifier = Modifier.padding(top = 4.dp))
        } else {
            YtxNote("Switch to YTX Style to choose from 10 looks, seekbar styles and button shapes. Classic keeps using Settings → Player controls.")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PlayerSwitcherSheet(onDismiss: () -> Unit) {
    FloatingSheet(onDismissRequest = onDismiss) {
        PlayerSwitcherContent(backdrop = LocalGlassBackdrop.current)
    }
}
