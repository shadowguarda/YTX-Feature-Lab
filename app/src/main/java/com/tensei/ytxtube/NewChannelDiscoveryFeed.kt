package com.tensei.ytxtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Feature G: On-device new-channel discovery and personalised local Home feed mixer.
 * Reuses [VideoItem] (YouTube.kt) and [DiscoveryRanker] patterns without external tracking.
 */
data class DiscoveredChannelCandidate(
    val channelName: String,
    val channelUrl: String?,
    val sampleVideo: VideoItem,
    val noveltyScore: Float,
    val reason: String,
)

sealed class LocalHomeFeedState {
    data object Loading : LocalHomeFeedState()
    data object Empty : LocalHomeFeedState()
    data class Error(val message: String) : LocalHomeFeedState()
    data class Ready(
        val feedVideos: List<VideoItem>,
        val newChannels: List<DiscoveredChannelCandidate>,
    ) : LocalHomeFeedState()
}

object LocalHomeFeedEngine {

    private val stopWords = setOf(
        "the", "and", "for", "with", "this", "that", "from", "your", "how", "why", "what", "official", "video",
    )

    fun extractInterestKeywords(watchedTitles: List<String>, maxKeywords: Int = 15): Set<String> {
        val freq = mutableMapOf<String, Int>()
        watchedTitles.forEach { title ->
            title.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 && it !in stopWords }
                .forEach { token -> freq[token] = (freq[token] ?: 0) + 1 }
        }
        return freq.entries
            .sortedByDescending { it.value }
            .take(maxKeywords)
            .map { it.key }
            .toSet()
    }

    fun discoverNewChannels(
        candidates: List<VideoItem>,
        subscribedChannelNames: Set<String>,
        watchedChannelNames: Set<String>,
        interestKeywords: Set<String>,
        limit: Int = 10,
    ): List<DiscoveredChannelCandidate> {
        val normalizedSubs = subscribedChannelNames.map { it.lowercase() }.toSet()
        val normalizedWatched = watchedChannelNames.map { it.lowercase() }.toSet()

        return candidates
            .filter { it.channel.isNotBlank() && it.channel.lowercase() !in normalizedSubs }
            .groupBy { it.channel.lowercase() }
            .values
            .mapNotNull { channelVideos ->
                val best = channelVideos.maxByOrNull { it.views } ?: return@mapNotNull null
                val isNeverWatched = best.channel.lowercase() !in normalizedWatched
                val titleTokens = best.title.lowercase().split(Regex("[^a-z0-9]+")).toSet()
                val matchedTopics = titleTokens.intersect(interestKeywords)
                val topicBoost = matchedTopics.size * 0.25f
                val noveltyBoost = if (isNeverWatched) 0.5f else 0.1f
                val score = noveltyBoost + topicBoost
                val reason = when {
                    matchedTopics.isNotEmpty() -> "Matches your interest in ${matchedTopics.take(2).joinToString(", ")}"
                    isNeverWatched -> "New creator outside your subscriptions"
                    else -> "Fresh upload from an unsubscribed channel"
                }
                DiscoveredChannelCandidate(
                    channelName = best.channel,
                    channelUrl = best.channelUrl,
                    sampleVideo = best,
                    noveltyScore = score,
                    reason = reason,
                )
            }
            .sortedByDescending { it.noveltyScore }
            .take(limit)
    }

    fun buildLocalHomeFeed(
        subscriptionVideos: List<VideoItem>,
        candidateVideos: List<VideoItem>,
        watchedVideoIds: Set<String>,
        interestKeywords: Set<String>,
        maxItems: Int = 40,
    ): List<VideoItem> {
        val unseenSubs = subscriptionVideos.filter { it.id !in watchedVideoIds }
        val scoredCandidates = candidateVideos
            .filter { it.id !in watchedVideoIds && unseenSubs.none { s -> s.id == it.id } }
            .sortedByDescending { video ->
                val tokens = video.title.lowercase().split(Regex("[^a-z0-9]+")).toSet()
                tokens.intersect(interestKeywords).size
            }

        // Interleave 2 subscription videos with 1 discovery video for balanced freshness
        val mixed = mutableListOf<VideoItem>()
        var sIdx = 0
        var cIdx = 0
        while (mixed.size < maxItems && (sIdx < unseenSubs.size || cIdx < scoredCandidates.size)) {
            repeat(2) {
                if (sIdx < unseenSubs.size && mixed.size < maxItems) {
                    mixed.add(unseenSubs[sIdx++])
                }
            }
            if (cIdx < scoredCandidates.size && mixed.size < maxItems) {
                mixed.add(scoredCandidates[cIdx++])
            }
        }
        return mixed.distinctBy { it.id }
    }
}

@Composable
fun NewChannelDiscoverySection(
    state: LocalHomeFeedState,
    onRetry: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Discover New Creators",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh discovery")
            }
        }

        when (state) {
            LocalHomeFeedState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            LocalHomeFeedState.Empty -> {
                Text(
                    text = "Watch a few videos to unlock on-device creator discovery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            is LocalHomeFeedState.Error -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
            is LocalHomeFeedState.Ready -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.newChannels, key = { it.channelName }) { candidate ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = glassFill(0.55f),
                            modifier = Modifier
                                .width(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onOpenVideo(candidate.sampleVideo) },
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = candidate.channelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = candidate.sampleVideo.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = candidate.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}