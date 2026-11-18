package com.tensei.ytxtube

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape

/**
 * Pure rules for the Personalization Studio and for applying the Liquid Glass Studio's saved
 * look to real screens (sheet shape and transparency, animation speed, glow colour).
 */
object PersonalizationLogic {
    const val DEFAULT_SHEET_ALPHA = 0.68f

    /** A duration scaled by the animation preset's multiplier. */
    fun motionMs(baseMs: Int, multiplier: Float): Int = (baseMs * multiplier).toInt().coerceAtLeast(0)

    /** Opaque at the default value, increasingly see-through below it (never below 35%). */
    fun sheetContainerAlpha(sheetSurfaceAlpha: Float): Float =
        (sheetSurfaceAlpha / DEFAULT_SHEET_ALPHA).coerceIn(0.35f, 1f)

    fun sheetCorner(dp: Int): Int = dp.coerceIn(12, 42)

    /** "#RRGGBB" (or without #) to opaque ARGB, or null when it is not a full six digit colour. */
    fun hexToArgb(hex: String): Int? {
        val n = ThemeAccentLogic.normalizeHex(hex) ?: return null
        val rgb = n.removePrefix("#").toLongOrNull(16) ?: return null
        return (0xFF000000L or rgb).toInt()
    }

    /** Transparency shown to the person: 0% is the default opaque sheet. */
    fun transparencyPercent(sheetSurfaceAlpha: Float): Int =
        Math.round((1f - sheetContainerAlpha(sheetSurfaceAlpha)) * 100f)

    /** The slider's transparency percent back to the stored surface alpha. */
    fun surfaceAlphaFromPercent(percent: Int): Float =
        (DEFAULT_SHEET_ALPHA * (1f - percent.coerceIn(0, 65) / 100f)).coerceIn(0.35f, DEFAULT_SHEET_ALPHA)
}

/** The Studio's sheet corner radius, as a top-corner shape. */
@Composable
fun studioSheetShape(): Shape {
    val r = PersonalizationLogic.sheetCorner(LiquidGlassStudio.sheetCornerRadiusDp).dp
    return RoundedCornerShape(topStart = r, topEnd = r)
}

/** The sheet background, made more see-through by the Studio's transparency setting. */
@Composable
fun studioSheetContainer(base: Color = MaterialTheme.colorScheme.surfaceContainerLow): Color =
    base.copy(alpha = PersonalizationLogic.sheetContainerAlpha(LiquidGlassStudio.sheetSurfaceAlpha))

@Composable
fun studioGlowColor(secondary: Boolean = false): Color {
    val hex = if (secondary) LiquidGlassStudio.secondaryGlowHex else LiquidGlassStudio.primaryGlowHex
    val argb = PersonalizationLogic.hexToArgb(hex) ?: 0xFF7C3AED.toInt()
    return Color(argb)
}

/** Border that slowly cycles between the two glow colours when the Studio's neon border is on. */
@Composable
fun Modifier.studioNeonBorder(shape: Shape, width: androidx.compose.ui.unit.Dp = 1.5.dp): Modifier {
    if (!LiquidGlassStudio.neonBorderEnabled) return this
    val a = studioGlowColor()
    val b = studioGlowColor(secondary = true)
    val t = if (LiquidGlassStudio.animatedBackgroundsEnabled) {
        val tr = rememberInfiniteTransition(label = "neon")
        val f by tr.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(PersonalizationLogic.motionMs(3200, LiquidGlassStudio.animationPreset.durationMultiplier).coerceAtLeast(400), easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Reverse),
            label = "neonPhase",
        )
        f
    } else 0.5f
    val mixed = androidx.compose.ui.graphics.lerp(a, b, t)
    return this.border(BorderStroke(width, Brush.linearGradient(listOf(a, mixed, b))), shape)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PersonalizationStudioSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    var accentText by remember { mutableStateOf(ThemeSettings.seed ?: "") }
    var glow1 by remember { mutableStateOf(LiquidGlassStudio.primaryGlowHex) }
    var glow2 by remember { mutableStateOf(LiquidGlassStudio.secondaryGlowHex) }
    var json by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = studioSheetShape(),
        containerColor = studioSheetContainer(),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
        ) {
            Text("Personalization Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            YtxNote("Everything here applies right away. The preview below uses your real colours, corners and neon border.")

            // Live preview of what the settings change.
            val previewShape = RoundedCornerShape(PersonalizationLogic.sheetCorner(LiquidGlassStudio.sheetCornerRadiusDp).dp)
            Column(
                Modifier
                    .padding(vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(previewShape)
                    .background(c.surfaceContainerHigh)
                    .studioNeonBorder(previewShape)
                    .padding(16.dp),
            ) {
                Text("Preview card", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Sheet corner ${LiquidGlassStudio.sheetCornerRadiusDp} dp · ${LiquidGlassStudio.animationPreset.label}", fontSize = 12.sp, color = c.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(studioGlowColor(), studioGlowColor(true)))))
            }

            YtxSectionLabel("Accent colour")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = accentText,
                    onValueChange = { accentText = it },
                    label = { Text("HEX (e.g. #7C3AED)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions.Default,
                )
                Button(onClick = {
                    val n = ThemeAccentLogic.normalizeHex(accentText)
                    if (n == null) { message = "Enter a full 6 digit HEX colour."; messageError = true }
                    else { ThemeSettings.choose(n); accentText = n; message = "Accent set to $n."; messageError = false }
                }) { Text("Apply") }
            }

            YtxSectionLabel("Sheets and glass")
            YtxSliderRow(
                "Sheet corner radius", LiquidGlassStudio.sheetCornerRadiusDp.toFloat(), 12f..42f, 29,
                "${LiquidGlassStudio.sheetCornerRadiusDp} dp",
            ) { LiquidGlassStudio.updateGlowAndStyle(sheetRadius = it.toInt()) }
            YtxSliderRow(
                "Sheet transparency", PersonalizationLogic.transparencyPercent(LiquidGlassStudio.sheetSurfaceAlpha).toFloat(), 0f..65f, 12,
                "${PersonalizationLogic.transparencyPercent(LiquidGlassStudio.sheetSurfaceAlpha)}%",
            ) { LiquidGlassStudio.updateSheetSurfaceAlpha(PersonalizationLogic.surfaceAlphaFromPercent(it.toInt())) }
            YtxSliderRow(
                "Sheet blur", LiquidGlassStudio.sheetBlurDp.toFloat(), 0f..40f, 39, "${LiquidGlassStudio.sheetBlurDp} dp",
            ) { LiquidGlassStudio.updateScreenBlurs(sheet = it.toInt()) }
            YtxSliderRow(
                "Border width", LiquidGlassStudio.borderWidthDp, 0.5f..3f, 4, String.format(java.util.Locale.US, "%.1f dp", LiquidGlassStudio.borderWidthDp),
            ) { LiquidGlassStudio.updateDepthAndBorders(borderW = it) }
            YtxSwitchRow("Neon border", "Animated glow border on the mini player, island and cards", LiquidGlassStudio.neonBorderEnabled) {
                LiquidGlassStudio.updateDepthAndBorders(neonBorder = it)
            }
            YtxSwitchRow("Animated glow", "Let the neon border drift between the two glow colours", LiquidGlassStudio.animatedBackgroundsEnabled) {
                LiquidGlassStudio.updateGlowAndStyle(animBg = it)
            }

            YtxSectionLabel("Glow colours")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(glow1, { glow1 = it }, label = { Text("Primary") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(glow2, { glow2 = it }, label = { Text("Secondary") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val a = ThemeAccentLogic.normalizeHex(glow1)
                val b = ThemeAccentLogic.normalizeHex(glow2)
                if (a == null || b == null) { message = "Both glow colours need a full 6 digit HEX."; messageError = true }
                else { LiquidGlassStudio.updateGlowAndStyle(glow1 = a, glow2 = b); glow1 = a; glow2 = b; message = "Glow colours updated."; messageError = false }
            }, modifier = Modifier.padding(top = 6.dp)) { Text("Apply glow colours") }

            YtxSectionLabel("Animation speed")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimationTimelinePreset.entries.forEach { p ->
                    YtxChip(label = p.label, selected = LiquidGlassStudio.animationPreset == p) { LiquidGlassStudio.updateGlowAndStyle(animTimeline = p) }
                }
            }

            YtxSectionLabel("Navigation indicator")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavIndicatorStyle.entries.forEach { st ->
                    YtxChip(label = st.label, selected = LiquidGlassStudio.navIndicatorStyle == st) { LiquidGlassStudio.updateGlowAndStyle(navStyle = st) }
                }
            }

            YtxSectionLabel("Import and export")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("YTX Theme Package", LiquidGlassStudio.exportCurrentThemeJson()))
                    message = "Theme JSON copied to the clipboard."; messageError = false
                }) { Text("Copy theme JSON") }
                OutlinedButton(onClick = { LiquidGlassStudio.resetAll(); message = "Glass Studio reset to defaults."; messageError = false }) { Text("Reset") }
            }
            OutlinedTextField(
                value = json, onValueChange = { json = it },
                label = { Text("Paste theme JSON to import") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3, maxLines = 6,
            )
            OutlinedButton(onClick = {
                val result = ThemePackageValidator.validate(json)
                val pkg = result.parsedPackage
                if (!result.valid || pkg == null) { message = "Not imported: " + result.errors.joinToString("; "); messageError = true }
                else { LiquidGlassStudio.applyThemePackage(pkg); json = ""; message = "Imported '${pkg.name}'."; messageError = false }
            }, modifier = Modifier.padding(top = 6.dp)) { Text("Import") }
            message?.let { YtxNote(it, isError = messageError) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
