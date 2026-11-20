package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class StudioTab(val label: String) {
    Components("Components"),
    Glass("Glass"),
    Player("Player"),
    Home("Home"),
    Navigation("Navigation"),
    Animation("Animation"),
    Presets("Presets"),
    Profiles("Profiles"),
    Json("JSON"),
}

/**
 * The Interface Studio: one place to design the whole app. A live preview stays pinned at the top
 * and reacts to every change; the tabs below edit components, glass, player, home, navigation,
 * animation, presets, saved profiles and JSON.
 */
@Composable
fun InterfaceStudioSheet(onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(StudioTab.Components) }
    FloatingSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Text("Interface Studio", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            StudioPreview(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { tab = it }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudioTab.entries.forEach { t -> YtxChip(label = t.label, selected = tab == t) { tab = t } }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (tab) {
                    StudioTab.Components -> ComponentsTab()
                    StudioTab.Glass -> GlassTab()
                    StudioTab.Player -> PlayerTab()
                    StudioTab.Home -> HomeTab()
                    StudioTab.Navigation -> NavigationTab()
                    StudioTab.Animation -> AnimationTab()
                    StudioTab.Presets -> PresetsTab()
                    StudioTab.Profiles -> ProfilesTab()
                    StudioTab.Json -> JsonTab()
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** A small stand-in screen drawn from the real settings: header, a feed card in the chosen style and the nav bar. */
@Composable
private fun StudioPreview(modifier: Modifier = Modifier, onSelectTab: (StudioTab) -> Unit) {
    val c = MaterialTheme.colorScheme
    val glow = studioGlowColor()
    val glow2 = studioGlowColor(secondary = true)
    val radius = PersonalizationLogic.sheetCorner(LiquidGlassStudio.sheetCornerRadiusDp).dp
    val frame = RoundedCornerShape(radius.coerceAtMost(24.dp))
    var navIndex by remember { mutableStateOf(0) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(frame)
            .background(c.surfaceContainerLow)
            .studioNeonBorder(frame)
            .clickable { onSelectTab(StudioTab.Glass) }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YTXTube", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f).clickable { onSelectTab(StudioTab.Components) })
            Box(Modifier.size(18.dp).clip(CircleShape).background(c.surfaceContainerHighest).clickable { onSelectTab(StudioTab.Player) })
            Box(Modifier.size(18.dp).clip(CircleShape).background(c.surfaceContainerHighest).clickable { onSelectTab(StudioTab.Animation) })
        }
        when (UiUpgrades.homeCardStyle) {
            FeedCardStyle.Comfortable -> Column(Modifier.fillMaxWidth().clickable { onSelectTab(StudioTab.Home) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(glow.copy(alpha = 0.55f), glow2.copy(alpha = 0.55f)))))
                Box(Modifier.fillMaxWidth(0.7f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.surfaceContainerHighest))
            }
            FeedCardStyle.Compact -> Row(Modifier.fillMaxWidth().clickable { onSelectTab(StudioTab.Home) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(78.dp).height(44.dp).clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(glow.copy(alpha = 0.55f), glow2.copy(alpha = 0.55f)))))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth(0.9f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.surfaceContainerHighest))
                    Box(Modifier.fillMaxWidth(0.5f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.surfaceContainerHighest))
                }
            }
            FeedCardStyle.Grid -> Row(Modifier.fillMaxWidth().clickable { onSelectTab(StudioTab.Home) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(glow.copy(alpha = 0.55f), glow2.copy(alpha = 0.55f))))) }
            }
        }
        // Nav bar: tap an icon to see the indicator style move.
        val indicator = LiquidGlassStudio.navIndicatorStyle
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).clickable { onSelectTab(StudioTab.Navigation) }.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            repeat(4) { i ->
                val selected = navIndex == i
                val big = selected && indicator == NavIndicatorStyle.MinimalScale
                Box(
                    Modifier
                        .size(width = 46.dp, height = 34.dp)
                        .clip(if (indicator == NavIndicatorStyle.FrostedSquircle) RoundedCornerShape(10.dp) else RoundedCornerShape(50))
                        .background(
                            if (selected && (indicator == NavIndicatorStyle.LiquidPill || indicator == NavIndicatorStyle.FrostedSquircle)) glow.copy(alpha = 0.28f) else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .drawBehind {
                            if (selected && indicator == NavIndicatorStyle.NeonUnderline) {
                                drawRoundRect(glow, Offset(size.width * 0.25f, size.height - 4.dp.toPx()), Size(size.width * 0.5f, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                            }
                            if (selected && indicator == NavIndicatorStyle.GlowingDot) {
                                drawCircle(glow.copy(alpha = 0.35f), 6.dp.toPx(), Offset(size.width / 2f, size.height - 4.dp.toPx()))
                                drawCircle(glow, 3.dp.toPx(), Offset(size.width / 2f, size.height - 4.dp.toPx()))
                            }
                        }
                        .clickable { navIndex = i; onSelectTab(StudioTab.Navigation) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(if (big) 18.dp else 13.dp).clip(CircleShape).background(if (selected) c.onSurface else c.onSurfaceVariant.copy(alpha = 0.6f)))
                }
            }
        }
    }
}

@Composable
private fun SubLabel(text: String) = YtxSectionLabel(text)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipChoice(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> YtxChip(label = label(o), selected = o == selected) { onPick(o) } }
    }
}

@Composable
private fun ComponentsTab() {
    var accent by remember { mutableStateOf(ThemeSettings.seed ?: "") }
    var g1 by remember { mutableStateOf(LiquidGlassStudio.primaryGlowHex) }
    var g2 by remember { mutableStateOf(LiquidGlassStudio.secondaryGlowHex) }
    var msg by remember { mutableStateOf<String?>(null) }
    SubLabel("Sheets and cards")
    YtxSliderRow("Corner radius", LiquidGlassStudio.sheetCornerRadiusDp.toFloat(), 12f..42f, 29, "${LiquidGlassStudio.sheetCornerRadiusDp} dp") { LiquidGlassStudio.updateGlowAndStyle(sheetRadius = it.toInt()) }
    YtxSliderRow("Sheet transparency", PersonalizationLogic.transparencyPercent(LiquidGlassStudio.sheetSurfaceAlpha).toFloat(), 0f..48f, 11, "${PersonalizationLogic.transparencyPercent(LiquidGlassStudio.sheetSurfaceAlpha)}%") {
        LiquidGlassStudio.updateSheetSurfaceAlpha(PersonalizationLogic.surfaceAlphaFromPercent(it.toInt()))
    }
    YtxSliderRow("Border width", LiquidGlassStudio.borderWidthDp, 0.5f..3f, 4, String.format(java.util.Locale.US, "%.1f dp", LiquidGlassStudio.borderWidthDp)) { LiquidGlassStudio.updateDepthAndBorders(borderW = it) }
    YtxSwitchRow("Neon border", "Glow border on the mini player, sheets and the preview", LiquidGlassStudio.neonBorderEnabled) { LiquidGlassStudio.updateDepthAndBorders(neonBorder = it) }
    SubLabel("Colours")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(accent, { accent = it }, label = { Text("Accent HEX") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = {
            val n = ThemeAccentLogic.normalizeHex(accent)
            if (n == null) msg = "Accent needs a full 6 digit HEX." else { ThemeSettings.choose(n); accent = n; msg = "Accent set." }
        }) { Text("Apply") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(g1, { g1 = it }, label = { Text("Glow 1") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(g2, { g2 = it }, label = { Text("Glow 2") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = {
            val a = ThemeAccentLogic.normalizeHex(g1); val b = ThemeAccentLogic.normalizeHex(g2)
            if (a == null || b == null) msg = "Glow colours need full 6 digit HEX values."
            else { LiquidGlassStudio.updateGlowAndStyle(glow1 = a, glow2 = b); g1 = a; g2 = b; msg = "Glow colours set." }
        }) { Text("Set") }
    }
    msg?.let { YtxNote(it) }
}

@Composable
private fun GlassTab() {
    SubLabel("Material")
    ChipChoice(GlassMaterialPreset.entries, LiquidGlassStudio.materialPreset, { it.label }) { LiquidGlassStudio.applyMaterialPreset(it) }
    SubLabel("Blur")
    YtxSliderRow("Player blur", LiquidGlassStudio.playerBlurDp.toFloat(), 0f..40f, 39, "${LiquidGlassStudio.playerBlurDp} dp") { LiquidGlassStudio.updateScreenBlurs(player = it.toInt()) }
    YtxSliderRow("Navigation bar blur", LiquidGlassStudio.navBarBlurDp.toFloat(), 0f..40f, 39, "${LiquidGlassStudio.navBarBlurDp} dp") { LiquidGlassStudio.updateScreenBlurs(navBar = it.toInt()) }
    YtxSliderRow("Sheet blur", LiquidGlassStudio.sheetBlurDp.toFloat(), 0f..40f, 39, "${LiquidGlassStudio.sheetBlurDp} dp") { LiquidGlassStudio.updateScreenBlurs(sheet = it.toInt()) }
    SubLabel("Refraction")
    YtxSliderRow("Edge height", LiquidGlassStudio.refractionHeightDp.toFloat(), 0f..36f, 35, "${LiquidGlassStudio.refractionHeightDp} dp") { LiquidGlassStudio.updateDepthAndBorders(refHeight = it.toInt()) }
    YtxSliderRow("Edge strength", LiquidGlassStudio.refractionAmountDp.toFloat(), 0f..44f, 43, "${LiquidGlassStudio.refractionAmountDp} dp") { LiquidGlassStudio.updateDepthAndBorders(refAmount = it.toInt()) }
    YtxSwitchRow("Colour fringing", "Chromatic aberration on glass edges", LiquidGlassStudio.chromaticAberration) { LiquidGlassStudio.updateDepthAndBorders(chromatic = it) }
    YtxNote("Glass needs Android 12 or newer; older phones use solid surfaces.")
}

@Composable
private fun PlayerTab() {
    SubLabel("Player")
    ChipChoice(PlayerEngineMode.entries, PlayerEngine.mode, { it.title }) { PlayerEngine.updateMode(it) }
    YtxNote(PlayerEngine.mode.description)
    SubLabel("Look (YTX Style)")
    ChipChoice(PlayerStylePreset.entries, CutePlayerPreferences.style, { it.title }) { CutePlayerPreferences.updateStyle(it) }
    SubLabel("Seekbar")
    ChipChoice(PlayerSeekbarStyle.entries, CutePlayerPreferences.seekbarStyle, { it.label }) { CutePlayerPreferences.updateSeekbarStyle(it) }
    SubLabel("Button shape")
    ChipChoice(PlayerButtonShape.entries, CutePlayerPreferences.buttonShape, { it.label }) { CutePlayerPreferences.updateButtonShape(it) }
    SubLabel("Buttons shown")
    YtxSwitchRow("Speed chip", "Shortcut to playback speed", CutePlayerPreferences.showSpeedChip) { CutePlayerPreferences.updateShowSpeedChip(it) }
    YtxSwitchRow("Quality chip", "Shortcut to video quality", CutePlayerPreferences.showQualityChip) { CutePlayerPreferences.updateShowQualityChip(it) }
    YtxSwitchRow("Captions button", "Shortcut to subtitles", CutePlayerPreferences.showCaptionsButton) { CutePlayerPreferences.updateShowCaptionsButton(it) }
    YtxSwitchRow("Like button", "Like the video from the player", CutePlayerPreferences.showLikeButton) { CutePlayerPreferences.updateShowLikeButton(it) }
    YtxSwitchRow("Loop button", "Repeat the current video", CutePlayerPreferences.showLoopButton) { CutePlayerPreferences.updateShowLoopButton(it) }
    SubLabel("Behaviour")
    YtxSliderRow("Hide controls after", CutePlayerPreferences.autoHideSeconds.toFloat(), 0f..10f, 9, if (CutePlayerPreferences.autoHideSeconds == 0) "Never" else "${CutePlayerPreferences.autoHideSeconds}s") { CutePlayerPreferences.updateAutoHideSeconds(it.toInt()) }
    YtxSliderRow("Double-tap seek", CutePlayerPreferences.doubleTapSeekSeconds.toFloat(), 5f..30f, 4, "${CutePlayerPreferences.doubleTapSeekSeconds}s") { CutePlayerPreferences.updateDoubleTapSeekSeconds(it.toInt()) }
    YtxNote("YTX Classic uses Settings → Player controls for its button layout.")
}

@Composable
private fun HomeTab() {
    SubLabel("Preset")
    val active = HomePresetLogic.matching(UiUpgrades.homeCardStyle, UiUpgrades.dockLabelMode, UiUpgrades.homeShelves)
    ChipChoice(HomePreset.entries, active ?: HomePreset.Classic, { it.label }) { UiUpgrades.applyHomePreset(it) }
    YtxNote(active?.blurb ?: "Custom layout.")
    SubLabel("Video cards")
    ChipChoice(FeedCardStyle.entries, UiUpgrades.homeCardStyle, { it.label }) { UiUpgrades.updateHomeCardStyle(it) }
    SubLabel("Shelves · hold and drag to reorder")
    UiUpgrades.homeShelves.forEach { item ->
        Row(Modifier.fillMaxWidth().reorderOnLongPress { UiUpgrades.moveShelf(item.name, it) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(HomeLayoutLogic.label(item.name), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(HomeLayoutLogic.blurb(item.name), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { UiUpgrades.moveShelf(item.name, -1) }) { Text("Up") }
            TextButton(onClick = { UiUpgrades.moveShelf(item.name, 1) }) { Text("Down") }
            YtxChip(label = if (item.visible) "Shown" else "Hidden", selected = item.visible) { UiUpgrades.toggleShelf(item.name) }
        }
    }
}

@Composable
private fun NavigationTab() {
    SubLabel("Indicator")
    ChipChoice(NavIndicatorStyle.entries, LiquidGlassStudio.navIndicatorStyle, { it.label }) { LiquidGlassStudio.updateGlowAndStyle(navStyle = it) }
    SubLabel("Labels")
    ChipChoice(DockLabelMode.entries, UiUpgrades.dockLabelMode, { it.label }) { UiUpgrades.updateDockLabelMode(it) }
    SubLabel("Tabs · hold and drag to reorder")
    UiUpgrades.currentDock().forEach { item ->
        Row(Modifier.fillMaxWidth().reorderOnLongPress { UiUpgrades.moveDockTab(item.name, it) }, verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { UiUpgrades.moveDockTab(item.name, -1) }) { Text("Left") }
            TextButton(onClick = { UiUpgrades.moveDockTab(item.name, 1) }) { Text("Right") }
            YtxChip(label = if (item.visible) "Shown" else "Hidden", selected = item.visible) { UiUpgrades.toggleDockTab(item.name) }
        }
    }
    YtxNote("Home stays visible and at least two tabs must remain.")
}

@Composable
private fun AnimationTab() {
    SubLabel("Speed")
    ChipChoice(AnimationTimelinePreset.entries, LiquidGlassStudio.animationPreset, { it.label }) { LiquidGlassStudio.updateGlowAndStyle(animTimeline = it) }
    YtxNote("Scales page transitions and floating panels. Currently ${(LiquidGlassStudio.animationPreset.durationMultiplier * 100).toInt()}% of normal length.")
    YtxSwitchRow("Animated glow", "Neon borders drift between the two glow colours", LiquidGlassStudio.animatedBackgroundsEnabled) { LiquidGlassStudio.updateGlowAndStyle(animBg = it) }
    YtxSwitchRow("Bouncy player buttons", "Springy press feedback in the YTX Style player", CutePlayerPreferences.bouncyAnimations) { CutePlayerPreferences.updateBouncyAnimations(it) }
}

@Composable
private fun PresetsTab() {
    SubLabel("Theme packages")
    LiquidGlassStudio.communityThemePackages.forEach { pkg ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pkg.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("by ${pkg.author}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { LiquidGlassStudio.applyThemePackage(pkg) }) { Text("Apply") }
        }
    }
    SubLabel("Home looks")
    ChipChoice(HomePreset.entries, HomePresetLogic.matching(UiUpgrades.homeCardStyle, UiUpgrades.dockLabelMode, UiUpgrades.homeShelves) ?: HomePreset.Classic, { it.label }) { UiUpgrades.applyHomePreset(it) }
    SubLabel("Reset")
    OutlinedButton(onClick = { LiquidGlassStudio.resetAll() }) { Text("Reset glass and animation to defaults") }
}

@Composable
private fun ProfilesTab() {
    var name by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    SubLabel("Save the current look")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = {
            val ok = InterfaceProfiles.saveCurrent(name)
            msg = if (ok) "Saved." else "You can keep up to ${InterfaceProfileLogic.MAX_PROFILES} profiles. Delete one first."
            if (ok) name = ""
        }) { Text("Save") }
    }
    msg?.let { YtxNote(it) }
    SubLabel("Saved profiles")
    if (InterfaceProfiles.saved.isEmpty()) YtxNote("Nothing saved yet. Saving with a name that exists replaces that profile.")
    InterfaceProfiles.saved.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${PlayerEngineMode.entries.firstOrNull { it.name == p.playerEngine }?.title ?: "Player"} · ${p.homeCardStyle} cards · ${p.navIndicator}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { InterfaceProfiles.apply(p); msg = "Applied '${p.name}'." }) { Text("Apply") }
            TextButton(onClick = { InterfaceProfiles.delete(p.name) }) { Text("Delete") }
        }
    }
}

@Composable
private fun JsonTab() {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    SubLabel("Export")
    Button(onClick = {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("YTX interface profile", InterfaceProfileLogic.toJson(InterfaceProfiles.capture("My interface"))))
        msg = "Copied the current interface as JSON."
    }) { Text("Copy current interface JSON") }
    SubLabel("Import")
    OutlinedTextField(text, { text = it }, label = { Text("Paste an interface profile") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val r = InterfaceProfileLogic.fromJson(text)
            val p = r.profile
            if (p == null) msg = "Not imported: " + r.errors.joinToString(" ")
            else {
                InterfaceProfiles.apply(p)
                msg = "Applied '${p.name}'." + if (r.notes.isNotEmpty()) " " + r.notes.joinToString(" ") else ""
            }
        }) { Text("Apply") }
        OutlinedButton(onClick = {
            val r = InterfaceProfileLogic.fromJson(text)
            val p = r.profile
            msg = when {
                p == null -> "Not imported: " + r.errors.joinToString(" ")
                InterfaceProfiles.saveImported(p) -> "Saved to Profiles as a new entry."
                else -> "Profile list is full. Delete one first."
            }
        }) { Text("Save to profiles") }
    }
    msg?.let { YtxNote(it) }
}
