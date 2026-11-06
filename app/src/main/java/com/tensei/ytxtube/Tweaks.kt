package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A persisted on/off tweak. Reads are observable, so the UI follows changes at once. */
class BoolTweak(private val key: String, private val default: Boolean) {
    var value by mutableStateOf(default)
        private set

    fun load(p: SharedPreferences) { value = p.getBoolean(key, default) }
    fun update(v: Boolean) { value = v; Tweaks.prefs?.edit()?.putBoolean(key, v)?.apply() }
}

/** A persisted number tweak. */
class IntTweak(private val key: String, private val default: Int) {
    var value by mutableIntStateOf(default)
        private set

    fun load(p: SharedPreferences) { value = p.getInt(key, default) }
    fun update(v: Int) { value = v; Tweaks.prefs?.edit()?.putInt(key, v)?.apply() }
}

/**
 * Small everyday options gathered from other open-source YouTube clients (NewPipe, LibreTube,
 * SmartTube, ReVanced): feed filters, gesture tuning and watch-page actions. All of them are
 * stored on the device and applied live or the next time a feed loads.
 */
object Tweaks {
    internal var prefs: SharedPreferences? = null

    // Feed filters
    val hideShorts = BoolTweak("hide_shorts", false)
    val hideLive = BoolTweak("hide_live", false)
    val hideWatched = BoolTweak("hide_watched", false)
    val minLengthMin = IntTweak("min_length_min", 0)
    val maxLengthMin = IntTweak("max_length_min", 0)
    val minViewsK = IntTweak("min_views_k", 0)

    // Player
    val seekStepSec = IntTweak("seek_step_sec", 10)
    val holdSpeedX10 = IntTweak("hold_speed_x10", 20)
    val controlsHideSec = IntTweak("controls_hide_sec", 2)
    val remainingTime = BoolTweak("remaining_time", false)
    val haptics = BoolTweak("haptics", true)
    val resume = BoolTweak("resume", true)

    // Watch page
    val showShareAtTime = BoolTweak("share_at_time", true)
    val showCopyLink = BoolTweak("copy_link", true)
    val showOpenBrowser = BoolTweak("open_browser", true)
    val showMarkWatched = BoolTweak("mark_watched", true)
    val hideDownload = BoolTweak("hide_download", false)
    val hideRelated = BoolTweak("hide_related", false)
    val hideCommentsCard = BoolTweak("hide_comments", false)
    val hideChaptersCard = BoolTweak("hide_chapters", false)

    private val bools = listOf(
        hideShorts, hideLive, hideWatched, remainingTime, haptics, resume, showShareAtTime, showCopyLink,
        showOpenBrowser, showMarkWatched, hideDownload, hideRelated, hideCommentsCard, hideChaptersCard,
    )
    private val ints = listOf(minLengthMin, maxLengthMin, minViewsK, seekStepSec, holdSpeedX10, controlsHideSec)

    fun init(context: Context) {
        val p = context.getSharedPreferences("tweaks", Context.MODE_PRIVATE).also { prefs = it }
        bools.forEach { it.load(p) }
        ints.forEach { it.load(p) }
    }

    val seekStepMs: Long get() = seekStepSec.value * 1000L
    val holdSpeed: Float get() = holdSpeedX10.value / 10f
    val controlsHideMs: Long get() = controlsHideSec.value * 1000L

    val anyFeedFilter: Boolean
        get() = hideShorts.value || hideLive.value || hideWatched.value ||
            minLengthMin.value > 0 || maxLengthMin.value > 0 || minViewsK.value > 0

    /** True when [video] passes every feed filter. Videos with an unknown length are never hidden by length. */
    fun allows(video: VideoItem): Boolean {
        if (hideLive.value && video.isLive) return false
        val len = video.durationSec
        if (len > 0 && !video.isLive) {
            if (hideShorts.value && len <= 60) return false
            if (minLengthMin.value > 0 && len < minLengthMin.value * 60L) return false
            if (maxLengthMin.value > 0 && len > maxLengthMin.value * 60L) return false
        }
        if (minViewsK.value > 0 && video.views > 0 && video.views < minViewsK.value * 1000L) return false
        if (hideWatched.value && Watched.has(video.id)) return false
        return true
    }
}

/**
 * Videos watched to the end (or marked by hand). ResumeStore forgets a video once it is finished,
 * so this keeps its own list, newest last, capped so it stays small.
 */
object Watched {
    private const val MAX = 3_000
    private var prefs: SharedPreferences? = null
    private val ids = androidx.compose.runtime.mutableStateListOf<String>()

    fun init(context: Context) {
        val p = context.getSharedPreferences("watched", Context.MODE_PRIVATE).also { prefs = it }
        ids.clear()
        ids.addAll(p.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    }

    fun has(id: String): Boolean = id in ids

    fun add(id: String) {
        if (id.isBlank() || id in ids) return
        ids.add(id)
        while (ids.size > MAX) ids.removeAt(0)
        save()
    }

    fun remove(id: String) {
        if (ids.remove(id)) save()
    }

    private fun save() {
        prefs?.edit()?.putString("ids", ids.joinToString(","))?.apply()
    }
}

/** Every tweak in one scrolling sheet, grouped like the sections above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweaksSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp),
        ) {
            Text("YTX Tweaks", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Spacer(Modifier.height(4.dp))

            TweakHeader("Feeds and search")
            TweakSwitch("Hide Shorts-length videos", "Videos of a minute or less", Tweaks.hideShorts)
            TweakSwitch("Hide live streams", null, Tweaks.hideLive)
            TweakSwitch("Hide watched videos", "Videos you have watched to the end", Tweaks.hideWatched)
            TweakChoice("Hide videos shorter than", Tweaks.minLengthMin, listOf(0, 1, 3, 5, 10)) { if (it == 0) "Off" else "$it min" }
            TweakChoice("Hide videos longer than", Tweaks.maxLengthMin, listOf(0, 30, 60, 120, 240)) { if (it == 0) "Off" else "$it min" }
            TweakChoice("Hide videos with fewer views than", Tweaks.minViewsK, listOf(0, 1, 10, 100, 1000)) { if (it == 0) "Off" else "${it}K" }

            TweakHeader("Player")
            TweakChoice("Double-tap seek", Tweaks.seekStepSec, listOf(5, 10, 15, 20, 30)) { "$it s" }
            TweakChoice("Hold to speed up", Tweaks.holdSpeedX10, listOf(15, 20, 30, 40)) { "${it / 10f}x" }
            TweakChoice("Hide controls after", Tweaks.controlsHideSec, listOf(2, 3, 5, 8)) { "$it s" }
            TweakSwitch("Show remaining time", "Count down instead of showing the total length", Tweaks.remainingTime)
            TweakSwitch("Haptic feedback", "Small vibrations on taps", Tweaks.haptics)
            TweakSwitch("Resume where you left off", null, Tweaks.resume)

            TweakHeader("Watch page")
            TweakSwitch("Share at current time button", null, Tweaks.showShareAtTime)
            TweakSwitch("Copy link button", null, Tweaks.showCopyLink)
            TweakSwitch("Open in browser button", null, Tweaks.showOpenBrowser)
            TweakSwitch("Mark as watched button", null, Tweaks.showMarkWatched)
            TweakSwitch("Hide Download button", null, Tweaks.hideDownload)
            TweakSwitch("Hide chapters card", null, Tweaks.hideChaptersCard)
            TweakSwitch("Hide comments card", null, Tweaks.hideCommentsCard)
            TweakSwitch("Hide related videos", null, Tweaks.hideRelated)

            Spacer(Modifier.height(8.dp))
            Text("Feed filters apply the next time a feed loads or refreshes.", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TweakHeader(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun TweakSwitch(title: String, subtitle: String?, tweak: BoolTweak) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        Switch(checked = tweak.value, onCheckedChange = { tweak.update(it) })
    }
}

@Composable
private fun TweakChoice(title: String, tweak: IntTweak, options: List<Int>, label: (Int) -> String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = tweak.value == option,
                    onClick = { tweak.update(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
