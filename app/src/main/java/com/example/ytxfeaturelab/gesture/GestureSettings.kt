package com.example.ytxfeaturelab.gesture

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytxfeaturelab.ui.pressable
import kotlin.math.roundToInt

/**
 * Portable copy of YTXTube's gesture settings + a small demo settings sheet.
 * In the real app this replaces the hard-coded "left edge = brightness, right
 * edge = volume, double tap = 10s" logic in VideoView.kt.
 */
object GestureSettings {
    private const val PREFS = "gestures"
    private const val K_SEEK = "seek_step_sec"
    private const val K_SENS = "swipe_sensitivity"
    private const val K_EDGE = "edge_fraction"
    private const val K_SWAP = "swap_edges"

    private var prefs: SharedPreferences? = null

    var seekStepSec by mutableIntStateOf(10)
        private set
    var swipeSensitivity by mutableFloatStateOf(1f)
        private set
    var edgeFraction by mutableFloatStateOf(0.22f)
        private set

    /** When true, brightness lives on the right edge and volume on the left. */
    var swapEdges by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        seekStepSec = p.getInt(K_SEEK, 10)
        swipeSensitivity = p.getFloat(K_SENS, 1f)
        edgeFraction = p.getFloat(K_EDGE, 0.22f)
        swapEdges = p.getBoolean(K_SWAP, false)
    }

    fun setSeekStep(sec: Int) {
        seekStepSec = sec
        prefs?.edit()?.putInt(K_SEEK, sec)?.apply()
    }

    fun setSensitivity(value: Float) {
        swipeSensitivity = value
        prefs?.edit()?.putFloat(K_SENS, value)?.apply()
    }

    fun updateEdgeFraction(value: Float) {
        edgeFraction = value
        prefs?.edit()?.putFloat(K_EDGE, value)?.apply()
    }

    fun updateSwapEdges(value: Boolean) {
        swapEdges = value
        prefs?.edit()?.putBoolean(K_SWAP, value)?.apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Gestures",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(40.dp).pressable(onClick = onDismiss).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Close", tint = c.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("Double-tap seek step", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30).forEach { sec ->
                    val on = GestureSettings.seekStepSec == sec
                    Box(
                        Modifier
                            .weight(1f)
                            .pressable { GestureSettings.setSeekStep(sec) }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) c.primary else c.surfaceContainerHigh)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${sec}s",
                            color = if (on) c.onPrimary else c.onSurface,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Swipe sensitivity", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "How fast brightness / volume change per swipe.",
                fontSize = 12.sp, color = c.onSurfaceVariant,
            )
            Slider(
                value = GestureSettings.swipeSensitivity,
                onValueChange = { GestureSettings.setSensitivity(it) },
                valueRange = 0.3f..2f,
            )
            Text(
                "${(GestureSettings.swipeSensitivity * 100).roundToInt()}%",
                fontSize = 11.sp, color = c.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Text("Edge zone width", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "Only the outer ${(GestureSettings.edgeFraction * 100).roundToInt()}% of each side starts a brightness / volume swipe.",
                fontSize = 12.sp, color = c.onSurfaceVariant,
            )
            Slider(
                value = GestureSettings.edgeFraction,
                onValueChange = { GestureSettings.updateEdgeFraction(it) },
                valueRange = 0.12f..0.35f,
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Swap edges", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (GestureSettings.swapEdges) "Brightness on the right, volume on the left."
                        else "Brightness on the left, volume on the right.",
                        fontSize = 12.sp, color = c.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = GestureSettings.swapEdges,
                    onCheckedChange = { GestureSettings.updateSwapEdges(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.primary,
                        checkedThumbColor = c.onPrimary,
                    ),
                )
            }
        }
    }
}
