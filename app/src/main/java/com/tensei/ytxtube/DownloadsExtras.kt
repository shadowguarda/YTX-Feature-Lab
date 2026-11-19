package com.tensei.ytxtube

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Free up space: pick finished downloads to remove by a simple rule (watched ones, oldest first or
 * largest first), see exactly what goes and how much it frees, then confirm.
 */
enum class CleanupStrategy(val label: String, val blurb: String) {
    Watched("Watched", "Videos you finished or marked as watched"),
    Oldest("Oldest", "Downloaded longest ago, until enough space is freed"),
    Largest("Largest", "The biggest files first, until enough space is freed"),
}

data class CleanupItem(val key: String, val title: String, val sizeBytes: Long, val order: Int, val watched: Boolean)

object DownloadCleanupLogic {
    /** Targets offered for the "free this much" rules. */
    val TARGETS_MB = listOf(500L, 1024L, 2048L, 5120L)

    fun targetBytes(mb: Long): Long = mb * 1024L * 1024L

    /** What would be removed. "Watched" removes every watched download; the others stop once [targetBytes] is reached. */
    fun plan(items: List<CleanupItem>, strategy: CleanupStrategy, targetBytes: Long): List<CleanupItem> {
        val usable = items.filter { it.sizeBytes > 0L }
        return when (strategy) {
            CleanupStrategy.Watched -> usable.filter { it.watched }.sortedBy { it.order }
            CleanupStrategy.Oldest -> takeUntil(usable.sortedBy { it.order }, targetBytes)
            CleanupStrategy.Largest -> takeUntil(usable.sortedByDescending { it.sizeBytes }, targetBytes)
        }
    }

    private fun takeUntil(ordered: List<CleanupItem>, targetBytes: Long): List<CleanupItem> {
        if (targetBytes <= 0L) return emptyList()
        val out = mutableListOf<CleanupItem>()
        var freed = 0L
        for (item in ordered) {
            if (freed >= targetBytes) break
            out += item
            freed += item.sizeBytes
        }
        return out
    }

    fun bytes(plan: List<CleanupItem>): Long = plan.sumOf { it.sizeBytes }
}

/** Play-all and shuffle for the offline list (pure part, unit-tested). */
object DownloadPlayLogic {
    /** The same seed always gives the same order, which keeps the result stable while it is queued. */
    fun <T> shuffled(items: List<T>, seed: Long): List<T> = items.shuffled(java.util.Random(seed))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsCleanupSheet(items: List<CleanupItem>, onDelete: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var strategy by remember { mutableStateOf(if (items.any { it.watched }) CleanupStrategy.Watched else CleanupStrategy.Oldest) }
    var targetMb by remember { mutableStateOf(1024L) }
    var confirm by remember { mutableStateOf(false) }
    val plan = DownloadCleanupLogic.plan(items, strategy, DownloadCleanupLogic.targetBytes(targetMb))
    val bytes = DownloadCleanupLogic.bytes(plan)

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${plan.size} download${if (plan.size == 1) "" else "s"}?") },
            text = { Text("This frees ${formatBytes(bytes)}. The files are removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete(plan.map { it.key }.toSet())
                    onDismiss()
                }) { Text("Delete", color = c.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }

    FloatingSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Free up space", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            if (items.isEmpty()) {
                YtxNote("You have no finished downloads to remove.")
                return@Column
            }
            YtxSectionLabel("Remove")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CleanupStrategy.entries.forEach { s -> YtxChip(label = s.label, selected = strategy == s) { strategy = s } }
            }
            YtxNote(strategy.blurb)
            if (strategy != CleanupStrategy.Watched) {
                YtxSectionLabel("Free up about")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadCleanupLogic.TARGETS_MB.forEach { mb ->
                        YtxChip(label = formatBytes(DownloadCleanupLogic.targetBytes(mb)), selected = targetMb == mb) { targetMb = mb }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (plan.isEmpty()) {
                YtxNote(
                    if (strategy == CleanupStrategy.Watched) "None of your downloads are marked as watched yet." else "Nothing to remove.",
                )
            } else {
                YtxPanel {
                    Text("${plan.size} download${if (plan.size == 1) "" else "s"} · ${formatBytes(bytes)}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    plan.take(8).forEach {
                        Text(
                            "${it.title} · ${formatBytes(it.sizeBytes)}",
                            fontSize = 12.5.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    if (plan.size > 8) Text("and ${plan.size - 8} more", fontSize = 12.5.sp, color = c.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                YtxChip(label = "Delete ${plan.size} · free ${formatBytes(bytes)}", selected = true) { confirm = true }
            }
        }
    }
}
