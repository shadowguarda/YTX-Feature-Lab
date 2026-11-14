package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Chapter Autopilot: rules matched against chapter titles. A chapter called "Sponsor" can be skipped
 * and one called "Recap" played faster. It works on videos that have chapters, and a skipped chapter is
 * only skipped once per video so you can still rewind into it.
 */
enum class AutopilotAction { Skip, Speed }

data class AutopilotRule(
    val keyword: String,
    val action: AutopilotAction,
    val speed: Float = 2f,
    val enabled: Boolean = true,
)

data class AutopilotConfig(
    val enabled: Boolean = false,
    val rules: List<AutopilotRule> = ChapterAutopilotLogic.defaultRules(),
)

sealed class AutopilotDecision {
    data object None : AutopilotDecision()
    data class Skip(val toMs: Long, val title: String, val chapterStartMs: Long) : AutopilotDecision()
    data class Speed(val speed: Float, val title: String) : AutopilotDecision()
}

/** Pure matching logic (unit-tested). */
object ChapterAutopilotLogic {
    fun defaultRules(): List<AutopilotRule> = listOf(
        AutopilotRule("sponsor", AutopilotAction.Skip, enabled = true),
        AutopilotRule("sponsored", AutopilotAction.Skip, enabled = true),
        AutopilotRule("ad", AutopilotAction.Skip, enabled = true),
        AutopilotRule("intro", AutopilotAction.Skip, enabled = false),
        AutopilotRule("outro", AutopilotAction.Skip, enabled = false),
        AutopilotRule("recap", AutopilotAction.Speed, speed = 1.5f, enabled = false),
    )

    /** True when [keyword] appears in [title] as a whole word or phrase, ignoring case ("ad" matches "Ad break", not "Road"). */
    fun matches(title: String, keyword: String): Boolean {
        val k = keyword.trim()
        if (k.isEmpty()) return false
        val word = "[\\p{L}\\p{N}]"
        return Regex("(?<!$word)${Regex.escape(k)}(?!$word)", RegexOption.IGNORE_CASE).containsMatchIn(title)
    }

    fun firstMatch(title: String, rules: List<AutopilotRule>): AutopilotRule? =
        rules.firstOrNull { it.enabled && matches(title, it.keyword) }

    /**
     * What to do at [positionMs]. The last chapter is never skipped (that would end the video), and a
     * chapter already skipped once (in [handled], by start time) is left alone.
     */
    fun decide(
        chapters: List<Chapter>,
        positionMs: Long,
        rules: List<AutopilotRule>,
        handled: Set<Long>,
    ): AutopilotDecision {
        if (chapters.size < 2) return AutopilotDecision.None
        val index = chapters.indexOfLast { it.startMs <= positionMs }
        if (index < 0) return AutopilotDecision.None
        val chapter = chapters[index]
        val rule = firstMatch(chapter.title, rules) ?: return AutopilotDecision.None
        return when (rule.action) {
            AutopilotAction.Speed -> AutopilotDecision.Speed(rule.speed, chapter.title)
            AutopilotAction.Skip -> {
                val next = chapters.getOrNull(index + 1)
                if (next == null || chapter.startMs in handled) AutopilotDecision.None
                else AutopilotDecision.Skip(next.startMs, chapter.title, chapter.startMs)
            }
        }
    }

    /** Adds a rule; returns the new list, or null when the keyword is blank or already there. */
    fun addRule(rules: List<AutopilotRule>, rule: AutopilotRule): List<AutopilotRule>? {
        val keyword = rule.keyword.trim().lowercase(Locale.ROOT)
        if (keyword.isEmpty() || rules.any { it.keyword.equals(keyword, ignoreCase = true) }) return null
        return rules + rule.copy(keyword = keyword)
    }

    fun label(rule: AutopilotRule): String = when (rule.action) {
        AutopilotAction.Skip -> "Skip"
        AutopilotAction.Speed -> String.format(Locale.ROOT, "Play at %.1fx", rule.speed)
    }

    fun serialize(config: AutopilotConfig): String {
        val arr = JSONArray()
        config.rules.forEach {
            arr.put(JSONObject().put("k", it.keyword).put("a", it.action.name).put("s", it.speed.toDouble()).put("e", it.enabled))
        }
        return JSONObject().put("enabled", config.enabled).put("rules", arr).toString()
    }

    fun deserialize(raw: String?): AutopilotConfig {
        if (raw.isNullOrBlank()) return AutopilotConfig()
        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("rules")
            val rules = if (arr == null) defaultRules() else (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val keyword = o.optString("k").trim()
                if (keyword.isEmpty()) return@mapNotNull null
                AutopilotRule(
                    keyword = keyword,
                    action = AutopilotAction.entries.find { it.name == o.optString("a") } ?: AutopilotAction.Skip,
                    speed = o.optDouble("s", 2.0).toFloat().coerceIn(1f, 4f),
                    enabled = o.optBoolean("e", true),
                )
            }
            AutopilotConfig(enabled = root.optBoolean("enabled", false), rules = rules)
        }.getOrDefault(AutopilotConfig())
    }
}

object ChapterAutopilot {
    private const val PREFS = "chapter_autopilot"
    private const val KEY = "config_json"
    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(AutopilotConfig())
    val config: StateFlow<AutopilotConfig> = _config.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        _config.value = ChapterAutopilotLogic.deserialize(p.getString(KEY, null))
    }

    fun setEnabled(enabled: Boolean) = save(_config.value.copy(enabled = enabled))

    fun setRuleEnabled(keyword: String, enabled: Boolean) =
        save(_config.value.copy(rules = _config.value.rules.map { if (it.keyword == keyword) it.copy(enabled = enabled) else it }))

    fun removeRule(keyword: String) =
        save(_config.value.copy(rules = _config.value.rules.filterNot { it.keyword == keyword }))

    /** Returns false when the keyword is blank or already has a rule. */
    fun addRule(rule: AutopilotRule): Boolean {
        val updated = ChapterAutopilotLogic.addRule(_config.value.rules, rule) ?: return false
        save(_config.value.copy(rules = updated))
        return true
    }

    fun resetRules() = save(_config.value.copy(rules = ChapterAutopilotLogic.defaultRules()))

    private fun save(config: AutopilotConfig) {
        _config.value = config
        prefs?.edit()?.putString(KEY, ChapterAutopilotLogic.serialize(config))?.apply()
    }
}

/**
 * Runs while the app is showing a video: watches the playback position and applies the rules.
 * Place it once next to the player state in the root composable.
 */
@Composable
fun ChapterAutopilotEffect(player: PlayerState) {
    val config by ChapterAutopilot.config.collectAsState()
    val chapters = player.details?.chapters.orEmpty()
    val videoId = player.current?.id

    LaunchedEffect(config, chapters, videoId) {
        var savedSpeed: Float? = null
        val handled = mutableSetOf<Long>()
        val sorted = chapters.sortedBy { it.startMs }
        try {
            if (config.enabled && sorted.size >= 2) {
                while (isActive) {
                    delay(400L)
                    val exo = PlayerBridge.exo ?: continue
                    if (!exo.isPlaying) continue
                    when (val decision = ChapterAutopilotLogic.decide(sorted, exo.currentPosition, config.rules, handled)) {
                        is AutopilotDecision.Skip -> {
                            handled += decision.chapterStartMs
                            exo.seekTo(decision.toMs)
                            player.actionMessage = "Skipped \"${decision.title}\""
                        }
                        is AutopilotDecision.Speed -> {
                            if (savedSpeed == null) savedSpeed = exo.playbackParameters.speed
                            if (exo.playbackParameters.speed != decision.speed) exo.setPlaybackSpeed(decision.speed)
                        }
                        AutopilotDecision.None -> {
                            savedSpeed?.let { exo.setPlaybackSpeed(it) }
                            savedSpeed = null
                        }
                    }
                }
            }
        } finally {
            // Leaving the video or turning the autopilot off puts the speed back.
            savedSpeed?.let { PlayerBridge.exo?.setPlaybackSpeed(it) }
        }
    }
}

@Composable
fun ChapterAutopilotSheet(player: PlayerState?, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val config by ChapterAutopilot.config.collectAsState()
    var keyword by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(AutopilotAction.Skip) }
    var speed by remember { mutableStateOf(2f) }
    var message by remember { mutableStateOf<String?>(null) }
    val chapterCount = player?.details?.chapters?.size ?: 0

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Chapter autopilot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) { Text("Done") }
        }
        YtxNote("Skips or speeds up chapters whose title matches a rule. Only works on videos that have chapters. A skipped chapter is skipped once, so you can rewind into it.")

        YtxPanel {
            YtxSwitchRow(
                title = "Turn on chapter autopilot",
                subtitle = if (player?.current == null) "No video open right now" else "This video has $chapterCount chapter${if (chapterCount == 1) "" else "s"}",
                checked = config.enabled,
                onChecked = { ChapterAutopilot.setEnabled(it) },
            )
        }

        YtxSectionLabel("Rules")
        if (config.rules.isEmpty()) {
            YtxNote("No rules. Add one below, or reset to the defaults.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                config.rules.forEach { rule ->
                    YtxPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("\"${rule.keyword}\"", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(ChapterAutopilotLogic.label(rule), fontSize = 12.sp, color = c.onSurfaceVariant)
                            }
                            YtxChip(label = if (rule.enabled) "On" else "Off", selected = rule.enabled) {
                                ChapterAutopilot.setRuleEnabled(rule.keyword, !rule.enabled)
                            }
                            IconButton(onClick = { ChapterAutopilot.removeRule(rule.keyword) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete rule ${rule.keyword}")
                            }
                        }
                    }
                }
            }
        }

        YtxSectionLabel("Add a rule")
        YtxPanel {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it; message = null },
                label = { Text("Chapter title contains") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YtxChip(label = "Skip", selected = action == AutopilotAction.Skip) { action = AutopilotAction.Skip }
                listOf(1.5f, 2f, 3f).forEach { s ->
                    YtxChip(
                        label = String.format(Locale.ROOT, "Play at %.1fx", s),
                        selected = action == AutopilotAction.Speed && speed == s,
                    ) { action = AutopilotAction.Speed; speed = s }
                }
            }
            Spacer(Modifier.height(8.dp))
            YtxChip(label = "Add rule", selected = false, enabled = keyword.isNotBlank()) {
                val added = ChapterAutopilot.addRule(AutopilotRule(keyword, action, speed))
                message = if (added) null else "That keyword already has a rule."
                if (added) keyword = ""
            }
            message?.let { YtxNote(it, isError = true) }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { ChapterAutopilot.resetRules() }) { Text("Reset rules to defaults") }
    }
}
