package com.example.ytxfeaturelab.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared pill/button and press-scale helper used by every screen. */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 1400f),
        label = "press",
    )
    if (!enabled) return@composed this
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Adds a focus ring (visible on TV remotes) and slight scale on focus. */
fun Modifier.focusRing(
    radiusDp: Int = 22,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF3EA6FF),
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            val s = if (focused) 1.03f else 1f
            scaleX = s; scaleY = s
        }
        .drawBehind {
            if (!focused) return@drawBehind
            val stroke = 2.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(radiusDp.dp.toPx(), radiusDp.dp.toPx()),
                style = Stroke(stroke),
            )
        }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(c.primary)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun SheetCard(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) { content() }
}
