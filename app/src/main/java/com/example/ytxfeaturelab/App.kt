package com.example.ytxfeaturelab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytxfeaturelab.ambient.AmbientBackground
import com.example.ytxfeaturelab.gesture.GestureSettings
import com.example.ytxfeaturelab.gesture.GestureSettingsSheet
import com.example.ytxfeaturelab.theme.ThemeScreen
import com.example.ytxfeaturelab.ui.PillButton
import com.example.ytxfeaturelab.ui.SectionHeader
import com.example.ytxfeaturelab.ui.SheetCard

private val TABS = listOf("Theme", "Gestures", "Ambient")

/** Root of the feature lab: one tab per portable feature. */
@Composable
fun App() {
    val c = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(containerColor = c.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "YTX Feature Lab",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = c.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TABS.forEachIndexed { index, label ->
                    PillButton(label, onClick = { tab = index })
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> ThemeScreen(PaddingValues(0.dp))
                    1 -> GesturesTab()
                    else -> AmbientTab()
                }
            }
        }
    }
}

@Composable
private fun GesturesTab() {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionHeader("Current values")
        SheetCard {
            Column(Modifier.padding(16.dp)) {
                val c = MaterialTheme.colorScheme
                Text("Seek step: ${GestureSettings.seekStepSec}s", color = c.onSurface)
                Text("Swipe sensitivity: ${"%.0f".format(GestureSettings.swipeSensitivity * 100)}%", color = c.onSurface)
                Text("Edge zone: ${"%.0f".format(GestureSettings.edgeFraction * 100)}%", color = c.onSurface)
                Text("Swap edges: ${GestureSettings.swapEdges}", color = c.onSurface)
            }
        }
        Spacer(Modifier.height(16.dp))
        PillButton("Open gesture settings", onClick = { open = true })
    }
    if (open) GestureSettingsSheet(onDismiss = { open = false })
}

@Composable
private fun AmbientTab() {
    // Static demo colours so the tab works offline; real use passes rememberAmbientColors(url).
    val colors = remember { Color(0xFFB5651D) to Color(0xFF3B5BDB) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientBackground(colors = colors)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Ambient mode preview",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
