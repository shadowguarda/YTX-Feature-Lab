package com.tensei.ytxtube

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Size rules for the floating panels: fractions of the screen, clamped, with three quick presets. */
object PanelSizeLogic {
    const val MIN_W = 0.62f
    const val MAX_W = 1f
    const val MIN_H = 0.35f
    const val MAX_H = 0.92f
    const val DEFAULT_W = 1f
    const val DEFAULT_H = 0.82f

    /** Small, medium and large, as (width, height) fractions. */
    val PRESETS = listOf(0.78f to 0.5f, 0.92f to 0.7f, 1f to 0.9f)

    fun clampW(w: Float) = w.coerceIn(MIN_W, MAX_W)
    fun clampH(h: Float) = h.coerceIn(MIN_H, MAX_H)

    /** Dragging the top-right grip: right grows the width (both sides, as the panel is centred), up grows the height. */
    fun resize(w: Float, h: Float, dxPx: Float, dyPx: Float, screenWPx: Float, screenHPx: Float): Pair<Float, Float> {
        if (screenWPx <= 0f || screenHPx <= 0f) return w to h
        return clampW(w + 2f * dxPx / screenWPx) to clampH(h - dyPx / screenHPx)
    }

    /** The preset after the one closest to the current size. */
    fun nextPreset(w: Float, h: Float): Pair<Float, Float> {
        val nearest = PRESETS.indices.minByOrNull { i -> Math.abs(PRESETS[i].first - w) + Math.abs(PRESETS[i].second - h) } ?: 0
        return PRESETS[(nearest + 1) % PRESETS.size]
    }
}

object PanelPrefs {
    private var prefs: android.content.SharedPreferences? = null

    var widthFraction by mutableStateOf(PanelSizeLogic.DEFAULT_W)
        private set
    var heightFraction by mutableStateOf(PanelSizeLogic.DEFAULT_H)
        private set

    fun init(context: android.content.Context) {
        val p = context.applicationContext.getSharedPreferences("panel_size", android.content.Context.MODE_PRIVATE)
        prefs = p
        widthFraction = PanelSizeLogic.clampW(p.getFloat("w", PanelSizeLogic.DEFAULT_W))
        heightFraction = PanelSizeLogic.clampH(p.getFloat("h", PanelSizeLogic.DEFAULT_H))
    }

    fun updateSize(w: Float, h: Float, save: Boolean = true) {
        widthFraction = PanelSizeLogic.clampW(w)
        heightFraction = PanelSizeLogic.clampH(h)
        if (save) prefs?.edit()?.putFloat("w", widthFraction)?.putFloat("h", heightFraction)?.apply()
    }

    fun reset() = updateSize(PanelSizeLogic.DEFAULT_W, PanelSizeLogic.DEFAULT_H)
}

/**
 * A settings panel that floats above the app with a margin on every side instead of sitting flush
 * to the bottom edge. The app behind it is dimmed and, on Android 12+, blurred. It opens with a
 * short rise-and-fade (scaled by the Studio's animation speed) and closes on a tap outside.
 *
 * Takes the same leading arguments as a Material bottom sheet so existing sheets can switch to it;
 * [sheetState], [shape] and [containerColor] are accepted for that reason and the panel uses the
 * Studio's corner radius and transparency instead.
 */
@Composable
fun FloatingSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") sheetState: Any? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val k = LiquidGlassStudio.animationPreset.durationMultiplier
    val enterMs = PersonalizationLogic.motionMs(320, k)
    val exitMs = PersonalizationLogic.motionMs(200, k)
    val close: () -> Unit = {
        shown = false
        scope.launch { delay(exitMs.toLong()); onDismissRequest() }
    }
    LaunchedEffect(Unit) { shown = true }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        LaunchedEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            window.setDimAmount(0.32f)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = 28
                window.attributes = attrs
            }
        }
        val c = MaterialTheme.colorScheme
        val corner = PersonalizationLogic.sheetCorner(LiquidGlassStudio.sheetCornerRadiusDp).dp
        val panelShape = RoundedCornerShape(corner)
        val screenHdp = LocalConfiguration.current.screenHeightDp
        val screenWdp = LocalConfiguration.current.screenWidthDp
        val density = androidx.compose.ui.platform.LocalDensity.current
        val maxHeight = (screenHdp * PanelPrefs.heightFraction).dp
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(enterMs)) + scaleIn(tween(enterMs), initialScale = 0.94f) + slideInVertically(tween(enterMs)) { it / 8 },
                exit = fadeOut(tween(exitMs)) + scaleOut(tween(exitMs), targetScale = 0.96f) + slideOutVertically(tween(exitMs)) { it / 10 },
            ) {
                Surface(
                    modifier = modifier
                        .offset { IntOffset(drag.value.x.roundToInt(), drag.value.y.roundToInt()) }
                        .fillMaxWidth(PanelPrefs.widthFraction)
                        .heightIn(max = maxHeight)
                        .studioNeonBorder(panelShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                    shape = panelShape,
                    color = studioSheetContainer(c.surfaceContainerHigh),
                    tonalElevation = 6.dp,
                    shadowElevation = 18.dp,
                    border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        // Grab area: drag the panel around; pull it down far enough (or fling) to close it.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    val closeAt = 120.dp.toPx()
                                    detectDragGestures(
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val next = drag.value + Offset(amount.x, amount.y)
                                            scope.launch { drag.snapTo(Offset(next.x.coerceIn(-90f, 90f), next.y.coerceAtLeast(-60f))) }
                                        },
                                        onDragEnd = {
                                            if (drag.value.y > closeAt) close()
                                            else scope.launch { drag.animateTo(Offset.Zero, spring(stiffness = 500f)) }
                                        },
                                        onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, spring(stiffness = 500f)) } },
                                    )
                                }
                                .padding(top = 12.dp, bottom = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.onSurfaceVariant.copy(alpha = 0.35f)))
                            // Resize grip: drag to change the panel's width and height, tap to cycle small / medium / large.
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 10.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            val (w, h) = PanelSizeLogic.nextPreset(PanelPrefs.widthFraction, PanelPrefs.heightFraction)
                                            PanelPrefs.updateSize(w, h)
                                        })
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDrag = { change, amount ->
                                                change.consume()
                                                val (w, h) = PanelSizeLogic.resize(
                                                    PanelPrefs.widthFraction, PanelPrefs.heightFraction, amount.x, amount.y,
                                                    screenWdp * density.density, screenHdp * density.density,
                                                )
                                                PanelPrefs.updateSize(w, h, save = false)
                                            },
                                            onDragEnd = { PanelPrefs.updateSize(PanelPrefs.widthFraction, PanelPrefs.heightFraction) },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.OpenInFull, "Resize panel", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                        content()
                    }
                }
            }
        }
    }
}
