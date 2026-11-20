package com.tensei.ytxtube

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A soft highlight sweeping across a placeholder block while content loads. */
@Composable
fun Modifier.shimmer(): Modifier {
    val c = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        -1f, 2f,
        infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerX",
    )
    val base = c.surfaceContainerHighest
    val hi = c.surfaceContainerHigh.copy(alpha = 0.35f)
    return this.background(
        Brush.linearGradient(
            colors = listOf(base, hi, base),
            start = androidx.compose.ui.geometry.Offset(x * 600f, 0f),
            end = androidx.compose.ui.geometry.Offset(x * 600f + 400f, 200f),
        ),
    )
}

@Composable
private fun Bone(modifier: Modifier, radius: Dp = 8.dp) {
    Spacer(modifier.clip(RoundedCornerShape(radius)).shimmer())
}

/** Placeholder shaped like the feed card for the given style, shown while the first page loads. */
@Composable
fun VideoCardSkeleton(style: FeedCardStyle, modifier: Modifier = Modifier) {
    when (style) {
        FeedCardStyle.Comfortable -> Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Bone(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 0.dp)
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                Bone(Modifier.width(36.dp).height(36.dp), 18.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Bone(Modifier.fillMaxWidth(0.9f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    Bone(Modifier.fillMaxWidth(0.55f).height(11.dp))
                }
            }
        }
        FeedCardStyle.Compact -> Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Bone(Modifier.width(Tweaks.thumbWidthDp.value.dp).aspectRatio(16f / 9f), 16.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Bone(Modifier.fillMaxWidth(0.95f).height(13.dp))
                Spacer(Modifier.height(8.dp))
                Bone(Modifier.fillMaxWidth(0.5f).height(11.dp))
            }
        }
        FeedCardStyle.Grid -> Row(modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            repeat(2) { i ->
                Column(Modifier.weight(1f).padding(end = if (i == 0) 10.dp else 0.dp, bottom = 14.dp)) {
                    Bone(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 12.dp)
                    Spacer(Modifier.height(6.dp))
                    Bone(Modifier.fillMaxWidth(0.9f).height(12.dp))
                    Spacer(Modifier.height(6.dp))
                    Bone(Modifier.fillMaxWidth(0.5f).height(10.dp))
                }
            }
        }
    }
}
