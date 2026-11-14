package com.tensei.ytxtube

import android.content.Context
import android.os.StatFs
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

/**
 * Downloads library: search, sorting, grouping, a storage summary, multi-select with bulk actions and
 * display customisation for the Downloads tab. Choices are saved on the device.
 */
enum class DownloadSort(val label: String) {
    Recent("Recent"),
    Title("Title"),
    Channel("Channel"),
    Largest("Largest"),
    Longest("Longest"),
}

enum class DownloadDensity(val label: String) { Comfortable("Comfortable"), Compact("Compact") }

/** What the list logic needs to know about one download (kept free of Compose state so it can be tested). */
data class DownloadEntry(
    val key: String,
    val title: String,
    val channel: String,
    val sizeBytes: Long,
    val durationSec: Long,
    /** Position in the saved list; a higher number was added later. */
    val order: Int,
)

object DownloadsViewLogic {
    /** Every word typed has to appear in the title or channel, ignoring case. */
    fun matches(entry: DownloadEntry, query: String): Boolean {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = (entry.title + " " + entry.channel).lowercase(Locale.ROOT)
        return words.all { it in hay }
    }

    fun filterAndSort(entries: List<DownloadEntry>, query: String, sort: DownloadSort): List<DownloadEntry> {
        val newestFirst = compareByDescending<DownloadEntry> { it.order }
        val comparator: Comparator<DownloadEntry> = when (sort) {
            DownloadSort.Recent -> newestFirst
            DownloadSort.Title -> compareBy<DownloadEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.then(newestFirst)
            DownloadSort.Channel -> compareBy<DownloadEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.channel }
                .then(compareBy<DownloadEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
                .then(newestFirst)
            DownloadSort.Largest -> compareByDescending<DownloadEntry> { it.sizeBytes }.then(newestFirst)
            DownloadSort.Longest -> compareByDescending<DownloadEntry> { it.durationSec }.then(newestFirst)
        }
        return entries.filter { matches(it, query) }.sortedWith(comparator)
    }

    /** Channel sections in A to Z order; entries keep the order they came in. */
    fun groupByChannel(entries: List<DownloadEntry>): List<Pair<String, List<DownloadEntry>>> =
        entries.groupBy { it.channel.ifBlank { "Unknown channel" } }
            .toList()
            .sortedBy { it.first.lowercase(Locale.ROOT) }

    fun totalBytes(entries: List<DownloadEntry>): Long = entries.sumOf { it.sizeBytes.coerceAtLeast(0L) }

    /** "12 downloads · 3.40 GB". */
    fun summary(count: Int, bytes: Long): String =
        "$count download${if (count == 1) "" else "s"} · ${formatBytes(bytes)}"

    /** Share of the used plus free space taken by downloads, or 0 when the free space is unknown. */
    fun usedFraction(downloadBytes: Long, freeBytes: Long): Float {
        if (freeBytes < 0L || downloadBytes < 0L) return 0f
        val total = downloadBytes + freeBytes
        return if (total <= 0L) 0f else (downloadBytes.toFloat() / total).coerceIn(0f, 1f)
    }

    fun toggle(selection: Set<String>, key: String): Set<String> =
        if (key in selection) selection - key else selection + key

    /** Keeps only keys that still exist (a download may finish deleting while selecting). */
    fun retain(selection: Set<String>, existing: Collection<String>): Set<String> = selection intersect existing.toSet()

    fun parseSort(name: String?): DownloadSort = DownloadSort.entries.find { it.name == name } ?: DownloadSort.Recent

    fun parseDensity(name: String?): DownloadDensity = DownloadDensity.entries.find { it.name == name } ?: DownloadDensity.Comfortable

    /** Free space on the volume holding [dir] (walking up to a folder that exists), or -1 if unknown. */
    fun freeBytes(dir: File): Long = runCatching {
        var d: File? = dir
        while (d != null && !d.exists()) d = d.parentFile
        if (d == null) -1L else StatFs(d.path).availableBytes
    }.getOrDefault(-1L)
}

object DownloadsViewPrefs {
    private const val PREFS = "downloads_view"
    private var prefs: android.content.SharedPreferences? = null

    var sort by mutableStateOf(DownloadSort.Recent)
        private set
    var density by mutableStateOf(DownloadDensity.Comfortable)
        private set
    var showThumbnails by mutableStateOf(true)
        private set
    var groupByChannel by mutableStateOf(false)
        private set
    var showStorage by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        sort = DownloadsViewLogic.parseSort(p.getString("sort", null))
        density = DownloadsViewLogic.parseDensity(p.getString("density", null))
        showThumbnails = p.getBoolean("thumbs", true)
        groupByChannel = p.getBoolean("group", false)
        showStorage = p.getBoolean("storage", true)
    }

    fun setSort(value: DownloadSort) { sort = value; prefs?.edit()?.putString("sort", value.name)?.apply() }
    fun setDensity(value: DownloadDensity) { density = value; prefs?.edit()?.putString("density", value.name)?.apply() }
    fun setShowThumbnails(value: Boolean) { showThumbnails = value; prefs?.edit()?.putBoolean("thumbs", value)?.apply() }
    fun setGroupByChannel(value: Boolean) { groupByChannel = value; prefs?.edit()?.putBoolean("group", value)?.apply() }
    fun setShowStorage(value: Boolean) { showStorage = value; prefs?.edit()?.putBoolean("storage", value)?.apply() }

    fun reset() {
        setSort(DownloadSort.Recent)
        setDensity(DownloadDensity.Comfortable)
        setShowThumbnails(true)
        setGroupByChannel(false)
        setShowStorage(true)
    }
}

@Composable
internal fun DownloadsSummaryCard(count: Int, bytes: Long, freeBytes: Long, activeCount: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    YtxPanel(modifier) {
        Text(DownloadsViewLogic.summary(count, bytes), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (freeBytes >= 0L) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { DownloadsViewLogic.usedFraction(bytes, freeBytes) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = c.primary,
                trackColor = c.onSurfaceVariant.copy(alpha = 0.18f),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Text("${formatBytes(freeBytes)} free on this device", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        if (activeCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text("$activeCount in progress", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DownloadsToolbar(
    query: String,
    onQuery: (String) -> Unit,
    sort: DownloadSort,
    onSort: (DownloadSort) -> Unit,
    selecting: Boolean,
    canSelect: Boolean,
    onToggleSelect: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search downloads") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadSort.entries.forEach { s ->
                YtxChip(label = s.label, selected = sort == s) { onSort(s) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YtxChip(label = if (selecting) "Cancel selection" else "Select", selected = selecting, enabled = canSelect || selecting) { onToggleSelect() }
            YtxChip(label = "Customize", selected = false) { onCustomize() }
        }
    }
}

/** Pause / resume / retry / clear actions over the whole list. A chip only shows when it would do something. */
@Composable
internal fun DownloadsBulkBar(
    activeCount: Int,
    pausedCount: Int,
    failedCount: Int,
    finishedCount: Int,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onClearFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activeCount + pausedCount + failedCount + finishedCount == 0) return
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (activeCount > 0) YtxChip(label = "Pause all", selected = false) { onPauseAll() }
        if (pausedCount > 0) YtxChip(label = "Resume all", selected = false) { onResumeAll() }
        if (failedCount > 0) YtxChip(label = "Retry failed ($failedCount)", selected = false) { onRetryFailed() }
        if (finishedCount > 0) YtxChip(label = "Clear finished", selected = false) { onClearFinished() }
    }
}

@Composable
internal fun DownloadsSelectionBar(
    selectedCount: Int,
    total: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    YtxPanel(modifier.padding(horizontal = 16.dp)) {
        Text(
            if (selectedCount == 0) "Tap downloads to select them" else "$selectedCount selected",
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YtxChip(label = if (selectedCount == total) "Select none" else "Select all", selected = false, enabled = total > 0) { onSelectAll() }
            YtxChip(label = "Delete", selected = selectedCount > 0, enabled = selectedCount > 0) { onDelete() }
        }
        if (selectedCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text("Deleting removes the files from this device.", fontSize = 12.sp, color = c.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsCustomizeSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Customize downloads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            YtxSectionLabel("Row size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadDensity.entries.forEach { d ->
                    YtxChip(label = d.label, selected = DownloadsViewPrefs.density == d) { DownloadsViewPrefs.setDensity(d) }
                }
            }
            YtxSectionLabel("Default order")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadSort.entries.forEach { s ->
                    YtxChip(label = s.label, selected = DownloadsViewPrefs.sort == s) { DownloadsViewPrefs.setSort(s) }
                }
            }
            Spacer(Modifier.height(8.dp))
            YtxSwitchRow("Show thumbnails", "Turn off for a plain text list", DownloadsViewPrefs.showThumbnails) {
                DownloadsViewPrefs.setShowThumbnails(it)
            }
            YtxSwitchRow("Group finished downloads by channel", "Adds a heading for each channel", DownloadsViewPrefs.groupByChannel) {
                DownloadsViewPrefs.setGroupByChannel(it)
            }
            YtxSwitchRow("Show storage summary", "Total size of downloads and free space", DownloadsViewPrefs.showStorage) {
                DownloadsViewPrefs.setShowStorage(it)
            }
            Spacer(Modifier.height(8.dp))
            YtxChip(label = "Reset to defaults", selected = false) { DownloadsViewPrefs.reset() }
        }
    }
}
