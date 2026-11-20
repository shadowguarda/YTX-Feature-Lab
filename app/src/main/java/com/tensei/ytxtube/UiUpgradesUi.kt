package com.tensei.ytxtube

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ---------------------------------------------------------------------------------------------
// Artwork colour
// ---------------------------------------------------------------------------------------------

/** Loads [url] small and picks its colour: the most vivid one when [vivid], otherwise the plain average. */
internal suspend fun loadArtworkColor(context: Context, url: String, vivid: Boolean): Color? = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context).data(url).size(48).allowHardware(false).build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap(24, 24)
            ?: return@runCatching null
        val pixels = IntArray(24 * 24)
        bitmap.getPixels(pixels, 0, 24, 0, 0, 24, 24)
        val fallback = 0xFF444444.toInt()
        Color(if (vivid) ArtworkColorLogic.dominant(pixels, fallback) else ArtworkColorLogic.average(pixels, fallback))
    }.getOrNull()
}


// ---------------------------------------------------------------------------------------------
// Ambient wallpaper
// ---------------------------------------------------------------------------------------------

/**
 * A slowly drifting, blurred copy of the current artwork behind the app, tinted with its colour.
 * Before Android 12 (no blur) only the colour wash is drawn.
 */
@Composable
fun AmbientWallpaper(thumbnail: String?, animated: Boolean, modifier: Modifier = Modifier) {
    if (!UiUpgrades.wallpaperEnabled || thumbnail == null) return
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var tint by remember(thumbnail) { mutableStateOf<Color?>(null) }
    LaunchedEffect(thumbnail) { tint = loadArtworkColor(context, thumbnail, vivid = true) }
    val shown by animateColorAsState(tint ?: c.primaryContainer, tween(800), label = "wallpaperTint")
    val drift = if (animated) {
        val transition = rememberInfiniteTransition(label = "wallpaperDrift")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "drift",
        )
        value
    } else {
        0.5f
    }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(shown.copy(alpha = 0.55f), shown.copy(alpha = 0.2f), Color.Transparent)),
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Crossfade(targetState = thumbnail, animationSpec = tween(900), label = "wallpaperArt") { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = 1.35f + 0.1f * drift
                            scaleX = s
                            scaleY = s
                            translationX = (drift - 0.5f) * 80f
                            translationY = (0.5f - drift) * 60f
                            alpha = 0.45f
                        }
                        .blur(56.dp),
                )
            }
        }
        // Keeps text readable on top of any artwork.
        Box(Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.35f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Interactive mini player: swipe left for the next video, right to restart
// ---------------------------------------------------------------------------------------------

@Composable
fun Modifier.miniPlayerSwipe(player: PlayerState, enabled: Boolean): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    if (!enabled) return this
    return this
        .graphicsLayer { translationX = offset.value }
        .pointerInput(player) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val moved = offset.value
                    scope.launch {
                        if (abs(moved) > threshold) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (moved < 0f) player.skipNext() else player.skipPrevious()
                        }
                        offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
            ) { change, drag ->
                change.consume()
                scope.launch { offset.snapTo((offset.value + drag * 0.6f).coerceIn(-threshold * 1.6f, threshold * 1.6f)) }
            }
        }
}

// ---------------------------------------------------------------------------------------------
// Quick Settings panel
// ---------------------------------------------------------------------------------------------

private data class QuickTile(val label: String, val on: Boolean, val onToggle: () -> Unit)

@Composable
fun QuickSettingsPanel(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val none = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = UiUpgrades.quickPanelOpen,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = none, indication = null) { UiUpgrades.quickPanelOpen = false },
        ) {
            val tiles = listOf(
                QuickTile("Incognito", Tweaks.incognito.value) { Tweaks.incognito.update(!Tweaks.incognito.value) },
                QuickTile("Pause watch history", Tweaks.pauseWatchHistory.value) { Tweaks.pauseWatchHistory.update(!Tweaks.pauseWatchHistory.value) },
                QuickTile("Liquid glass", GlassSettings.enabled) { GlassSettings.updateEnabled(!GlassSettings.enabled) },
                QuickTile("Ambient glow", AmbientSettings.enabled) { AmbientSettings.updateEnabled(!AmbientSettings.enabled) },
                QuickTile("Dynamic island", UiUpgrades.islandEnabled) { UiUpgrades.updateIslandEnabled(!UiUpgrades.islandEnabled) },
                QuickTile("Artwork wallpaper", UiUpgrades.wallpaperEnabled) { UiUpgrades.updateWallpaperEnabled(!UiUpgrades.wallpaperEnabled) },
                QuickTile("Artwork colours in player", UiUpgrades.dynamicArtwork) { UiUpgrades.updateDynamicArtwork(!UiUpgrades.dynamicArtwork) },
                QuickTile("Mini player swipes", UiUpgrades.miniSwipe) { UiUpgrades.updateMiniSwipe(!UiUpgrades.miniSwipe) },
                QuickTile("Icon-only dock", Tweaks.iconOnlyNav.value) { Tweaks.iconOnlyNav.update(!Tweaks.iconOnlyNav.value) },
            )
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.surfaceContainerHigh)
                    .clickable(interactionSource = none, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Quick settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { UiUpgrades.quickPanelOpen = false }) { Text("Done") }
                }
                Spacer(Modifier.height(8.dp))
                tiles.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { tile -> QuickTileView(tile, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                YtxSectionLabel("Theme")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark", "amoled" to "AMOLED").forEach { (value, label) ->
                        YtxChip(label = label, selected = ThemeSettings.mode == value) { ThemeSettings.chooseMode(value) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YtxChip(label = "Customize layout", selected = false) {
                        UiUpgrades.quickPanelOpen = false
                        UiUpgrades.layoutSheetOpen = true
                    }
                    YtxChip(label = "All settings", selected = false) {
                        UiUpgrades.quickPanelOpen = false
                        onOpenSettings()
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTileView(tile: QuickTile, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (tile.on) c.primaryContainer else c.surfaceContainerHighest)
            .toggleable(value = tile.on, role = Role.Switch, onValueChange = { tile.onToggle() })
            .padding(12.dp),
    ) {
        Text(tile.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (tile.on) "On" else "Off", fontSize = 11.sp, color = c.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------
// Layout sheet: dock tabs and home shelves
// ---------------------------------------------------------------------------------------------

/**
 * Long-press a row and drag vertically to move it by one or more positions.
 * The explicit Up/Down buttons remain available as an accessible alternative.
 */
fun Modifier.reorderOnLongPress(onMove: (Int) -> Unit): Modifier {
    val thresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    return pointerInput(onMove, thresholdPx) {
        var totalY = 0f
        detectDragGesturesAfterLongPress(
            onDragStart = { totalY = 0f },
            onDragEnd = {
                val steps = (totalY / thresholdPx).toInt().coerceIn(-8, 8)
                if (steps != 0) onMove(steps)
            },
            onDragCancel = { totalY = 0f },
        ) { change, dragAmount ->
            totalY += dragAmount.y
            change.consume()
        }
    }
}

@Composable
private fun LayoutRow(
    title: String,
    subtitle: String?,
    visible: Boolean,
    canToggle: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).reorderOnLongPress { onMove(it) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = c.onSurfaceVariant)
        }
        TextButton(onClick = onUp, enabled = canUp, modifier = Modifier.semantics { contentDescription = "Move $title up" }) { Text("Up") }
        TextButton(onClick = onDown, enabled = canDown, modifier = Modifier.semantics { contentDescription = "Move $title down" }) { Text("Down") }
        Switch(
            checked = visible,
            onCheckedChange = { onToggle() },
            enabled = canToggle,
            modifier = Modifier.semantics { contentDescription = "Show $title" },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutCustomizeSheet(onDismiss: () -> Unit) {
    val dock = UiUpgrades.currentDock()
    val shelves = UiUpgrades.homeShelves
    val visibleTabs = dock.count { it.visible }
    FloatingSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Customize layout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            YtxSectionLabel("Bottom dock")
            YtxNote("Show or hide tabs and change their order. Long-press a row and drag it; Up/Down buttons are also available. Home always stays, and at least two tabs remain.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DockLabelMode.entries.forEach { m ->
                    YtxChip(label = m.label, selected = UiUpgrades.dockLabelMode == m) { UiUpgrades.updateDockLabelMode(m) }
                }
            }
            Spacer(Modifier.height(6.dp))
            dock.forEachIndexed { i, item ->
                val tab = Tab.entries.firstOrNull { it.name == item.name }
                LayoutRow(
                    title = tab?.label ?: item.name,
                    subtitle = if (item.name == NavDockLogic.LOCKED_TAB) "Always shown" else null,
                    visible = item.visible,
                    canToggle = item.name != NavDockLogic.LOCKED_TAB && (!item.visible || visibleTabs > NavDockLogic.MIN_VISIBLE_TABS),
                    canUp = i > 0,
                    canDown = i < dock.lastIndex,
                    onToggle = { UiUpgrades.toggleDockTab(item.name) },
                    onUp = { UiUpgrades.moveDockTab(item.name, -1) },
                    onDown = { UiUpgrades.moveDockTab(item.name, 1) },
                )
            }
            YtxSectionLabel("Dynamic Island")
            YtxNote("Position, size, colour, gestures, downloads and more have their own sheet.")
            var islandSheet by remember { mutableStateOf(false) }
            TextButton(onClick = { islandSheet = true }) { Text("Open Dynamic Island settings") }
            if (islandSheet) IslandSettingsSheet(onDismiss = { islandSheet = false })
            YtxSectionLabel("Home presets")
            val activePreset = HomePresetLogic.matching(UiUpgrades.homeCardStyle, UiUpgrades.dockLabelMode, UiUpgrades.homeShelves)
            YtxNote(activePreset?.blurb ?: "Custom layout. Pick a preset to replace card style, shelves and dock labels in one go.")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomePreset.entries.forEach { pr ->
                    YtxChip(label = pr.label, selected = activePreset == pr) { UiUpgrades.applyHomePreset(pr) }
                }
            }
            YtxSectionLabel("Home video cards")
            YtxNote("How videos look in the home feed.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedCardStyle.entries.forEach { st ->
                    YtxChip(label = st.label, selected = UiUpgrades.homeCardStyle == st) { UiUpgrades.updateHomeCardStyle(st) }
                }
            }
            YtxSectionLabel("Home shelves")
            YtxNote("Rows shown above your home feed. Long-press a row and drag it to reorder. A shelf with nothing to show stays out of the way.")
            shelves.forEachIndexed { i, item ->
                LayoutRow(
                    title = HomeLayoutLogic.label(item.name),
                    subtitle = HomeLayoutLogic.blurb(item.name),
                    visible = item.visible,
                    canToggle = true,
                    canUp = i > 0,
                    canDown = i < shelves.lastIndex,
                    onToggle = { UiUpgrades.toggleShelf(item.name) },
                    onUp = { UiUpgrades.moveShelf(item.name, -1) },
                    onDown = { UiUpgrades.moveShelf(item.name, 1) },
                )
            }
            Spacer(Modifier.height(10.dp))
            YtxChip(label = "Reset layout", selected = false) { UiUpgrades.resetLayout() }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Home shelves
// ---------------------------------------------------------------------------------------------

private fun thumbFor(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

@Composable
private fun ShelfRow(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        content()
    }
}

@Composable
private fun ShelfCard(video: VideoItem, progress: Float?, onOpen: (VideoItem) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Play ${video.title}") { onOpen(video) },
    ) {
        Thumbnail(video.thumbnail, Modifier.fillMaxWidth().height(94.dp), radius = 14, videoId = video.id)
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = c.primary,
                trackColor = c.onSurfaceVariant.copy(alpha = 0.2f),
                drawStopIndicator = {},
            )
        }
        Text(
            video.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(video.channel, fontSize = 11.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShelfList(videos: List<VideoItem>, progress: (VideoItem) -> Float? = { null }, onOpen: (VideoItem) -> Unit) {
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos, key = { it.id }) { v -> ShelfCard(v, progress(v), onOpen) }
    }
}

@Composable
private fun ContinueShelf(onOpen: (VideoItem) -> Unit) {
    val sessions by WatchLog.sessions.collectAsState()
    val items = HomeLayoutLogic.continueWatching(WatchInsightsLogic.videos(sessions), { ResumeStore.progress(it) })
    if (items.isEmpty()) return
    ShelfRow(HomeLayoutLogic.label(HomeLayoutLogic.CONTINUE)) {
        val videos = items.map { VideoItem(it.videoId, it.title, it.channel, null, thumbFor(it.videoId), null, 0L, 0L, null) }
        ShelfList(videos, progress = { ResumeStore.progress(it.id) }, onOpen = onOpen)
    }
}

@Composable
private fun SubscriptionsShelf(lib: LibraryData, onOpen: (VideoItem) -> Unit) {
    if (!Account.signedIn) return
    LaunchedEffect(Unit) { lib.subscriptions.ensureLoaded() }
    val videos = lib.subscriptions.videos.filter { !it.isLive }.take(12)
    if (videos.isEmpty()) return
    ShelfRow(HomeLayoutLogic.label(HomeLayoutLogic.SUBSCRIPTIONS)) { ShelfList(videos, onOpen = onOpen) }
}

@Composable
private fun PlannedShelf(onOpen: (VideoItem) -> Unit) {
    val planned by WatchPlanManager.items.collectAsState()
    val videos = planned.filter { !it.done }.sortedBy { it.dueAtMs }.take(12).map {
        VideoItem(it.id, it.title, it.channel, null, it.thumbnail ?: thumbFor(it.id), null, it.durationSec, 0L, null)
    }
    if (videos.isEmpty()) return
    ShelfRow(HomeLayoutLogic.label(HomeLayoutLogic.PLANNED)) { ShelfList(videos, onOpen = onOpen) }
}

/** The shelves above the home feed, in the order and visibility chosen in Customize layout. */
fun LazyListScope.homeShelfItems(lib: LibraryData, onOpen: (VideoItem) -> Unit) {
    OrderedLayoutLogic.visibleNames(UiUpgrades.homeShelves).forEach { name ->
        item(key = "shelf_$name") {
            when (name) {
                HomeLayoutLogic.CONTINUE -> ContinueShelf(onOpen)
                HomeLayoutLogic.SUBSCRIPTIONS -> SubscriptionsShelf(lib, onOpen)
                HomeLayoutLogic.PLANNED -> PlannedShelf(onOpen)
            }
        }
    }
}
