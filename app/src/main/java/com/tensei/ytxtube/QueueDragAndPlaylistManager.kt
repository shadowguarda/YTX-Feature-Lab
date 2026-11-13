package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * Feature D: Queue drag/step reordering with undo and Save-as-Playlist persistence.
 * Integrates with [VideoItem] (YouTube.kt), [QueueStore] (QueueStore.kt), and [PlayerState].
 */
data class LocalSavedPlaylist(
    val id: String,
    val name: String,
    val items: List<VideoItem>,
    val createdAtMs: Long = System.currentTimeMillis(),
)

object QueueReorderLogic {
    fun moveItem(items: List<VideoItem>, fromIndex: Int, toIndex: Int): List<VideoItem> {
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable
    }

    fun serializePlaylists(playlists: List<LocalSavedPlaylist>): String {
        val arr = JSONArray()
        playlists.forEach { pl ->
            val itemsArr = JSONArray()
            pl.items.forEach { v ->
                itemsArr.put(
                    JSONObject()
                        .put("id", v.id)
                        .put("title", v.title)
                        .put("channel", v.channel)
                        .put("channelUrl", v.channelUrl.orEmpty())
                        .put("thumbnail", v.thumbnail.orEmpty())
                        .put("dur", v.durationSec),
                )
            }
            arr.put(
                JSONObject()
                    .put("id", pl.id)
                    .put("name", pl.name)
                    .put("createdAtMs", pl.createdAtMs)
                    .put("items", itemsArr),
            )
        }
        return arr.toString()
    }

    fun deserializePlaylists(raw: String?): List<LocalSavedPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val items = (0 until itemsArr.length()).mapNotNull { j ->
                    val vo = itemsArr.optJSONObject(j) ?: return@mapNotNull null
                    val id = vo.optString("id")
                    if (id.isBlank()) return@mapNotNull null
                    VideoItem(
                        id = id,
                        title = vo.optString("title"),
                        channel = vo.optString("channel"),
                        channelUrl = vo.optString("channelUrl").ifBlank { null },
                        thumbnail = vo.optString("thumbnail").ifBlank { null },
                        avatar = null,
                        durationSec = vo.optLong("dur"),
                        views = -1,
                        uploaded = null,
                    )
                }
                LocalSavedPlaylist(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "Saved Queue"),
                    items = items,
                    createdAtMs = o.optLong("createdAtMs", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }
}

object QueueDragAndPlaylistManager {
    private const val PREFS_NAME = "local_saved_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"
    private var prefs: SharedPreferences? = null

    private val _savedPlaylists = MutableStateFlow<List<LocalSavedPlaylist>>(emptyList())
    val savedPlaylists: StateFlow<List<LocalSavedPlaylist>> = _savedPlaylists.asStateFlow()

    private val _lastUndoSnapshot = MutableStateFlow<List<VideoItem>?>(null)
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        _savedPlaylists.value = QueueReorderLogic.deserializePlaylists(p.getString(KEY_PLAYLISTS, null))
    }

    fun reorderQueue(
        currentQueue: List<VideoItem>,
        fromIndex: Int,
        toIndex: Int,
        onApplyQueue: (List<VideoItem>) -> Unit,
    ) {
        val updated = QueueReorderLogic.moveItem(currentQueue, fromIndex, toIndex)
        if (updated != currentQueue) {
            _lastUndoSnapshot.value = currentQueue
            _canUndo.value = true
            onApplyQueue(updated)
        }
    }

    fun undoLastReorder(onApplyQueue: (List<VideoItem>) -> Unit): Boolean {
        val snapshot = _lastUndoSnapshot.value ?: return false
        _lastUndoSnapshot.value = null
        _canUndo.value = false
        onApplyQueue(snapshot)
        return true
    }

    fun saveQueueAsPlaylist(name: String, items: List<VideoItem>): LocalSavedPlaylist? {
        if (items.isEmpty()) return null
        val playlist = LocalSavedPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Saved Queue" },
            items = items.distinctBy { it.id },
        )
        val updated = listOf(playlist) + _savedPlaylists.value
        _savedPlaylists.value = updated
        prefs?.edit()?.putString(KEY_PLAYLISTS, QueueReorderLogic.serializePlaylists(updated))?.apply()
        return playlist
    }

    fun deleteSavedPlaylist(playlistId: String) {
        val updated = _savedPlaylists.value.filterNot { it.id == playlistId }
        _savedPlaylists.value = updated
        prefs?.edit()?.putString(KEY_PLAYLISTS, QueueReorderLogic.serializePlaylists(updated))?.apply()
    }
}

@Composable
fun QueueReorderAndSaveSheet(
    queue: List<VideoItem>,
    onApplyQueue: (List<VideoItem>) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onPlayAt: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canUndo by QueueDragAndPlaylistManager.canUndo.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

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
                text = "Reorder Queue (${queue.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row {
                if (canUndo) {
                    IconButton(onClick = { QueueDragAndPlaylistManager.undoLastReorder(onApplyQueue) }) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo reorder")
                    }
                }
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    enabled = queue.isNotEmpty(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Playlist")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (queue.isEmpty()) {
            Text(
                text = "Your queue is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(queue, key = { idx, item -> "${item.id}_$idx" }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(glassFill(0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        ) {
                            Text(
                                text = item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = item.channel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                QueueDragAndPlaylistManager.reorderQueue(queue, index, index - 1, onApplyQueue)
                            },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = {
                                QueueDragAndPlaylistManager.reorderQueue(queue, index, index + 1, onApplyQueue)
                            },
                            enabled = index < queue.lastIndex,
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                        }
                        IconButton(onClick = { onRemoveAt(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove from queue")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Queue as Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        QueueDragAndPlaylistManager.saveQueueAsPlaylist(playlistName, queue)
                        playlistName = ""
                        showSaveDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }
}