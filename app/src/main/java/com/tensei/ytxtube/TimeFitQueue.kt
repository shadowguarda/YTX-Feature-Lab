package com.tensei.ytxtube

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Time-Fit Queue: "I have 30 minutes". Builds a queue from your feeds that fits the time, putting
 * first the videos that match what you actually watch (taken from your on-device watch log) and the
 * videos you planned. It never goes over the time you give it.
 */
data class TimeFitResult(val picks: List<VideoItem>, val usedSec: Long, val budgetSec: Long) {
    val leftSec: Long get() = (budgetSec - usedSec).coerceAtLeast(0L)
}

/** Pure selection logic (unit-tested). */
object TimeFitLogic {
    private const val MIN_SECONDS = 30L

    /**
     * Points for a video: 3 for each of your interest words in its title, 2 if the channel is one you
     * watch a lot, 5 if you planned it.
     */
    fun score(video: VideoItem, interest: Set<String>, favoriteChannels: Set<String>, priorityIds: Set<String>): Int {
        val words = video.title.lowercase().split(Regex("[^a-z0-9]+")).toSet()
        var points = words.intersect(interest).size * 3
        if (video.channel.lowercase() in favoriteChannels) points += 2
        if (video.id in priorityIds) points += 5
        return points
    }

    /**
     * Picks the best-scoring videos that still fit, in score order (shorter first on ties). Live
     * streams, videos already in [exclude], duplicates and videos longer than the whole budget are skipped.
     */
    fun select(
        candidates: List<VideoItem>,
        budgetSec: Long,
        interest: Set<String> = emptySet(),
        favoriteChannels: Set<String> = emptySet(),
        priorityIds: Set<String> = emptySet(),
        exclude: Set<String> = emptySet(),
        maxItems: Int = 15,
    ): TimeFitResult {
        val pool = candidates
            .filter { !it.isLive && it.durationSec in MIN_SECONDS..budgetSec && it.id !in exclude }
            .distinctBy { it.id }
        val ranked = pool
            .map { it to score(it, interest, favoriteChannels, priorityIds) }
            .sortedWith(compareByDescending<Pair<VideoItem, Int>> { it.second }.thenBy { it.first.durationSec })
        val picks = mutableListOf<VideoItem>()
        var used = 0L
        for ((video, _) in ranked) {
            if (picks.size >= maxItems) break
            if (used + video.durationSec <= budgetSec) {
                picks += video
                used += video.durationSec
            }
        }
        return TimeFitResult(picks, used, budgetSec)
    }

    /** "28 of 30 min · 5 videos". */
    fun summary(result: TimeFitResult): String {
        val n = result.picks.size
        return "${(result.usedSec + 59) / 60} of ${result.budgetSec / 60} min · $n video${if (n == 1) "" else "s"}"
    }
}

/**
 * Starts the first video and queues the rest. The first video has to be loaded into the player before
 * more can be added (otherwise adding would replace it), so this waits for that.
 */
private suspend fun playOrQueue(player: PlayerState, picks: List<VideoItem>) {
    val first = picks.firstOrNull() ?: return
    if (player.current == null) {
        player.open(first)
        val loaded = withTimeoutOrNull(25_000L) {
            while (PlayerBridge.exo?.currentMediaItem?.mediaId != first.id) delay(150L)
            true
        }
        if (loaded != true) {
            player.actionMessage = "Could not start the first video, so the rest were not queued"
            return
        }
        picks.drop(1).forEach { player.addToQueue(it) }
    } else {
        picks.forEach { player.addToQueue(it) }
    }
}

@Composable
fun TimeFitSheet(player: PlayerState?, lib: LibraryData?, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val sessions by WatchLog.sessions.collectAsState()
    val planned by WatchPlanManager.items.collectAsState()
    var minutes by remember { mutableStateOf(30) }
    var built by remember { mutableStateOf<TimeFitResult?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(lib) {
        lib?.home?.ensureLoaded()
        lib?.subscriptions?.ensureLoaded()
    }

    val homeVideos = lib?.home?.videos.orEmpty()
    val subVideos = lib?.subscriptions?.videos.orEmpty()
    val loading = (lib?.home?.loading == true || lib?.subscriptions?.loading == true)
    val feedError = lib?.home?.error ?: lib?.subscriptions?.error
    val plannedVideos = remember(planned) {
        planned.filter { !it.done }.map { VideoItem(it.id, it.title, it.channel, null, it.thumbnail, null, it.durationSec, -1, null) }
    }
    val candidates = remember(plannedVideos, homeVideos, subVideos) { plannedVideos + subVideos + homeVideos }
    val interest = remember(sessions) {
        LocalHomeFeedEngine.extractInterestKeywords(WatchInsightsLogic.videos(sessions).take(60).map { it.title })
    }
    val favorites = remember(sessions) {
        WatchInsightsLogic.topChannels(sessions, 8).map { it.channel.lowercase() }.toSet()
    }
    val watchedIds = remember(sessions) { sessions.map { it.videoId }.toSet() }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Time-fit queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) { Text("Done") }
        }
        YtxNote("Tell it how long you have. It picks videos from your home feed, subscriptions and watch plan that fit, favouring what you usually watch.")

        YtxSectionLabel("How much time do you have?")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 20, 30, 45, 60, 90).forEach { m ->
                YtxChip(label = "$m min", selected = minutes == m) { minutes = m; built = null; status = null }
            }
        }

        Spacer(Modifier.height(12.dp))
        when {
            lib == null -> YtxNote("The library is not ready yet. Close Labs, open the Home tab once, then try again.", isError = true)
            loading && candidates.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("Loading your feeds…", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            candidates.isEmpty() -> {
                YtxNote(feedError ?: "No videos are loaded yet, so there is nothing to choose from.", isError = feedError != null)
                YtxChip(label = "Try loading again", selected = false) {
                    lib.home.refresh()
                    lib.subscriptions.refresh()
                }
            }
            else -> YtxChip(label = "Build a ${minutes}-minute queue", selected = true, enabled = !working) {
                built = TimeFitLogic.select(
                    candidates = candidates,
                    budgetSec = minutes * 60L,
                    interest = interest,
                    favoriteChannels = favorites,
                    priorityIds = plannedVideos.map { it.id }.toSet(),
                    exclude = watchedIds - plannedVideos.map { it.id }.toSet(),
                )
                status = null
            }
        }

        val result = built
        if (result != null) {
            YtxSectionLabel("Your queue")
            if (result.picks.isEmpty()) {
                YtxNote("Nothing in your feeds fits ${minutes} minutes. Try a longer time.")
            } else {
                YtxPanel {
                    Text(TimeFitLogic.summary(result), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    result.picks.forEachIndexed { i, v ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${i + 1}.", fontSize = 13.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("${v.channel} · ${(v.durationSec + 59) / 60} min", fontSize = 12.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (player == null) {
                    YtxNote("The player is not available here, so the queue cannot be started.", isError = true)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YtxChip(label = if (player.current == null) "Play now" else "Add to queue", selected = true, enabled = !working) {
                            working = true
                            scope.launch {
                                playOrQueue(player, result.picks)
                                working = false
                                status = "${result.picks.size} video${if (result.picks.size == 1) "" else "s"} queued"
                            }
                        }
                    }
                }
            }
        }
        status?.let { YtxNote(it) }
    }
}
