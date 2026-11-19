package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The floating "Dynamic Island" pill, in the spirit of Smart Island: a small pill that expands into a
 * card, shows what is happening (playback, downloads), reacts to swipes and can be styled. It lives
 * inside YTXTube; it is not a system-wide overlay.
 */
enum class IslandPosition(val label: String) { Center("Center"), Left("Left"), Right("Right") }

enum class IslandSize(val label: String, val scale: Float) {
    Compact("Compact", 0.88f),
    Regular("Regular", 1f),
    Large("Large", 1.14f),
}

enum class IslandColor(val label: String) { Black("Black"), Artwork("Artwork"), Accent("Accent") }

enum class IslandSpeed(val label: String, val factor: Float) {
    Off("Off", 0f),
    Fast("Fast", 0.6f),
    Normal("Normal", 1f),
    Relaxed("Relaxed", 1.5f),
}

enum class IslandAction(val label: String) {
    None("Nothing"),
    PlayPause("Play / pause"),
    Next("Next video"),
    Restart("Restart video"),
    Expand("Expand / collapse"),
    Hide("Hide for now"),
    OpenPlayer("Open player"),
}

enum class IslandGesture(val label: String) {
    Tap("Tap"),
    LongPress("Long press"),
    SwipeLeft("Swipe left"),
    SwipeRight("Swipe right"),
    SwipeUp("Swipe up"),
    SwipeDown("Swipe down"),
}

enum class IslandKind { Media, Download }

data class DownloadSnap(val downloading: Boolean, val downloaded: Long, val total: Long, val speedBps: Long)

data class DownloadIslandSummary(val count: Int, val fraction: Float, val speedBps: Long, val etaSeconds: Long)

object SmartIslandLogic {
    fun defaultGestures(): Map<IslandGesture, IslandAction> = mapOf(
        IslandGesture.Tap to IslandAction.Expand,
        IslandGesture.LongPress to IslandAction.OpenPlayer,
        IslandGesture.SwipeLeft to IslandAction.Next,
        IslandGesture.SwipeRight to IslandAction.Restart,
        IslandGesture.SwipeUp to IslandAction.Hide,
        IslandGesture.SwipeDown to IslandAction.Expand,
    )

    fun serializeGestures(map: Map<IslandGesture, IslandAction>): String =
        IslandGesture.entries.joinToString(",") { "${it.name}:${(map[it] ?: IslandAction.None).name}" }

    /** Unknown or missing entries keep their default. */
    fun parseGestures(raw: String?): Map<IslandGesture, IslandAction> {
        val out = defaultGestures().toMutableMap()
        raw.orEmpty().split(',').forEach { part ->
            val bits = part.split(':')
            val gesture = IslandGesture.entries.find { it.name == bits.getOrNull(0)?.trim() } ?: return@forEach
            val action = IslandAction.entries.find { it.name == bits.getOrNull(1)?.trim() } ?: return@forEach
            out[gesture] = action
        }
        return out
    }

    /** The swipe a drag of ([dx], [dy]) counts as, or null when it is too short. */
    fun direction(dx: Float, dy: Float, threshold: Float): IslandGesture? {
        if (maxOf(abs(dx), abs(dy)) < threshold) return null
        return if (abs(dx) >= abs(dy)) {
            if (dx < 0f) IslandGesture.SwipeLeft else IslandGesture.SwipeRight
        } else {
            if (dy < 0f) IslandGesture.SwipeUp else IslandGesture.SwipeDown
        }
    }

    /** Length in ms for an animation that takes [baseMs] at normal speed; 0 means no animation. */
    fun animMs(baseMs: Int, speed: IslandSpeed): Int = (baseMs * speed.factor).toInt()

    /** With several events active they take turns: [tick] picks which one is shown. */
    fun pickEvent(kinds: List<IslandKind>, tick: Int): IslandKind? =
        if (kinds.isEmpty()) null else kinds[Math.floorMod(tick, kinds.size)]

    /** All running downloads in one line, or null when none is running. */
    fun downloadSummary(tasks: List<DownloadSnap>): DownloadIslandSummary? {
        val active = tasks.filter { it.downloading }
        if (active.isEmpty()) return null
        val known = active.filter { it.total > 0L }
        val total = known.sumOf { it.total }
        val done = known.sumOf { it.downloaded.coerceIn(0L, it.total) }
        val speed = active.sumOf { it.speedBps }
        val fraction = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
        val eta = DownloadProgressLogic.etaSeconds(total - done, 0L, speed)
        return DownloadIslandSummary(active.size, fraction, speed, eta)
    }

    /** Pill colour as #RRGGBB: black, or black mixed with the artwork or accent colour. */
    fun background(color: IslandColor, artworkHex: String?, accentHex: String): String {
        val base = "#0B0B0D"
        return when (color) {
            IslandColor.Black -> base
            IslandColor.Artwork -> if (artworkHex != null) ThemeAccentLogic.blendHex(base, artworkHex, 0.45f) else base
            IslandColor.Accent -> ThemeAccentLogic.blendHex(base, accentHex, 0.5f)
        }
    }

    fun colorToHex(color: Color): String = String.format(java.util.Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)
}

object IslandSettings {
    private const val PREFS = "smart_island"
    private var prefs: SharedPreferences? = null

    var position by mutableStateOf(IslandPosition.Center)
        private set
    var size by mutableStateOf(IslandSize.Regular)
        private set
    var color by mutableStateOf(IslandColor.Black)
        private set
    var speed by mutableStateOf(IslandSpeed.Normal)
        private set
    var cornerDp by mutableFloatStateOf(28f)
        private set
    var opacity by mutableFloatStateOf(0.95f)
        private set
    var offsetDp by mutableFloatStateOf(6f)
        private set
    /** Seconds the pill stays after playback pauses; 0 hides it at once. */
    var lingerSeconds by mutableIntStateOf(4)
        private set
    var showMedia by mutableStateOf(true)
        private set
    var showDownloads by mutableStateOf(true)
        private set
    var haptics by mutableStateOf(true)
        private set
    var neonBorder by mutableStateOf(true)
        private set
    var gestures by mutableStateOf(SmartIslandLogic.defaultGestures())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        position = IslandPosition.entries.find { it.name == p.getString("position", null) } ?: IslandPosition.Center
        size = IslandSize.entries.find { it.name == p.getString("size", null) } ?: IslandSize.Regular
        color = IslandColor.entries.find { it.name == p.getString("color", null) } ?: IslandColor.Black
        speed = IslandSpeed.entries.find { it.name == p.getString("speed", null) } ?: IslandSpeed.Normal
        cornerDp = p.getFloat("corner", 28f).coerceIn(8f, 30f)
        opacity = p.getFloat("opacity", 0.95f).coerceIn(0.5f, 1f)
        offsetDp = p.getFloat("offset", 6f).coerceIn(0f, 48f)
        lingerSeconds = p.getInt("linger", 4).coerceIn(0, 30)
        showMedia = p.getBoolean("media", true)
        showDownloads = p.getBoolean("downloads", true)
        haptics = p.getBoolean("haptics", true)
        neonBorder = p.getBoolean("neon", true)
        gestures = SmartIslandLogic.parseGestures(p.getString("gestures", null))
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) { prefs?.edit()?.apply(block)?.apply() }

    fun updatePosition(v: IslandPosition) { position = v; edit { putString("position", v.name) } }
    fun updateSize(v: IslandSize) { size = v; edit { putString("size", v.name) } }
    fun updateColor(v: IslandColor) { color = v; edit { putString("color", v.name) } }
    fun updateSpeed(v: IslandSpeed) { speed = v; edit { putString("speed", v.name) } }
    fun updateCornerDp(v: Float) { cornerDp = v.coerceIn(8f, 30f); edit { putFloat("corner", cornerDp) } }
    fun updateOpacity(v: Float) { opacity = v.coerceIn(0.5f, 1f); edit { putFloat("opacity", opacity) } }
    fun updateOffsetDp(v: Float) { offsetDp = v.coerceIn(0f, 48f); edit { putFloat("offset", offsetDp) } }
    fun updateLingerSeconds(v: Int) { lingerSeconds = v.coerceIn(0, 30); edit { putInt("linger", lingerSeconds) } }
    fun updateShowMedia(v: Boolean) { showMedia = v; edit { putBoolean("media", v) } }
    fun updateShowDownloads(v: Boolean) { showDownloads = v; edit { putBoolean("downloads", v) } }
    fun updateHaptics(v: Boolean) { haptics = v; edit { putBoolean("haptics", v) } }
    fun updateNeonBorder(v: Boolean) { neonBorder = v; edit { putBoolean("neon", v) } }

    fun updateGesture(gesture: IslandGesture, action: IslandAction) {
        gestures = gestures + (gesture to action)
        edit { putString("gestures", SmartIslandLogic.serializeGestures(gestures)) }
    }

    fun reset() {
        updatePosition(IslandPosition.Center); updateSize(IslandSize.Regular); updateColor(IslandColor.Black)
        updateSpeed(IslandSpeed.Normal); updateCornerDp(28f); updateOpacity(0.95f); updateOffsetDp(6f)
        updateLingerSeconds(4); updateShowMedia(true); updateShowDownloads(true); updateHaptics(true); updateNeonBorder(true)
        gestures = SmartIslandLogic.defaultGestures()
        edit { putString("gestures", SmartIslandLogic.serializeGestures(gestures)) }
    }
}

/** The pill's surface: colour, corner, border and size from the island settings. Used by the real island and the preview. */
@Composable
private fun IslandSurface(
    expanded: Boolean,
    artworkHex: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accentHex = SmartIslandLogic.colorToHex(MaterialTheme.colorScheme.primary)
    val bg = Color(android.graphics.Color.parseColor(SmartIslandLogic.background(IslandSettings.color, artworkHex, accentHex)))
    val radius = if (expanded) minOf(IslandSettings.cornerDp, 28f) else IslandSettings.cornerDp
    val shape = RoundedCornerShape(radius.dp)
    val ms = SmartIslandLogic.animMs(260, IslandSettings.speed)
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * IslandSettings.size.scale, base.fontScale)) {
        Box(
            modifier
                .widthIn(max = if (expanded) 360.dp else 320.dp)
                .animateContentSize(if (ms == 0) snap() else spring(dampingRatio = 0.78f, stiffness = 420f / IslandSettings.speed.factor))
                .clip(shape)
                .background(bg.copy(alpha = IslandSettings.opacity))
                .then(
                    if (IslandSettings.neonBorder && LiquidGlassStudio.neonBorderEnabled) {
                        Modifier.border(1.dp, LiquidGlassStudio.borderGradientBrush(), shape)
                    } else Modifier,
                ),
        ) { content() }
    }
}

@Composable
private fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val arc = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arc, style = stroke)
            drawArc(Color.White, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = stroke)
        }
        content()
    }
}

@Composable
fun DynamicIsland(
    player: PlayerState,
    visible: Boolean,
    onOpenPlayer: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val item = player.current

    // Playback counts as active while playing and for a few seconds after a pause.
    var hold by remember { mutableStateOf(false) }
    LaunchedEffect(player.isPlaying, item?.id, IslandSettings.lingerSeconds) {
        if (player.isPlaying) hold = true else if (hold) {
            delay(IslandSettings.lingerSeconds * 1_000L)
            hold = false
        }
    }
    var hiddenFor by remember { mutableStateOf<String?>(null) }
    var hideDownloads by remember { mutableStateOf(false) }

    val downloads = SmartIslandLogic.downloadSummary(
        Downloads.tasks.map { DownloadSnap(it.status == DownloadStatus.Downloading, it.downloaded, it.total, it.speedBps) },
    )
    LaunchedEffect(downloads == null) { if (downloads == null) hideDownloads = false }

    val kinds = buildList {
        if (IslandSettings.showMedia && item != null && (player.isPlaying || hold) && hiddenFor != item.id) add(IslandKind.Media)
        if (IslandSettings.showDownloads && downloads != null && !hideDownloads) add(IslandKind.Download)
    }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(kinds.size) {
        while (kinds.size > 1) {
            delay(6_000)
            tick++
        }
    }
    val kind = SmartIslandLogic.pickEvent(kinds, tick)
    val show = UiUpgrades.islandEnabled && visible && kind != null

    var expanded by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded, touches) {
        if (expanded) {
            delay(8_000)
            expanded = false
        }
    }
    LaunchedEffect(show) { if (!show) expanded = false }

    var artworkHex by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item?.thumbnail, IslandSettings.color) {
        val thumb = item?.thumbnail
        artworkHex = if (thumb != null && IslandSettings.color == IslandColor.Artwork) {
            loadArtworkColor(context, thumb, vivid = true)?.let { SmartIslandLogic.colorToHex(it) }
        } else null
    }

    val perform: (IslandGesture) -> Unit = { gesture ->
        val action = IslandSettings.gestures[gesture] ?: IslandAction.None
        if (action != IslandAction.None && IslandSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        touches++
        when (action) {
            IslandAction.None -> Unit
            IslandAction.PlayPause -> if (item != null) player.togglePlay()
            IslandAction.Next -> if (item != null) player.skipNext()
            IslandAction.Restart -> if (item != null) player.skipPrevious()
            IslandAction.Expand -> expanded = !expanded
            IslandAction.Hide -> {
                expanded = false
                if (kind == IslandKind.Media) hiddenFor = item?.id else hideDownloads = true
            }
            IslandAction.OpenPlayer -> if (kind == IslandKind.Download) onOpenDownloads() else onOpenPlayer()
        }
    }

    val enterMs = SmartIslandLogic.animMs(260, IslandSettings.speed)
    val alignment = when (IslandSettings.position) {
        IslandPosition.Center -> Alignment.TopCenter
        IslandPosition.Left -> Alignment.TopStart
        IslandPosition.Right -> Alignment.TopEnd
    }
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = if (enterMs == 0) fadeIn(snap()) else
            slideInVertically(tween(enterMs)) { -it } + fadeIn(tween(enterMs)) + scaleIn(tween(enterMs), initialScale = 0.8f),
        exit = if (enterMs == 0) fadeOut(snap()) else
            slideOutVertically(tween(enterMs)) { -it } + fadeOut(tween(enterMs)) + scaleOut(tween(enterMs), targetScale = 0.85f),
    ) {
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = IslandSettings.offsetDp.dp, start = 12.dp, end = 12.dp),
            contentAlignment = alignment,
        ) {
            val threshold = with(LocalDensity.current) { 36.dp.toPx() }
            IslandSurface(
                expanded = expanded,
                artworkHex = artworkHex,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { perform(IslandGesture.Tap) }, onLongPress = { perform(IslandGesture.LongPress) })
                    }
                    .pointerInput(Unit) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f },
                            onDragEnd = { SmartIslandLogic.direction(dx, dy, threshold)?.let(perform) },
                        ) { change, drag ->
                            change.consume()
                            dx += drag.x
                            dy += drag.y
                        }
                    },
            ) {
                when (kind) {
                    IslandKind.Media -> if (item != null) MediaIsland(player, item, expanded, onOpenPlayer) { touches++ }
                    IslandKind.Download -> if (downloads != null) DownloadIsland(downloads, expanded, onOpenDownloads)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun MediaIsland(player: PlayerState, item: VideoItem, expanded: Boolean, onOpenPlayer: () -> Unit, onTouch: () -> Unit) {
    val fraction = IslandLogic.fraction(player.positionMs, player.durationMs)
    if (!expanded) {
        Row(Modifier.height(52.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction, Modifier.size(40.dp)) {
                Thumbnail(item.thumbnail, Modifier.size(30.dp).clip(CircleShape), radius = 15, videoId = item.id)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(item.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.channel, color = Color.White.copy(alpha = 0.65f), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onTouch(); player.togglePlay() }, modifier = Modifier.size(40.dp)) {
                Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.isPlaying) "Pause" else "Play", tint = Color.White)
            }
            IconButton(onClick = { onTouch(); player.skipNext() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White)
            }
        }
        return
    }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(item.thumbnail, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), radius = 14, videoId = item.id)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.channel, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Slider(
            value = if (dragging) dragValue else fraction,
            onValueChange = { dragging = true; dragValue = it; onTouch() },
            onValueChangeFinished = {
                if (player.durationMs > 0) player.seekTo((dragValue * player.durationMs).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onTouch(); player.skipPrevious() }) { Icon(Icons.Rounded.Replay, "Restart", tint = Color.White) }
            IconButton(onClick = { onTouch(); player.togglePlay() }, modifier = Modifier.size(52.dp)) {
                Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { onTouch(); player.skipNext() }) { Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White) }
            TextButton(onClick = onOpenPlayer) { Text("Open", color = Color.White) }
        }
    }
}

@Composable
private fun DownloadIsland(summary: DownloadIslandSummary, expanded: Boolean, onOpenDownloads: () -> Unit) {
    val line = listOf(
        DownloadProgressLogic.formatSpeed(summary.speedBps),
        DownloadProgressLogic.formatEta(summary.etaSeconds),
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    val title = "Downloading ${summary.count} video${if (summary.count == 1) "" else "s"}"
    if (!expanded) {
        Row(Modifier.height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(summary.fraction, Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(end = 8.dp)) {
                Text(title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (line.isNotEmpty()) Text(line, color = Color.White.copy(alpha = 0.65f), fontSize = 10.5.sp, maxLines = 1)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (line.isNotEmpty()) Text(line, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Downloads.tasks.filter { it.status == DownloadStatus.Downloading }.take(3).forEach { t ->
            val p = if (t.total > 0L) (t.downloaded.toFloat() / t.total).coerceIn(0f, 1f) else 0f
            Text(t.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(3.dp),
                color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), drawStopIndicator = {},
            )
        }
        TextButton(onClick = onOpenDownloads) { Text("Open downloads", color = Color.White) }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslandSettingsSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    FloatingSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Dynamic island", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            YtxNote("A floating pill inside YTXTube that shows playback and downloads. Swipe it, tap it to expand it, and style it below.")
            YtxSwitchRow("Show the island", "Master switch", UiUpgrades.islandEnabled) { UiUpgrades.updateIslandEnabled(it) }

            YtxSectionLabel("Preview")
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = when (IslandSettings.position) {
                IslandPosition.Center -> Alignment.Center
                IslandPosition.Left -> Alignment.CenterStart
                IslandPosition.Right -> Alignment.CenterEnd
            }) {
                IslandSurface(expanded = false, artworkHex = null) {
                    Row(Modifier.height(52.dp).padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(0.35f, Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Island preview", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Your settings, live", color = Color.White.copy(alpha = 0.65f), fontSize = 10.5.sp)
                        }
                    }
                }
            }

            YtxSectionLabel("Shows")
            YtxSwitchRow("Playback", "Artwork, progress, play/pause and next", IslandSettings.showMedia) { IslandSettings.updateShowMedia(it) }
            YtxSwitchRow("Downloads", "Overall progress, speed and time left", IslandSettings.showDownloads) { IslandSettings.updateShowDownloads(it) }
            YtxSectionLabel("Stays after a pause")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Hide at once", 4 to "4 s", 10 to "10 s", 30 to "30 s").forEach { (s, label) ->
                    YtxChip(label = label, selected = IslandSettings.lingerSeconds == s) { IslandSettings.updateLingerSeconds(s) }
                }
            }

            YtxSectionLabel("Position")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IslandPosition.entries.forEach { p -> YtxChip(label = p.label, selected = IslandSettings.position == p) { IslandSettings.updatePosition(p) } }
            }
            YtxSliderRow("Distance from the top", IslandSettings.offsetDp, 0f..48f, 0, "${IslandSettings.offsetDp.toInt()} dp") { IslandSettings.updateOffsetDp(it) }
            YtxSectionLabel("Size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IslandSize.entries.forEach { s -> YtxChip(label = s.label, selected = IslandSettings.size == s) { IslandSettings.updateSize(s) } }
            }

            YtxSectionLabel("Look")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IslandColor.entries.forEach { col -> YtxChip(label = col.label, selected = IslandSettings.color == col) { IslandSettings.updateColor(col) } }
            }
            YtxSliderRow("Corner radius", IslandSettings.cornerDp, 8f..30f, 0, "${IslandSettings.cornerDp.toInt()} dp") { IslandSettings.updateCornerDp(it) }
            YtxSliderRow("Opacity", IslandSettings.opacity, 0.5f..1f, 0, "${(IslandSettings.opacity * 100).toInt()}%") { IslandSettings.updateOpacity(it) }
            YtxSwitchRow("Glowing border", "Uses the Liquid Glass Studio border colours", IslandSettings.neonBorder) { IslandSettings.updateNeonBorder(it) }
            YtxSectionLabel("Animation speed")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IslandSpeed.entries.forEach { s -> YtxChip(label = s.label, selected = IslandSettings.speed == s) { IslandSettings.updateSpeed(s) } }
            }

            YtxSectionLabel("Gestures")
            YtxNote("Choose what each gesture does on the island.")
            IslandGesture.entries.forEach { g ->
                Text(g.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IslandAction.entries.forEach { a ->
                        YtxChip(label = a.label, selected = (IslandSettings.gestures[g] ?: IslandAction.None) == a) { IslandSettings.updateGesture(g, a) }
                    }
                }
            }
            YtxSwitchRow("Haptic feedback", "A small buzz when a gesture works", IslandSettings.haptics) { IslandSettings.updateHaptics(it) }
            Spacer(Modifier.height(10.dp))
            YtxChip(label = "Reset island settings", selected = false) { IslandSettings.reset() }
            Text(
                "This island works inside YTXTube. It does not draw over other apps.",
                fontSize = 11.5.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
