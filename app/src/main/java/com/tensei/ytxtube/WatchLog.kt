package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Watch Insights: an on-device log of what you watch and for how long, with totals, a 7-day chart,
 * an hour-of-day heatmap, a streak, searchable history and per-channel "do not log". Nothing is sent
 * anywhere. Logging stops while incognito or "pause watch history" is on.
 */
data class WatchSession(val videoId: String, val title: String, val channel: String, val startMs: Long, val seconds: Int)

data class ChannelTotal(val channel: String, val seconds: Int)

data class LoggedVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val totalSeconds: Int,
    val lastWatchedMs: Long,
    val sessions: Int,
)

/** Pure analytics over the session list (unit-tested). */
object WatchInsightsLogic {
    const val MAX_SESSIONS = 1500
    const val RETENTION_DAYS = 120
    const val MERGE_GAP_MS = 30_000L
    const val MIN_SECONDS = 5

    /** Adds a session, merging it into the previous one when it is the same video resumed within 30 s. */
    fun addSession(list: List<WatchSession>, s: WatchSession, nowMs: Long): List<WatchSession> {
        if (s.seconds <= 0) return list
        val last = list.lastOrNull()
        val gap = if (last != null) s.startMs - (last.startMs + last.seconds * 1000L) else -1L
        val merged = if (last != null && last.videoId == s.videoId && gap in 0..MERGE_GAP_MS) {
            list.dropLast(1) + last.copy(seconds = last.seconds + s.seconds)
        } else {
            list + s
        }
        return prune(merged, nowMs)
    }

    /** Drops sessions older than the retention period and keeps at most [MAX_SESSIONS]. */
    fun prune(list: List<WatchSession>, nowMs: Long): List<WatchSession> {
        val cutoff = nowMs - RETENTION_DAYS * 86_400_000L
        return list.filter { it.startMs >= cutoff }.takeLast(MAX_SESSIONS)
    }

    fun dayStart(ms: Long, tz: TimeZone, dayOffset: Int = 0): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = ms
        c.add(Calendar.DAY_OF_YEAR, dayOffset)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun totalSeconds(list: List<WatchSession>, fromMs: Long, toMs: Long): Int =
        list.filter { it.startMs in fromMs until toMs }.sumOf { it.seconds }

    /** Seconds watched per local day for the last [days] days, oldest first, ending today. */
    fun dailyTotals(list: List<WatchSession>, days: Int, nowMs: Long, tz: TimeZone): List<Int> =
        (days - 1 downTo 0).map { back ->
            totalSeconds(list, dayStart(nowMs, tz, -back), dayStart(nowMs, tz, -back + 1))
        }

    /** Seconds watched by the hour the session started (index 0..23, local time). */
    fun hourHistogram(list: List<WatchSession>, tz: TimeZone): IntArray {
        val out = IntArray(24)
        val c = Calendar.getInstance(tz)
        list.forEach {
            c.timeInMillis = it.startMs
            out[c.get(Calendar.HOUR_OF_DAY)] += it.seconds
        }
        return out
    }

    fun topChannels(list: List<WatchSession>, limit: Int, fromMs: Long = 0L): List<ChannelTotal> =
        list.filter { it.startMs >= fromMs && it.channel.isNotBlank() }
            .groupBy { it.channel }
            .map { (name, group) -> ChannelTotal(name, group.sumOf { it.seconds }) }
            .sortedByDescending { it.seconds }
            .take(limit)

    /**
     * Consecutive days with any watching, counted back from today. A day without watching yet does
     * not break the streak until the day is over, so the count starts from yesterday in that case.
     */
    fun streakDays(list: List<WatchSession>, nowMs: Long, tz: TimeZone): Int {
        val days = list.map { dayStart(it.startMs, tz) }.toSet()
        var back = if (dayStart(nowMs, tz) in days) 0 else 1
        var streak = 0
        while (dayStart(nowMs, tz, -back) in days) {
            streak++
            back++
        }
        return streak
    }

    fun videos(list: List<WatchSession>): List<LoggedVideo> =
        list.groupBy { it.videoId }
            .map { (id, group) ->
                val newest = group.maxByOrNull { it.startMs }!!
                LoggedVideo(id, newest.title, newest.channel, group.sumOf { it.seconds }, newest.startMs, group.size)
            }
            .sortedByDescending { it.lastWatchedMs }

    /** Every word typed must appear in the title or channel (case-insensitive). */
    fun search(videos: List<LoggedVideo>, query: String): List<LoggedVideo> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return videos
        return videos.filter { v ->
            val hay = (v.title + " " + v.channel).lowercase()
            words.all { it in hay }
        }
    }

    /** 45 -> "<1 min", 780 -> "13 min", 3900 -> "1 h 05 min". */
    fun formatDuration(seconds: Int): String {
        if (seconds < 60) return "<1 min"
        val minutes = seconds / 60
        return if (minutes < 60) "$minutes min" else String.format(Locale.ROOT, "%d h %02d min", minutes / 60, minutes % 60)
    }

    fun serialize(list: List<WatchSession>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("v", it.videoId).put("t", it.title).put("c", it.channel).put("s", it.startMs).put("d", it.seconds))
        }
        return arr.toString()
    }

    fun deserialize(raw: String?): List<WatchSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("v")
                if (id.isBlank()) return@mapNotNull null
                WatchSession(id, o.optString("t"), o.optString("c"), o.optLong("s"), o.optInt("d"))
            }
        }.getOrDefault(emptyList())
    }
}

object WatchLog {
    private const val PREFS = "watch_log"
    private const val KEY_SESSIONS = "sessions_json"
    private const val KEY_EXCLUDED = "excluded_channels"
    private var prefs: SharedPreferences? = null

    private val _sessions = MutableStateFlow<List<WatchSession>>(emptyList())
    val sessions: StateFlow<List<WatchSession>> = _sessions.asStateFlow()

    private val _excluded = MutableStateFlow<Set<String>>(emptySet())
    val excludedChannels: StateFlow<Set<String>> = _excluded.asStateFlow()

    /** False while incognito or "pause watch history" is switched on. */
    val loggingAllowed: Boolean get() = !Tweaks.noWatchHistory

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        _sessions.value = WatchInsightsLogic.deserialize(p.getString(KEY_SESSIONS, null))
        _excluded.value = p.getStringSet(KEY_EXCLUDED, emptySet()).orEmpty().toSet()
    }

    fun record(videoId: String, title: String, channel: String, startMs: Long, seconds: Int) {
        if (!loggingAllowed || videoId.isBlank() || seconds < WatchInsightsLogic.MIN_SECONDS) return
        if (channel in _excluded.value) return
        save(WatchInsightsLogic.addSession(_sessions.value, WatchSession(videoId, title, channel, startMs, seconds), System.currentTimeMillis()))
    }

    /** Stops logging a channel and removes what was already logged for it. */
    fun setExcluded(channel: String, excluded: Boolean) {
        val set = if (excluded) _excluded.value + channel else _excluded.value - channel
        _excluded.value = set
        prefs?.edit()?.putStringSet(KEY_EXCLUDED, set)?.apply()
        if (excluded) save(_sessions.value.filterNot { it.channel == channel })
    }

    fun deleteVideo(videoId: String) = save(_sessions.value.filterNot { it.videoId == videoId })

    fun clear() = save(emptyList())

    private fun save(list: List<WatchSession>) {
        _sessions.value = list
        prefs?.edit()?.putString(KEY_SESSIONS, WatchInsightsLogic.serialize(list))?.apply()
    }
}

/**
 * Listens to the playback service's player and logs how long each video actually played. Short
 * pauses and buffering are merged back into one session by [WatchInsightsLogic.addSession].
 */
class WatchSessionTracker(private val player: Player) : Player.Listener {
    private var videoId: String? = null
    private var title = ""
    private var channel = ""
    private var startMs = 0L
    private var startElapsed = 0L

    fun start() {
        player.addListener(this)
        if (player.isPlaying) begin()
    }

    fun stop() {
        flush()
        player.removeListener(this)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) begin() else flush()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        flush()
        if (player.isPlaying) begin()
    }

    private fun begin() {
        if (videoId != null) return
        val item = player.currentMediaItem ?: return
        videoId = item.mediaId
        title = item.mediaMetadata.title?.toString().orEmpty()
        channel = item.mediaMetadata.artist?.toString().orEmpty()
        startMs = System.currentTimeMillis()
        startElapsed = SystemClock.elapsedRealtime()
    }

    private fun flush() {
        val id = videoId ?: return
        videoId = null
        val seconds = ((SystemClock.elapsedRealtime() - startElapsed) / 1000L).toInt()
        WatchLog.record(id, title, channel, startMs, seconds)
    }
}

@Composable
fun WatchInsightsSheet(
    onOpenVideo: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sessions by WatchLog.sessions.collectAsState()
    val excluded by WatchLog.excludedChannels.collectAsState()
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val tz = remember { TimeZone.getDefault() }
    val now = System.currentTimeMillis()
    val format = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    val today = WatchInsightsLogic.totalSeconds(sessions, WatchInsightsLogic.dayStart(now, tz), Long.MAX_VALUE)
    val week = remember(sessions) { WatchInsightsLogic.dailyTotals(sessions, 7, System.currentTimeMillis(), tz) }
    val hours = remember(sessions) { WatchInsightsLogic.hourHistogram(sessions, tz) }
    val streak = remember(sessions) { WatchInsightsLogic.streakDays(sessions, System.currentTimeMillis(), tz) }
    val top = remember(sessions) { WatchInsightsLogic.topChannels(sessions, 5) }
    val videos = remember(sessions) { WatchInsightsLogic.videos(sessions) }
    val found = remember(videos, query) { WatchInsightsLogic.search(videos, query).take(30) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Watch insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) { Text("Done") }
        }
        YtxNote("Kept on this phone only. Backups include it; clearing it here deletes it for good.")
        if (!WatchLog.loggingAllowed) {
            YtxNote("Logging is paused because incognito or \"pause watch history\" is on.", isError = true)
        }

        if (sessions.isEmpty()) {
            YtxPanel {
                YtxNote("Nothing recorded yet. Watch something for a few seconds and your stats will appear here.")
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTileCompat("Today", WatchInsightsLogic.formatDuration(today), Modifier.weight(1f))
                StatTileCompat("7 days", WatchInsightsLogic.formatDuration(week.sum()), Modifier.weight(1f))
                StatTileCompat("Streak", if (streak == 1) "1 day" else "$streak days", Modifier.weight(1f))
            }

            YtxSectionLabel("Last 7 days")
            YtxPanel {
                val peak = (week.maxOrNull() ?: 0).coerceAtLeast(1)
                val labels = remember {
                    val cal = Calendar.getInstance(tz)
                    (6 downTo 0).map { back ->
                        cal.timeInMillis = WatchInsightsLogic.dayStart(System.currentTimeMillis(), tz, -back)
                        cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty().take(2)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .semantics { contentDescription = "Minutes per day: " + week.joinToString { (it / 60).toString() } },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    week.forEachIndexed { i, secs ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((64f * secs / peak).coerceAtLeast(2f).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c.primary.copy(alpha = if (secs == 0) 0.2f else 0.85f)),
                            )
                            Text(labels[i], fontSize = 11.sp, color = c.onSurfaceVariant)
                        }
                    }
                }
            }

            YtxSectionLabel("When you watch")
            YtxPanel {
                val peakHour = hours.indices.maxByOrNull { hours[it] } ?: 0
                val peakValue = hours.max().coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().semantics { contentDescription = "Most active hour: $peakHour:00" },
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    hours.forEach { secs ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(c.primary.copy(alpha = 0.12f + 0.78f * secs / peakValue)),
                        )
                    }
                }
                YtxNote("Most active around ${String.format(Locale.ROOT, "%02d:00", peakHour)}  ·  midnight to 23:00, left to right")
            }

            YtxSectionLabel("Top channels")
            YtxPanel {
                top.forEach { ch ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ch.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(WatchInsightsLogic.formatDuration(ch.seconds), fontSize = 12.sp, color = c.onSurfaceVariant)
                        }
                        YtxChip(label = "Don't log", selected = false) { WatchLog.setExcluded(ch.channel, true) }
                    }
                }
            }
        }

        if (excluded.isNotEmpty()) {
            YtxSectionLabel("Channels not logged")
            YtxPanel {
                excluded.sorted().forEach { name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        YtxChip(label = "Log again", selected = false) { WatchLog.setExcluded(name, false) }
                    }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            YtxSectionLabel("History")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search title or channel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (found.isEmpty()) {
                YtxNote("No watched video matches \"$query\".")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    found.forEach { v ->
                        YtxPanel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable(onClickLabel = "Open ${v.title}") {
                                            onOpenVideo(VideoItem(v.videoId, v.title, v.channel, null, null, null, 0L, -1, null))
                                        },
                                ) {
                                    Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${v.channel} · ${WatchInsightsLogic.formatDuration(v.totalSeconds)} · ${format.format(Date(v.lastWatchedMs))}",
                                        fontSize = 12.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { WatchLog.deleteVideo(v.videoId) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove ${v.title} from the log")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (confirmClear) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YtxChip(label = "Yes, delete the whole log", selected = true) { WatchLog.clear(); confirmClear = false }
                    YtxChip(label = "Cancel", selected = false) { confirmClear = false }
                }
            } else {
                YtxChip(label = "Clear watch log", selected = false) { confirmClear = true }
            }
        }
    }
}

@Composable
private fun StatTileCompat(label: String, value: String, modifier: Modifier = Modifier) {
    YtxPanel(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
