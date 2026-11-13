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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Feature E: Music crossfade volume-ramp controller and custom music folders.
 * Complements [CrossfadePreset] and [ProfessionalMusicSystem] (ProfessionalMusicEngine.kt).
 */
enum class CrossfadeCurve(val label: String) {
    LINEAR("Linear"),
    EQUAL_POWER("Equal Power"),
    EXPONENTIAL("Exponential"),
}

data class MusicCrossfadeConfig(
    val enabled: Boolean = false,
    val durationSec: Float = 4f,
    val curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER,
)

data class MusicFolder(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
)

object MusicCrossfadeMath {
    /**
     * Returns Pair(outgoingVolume, incomingVolume) in 0f..1f for normalized progress t in 0f..1f.
     */
    fun computeGains(progress: Float, curve: CrossfadeCurve): Pair<Float, Float> {
        val t = progress.coerceIn(0f, 1f)
        return when (curve) {
            CrossfadeCurve.LINEAR -> (1f - t) to t
            CrossfadeCurve.EQUAL_POWER -> {
                val angle = (t * (PI / 2.0)).toFloat()
                cos(angle).coerceIn(0f, 1f) to sin(angle).coerceIn(0f, 1f)
            }
            CrossfadeCurve.EXPONENTIAL -> {
                val outGain = (1f - t).pow(2f)
                val inGain = t.pow(2f)
                outGain to inGain
            }
        }
    }
}

class MusicCrossfadeController(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate),
) {
    private var monitorJob: Job? = null
    private var lastBaseVolume: Float = 1f

    fun attach(
        player: Player,
        configFlow: StateFlow<MusicCrossfadeConfig>,
        onAdvanceToNext: () -> Unit,
    ) {
        detach()
        lastBaseVolume = player.volume.coerceIn(0.05f, 1f)
        monitorJob = scope.launch {
            var triggeredTransition = false
            while (isActive) {
                val cfg = configFlow.value
                if (cfg.enabled && player.isPlaying && player.duration > 0L) {
                    val remainingMs = (player.duration - player.currentPosition).coerceAtLeast(0L)
                    val fadeMs = (cfg.durationSec * 1000f).toLong().coerceAtLeast(500L)
                    if (remainingMs in 1L..fadeMs) {
                        val progress = 1f - (remainingMs.toFloat() / fadeMs.toFloat())
                        val (outGain, _) = MusicCrossfadeMath.computeGains(progress, cfg.curve)
                        player.volume = (lastBaseVolume * outGain).coerceIn(0f, 1f)
                        if (!triggeredTransition && remainingMs <= 160L) {
                            triggeredTransition = true
                            onAdvanceToNext()
                            rampIn(player, cfg, fadeMs)
                            triggeredTransition = false
                        }
                    } else if (player.volume != lastBaseVolume) {
                        player.volume = lastBaseVolume
                    }
                } else {
                    triggeredTransition = false
                }
                delay(80L)
            }
        }
    }

    private suspend fun rampIn(player: Player, cfg: MusicCrossfadeConfig, fadeMs: Long) {
        val steps = 10
        val stepDelay = (fadeMs / 2L / steps).coerceAtLeast(25L)
        for (step in 1..steps) {
            val progress = step.toFloat() / steps.toFloat()
            val (_, inGain) = MusicCrossfadeMath.computeGains(progress, cfg.curve)
            player.volume = (lastBaseVolume * inGain).coerceIn(0f, 1f)
            delay(stepDelay)
        }
        player.volume = lastBaseVolume
    }

    fun detach() {
        monitorJob?.cancel()
        monitorJob = null
    }
}

object MusicFoldersManager {
    private const val PREFS_NAME = "music_crossfade_folders"
    private const val KEY_JSON = "state_json"
    private var prefs: SharedPreferences? = null

    private val _crossfadeConfig = MutableStateFlow(MusicCrossfadeConfig())
    val crossfadeConfig: StateFlow<MusicCrossfadeConfig> = _crossfadeConfig.asStateFlow()

    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        deserializeAndApply(p.getString(KEY_JSON, null))
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeConfig.value = _crossfadeConfig.value.copy(enabled = enabled)
        persist()
    }

    fun setCrossfadeDuration(seconds: Float) {
        _crossfadeConfig.value = _crossfadeConfig.value.copy(durationSec = seconds.coerceIn(1f, 12f))
        persist()
    }

    fun setCrossfadeCurve(curve: CrossfadeCurve) {
        _crossfadeConfig.value = _crossfadeConfig.value.copy(curve = curve)
        persist()
    }

    fun createFolder(name: String): MusicFolder {
        val folder = MusicFolder(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Music Folder" },
        )
        _folders.value = _folders.value + folder
        persist()
        return folder
    }

    fun deleteFolder(folderId: String) {
        _folders.value = _folders.value.filterNot { it.id == folderId }
        persist()
    }

    fun addTrackToFolder(folderId: String, trackId: String) {
        _folders.value = _folders.value.map { folder ->
            if (folder.id == folderId && trackId !in folder.trackIds) {
                folder.copy(trackIds = folder.trackIds + trackId)
            } else {
                folder
            }
        }
        persist()
    }

    fun removeTrackFromFolder(folderId: String, trackId: String) {
        _folders.value = _folders.value.map { folder ->
            if (folder.id == folderId) {
                folder.copy(trackIds = folder.trackIds.filterNot { it == trackId })
            } else {
                folder
            }
        }
        persist()
    }

    internal fun serializeState(cfg: MusicCrossfadeConfig, folderList: List<MusicFolder>): String {
        val root = JSONObject()
        root.put(
            "crossfade",
            JSONObject()
                .put("enabled", cfg.enabled)
                .put("durationSec", cfg.durationSec.toDouble())
                .put("curve", cfg.curve.name),
        )
        val arr = JSONArray()
        folderList.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("trackIds", JSONArray(f.trackIds)),
            )
        }
        root.put("folders", arr)
        return root.toString()
    }

    internal fun deserializeAndApply(raw: String?) {
        if (raw.isNullOrBlank()) return
        runCatching {
            val root = JSONObject(raw)
            root.optJSONObject("crossfade")?.let { c ->
                val curve = CrossfadeCurve.entries.find { it.name == c.optString("curve") }
                    ?: CrossfadeCurve.EQUAL_POWER
                _crossfadeConfig.value = MusicCrossfadeConfig(
                    enabled = c.optBoolean("enabled", false),
                    durationSec = c.optDouble("durationSec", 4.0).toFloat().coerceIn(1f, 12f),
                    curve = curve,
                )
            }
            val arr = root.optJSONArray("folders") ?: JSONArray()
            _folders.value = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ids = o.optJSONArray("trackIds") ?: JSONArray()
                val tracks = (0 until ids.length()).mapNotNull { j -> ids.optString(j).takeIf { it.isNotBlank() } }
                MusicFolder(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "Music Folder"),
                    trackIds = tracks,
                )
            }
        }
    }

    private fun persist() {
        prefs?.edit()?.putString(KEY_JSON, serializeState(_crossfadeConfig.value, _folders.value))?.apply()
    }
}

@Composable
fun MusicFoldersAndCrossfadeSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cfg by MusicFoldersManager.crossfadeConfig.collectAsState()
    val folders by MusicFoldersManager.folders.collectAsState()
    var folderName by remember { mutableStateOf("") }

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
                text = "Music Crossfade & Folders",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onDismiss) { Text("Done") }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(glassFill(0.55f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Smooth Track Crossfade", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${cfg.durationSec.toInt()}s · ${cfg.curve.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = cfg.enabled,
                onCheckedChange = { MusicFoldersManager.setCrossfadeEnabled(it) },
            )
        }

        if (cfg.enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = cfg.durationSec,
                onValueChange = { MusicFoldersManager.setCrossfadeDuration(it) },
                valueRange = 1f..12f,
                steps = 10,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("New music folder") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (folderName.isNotBlank()) {
                        MusicFoldersManager.createFolder(folderName)
                        folderName = ""
                    }
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create folder")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            folders.forEach { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassFill(0.45f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${folder.trackIds.size} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { MusicFoldersManager.deleteFolder(folder.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete folder")
                    }
                }
            }
        }
    }
}