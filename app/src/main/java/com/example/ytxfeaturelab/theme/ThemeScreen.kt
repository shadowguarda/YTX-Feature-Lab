package com.example.ytxfeaturelab.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytxfeaturelab.ui.pressable

private data class Swatch(val label: String, val hex: String)

private val SWATCHES = listOf(
    Swatch("Cream Rose", "#833761"),
    Swatch("Erica", "#6C2BD9"),
    Swatch("Lilac Air", "#7454CE"),
    Swatch("Jai", "#D6005C"),
    Swatch("Pumpkin", "#B5651D"),
    Swatch("Moonstone", "#8A8F98"),
    Swatch("Iris", "#6750A4"),
    Swatch("Harbor", "#3B5BDB"),
    Swatch("Sunny", "#F59E0B"),
    Swatch("Forest", "#2E7D32"),
)

private val MODES = listOf(
    "system" to "System",
    "light" to "Light",
    "dark" to "Dark",
    "amoled" to "AMOLED",
)

@Composable
fun ThemeScreen(contentPadding: PaddingValues) {
    val c = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text("Theme mode", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MODES.forEach { (key, label) ->
                val on = ThemeSettings.mode == key
                Box(
                    Modifier
                        .weight(1f)
                        .pressable { ThemeSettings.chooseMode(key) }
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) c.primary else c.surfaceContainerHigh)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (on) c.onPrimary else c.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Accent colour", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "Every surface tints from this seed. Settings survive restarts.",
            fontSize = 12.5.sp, color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(SWATCHES) { swatch ->
                val selected = ThemeSettings.seed.equals(swatch.hex, ignoreCase = true)
                val preview = previewScheme(swatch.hex, dark)
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .pressable { ThemeSettings.chooseSeed(swatch.hex) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (selected) preview.primaryContainer else Color.Transparent)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(preview.primary, preview.primaryContainer))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, "Selected", tint = preview.onPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        swatch.label,
                        fontSize = 11.sp,
                        maxLines = 1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) c.onSurface else c.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Text("How to lift this", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Copy Theme.kt, ThemeSettings.kt, and ThemeScreen.kt into com.tensei.ytxtube " +
                    "(ThemeSettings and Color.kt already exist there — merge, don't overwrite). " +
                    "Call ThemeSettings.chooseSeed() from SettingsScreen and read it via YtxTheme.",
                fontSize = 12.5.sp, lineHeight = 18.sp, color = c.onSurfaceVariant,
            )
        }
    }
}
