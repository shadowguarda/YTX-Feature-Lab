package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class BannerKind(val showMs: Long) {
    Success(3500L),
    Info(3500L),
    Error(6000L),
}

data class Banner(
    val id: Long,
    val kind: BannerKind,
    val title: String,
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

/** What a download did between two looks at the list, worth telling the person about. */
data class DownloadEvent(val key: String, val title: String, val completed: Boolean, val error: String?)

object BannerLogic {
    const val MAX_VISIBLE = 3
    const val DUPLICATE_WINDOW_MS = 2000L

    /** Adds [b] unless an identical banner (kind, title, message) was added within the window; keeps the newest [MAX_VISIBLE]. */
    fun add(list: List<Banner>, b: Banner, lastAddedMs: Map<String, Long>, nowMs: Long): Pair<List<Banner>, Map<String, Long>> {
        val sig = "${b.kind}|${b.title}|${b.message}"
        val last = lastAddedMs[sig]
        if (last != null && nowMs - last < DUPLICATE_WINDOW_MS) return list to lastAddedMs
        return (list + b).takeLast(MAX_VISIBLE) to (lastAddedMs + (sig to nowMs))
    }

    fun remove(list: List<Banner>, id: Long): List<Banner> = list.filterNot { it.id == id }

    /**
     * Finished or failed downloads since [previous]. A task seen for the first time, or already in its
     * final state, produces nothing, so restoring the saved list at startup stays quiet.
     */
    fun downloadEvents(
        previous: Map<String, DownloadStatus>,
        now: List<Triple<String, String, DownloadStatus>>,
        errors: Map<String, String?> = emptyMap(),
    ): List<DownloadEvent> = now.mapNotNull { (key, title, status) ->
        val was = previous[key] ?: return@mapNotNull null
        if (was == status) return@mapNotNull null
        val wasActive = was == DownloadStatus.Queued || was == DownloadStatus.Downloading || was == DownloadStatus.Processing
        when {
            wasActive && status == DownloadStatus.Complete -> DownloadEvent(key, title, true, null)
            wasActive && status == DownloadStatus.Failed -> DownloadEvent(key, title, false, errors[key])
            else -> null
        }
    }
}

object Banners {
    private var prefs: SharedPreferences? = null
    private var nextId = 1L
    private var lastAdded: Map<String, Long> = emptyMap()

    var enabled by mutableStateOf(true)
        private set
    var visible by mutableStateOf<List<Banner>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("banners", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", true)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
        if (!value) visible = emptyList()
    }

    fun post(kind: BannerKind, title: String, message: String = "", actionLabel: String? = null, action: (() -> Unit)? = null) {
        if (!enabled) return
        val (next, stamps) = BannerLogic.add(visible, Banner(nextId++, kind, title, message, actionLabel, action), lastAdded, android.os.SystemClock.elapsedRealtime())
        visible = next
        lastAdded = stamps
    }

    fun dismiss(id: Long) { visible = BannerLogic.remove(visible, id) }
}

/** Shows banners at the top and turns finished or failed downloads into banners. Place once, near the app root. */
@Composable
fun BannerHost(onOpenDownloads: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        var previous: Map<String, DownloadStatus> = Downloads.tasks.associate { it.key to it.status }
        snapshotFlow { Downloads.tasks.map { Triple(it.key, it.title, it.status) } }.collect { now ->
            val errors = Downloads.tasks.associate { it.key to it.error }
            BannerLogic.downloadEvents(previous, now, errors).forEach { e ->
                if (e.completed) Banners.post(BannerKind.Success, "Download complete", e.title, "Open", onOpenDownloads)
                else Banners.post(BannerKind.Error, "Download failed", e.title + (e.error?.let { ": $it" } ?: ""), "Open", onOpenDownloads)
            }
            previous = now.associate { it.first to it.third }
        }
    }
    Column(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Banners.visible.forEach { b -> key(b.id) { BannerCard(b) } }
    }
}

@Composable
private fun BannerCard(b: Banner) {
    val c = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(b.id) {
        shown = true
        delay(b.kind.showMs)
        shown = false
        delay(PersonalizationLogic.motionMs(220, LiquidGlassStudio.animationPreset.durationMultiplier).toLong())
        Banners.dismiss(b.id)
    }
    val accent = when (b.kind) {
        BannerKind.Success -> Color(0xFF22C55E)
        BannerKind.Error -> c.error
        BannerKind.Info -> c.primary
    }
    val k = LiquidGlassStudio.animationPreset.durationMultiplier
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(PersonalizationLogic.motionMs(220, k))) + slideInVertically(tween(PersonalizationLogic.motionMs(260, k))) { -it },
        exit = fadeOut(tween(PersonalizationLogic.motionMs(200, k))) + slideOutVertically(tween(PersonalizationLogic.motionMs(200, k))) { -it },
    ) {
        val shape = RoundedCornerShape(20.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(studioSheetContainer(c.surfaceContainerHigh).copy(alpha = 0.97f))
                .border(1.dp, accent.copy(alpha = 0.55f), shape)
                .clickable { shown = false; Banners.dismiss(b.id) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(
                    when (b.kind) {
                        BannerKind.Success -> Icons.Rounded.CheckCircle
                        BannerKind.Error -> Icons.Rounded.ErrorOutline
                        BannerKind.Info -> Icons.Rounded.Info
                    },
                    contentDescription = null, tint = accent, modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(b.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (b.message.isNotBlank()) Text(b.message, fontSize = 12.sp, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (b.actionLabel != null && b.action != null) {
                TextButton(onClick = { b.action.invoke(); shown = false; Banners.dismiss(b.id) }) { Text(b.actionLabel) }
            }
        }
    }
}
