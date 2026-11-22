package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * 10 Distinct Selectable Video Player Types, each with its own layout geometry,
 * transport framing, typography feel, and color identity.
 */
enum class PlayerStylePreset(
    val title: String,
    val subtitle: String,
    val badge: String,
    val primaryHex: String,
    val secondaryHex: String,
) {
    KawaiiGlass(
        title = "Kawaii Glass",
        subtitle = "Soft pastel pink & lavender glass pills with bouncy sparkles",
        badge = "Cute",
        primaryHex = "#F472B6",
        secondaryHex = "#A78BFA",
    ),
    NeonLiquid(
        title = "Neon Liquid",
        subtitle = "Signature YTXTube purple/blue translucent Liquid Glass",
        badge = "Signature",
        primaryHex = "#7C3AED",
        secondaryHex = "#3B82F6",
    ),
    DynamicIslandPill(
        title = "Dynamic Pill",
        subtitle = "Compact floating island capsules with ultra-tight spacing",
        badge = "Compact",
        primaryHex = "#38BDF8",
        secondaryHex = "#818CF8",
    ),
    MinimalCinema(
        title = "Minimal Cinema",
        subtitle = "Ultra-clean distraction-free theater controls",
        badge = "Pro",
        primaryHex = "#E2E8F0",
        secondaryHex = "#94A3B8",
    ),
    CyberpunkHud(
        title = "Cyberpunk HUD",
        subtitle = "Chamfered neon cyan/magenta frame with mono time readout",
        badge = "Sci-Fi",
        primaryHex = "#06B6D4",
        secondaryHex = "#EC4899",
    ),
    Y2KPop(
        title = "Y2K Walkman",
        subtitle = "Playful retro-chrome mint & sky-blue double-ringed deck",
        badge = "Retro",
        primaryHex = "#34D399",
        secondaryHex = "#60A5FA",
    ),
    VinylLoFi(
        title = "Vinyl Lo-Fi",
        subtitle = "Warm turntable-inspired center ring with soft gold accents",
        badge = "Chill",
        primaryHex = "#FBBF24",
        secondaryHex = "#D97706",
    ),
    CozyRetro(
        title = "Cozy Studio",
        subtitle = "Warm peach & coral rounded deck with tactile badges",
        badge = "Warm",
        primaryHex = "#FB923C",
        secondaryHex = "#F43F5E",
    ),
    MatchaZen(
        title = "Matcha Zen",
        subtitle = "Botanical sage & cream low-glare calm player interface",
        badge = "Calm",
        primaryHex = "#86EFAC",
        secondaryHex = "#4ADE80",
    ),
    OledPure(
        title = "OLED Pure",
        subtitle = "True-black zero-bleed floating controls for AMOLED screens",
        badge = "AMOLED",
        primaryHex = "#10B981",
        secondaryHex = "#3B82F6",
    ),
}

enum class PlayerSeekbarStyle(val label: String) {
    WavyCute("Wavy Cute"),
    NeonPill("Neon Pill"),
    SlimCinema("Slim Line"),
    SegmentedTicks("Segmented"),
    DottedCandy("Candy Dots"),
}

enum class PlayerButtonShape(val label: String) {
    Circle("Circle"),
    Squircle("Squircle"),
    SoftPill("Soft Pill"),
    Chamfered("Chamfered"),
}

/**
 * Persistent preferences for the Cute & Customizable Video Player.
 */
object CutePlayerPreferences {
    private const val PREFS = "ytx_cute_player"
    private const val KEY_STYLE = "player_style"
    private const val KEY_SEEKBAR = "seekbar_style"
    private const val KEY_BTN_SHAPE = "btn_shape"
    private const val KEY_BOUNCY = "bouncy_animations"
    private const val KEY_AUTO_HIDE_SEC = "auto_hide_sec"
    private const val KEY_DOUBLE_TAP_SEC = "double_tap_sec"
    private const val KEY_SHOW_SPEED = "show_speed"
    private const val KEY_SHOW_QUALITY = "show_quality"
    private const val KEY_SHOW_CAPTIONS = "show_captions"
    private const val KEY_SHOW_LIKE = "show_like"
    private const val KEY_SHOW_LOOP = "show_loop"
    private const val KEY_GLASS_ALPHA = "player_glass_alpha"

    var style by mutableStateOf(PlayerStylePreset.KawaiiGlass)
        private set
    var seekbarStyle by mutableStateOf(PlayerSeekbarStyle.WavyCute)
        private set
    var buttonShape by mutableStateOf(PlayerButtonShape.Squircle)
        private set
    var bouncyAnimations by mutableStateOf(true)
        private set
    var autoHideSeconds by mutableIntStateOf(4)
        private set
    var doubleTapSeekSeconds by mutableIntStateOf(10)
        private set
    var showSpeedChip by mutableStateOf(true)
        private set
    var showQualityChip by mutableStateOf(true)
        private set
    var showCaptionsButton by mutableStateOf(true)
        private set
    var showLikeButton by mutableStateOf(true)
        private set
    var showLoopButton by mutableStateOf(true)
        private set
    var hudGlassAlpha by mutableFloatStateOf(0.44f)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        style = PlayerStylePreset.entries.firstOrNull {
            it.name == prefs?.getString(KEY_STYLE, PlayerStylePreset.KawaiiGlass.name)
        } ?: PlayerStylePreset.KawaiiGlass
        seekbarStyle = PlayerSeekbarStyle.entries.firstOrNull {
            it.name == prefs?.getString(KEY_SEEKBAR, PlayerSeekbarStyle.WavyCute.name)
        } ?: PlayerSeekbarStyle.WavyCute
        buttonShape = PlayerButtonShape.entries.firstOrNull {
            it.name == prefs?.getString(KEY_BTN_SHAPE, PlayerButtonShape.Squircle.name)
        } ?: PlayerButtonShape.Squircle
        bouncyAnimations = prefs?.getBoolean(KEY_BOUNCY, true) ?: true
        autoHideSeconds = prefs?.getInt(KEY_AUTO_HIDE_SEC, 4) ?: 4
        doubleTapSeekSeconds = prefs?.getInt(KEY_DOUBLE_TAP_SEC, 10) ?: 10
        showSpeedChip = prefs?.getBoolean(KEY_SHOW_SPEED, true) ?: true
        showQualityChip = prefs?.getBoolean(KEY_SHOW_QUALITY, true) ?: true
        showCaptionsButton = prefs?.getBoolean(KEY_SHOW_CAPTIONS, true) ?: true
        showLikeButton = prefs?.getBoolean(KEY_SHOW_LIKE, true) ?: true
        showLoopButton = prefs?.getBoolean(KEY_SHOW_LOOP, true) ?: true
        hudGlassAlpha = prefs?.getFloat(KEY_GLASS_ALPHA, 0.44f) ?: 0.44f
    }

    fun updateStyle(value: PlayerStylePreset) {
        style = value
        prefs?.edit()?.putString(KEY_STYLE, value.name)?.apply()
    }

    fun updateSeekbarStyle(value: PlayerSeekbarStyle) {
        seekbarStyle = value
        prefs?.edit()?.putString(KEY_SEEKBAR, value.name)?.apply()
    }

    fun updateButtonShape(value: PlayerButtonShape) {
        buttonShape = value
        prefs?.edit()?.putString(KEY_BTN_SHAPE, value.name)?.apply()
    }

    fun updateBouncyAnimations(value: Boolean) {
        bouncyAnimations = value
        prefs?.edit()?.putBoolean(KEY_BOUNCY, value)?.apply()
    }

    fun updateAutoHideSeconds(sec: Int) {
        autoHideSeconds = sec.coerceIn(0, 10)
        prefs?.edit()?.putInt(KEY_AUTO_HIDE_SEC, autoHideSeconds)?.apply()
    }

    fun updateDoubleTapSeekSeconds(sec: Int) {
        doubleTapSeekSeconds = sec.coerceIn(5, 30)
        prefs?.edit()?.putInt(KEY_DOUBLE_TAP_SEC, doubleTapSeekSeconds)?.apply()
    }

    fun updateShowSpeedChip(value: Boolean) {
        showSpeedChip = value
        prefs?.edit()?.putBoolean(KEY_SHOW_SPEED, value)?.apply()
    }

    fun updateShowQualityChip(value: Boolean) {
        showQualityChip = value
        prefs?.edit()?.putBoolean(KEY_SHOW_QUALITY, value)?.apply()
    }

    fun updateShowCaptionsButton(value: Boolean) {
        showCaptionsButton = value
        prefs?.edit()?.putBoolean(KEY_SHOW_CAPTIONS, value)?.apply()
    }

    fun updateShowLikeButton(value: Boolean) {
        showLikeButton = value
        prefs?.edit()?.putBoolean(KEY_SHOW_LIKE, value)?.apply()
    }

    fun updateShowLoopButton(value: Boolean) {
        showLoopButton = value
        prefs?.edit()?.putBoolean(KEY_SHOW_LOOP, value)?.apply()
    }

    fun updateHudGlassAlpha(value: Float) {
        hudGlassAlpha = value.coerceIn(0.15f, 0.88f)
        prefs?.edit()?.putFloat(KEY_GLASS_ALPHA, hudGlassAlpha)?.apply()
    }
}

/**
 * YTX Style player controls: one clean layout whose colours, button shape and glass follow the chosen
 * look. Top bar (back, title, captions, looks), centre transport, then a scrollable chip row and a
 * single seek row (time, seekbar, duration, fullscreen). Chips hide on very short players.
 */
@Composable
fun CuteModernVideoPlayerOverlay(
    title: String,
    channelName: String,
    isPlaying: Boolean,
    isFullscreen: Boolean,
    isLiked: Boolean,
    isLooping: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    speed: Float,
    qualityLabel: String,
    backdrop: Backdrop?,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleLoop: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenQuality: () -> Unit,
    onToggleCaptions: () -> Unit,
    onOpenCustomizer: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, the host owns tap/double-tap handling and decides when the controls show. */
    externalControlsVisible: Boolean? = null,
    chapters: List<Chapter> = emptyList(),
) {
    val style = CutePlayerPreferences.style
    val primaryColor = YtxNeonPalette.parseHex(style.primaryHex, YtxNeonPalette.VioletCore)
    val secondaryColor = YtxNeonPalette.parseHex(style.secondaryHex, YtxNeonPalette.ElectricBlue)

    var internalVisible by remember { mutableStateOf(true) }
    val controlsVisible = externalControlsVisible ?: internalVisible
    var isLocked by remember { mutableStateOf(false) }
    var doubleTapSide by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(controlsVisible, isPlaying, CutePlayerPreferences.autoHideSeconds, isLocked) {
        val timeout = CutePlayerPreferences.autoHideSeconds
        if (externalControlsVisible == null && controlsVisible && isPlaying && timeout > 0 && !isLocked) {
            delay(timeout * 1000L)
            internalVisible = false
        }
    }
    LaunchedEffect(doubleTapSide) {
        if (doubleTapSide != null) {
            delay(650L)
            doubleTapSide = null
        }
    }

    val buttonShape: Shape = when {
        style == PlayerStylePreset.CyberpunkHud -> CutCornerShape(10.dp)
        CutePlayerPreferences.buttonShape == PlayerButtonShape.Circle -> CircleShape
        CutePlayerPreferences.buttonShape == PlayerButtonShape.Squircle -> RoundedCornerShape(14.dp)
        CutePlayerPreferences.buttonShape == PlayerButtonShape.Chamfered -> CutCornerShape(10.dp)
        else -> RoundedCornerShape(22.dp)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(durationMs, positionMs, isLocked, externalControlsVisible == null) {
                if (externalControlsVisible != null) return@pointerInput
                detectTapGestures(
                    onTap = { internalVisible = !internalVisible },
                    onDoubleTap = { offset ->
                        if (isLocked || durationMs <= 0L) return@detectTapGestures
                        val stepMs = CutePlayerPreferences.doubleTapSeekSeconds * 1000L
                        if (offset.x < size.width / 2f) {
                            onSeekTo((positionMs - stepMs).coerceAtLeast(0L))
                            doubleTapSide = -1
                        } else {
                            onSeekTo((positionMs + stepMs).coerceAtMost(durationMs))
                            doubleTapSide = 1
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                )
            },
    ) {
        // Tall enough for the chip row? Short portrait players drop it so nothing overlaps.
        val roomForChips = maxHeight >= 230.dp
        val compact = maxHeight < 230.dp

        AnimatedVisibility(
            visible = doubleTapSide != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(if (doubleTapSide == -1) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 28.dp),
        ) {
            val isBack = doubleTapSide == -1
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (isBack) Icons.Rounded.FastRewind else Icons.Rounded.FastForward, null, tint = primaryColor, modifier = Modifier.size(18.dp))
                Text("${if (isBack) "-" else "+"}${CutePlayerPreferences.doubleTapSeekSeconds}s", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = isLocked && controlsVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
        ) {
            CutePlayerPillButton(Icons.Rounded.LockOpen, "Unlock", buttonShape, primaryColor, backdrop) { isLocked = false }
        }

        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            val top = if (style == PlayerStylePreset.OledPure) 0.7f else 0.5f
            val bottom = if (style == PlayerStylePreset.OledPure) 0.8f else 0.6f
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = top), Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = bottom))))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // Top: back, title, captions, looks.
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CuteIconTile(Icons.Rounded.KeyboardArrowDown, "Minimize player", buttonShape, primaryColor, onCollapse, size = 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val chapterNow = chapters.takeIf { it.size >= 2 }?.at(positionMs)?.title
                        if ((chapterNow != null || channelName.isNotBlank()) && !compact) {
                            Text(chapterNow ?: channelName, color = Color.White.copy(alpha = 0.72f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (CutePlayerPreferences.showCaptionsButton) {
                        CuteIconTile(Icons.Rounded.ClosedCaption, "Captions", buttonShape, primaryColor, onToggleCaptions, size = 34.dp)
                    }
                    CuteIconTile(Icons.Rounded.Palette, "Player and looks", buttonShape, primaryColor, onOpenCustomizer, size = 34.dp)
                }

                // Centre transport: previous, play/pause, next.
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp),
                ) {
                    CuteIconTile(Icons.Rounded.SkipPrevious, "Previous", CircleShape, primaryColor, onPrevious, size = if (compact) 38.dp else 44.dp)
                    val playSize = if (compact) 52.dp else 64.dp
                    Box(
                        Modifier
                            .size(playSize)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(primaryColor, secondaryColor)))
                            .liquidPressable(pressedScale = if (CutePlayerPreferences.bouncyAnimations) 0.9f else 0.96f, haptic = true, onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(playSize * 0.55f),
                        )
                    }
                    CuteIconTile(Icons.Rounded.SkipNext, "Next", CircleShape, primaryColor, onNext, size = if (compact) 38.dp else 44.dp)
                }

                // Bottom: chip row (scrolls sideways, never wraps), then one seek row.
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (roomForChips) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (CutePlayerPreferences.showSpeedChip) {
                                CutePlayerPillButton(Icons.Rounded.Speed, "${speed}x", buttonShape, primaryColor, backdrop, onOpenSpeed)
                            }
                            if (CutePlayerPreferences.showQualityChip) {
                                CutePlayerPillButton(Icons.Rounded.HighQuality, qualityLabel, buttonShape, secondaryColor, backdrop, onOpenQuality)
                            }
                            if (CutePlayerPreferences.showLoopButton) {
                                CuteIconTile(Icons.Rounded.Repeat, "Loop", buttonShape, primaryColor, onToggleLoop, size = 32.dp, highlighted = isLooping)
                            }
                            if (CutePlayerPreferences.showLikeButton) {
                                CuteIconTile(
                                    if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like video", buttonShape,
                                    if (isLiked) Color(0xFFF472B6) else primaryColor, onToggleLike, size = 32.dp, highlighted = isLiked,
                                )
                            }
                            CuteIconTile(Icons.Rounded.Lock, "Lock controls", buttonShape, primaryColor, { isLocked = true }, size = 32.dp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatCuteTime(positionMs), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        CuteAdaptiveSeekbar(
                            seekbarStyle = CutePlayerPreferences.seekbarStyle,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            bufferedMs = bufferedMs,
                            isPlaying = isPlaying,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.weight(1f),
                            chapters = chapters,
                        )
                        Text(formatCuteTime(durationMs), color = Color.White.copy(alpha = 0.78f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        CuteIconTile(
                            if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                            buttonShape, primaryColor, onToggleFullscreen, size = 32.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CuteAdaptiveSeekbar(
    seekbarStyle: PlayerSeekbarStyle,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    isPlaying: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    chapters: List<Chapter> = emptyList(),
) {
    var scrubbing by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    var lastScrubChapter by remember { mutableIntStateOf(-1) }
    val scrubHaptics = LocalHapticFeedback.current
    val hasChapters = chapters.size >= 2
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val actualFraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayFraction = if (scrubbing) scrubFraction else actualFraction

    val infiniteTransition = rememberInfiniteTransition(label = "wavySeek")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    val trackHeight by animateDpAsState(
        targetValue = when {
            scrubbing -> 7.dp
            seekbarStyle == PlayerSeekbarStyle.SlimCinema -> 3.dp
            else -> 4.5.dp
        },
        animationSpec = YtxMotion.snappySpring(),
        label = "cuteSeekHeight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0L && size.width > 0) {
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekTo((frac * durationMs).toLong())
                    }
                }
            }
            .pointerInput(durationMs, chapters) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (size.width > 0) {
                            scrubbing = true
                            scrubFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (size.width > 0) {
                            scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            if (hasChapters && durationMs > 0L) {
                                val idx = chapters.indexOfLast { it.startMs <= (scrubFraction * durationMs).toLong() }
                                if (idx != lastScrubChapter) {
                                    if (lastScrubChapter != -1) scrubHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastScrubChapter = idx
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (durationMs > 0L) onSeekTo((scrubFraction * durationMs).toLong())
                        scrubbing = false
                        lastScrubChapter = -1
                    },
                    onDragCancel = { scrubbing = false; lastScrubChapter = -1 },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        when (seekbarStyle) {
            PlayerSeekbarStyle.WavyCute -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midY = size.height / 2f
                    val activeW = size.width * displayFraction
                    val bufferedW = size.width * bufferedFraction

                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.36f),
                        start = Offset(0f, midY),
                        end = Offset(bufferedW, midY),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    if (activeW > 2f) {
                        val path = Path()
                        val amp = if (isPlaying && !scrubbing) 3.dp.toPx() else 0.8.dp.toPx()
                        val freq = 0.085f
                        path.moveTo(0f, midY)
                        var x = 0f
                        while (x <= activeW) {
                            val y = midY + sin(x * freq + wavePhase) * amp
                            path.lineTo(x, y)
                            x += 4f
                        }
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor)),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }

                    drawCircle(
                        color = Color.White,
                        radius = if (scrubbing) 8.dp.toPx() else 6.dp.toPx(),
                        center = Offset(activeW.coerceIn(6.dp.toPx(), size.width - 6.dp.toPx()), midY),
                    )
                }
            }
            PlayerSeekbarStyle.SegmentedTicks -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val segments = 28
                    val gap = 3.dp.toPx()
                    val segW = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(2f)
                    val segH = if (scrubbing) 8.dp.toPx() else 5.5.dp.toPx()
                    val topY = (size.height - segH) / 2f
                    val activeCount = (displayFraction * segments).toInt()
                    val bufferedCount = (bufferedFraction * segments).toInt()

                    for (i in 0 until segments) {
                        val x = i * (segW + gap)
                        val color = when {
                            i <= activeCount -> if (i % 2 == 0) primaryColor else secondaryColor
                            i <= bufferedCount -> Color.White.copy(alpha = 0.36f)
                            else -> Color.White.copy(alpha = 0.18f)
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, topY),
                            size = Size(segW, segH),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                }
            }
            PlayerSeekbarStyle.DottedCandy -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dots = 24
                    val spacing = size.width / dots.toFloat()
                    val midY = size.height / 2f
                    val activeDots = (displayFraction * dots).toInt()

                    for (i in 0 until dots) {
                        val cx = spacing * i + spacing / 2f
                        val isActive = i <= activeDots
                        val radius = when {
                            i == activeDots -> if (scrubbing) 7.dp.toPx() else 5.5.dp.toPx()
                            isActive -> 3.6.dp.toPx()
                            else -> 2.4.dp.toPx()
                        }
                        drawCircle(
                            color = if (isActive) {
                                if (i == activeDots) Color.White else primaryColor
                            } else {
                                Color.White.copy(alpha = 0.25f)
                            },
                            radius = radius,
                            center = Offset(cx, midY),
                        )
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferedFraction)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.36f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(displayFraction)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(primaryColor, secondaryColor))),
                    )
                }
            }
        }

        if (hasChapters && durationMs > 0L) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height / 2f
                val half = trackHeight.toPx() / 2f + 1.5f
                chapters.forEach { ch ->
                    if (ch.startMs > 0L && ch.startMs < durationMs) {
                        val x = size.width * (ch.startMs.toFloat() / durationMs)
                        drawLine(Color.Black.copy(alpha = 0.6f), Offset(x, midY - half), Offset(x, midY + half), strokeWidth = 2.dp.toPx())
                    }
                }
            }
        }

        if (scrubbing && durationMs > 0L && widthPx > 0) {
            val ms = (scrubFraction * durationMs).toLong()
            val chapterTitle = if (hasChapters) chapters.at(ms)?.title else null
            val bubbleW = if (chapterTitle != null) 150.dp else 64.dp
            val bubbleWpx = with(androidx.compose.ui.platform.LocalDensity.current) { bubbleW.roundToPx() }
            val x = (widthPx * scrubFraction - bubbleWpx / 2f).coerceIn(0f, (widthPx - bubbleWpx).coerceAtLeast(0).toFloat())
            val upPx = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.roundToPx() }
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(x.roundToInt(), -upPx) }
                    .width(bubbleW)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(formatCuteTime(ms), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (chapterTitle != null) {
                    Text(chapterTitle, color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CuteIconTile(
    icon: ImageVector,
    contentDescription: String?,
    shape: Shape,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    highlighted: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                if (highlighted) accent.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.36f),
            )
            .border(
                width = 0.9.dp,
                color = if (highlighted) accent.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.18f),
                shape = shape,
            )
            .liquidPressable(
                pressedScale = if (CutePlayerPreferences.bouncyAnimations) 0.88f else 0.95f,
                haptic = true,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlighted) accent else Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CutePlayerPillButton(
    icon: ImageVector,
    label: String,
    shape: Shape,
    accent: Color,
    backdrop: Backdrop?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .selectiveGlassSurface(
                backdrop = backdrop,
                role = GlassRole.Player,
                shape = shape,
                useNeonBorder = false,
            )
            .border(0.9.dp, accent.copy(alpha = 0.45f), shape)
            .liquidPressable(
                pressedScale = if (CutePlayerPreferences.bouncyAnimations) 0.91f else 0.96f,
                haptic = true,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Interactive Player Switcher & Customizer Sheet with 10 distinct player types,
 * 5 seekbar styles, 4 button shapes, and HUD element toggles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerStyleAndCustomizerSheet(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalYtxDesignTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = tokens.primaryGlow,
            )
            Text(
                text = "Player Types & Customizer",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // 1. Switch Player Type (10 Presets)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Choose Player Type (${PlayerStylePreset.entries.size} Styles)",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            PlayerStylePreset.entries.forEach { preset ->
                val selected = CutePlayerPreferences.style == preset
                val primary = YtxNeonPalette.parseHex(preset.primaryHex, tokens.primaryGlow)
                val shape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            if (selected) primary.copy(alpha = 0.16f) else tokens.cardSurface,
                        )
                        .border(
                            width = if (selected) 1.4.dp else 0.75.dp,
                            color = if (selected) primary else tokens.subtleBorder,
                            shape = shape,
                        )
                        .liquidPressable(pressedScale = 0.98f, haptic = true) {
                            CutePlayerPreferences.updateStyle(preset)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        primary,
                                        YtxNeonPalette.parseHex(preset.secondaryHex, tokens.secondaryGlow),
                                    ),
                                ),
                            ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = preset.subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(primary.copy(alpha = 0.18f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = preset.badge,
                            color = if (tokens.isDark) Color.White else primary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // 2. Seekbar & Button Shape
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Seekbar Style",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerSeekbarStyle.entries.forEach { sb ->
                    GlassPillChip(
                        label = sb.label,
                        selected = CutePlayerPreferences.seekbarStyle == sb,
                        onClick = { CutePlayerPreferences.updateSeekbarStyle(sb) },
                        backdrop = backdrop,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Control Button Shape",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerButtonShape.entries.forEach { bs ->
                    GlassPillChip(
                        label = bs.label,
                        selected = CutePlayerPreferences.buttonShape == bs,
                        onClick = { CutePlayerPreferences.updateButtonShape(bs) },
                        backdrop = backdrop,
                    )
                }
            }
        }

        // 3. Timings & Toggles
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Auto-Hide Controls: ${if (CutePlayerPreferences.autoHideSeconds == 0) "Never" else "${CutePlayerPreferences.autoHideSeconds}s"}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Slider(
                value = CutePlayerPreferences.autoHideSeconds.toFloat(),
                onValueChange = { CutePlayerPreferences.updateAutoHideSeconds(it.toInt()) },
                valueRange = 0f..10f,
                colors = SliderDefaults.colors(
                    thumbColor = tokens.primaryGlow,
                    activeTrackColor = tokens.primaryAccent,
                ),
            )

            Text(
                text = "Double-Tap Seek Step: ${CutePlayerPreferences.doubleTapSeekSeconds}s",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Slider(
                value = CutePlayerPreferences.doubleTapSeekSeconds.toFloat(),
                onValueChange = { CutePlayerPreferences.updateDoubleTapSeekSeconds(it.toInt()) },
                valueRange = 5f..30f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = tokens.primaryGlow,
                    activeTrackColor = tokens.primaryAccent,
                ),
            )
        }

        CustomizerToggleRow(
            label = "Bouncy Spring Micro-Animations",
            checked = CutePlayerPreferences.bouncyAnimations,
            onCheckedChange = { CutePlayerPreferences.updateBouncyAnimations(it) },
        )
        CustomizerToggleRow(
            label = "Show Heart / Like Button",
            checked = CutePlayerPreferences.showLikeButton,
            onCheckedChange = { CutePlayerPreferences.updateShowLikeButton(it) },
        )
        CustomizerToggleRow(
            label = "Show Loop Button",
            checked = CutePlayerPreferences.showLoopButton,
            onCheckedChange = { CutePlayerPreferences.updateShowLoopButton(it) },
        )
        CustomizerToggleRow(
            label = "Show Speed Chip",
            checked = CutePlayerPreferences.showSpeedChip,
            onCheckedChange = { CutePlayerPreferences.updateShowSpeedChip(it) },
        )
        CustomizerToggleRow(
            label = "Show Quality Chip",
            checked = CutePlayerPreferences.showQualityChip,
            onCheckedChange = { CutePlayerPreferences.updateShowQualityChip(it) },
        )
        CustomizerToggleRow(
            label = "Show Captions Button",
            checked = CutePlayerPreferences.showCaptionsButton,
            onCheckedChange = { CutePlayerPreferences.updateShowCaptionsButton(it) },
        )
    }
}

@Composable
private fun CustomizerToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalYtxDesignTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = tokens.primaryAccent,
            ),
        )
    }
}

private fun formatCuteTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
