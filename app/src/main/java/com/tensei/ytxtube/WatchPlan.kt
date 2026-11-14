package com.tensei.ytxtube

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Watch Plan: schedule a video for a time ("tonight 9 PM", "tomorrow morning") and get a local
 * reminder notification. Everything stays on the device.
 */
data class PlannedVideo(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    val durationSec: Long,
    val dueAtMs: Long,
    val done: Boolean = false,
)

enum class PlanStatus { Upcoming, Due, Overdue, Done }

data class PlanSlot(val label: String, val atMs: Long)

/** Pure scheduling logic (unit-tested). */
object WatchPlanLogic {
    /** How long after its time an item still counts as "due now" rather than overdue. */
    const val DUE_WINDOW_MS = 15 * 60_000L

    fun status(item: PlannedVideo, nowMs: Long): PlanStatus = when {
        item.done -> PlanStatus.Done
        nowMs < item.dueAtMs -> PlanStatus.Upcoming
        nowMs - item.dueAtMs <= DUE_WINDOW_MS -> PlanStatus.Due
        else -> PlanStatus.Overdue
    }

    /** [hour]:00 local time, [dayOffset] days from [nowMs]. */
    fun atHour(nowMs: Long, tz: TimeZone, dayOffset: Int, hour: Int): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = nowMs
        c.add(Calendar.DAY_OF_YEAR, dayOffset)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** The next [dayOfWeek] (a Calendar constant) at [hour]:00 that is more than ten minutes away. */
    fun nextWeekday(nowMs: Long, tz: TimeZone, dayOfWeek: Int, hour: Int): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = nowMs
        val offset = (dayOfWeek - c.get(Calendar.DAY_OF_WEEK) + 7) % 7
        val first = atHour(nowMs, tz, offset, hour)
        return if (first > nowMs + 10 * 60_000L) first else atHour(nowMs, tz, offset + 7, hour)
    }

    /** Ready-made times to offer; the "tonight" slot only appears if it is a sensible distance away. */
    fun quickSlots(nowMs: Long, tz: TimeZone = TimeZone.getDefault()): List<PlanSlot> {
        val slots = mutableListOf(PlanSlot("In 1 hour", nowMs + 3_600_000L))
        val tonight = atHour(nowMs, tz, 0, 21)
        if (tonight > nowMs + 70 * 60_000L) slots += PlanSlot("Tonight 9 PM", tonight)
        slots += PlanSlot("Tomorrow 9 AM", atHour(nowMs, tz, 1, 9))
        slots += PlanSlot("Tomorrow 9 PM", atHour(nowMs, tz, 1, 21))
        slots += PlanSlot("Saturday 10 AM", nextWeekday(nowMs, tz, Calendar.SATURDAY, 10))
        return slots
    }

    /** Pending items by time (soonest first), then finished items (latest first). */
    fun sorted(items: List<PlannedVideo>): List<PlannedVideo> {
        val pending = items.filter { !it.done }.sortedBy { it.dueAtMs }
        val finished = items.filter { it.done }.sortedByDescending { it.dueAtMs }
        return pending + finished
    }

    /** Replaces the item with the same id, or adds it. */
    fun upsert(items: List<PlannedVideo>, item: PlannedVideo): List<PlannedVideo> =
        if (items.any { it.id == item.id }) items.map { if (it.id == item.id) item else it } else items + item

    fun pendingCount(items: List<PlannedVideo>, nowMs: Long): Int =
        items.count { val s = status(it, nowMs); s == PlanStatus.Due || s == PlanStatus.Overdue }

    fun serialize(items: List<PlannedVideo>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("channel", it.channel)
                    .put("thumb", it.thumbnail.orEmpty()).put("dur", it.durationSec)
                    .put("due", it.dueAtMs).put("done", it.done),
            )
        }
        return arr.toString()
    }

    fun deserialize(raw: String?): List<PlannedVideo> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                PlannedVideo(
                    id = id,
                    title = o.optString("title"),
                    channel = o.optString("channel"),
                    thumbnail = o.optString("thumb").ifBlank { null },
                    durationSec = o.optLong("dur"),
                    dueAtMs = o.optLong("due"),
                    done = o.optBoolean("done", false),
                )
            }
        }.getOrDefault(emptyList())
    }
}

object WatchPlanManager {
    private const val PREFS = "watch_plan"
    private const val KEY = "items_json"
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val _items = MutableStateFlow<List<PlannedVideo>>(emptyList())
    val items: StateFlow<List<PlannedVideo>> = _items.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        _items.value = WatchPlanLogic.deserialize(p.getString(KEY, null))
        rescheduleAll()
    }

    fun schedule(video: VideoItem, dueAtMs: Long) {
        val item = PlannedVideo(video.id, video.title, video.channel, video.thumbnail, video.durationSec, dueAtMs)
        save(WatchPlanLogic.upsert(_items.value, item))
        appContext?.let { WatchPlanReminders.schedule(it, item) }
    }

    fun markDone(id: String) {
        save(_items.value.map { if (it.id == id) it.copy(done = true) else it })
        appContext?.let { WatchPlanReminders.cancel(it, id) }
    }

    fun snooze(id: String, minutes: Int) {
        val now = System.currentTimeMillis()
        val updated = _items.value.map { if (it.id == id) it.copy(dueAtMs = now + minutes * 60_000L, done = false) else it }
        save(updated)
        updated.firstOrNull { it.id == id }?.let { item -> appContext?.let { WatchPlanReminders.schedule(it, item) } }
    }

    fun remove(id: String) {
        save(_items.value.filterNot { it.id == id })
        appContext?.let { WatchPlanReminders.cancel(it, id) }
    }

    fun clearDone() = save(_items.value.filterNot { it.done })

    /** Alarms are lost when the phone restarts, so every future reminder is set again at app start. */
    private fun rescheduleAll() {
        val c = appContext ?: return
        val now = System.currentTimeMillis()
        _items.value.filter { !it.done && it.dueAtMs > now }.forEach { WatchPlanReminders.schedule(c, it) }
    }

    private fun save(list: List<PlannedVideo>) {
        _items.value = list
        prefs?.edit()?.putString(KEY, WatchPlanLogic.serialize(list))?.apply()
    }
}

/** Alarm and notification plumbing for the reminders. */
object WatchPlanReminders {
    const val CHANNEL_ID = "watch_plan"
    const val EXTRA_ID = "plan_id"
    const val EXTRA_TITLE = "plan_title"
    const val EXTRA_CHANNEL = "plan_channel"

    private fun pending(context: Context, id: String, title: String, channel: String): PendingIntent {
        val intent = Intent(context, WatchPlanReceiver::class.java)
            .setAction("com.tensei.ytxtube.WATCH_PLAN.$id")
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_CHANNEL, channel)
        return PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** An inexact alarm: it needs no special permission and may arrive a few minutes late in Doze. */
    fun schedule(context: Context, item: PlannedVideo) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.dueAtMs, pending(context, item.id, item.title, item.channel))
        }
    }

    fun cancel(context: Context, id: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pending(context, id, "", "")) }
    }

    @SuppressLint("MissingPermission")
    fun notifyDue(context: Context, id: String, title: String, channel: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Watch plan reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Time to watch: $title")
            .setContentText(channel.ifBlank { "From your watch plan" })
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
    }
}

class WatchPlanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(WatchPlanReminders.EXTRA_ID) ?: return
        WatchPlanReminders.notifyDue(
            context, id,
            intent.getStringExtra(WatchPlanReminders.EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(WatchPlanReminders.EXTRA_CHANNEL).orEmpty(),
        )
    }
}

@Composable
fun WatchPlanSheet(
    player: PlayerState?,
    onOpenVideo: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val items by WatchPlanManager.items.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val notificationsOn = remember(now) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val current = player?.current

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Watch plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) { Text("Done") }
        }
        if (!notificationsOn) {
            YtxNote("Notifications are off for YTX, so reminders will not pop up. You can still see due items here.", isError = true)
        }

        YtxSectionLabel("Plan the video you are watching")
        YtxPanel {
            if (current == null) {
                YtxNote("Open a video, then come back here to schedule it for later.")
            } else {
                Text(current.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatchPlanLogic.quickSlots(now).forEach { slot ->
                        YtxChip(label = slot.label, selected = false) { WatchPlanManager.schedule(current, slot.atMs) }
                    }
                }
            }
        }

        YtxSectionLabel("Planned (${items.count { !it.done }})")
        if (items.isEmpty()) {
            YtxNote("Nothing planned yet. Schedule a video above and it will remind you at that time.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchPlanLogic.sorted(items).forEach { item ->
                    val status = WatchPlanLogic.status(item, now)
                    YtxPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                val minutes = if (item.durationSec > 0) " · ${(item.durationSec + 59) / 60} min" else ""
                                Text(item.channel + minutes, fontSize = 12.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = when (status) {
                                        PlanStatus.Upcoming -> "Reminder ${format.format(Date(item.dueAtMs))}"
                                        PlanStatus.Due -> "Due now"
                                        PlanStatus.Overdue -> "Overdue since ${format.format(Date(item.dueAtMs))}"
                                        PlanStatus.Done -> "Watched"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (status) {
                                        PlanStatus.Overdue -> c.error
                                        PlanStatus.Due -> c.primary
                                        else -> c.onSurfaceVariant
                                    },
                                )
                            }
                            IconButton(onClick = {
                                onOpenVideo(VideoItem(item.id, item.title, item.channel, null, item.thumbnail, null, item.durationSec, -1, null))
                                WatchPlanManager.markDone(item.id)
                            }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${item.title}") }
                            if (!item.done) {
                                IconButton(onClick = { WatchPlanManager.markDone(item.id) }) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Mark ${item.title} as watched")
                                }
                            }
                            IconButton(onClick = { WatchPlanManager.remove(item.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove ${item.title} from plan")
                            }
                        }
                        if (!item.done && status != PlanStatus.Upcoming) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                YtxChip(label = "Remind in 1 hour", selected = false) { WatchPlanManager.snooze(item.id, 60) }
                                YtxChip(label = "Remind in 10 min", selected = false) { WatchPlanManager.snooze(item.id, 10) }
                            }
                        }
                    }
                }
            }
            if (items.any { it.done }) {
                Spacer(Modifier.width(1.dp))
                TextButton(onClick = { WatchPlanManager.clearDone() }) { Text("Clear watched items") }
            }
        }
    }
}
