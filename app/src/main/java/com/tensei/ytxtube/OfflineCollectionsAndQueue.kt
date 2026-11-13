package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Feature C: Offline collections, download queue reordering, and download profile application.
 * Integrates with [DownloadTask] (Downloads.kt), identified by [DownloadTask.key] and [DownloadQueueProfile] / [DownloadPriority] (OfflineIntelligenceEngine.kt).
 */
data class OfflineCollection(
    val id: String,
    val name: String,
    val videoIds: List<String>,
    val createdAtMs: Long = System.currentTimeMillis(),
)

data class OfflineCollectionsState(
    val collections: List<OfflineCollection> = emptyList(),
    val orderedQueueIds: List<String> = emptyList(),
    val activeProfileId: String = "balanced",
)

object OfflineCollectionsLogic {
    fun moveId(list: List<String>, fromIndex: Int, toIndex: Int): List<String> {
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return list
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    fun sortTasksByOrderAndPriority(
        tasks: List<DownloadTask>,
        orderedIds: List<String>,
        priorities: Map<String, DownloadPriority> = emptyMap(),
    ): List<DownloadTask> {
        val rankMap = orderedIds.withIndex().associate { (idx, id) -> id to idx }
        return tasks.sortedWith(
            compareBy<DownloadTask>(
                { priorities[it.key]?.weight?.let { w -> -w } ?: 0 },
                { rankMap[it.key] ?: Int.MAX_VALUE },
            ),
        )
    }

    fun serialize(state: OfflineCollectionsState): String {
        val root = JSONObject()
        root.put("activeProfileId", state.activeProfileId)
        root.put("orderedQueueIds", JSONArray(state.orderedQueueIds))
        val colArr = JSONArray()
        state.collections.forEach { col ->
            colArr.put(
                JSONObject()
                    .put("id", col.id)
                    .put("name", col.name)
                    .put("createdAtMs", col.createdAtMs)
                    .put("videoIds", JSONArray(col.videoIds)),
            )
        }
        root.put("collections", colArr)
        return root.toString()
    }

    fun deserialize(raw: String?): OfflineCollectionsState {
        if (raw.isNullOrBlank()) return OfflineCollectionsState()
        return runCatching {
            val root = JSONObject(raw)
            val profileId = root.optString("activeProfileId", "balanced")
            val queueArr = root.optJSONArray("orderedQueueIds") ?: JSONArray()
            val orderedIds = (0 until queueArr.length()).mapNotNull { queueArr.optString(it).takeIf { s -> s.isNotBlank() } }
            val colArr = root.optJSONArray("collections") ?: JSONArray()
            val collections = (0 until colArr.length()).mapNotNull { i ->
                val o = colArr.optJSONObject(i) ?: return@mapNotNull null
                val idsArr = o.optJSONArray("videoIds") ?: JSONArray()
                val vids = (0 until idsArr.length()).mapNotNull { j -> idsArr.optString(j).takeIf { it.isNotBlank() } }
                OfflineCollection(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "Collection"),
                    videoIds = vids,
                    createdAtMs = o.optLong("createdAtMs", 0L),
                )
            }
            OfflineCollectionsState(
                collections = collections,
                orderedQueueIds = orderedIds,
                activeProfileId = profileId,
            )
        }.getOrDefault(OfflineCollectionsState())
    }
}

object OfflineCollectionsManager {
    private const val PREFS_NAME = "offline_collections_prefs"
    private const val KEY_STATE = "state_json"
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(OfflineCollectionsState())
    val state: StateFlow<OfflineCollectionsState> = _state.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        _state.value = OfflineCollectionsLogic.deserialize(p.getString(KEY_STATE, null))
    }

    fun createCollection(name: String): OfflineCollection {
        val trimmed = name.trim().ifEmpty { "Offline Mix" }
        val col = OfflineCollection(id = UUID.randomUUID().toString(), name = trimmed, videoIds = emptyList())
        save(_state.value.copy(collections = _state.value.collections + col))
        return col
    }

    fun deleteCollection(collectionId: String) {
        save(_state.value.copy(collections = _state.value.collections.filterNot { it.id == collectionId }))
    }

    fun addVideoToCollection(collectionId: String, videoId: String) {
        val updated = _state.value.collections.map { col ->
            if (col.id == collectionId && videoId !in col.videoIds) {
                col.copy(videoIds = col.videoIds + videoId)
            } else {
                col
            }
        }
        save(_state.value.copy(collections = updated))
    }

    fun removeVideoFromCollection(collectionId: String, videoId: String) {
        val updated = _state.value.collections.map { col ->
            if (col.id == collectionId) col.copy(videoIds = col.videoIds.filterNot { it == videoId }) else col
        }
        save(_state.value.copy(collections = updated))
    }

    fun syncQueueIds(currentTaskIds: List<String>) {
        val existing = _state.value.orderedQueueIds.filter { it in currentTaskIds }
        val newIds = currentTaskIds.filter { it !in existing }
        save(_state.value.copy(orderedQueueIds = existing + newIds))
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val moved = OfflineCollectionsLogic.moveId(_state.value.orderedQueueIds, fromIndex, toIndex)
        save(_state.value.copy(orderedQueueIds = moved))
    }

    fun setActiveProfile(profileId: String) {
        save(_state.value.copy(activeProfileId = profileId))
    }

    private fun save(newState: OfflineCollectionsState) {
        _state.value = newState
        prefs?.edit()?.putString(KEY_STATE, OfflineCollectionsLogic.serialize(newState))?.apply()
    }
}

@Composable
fun OfflineCollectionsSheet(
    tasks: List<DownloadTask>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by OfflineCollectionsManager.state.collectAsState()
    val taskKeys = tasks.map { it.key }
    LaunchedEffect(taskKeys) { OfflineCollectionsManager.syncQueueIds(taskKeys) }
    var newCollectionName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Offline Collections & Queue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onDismiss) { Text("Done") }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newCollectionName,
                onValueChange = { newCollectionName = it },
                label = { Text("New collection name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newCollectionName.isNotBlank()) {
                        OfflineCollectionsManager.createCollection(newCollectionName)
                        newCollectionName = ""
                    }
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create collection")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Collections (${state.collections.size})", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))

        if (state.collections.isEmpty()) {
            Text(
                text = "No offline collections yet. Create one above to group downloaded videos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.collections.forEach { col ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassFill(0.5f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(col.name, fontWeight = FontWeight.SemiBold)
                        Text("${col.videoIds.size} items", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { OfflineCollectionsManager.deleteCollection(col.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete collection")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Download Queue Order", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))

        val orderedTasks = OfflineCollectionsLogic.sortTasksByOrderAndPriority(tasks, state.orderedQueueIds)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            orderedTasks.forEachIndexed { idx, task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassFill(0.45f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${task.status.name} · ${task.height}p",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { OfflineCollectionsManager.moveQueueItem(idx, idx - 1) }) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = { OfflineCollectionsManager.moveQueueItem(idx, idx + 1) }) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                    }
                }
            }
        }
    }
}