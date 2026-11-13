package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Feature A: Alternative player layouts & customisable control layout.
 * Reuses [ControlLayoutPreset] from PlayerEnhancements.kt and [glassFill] from Glass.kt.
 */
/** Layout presets for the customizable control bars (separate from the older PlayerLayoutPreset). */
enum class ControlLayoutPreset(val label: String) {
    Standard("Standard"),
    Minimal("Minimal"),
    Cinematic("Cinematic"),
    Compact("Compact"),
    Studio("Studio"),
}

enum class PlayerControlAction(val label: String, val icon: ImageVector) {
    PREVIOUS("Previous", Icons.Rounded.SkipPrevious),
    REWIND("Rewind", Icons.Rounded.FastRewind),
    PLAY_PAUSE("Play / Pause", Icons.Rounded.Pause),
    FORWARD("Forward", Icons.Rounded.FastForward),
    NEXT("Next", Icons.Rounded.SkipNext),
    CAPTIONS("Captions", Icons.Rounded.ClosedCaption),
    SPEED("Speed", Icons.Rounded.Speed),
    ASPECT_RATIO("Aspect Ratio", Icons.Rounded.AspectRatio),
    PIP("Picture-in-Picture", Icons.Rounded.PictureInPictureAlt),
    LOCK("Lock Screen", Icons.Rounded.Lock),
    QUEUE("Add to Queue", Icons.AutoMirrored.Rounded.PlaylistAdd),
    SETTINGS("Player Settings", Icons.Rounded.Settings),
    FULLSCREEN("Fullscreen", Icons.Rounded.Fullscreen),
}

enum class PlayerControlZone(val label: String) {
    TOP_BAR("Top Bar"),
    CENTER_TRANSPORT("Center Transport"),
    BOTTOM_BAR("Bottom Bar"),
}

data class PlayerControlSlot(
    val action: PlayerControlAction,
    val zone: PlayerControlZone,
    val visible: Boolean = true,
    val order: Int = 0,
)

data class PlayerLayoutState(
    val activePreset: ControlLayoutPreset = ControlLayoutPreset.Standard,
    val slots: List<PlayerControlSlot> = PlayerLayoutLogic.defaultSlots(ControlLayoutPreset.Standard),
    val compactTopBar: Boolean = false,
    val showTimeRemaining: Boolean = false,
)

object PlayerLayoutLogic {

    fun defaultSlots(preset: ControlLayoutPreset): List<PlayerControlSlot> = when (preset) {
        ControlLayoutPreset.Minimal -> listOf(
            PlayerControlSlot(PlayerControlAction.LOCK, PlayerControlZone.TOP_BAR, true, 0),
            PlayerControlSlot(PlayerControlAction.SETTINGS, PlayerControlZone.TOP_BAR, true, 1),
            PlayerControlSlot(PlayerControlAction.PREVIOUS, PlayerControlZone.CENTER_TRANSPORT, false, 0),
            PlayerControlSlot(PlayerControlAction.PLAY_PAUSE, PlayerControlZone.CENTER_TRANSPORT, true, 1),
            PlayerControlSlot(PlayerControlAction.NEXT, PlayerControlZone.CENTER_TRANSPORT, false, 2),
            PlayerControlSlot(PlayerControlAction.FULLSCREEN, PlayerControlZone.BOTTOM_BAR, true, 0),
        )
        ControlLayoutPreset.Cinematic -> listOf(
            PlayerControlSlot(PlayerControlAction.CAPTIONS, PlayerControlZone.TOP_BAR, true, 0),
            PlayerControlSlot(PlayerControlAction.ASPECT_RATIO, PlayerControlZone.TOP_BAR, true, 1),
            PlayerControlSlot(PlayerControlAction.SETTINGS, PlayerControlZone.TOP_BAR, true, 2),
            PlayerControlSlot(PlayerControlAction.REWIND, PlayerControlZone.CENTER_TRANSPORT, true, 0),
            PlayerControlSlot(PlayerControlAction.PLAY_PAUSE, PlayerControlZone.CENTER_TRANSPORT, true, 1),
            PlayerControlSlot(PlayerControlAction.FORWARD, PlayerControlZone.CENTER_TRANSPORT, true, 2),
            PlayerControlSlot(PlayerControlAction.SPEED, PlayerControlZone.BOTTOM_BAR, true, 0),
            PlayerControlSlot(PlayerControlAction.PIP, PlayerControlZone.BOTTOM_BAR, true, 1),
            PlayerControlSlot(PlayerControlAction.FULLSCREEN, PlayerControlZone.BOTTOM_BAR, true, 2),
        )
        ControlLayoutPreset.Compact -> listOf(
            PlayerControlSlot(PlayerControlAction.PLAY_PAUSE, PlayerControlZone.CENTER_TRANSPORT, true, 0),
            PlayerControlSlot(PlayerControlAction.SPEED, PlayerControlZone.BOTTOM_BAR, true, 0),
            PlayerControlSlot(PlayerControlAction.CAPTIONS, PlayerControlZone.BOTTOM_BAR, true, 1),
            PlayerControlSlot(PlayerControlAction.FULLSCREEN, PlayerControlZone.BOTTOM_BAR, true, 2),
        )
        ControlLayoutPreset.Studio -> listOf(
            PlayerControlSlot(PlayerControlAction.LOCK, PlayerControlZone.TOP_BAR, true, 0),
            PlayerControlSlot(PlayerControlAction.CAPTIONS, PlayerControlZone.TOP_BAR, true, 1),
            PlayerControlSlot(PlayerControlAction.ASPECT_RATIO, PlayerControlZone.TOP_BAR, true, 2),
            PlayerControlSlot(PlayerControlAction.QUEUE, PlayerControlZone.TOP_BAR, true, 3),
            PlayerControlSlot(PlayerControlAction.SETTINGS, PlayerControlZone.TOP_BAR, true, 4),
            PlayerControlSlot(PlayerControlAction.PREVIOUS, PlayerControlZone.CENTER_TRANSPORT, true, 0),
            PlayerControlSlot(PlayerControlAction.REWIND, PlayerControlZone.CENTER_TRANSPORT, true, 1),
            PlayerControlSlot(PlayerControlAction.PLAY_PAUSE, PlayerControlZone.CENTER_TRANSPORT, true, 2),
            PlayerControlSlot(PlayerControlAction.FORWARD, PlayerControlZone.CENTER_TRANSPORT, true, 3),
            PlayerControlSlot(PlayerControlAction.NEXT, PlayerControlZone.CENTER_TRANSPORT, true, 4),
            PlayerControlSlot(PlayerControlAction.SPEED, PlayerControlZone.BOTTOM_BAR, true, 0),
            PlayerControlSlot(PlayerControlAction.PIP, PlayerControlZone.BOTTOM_BAR, true, 1),
            PlayerControlSlot(PlayerControlAction.FULLSCREEN, PlayerControlZone.BOTTOM_BAR, true, 2),
        )
        ControlLayoutPreset.Standard -> listOf(
            PlayerControlSlot(PlayerControlAction.LOCK, PlayerControlZone.TOP_BAR, true, 0),
            PlayerControlSlot(PlayerControlAction.CAPTIONS, PlayerControlZone.TOP_BAR, true, 1),
            PlayerControlSlot(PlayerControlAction.PIP, PlayerControlZone.TOP_BAR, true, 2),
            PlayerControlSlot(PlayerControlAction.SETTINGS, PlayerControlZone.TOP_BAR, true, 3),
            PlayerControlSlot(PlayerControlAction.PREVIOUS, PlayerControlZone.CENTER_TRANSPORT, true, 0),
            PlayerControlSlot(PlayerControlAction.PLAY_PAUSE, PlayerControlZone.CENTER_TRANSPORT, true, 1),
            PlayerControlSlot(PlayerControlAction.NEXT, PlayerControlZone.CENTER_TRANSPORT, true, 2),
            PlayerControlSlot(PlayerControlAction.SPEED, PlayerControlZone.BOTTOM_BAR, true, 0),
            PlayerControlSlot(PlayerControlAction.ASPECT_RATIO, PlayerControlZone.BOTTOM_BAR, true, 1),
            PlayerControlSlot(PlayerControlAction.FULLSCREEN, PlayerControlZone.BOTTOM_BAR, true, 2),
        )
    }

    fun moveSlotWithinZone(
        slots: List<PlayerControlSlot>,
        action: PlayerControlAction,
        delta: Int,
    ): List<PlayerControlSlot> {
        val target = slots.find { it.action == action } ?: return slots
        val zoneSlots = slots.filter { it.zone == target.zone }.sortedBy { it.order }.toMutableList()
        val index = zoneSlots.indexOfFirst { it.action == action }
        if (index == -1) return slots
        val newIndex = (index + delta).coerceIn(0, zoneSlots.lastIndex)
        if (index == newIndex) return slots
        val item = zoneSlots.removeAt(index)
        zoneSlots.add(newIndex, item)
        val reindexedZone = zoneSlots.mapIndexed { idx, slot -> slot.copy(order = idx) }
        val others = slots.filter { it.zone != target.zone }
        return (others + reindexedZone).sortedWith(compareBy({ it.zone.ordinal }, { it.order }))
    }

    fun serialize(state: PlayerLayoutState): String {
        val root = JSONObject()
        root.put("preset", state.activePreset.name)
        root.put("compactTopBar", state.compactTopBar)
        root.put("showTimeRemaining", state.showTimeRemaining)
        val arr = JSONArray()
        state.slots.forEach { slot ->
            arr.put(
                JSONObject()
                    .put("action", slot.action.name)
                    .put("zone", slot.zone.name)
                    .put("visible", slot.visible)
                    .put("order", slot.order),
            )
        }
        root.put("slots", arr)
        return root.toString()
    }

    fun deserialize(raw: String?): PlayerLayoutState {
        if (raw.isNullOrBlank()) return PlayerLayoutState()
        return runCatching {
            val root = JSONObject(raw)
            val preset = ControlLayoutPreset.entries.find { it.name == root.optString("preset") }
                ?: ControlLayoutPreset.Standard
            val compactTopBar = root.optBoolean("compactTopBar", false)
            val showTimeRemaining = root.optBoolean("showTimeRemaining", false)
            val arr = root.optJSONArray("slots")
            val slots = if (arr != null && arr.length() > 0) {
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val action = PlayerControlAction.entries.find { it.name == o.optString("action") }
                        ?: return@mapNotNull null
                    val zone = PlayerControlZone.entries.find { it.name == o.optString("zone") }
                        ?: PlayerControlZone.BOTTOM_BAR
                    PlayerControlSlot(
                        action = action,
                        zone = zone,
                        visible = o.optBoolean("visible", true),
                        order = o.optInt("order", i),
                    )
                }.sortedWith(compareBy({ it.zone.ordinal }, { it.order }))
            } else {
                defaultSlots(preset)
            }
            PlayerLayoutState(
                activePreset = preset,
                slots = slots.ifEmpty { defaultSlots(preset) },
                compactTopBar = compactTopBar,
                showTimeRemaining = showTimeRemaining,
            )
        }.getOrDefault(PlayerLayoutState())
    }
}

object PlayerLayoutManager {
    private const val PREFS_NAME = "player_layout_customizer"
    private const val KEY_STATE = "layout_state_json"
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(PlayerLayoutState())
    val state: StateFlow<PlayerLayoutState> = _state.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        _state.value = PlayerLayoutLogic.deserialize(p.getString(KEY_STATE, null))
    }

    fun applyPreset(preset: ControlLayoutPreset) {
        updateState(
            _state.value.copy(
                activePreset = preset,
                slots = PlayerLayoutLogic.defaultSlots(preset),
            ),
        )
    }

    fun toggleSlotVisibility(action: PlayerControlAction) {
        val updated = _state.value.slots.map { slot ->
            if (slot.action == action && action != PlayerControlAction.PLAY_PAUSE) {
                slot.copy(visible = !slot.visible)
            } else {
                slot
            }
        }
        updateState(_state.value.copy(slots = updated))
    }

    fun moveSlot(action: PlayerControlAction, delta: Int) {
        val updated = PlayerLayoutLogic.moveSlotWithinZone(_state.value.slots, action, delta)
        updateState(_state.value.copy(slots = updated))
    }

    fun setCompactTopBar(compact: Boolean) {
        updateState(_state.value.copy(compactTopBar = compact))
    }

    fun resetToDefault() {
        updateState(PlayerLayoutState())
    }

    private fun updateState(newState: PlayerLayoutState) {
        _state.value = newState
        prefs?.edit()?.putString(KEY_STATE, PlayerLayoutLogic.serialize(newState))?.apply()
    }
}

@Composable
fun CustomizablePlayerControlsBar(
    zone: PlayerControlZone,
    isPlaying: Boolean,
    onAction: (PlayerControlAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutState by PlayerLayoutManager.state.collectAsState()
    val visibleSlots = layoutState.slots
        .filter { it.zone == zone && it.visible }
        .sortedBy { it.order }

    AnimatedVisibility(
        visible = visibleSlots.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (layoutState.compactTopBar) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(glassFill(0.75f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            visibleSlots.forEach { slot ->
                val icon = if (slot.action == PlayerControlAction.PLAY_PAUSE && !isPlaying) {
                    Icons.Rounded.PlayArrow
                } else {
                    slot.action.icon
                }
                IconButton(
                    onClick = { onAction(slot.action) },
                    modifier = Modifier.size(if (slot.action == PlayerControlAction.PLAY_PAUSE) 44.dp else 36.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = slot.action.label,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerLayoutCustomizerSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutState by PlayerLayoutManager.state.collectAsState()

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
                text = "Player Layout & Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row {
                IconButton(onClick = { PlayerLayoutManager.resetToDefault() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset layout")
                }
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Layout Preset",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlLayoutPreset.entries.forEach { preset ->
                val selected = layoutState.activePreset == preset
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    } else {
                        glassFill(0.55f)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { PlayerLayoutManager.applyPreset(preset) }
                        .border(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(14.dp),
                        ),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            layoutState.slots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(glassFill(0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = slot.action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = slot.action.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = slot.zone.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { PlayerLayoutManager.moveSlot(slot.action, -1) }) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = { PlayerLayoutManager.moveSlot(slot.action, 1) }) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                    }
                    IconButton(onClick = { PlayerLayoutManager.toggleSlotVisibility(slot.action) }) {
                        Icon(
                            imageVector = if (slot.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = if (slot.visible) "Hide control" else "Show control",
                            tint = if (slot.visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}