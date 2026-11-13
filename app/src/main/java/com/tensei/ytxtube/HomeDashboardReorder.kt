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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Feature H: Reorderable Home dashboard sections with pin-to-top and visibility persistence.
 * Reuses [HomeSection] from HomeFeedBuilder.kt.
 */
data class HomeDashboardSectionConfig(
    val section: HomeSection,
    val visible: Boolean = true,
    val pinned: Boolean = false,
)

object HomeDashboardLogic {
    fun defaultSections(): List<HomeDashboardSectionConfig> =
        HomeSection.entries.map { HomeDashboardSectionConfig(section = it, visible = true, pinned = false) }

    fun moveSection(
        list: List<HomeDashboardSectionConfig>,
        fromIndex: Int,
        toIndex: Int,
    ): List<HomeDashboardSectionConfig> {
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return list
        val mutable = list.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return normalizePinnedOrder(mutable)
    }

    fun normalizePinnedOrder(list: List<HomeDashboardSectionConfig>): List<HomeDashboardSectionConfig> {
        val pinned = list.filter { it.pinned }
        val unpinned = list.filterNot { it.pinned }
        return pinned + unpinned
    }

    fun serialize(list: List<HomeDashboardSectionConfig>): String {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject()
                    .put("section", item.section.name)
                    .put("visible", item.visible)
                    .put("pinned", item.pinned),
            )
        }
        return arr.toString()
    }

    fun deserialize(raw: String?): List<HomeDashboardSectionConfig> {
        if (raw.isNullOrBlank()) return defaultSections()
        return runCatching {
            val arr = JSONArray(raw)
            val parsed = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val section = HomeSection.entries.find { it.name == o.optString("section") }
                    ?: return@mapNotNull null
                HomeDashboardSectionConfig(
                    section = section,
                    visible = o.optBoolean("visible", true),
                    pinned = o.optBoolean("pinned", false),
                )
            }
            val missing = HomeSection.entries
                .filter { s -> parsed.none { it.section == s } }
                .map { HomeDashboardSectionConfig(it) }
            normalizePinnedOrder((parsed + missing).distinctBy { it.section })
        }.getOrDefault(defaultSections())
    }
}

object HomeDashboardManager {
    private const val PREFS_NAME = "home_dashboard_reorder"
    private const val KEY_JSON = "sections_json"
    private var prefs: SharedPreferences? = null

    private val _sections = MutableStateFlow(HomeDashboardLogic.defaultSections())
    val sections: StateFlow<List<HomeDashboardSectionConfig>> = _sections.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        _sections.value = HomeDashboardLogic.deserialize(p.getString(KEY_JSON, null))
    }

    fun move(fromIndex: Int, toIndex: Int) {
        save(HomeDashboardLogic.moveSection(_sections.value, fromIndex, toIndex))
    }

    fun toggleVisibility(section: HomeSection) {
        val updated = _sections.value.map {
            if (it.section == section) it.copy(visible = !it.visible) else it
        }
        save(updated)
    }

    fun togglePinned(section: HomeSection) {
        val updated = _sections.value.map {
            if (it.section == section) it.copy(pinned = !it.pinned) else it
        }
        save(HomeDashboardLogic.normalizePinnedOrder(updated))
    }

    fun resetToDefault() {
        save(HomeDashboardLogic.defaultSections())
    }

    fun visibleOrderedSections(): List<HomeSection> =
        _sections.value.filter { it.visible }.map { it.section }

    private fun save(newList: List<HomeDashboardSectionConfig>) {
        _sections.value = newList
        prefs?.edit()?.putString(KEY_JSON, HomeDashboardLogic.serialize(newList))?.apply()
    }
}

@Composable
fun HomeDashboardReorderSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections by HomeDashboardManager.sections.collectAsState()

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
                text = "Reorder Home Sections",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row {
                IconButton(onClick = { HomeDashboardManager.resetToDefault() }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset sections")
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(glassFill(0.5f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.section.label,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (item.pinned) {
                            Text(
                                text = "Pinned to top",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = { HomeDashboardManager.togglePinned(item.section) }) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin section",
                            tint = if (item.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { HomeDashboardManager.move(index, index - 1) },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(
                        onClick = { HomeDashboardManager.move(index, index + 1) },
                        enabled = index < sections.lastIndex,
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                    }
                    IconButton(onClick = { HomeDashboardManager.toggleVisibility(item.section) }) {
                        Icon(
                            imageVector = if (item.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = "Toggle visibility",
                            tint = if (item.visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}