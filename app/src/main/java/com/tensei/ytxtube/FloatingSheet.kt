package com.tensei.ytxtube

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
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
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
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
                        .fillMaxWidth()
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
                        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.onSurfaceVariant.copy(alpha = 0.35f)))
                        }
                        content()
                    }
                }
            }
        }
    }
}
