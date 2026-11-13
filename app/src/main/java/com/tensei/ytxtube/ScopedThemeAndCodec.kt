package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Feature B: Separate player/home themes and theme import/export codec.
 * Complements [ThemeSettings] (Theme.kt) and [LiquidGlassThemeEngine].
 */
enum class ThemeScope(val label: String) {
    HOME("Home & Browse"),
    PLAYER("Video & Music Player"),
}

data class ScopedThemeConfig(
    val accentHex: String = "#7C4DFF",
    val backgroundTintHex: String = "#0B0D14",
    val glassOpacity: Float = 0.72f,
    val neonGlowIntensity: Float = 0.55f,
    val useDynamicAccent: Boolean = false,
)

data class ScopedThemeState(
    val separatePlayerTheme: Boolean = false,
    val homeTheme: ScopedThemeConfig = ScopedThemeConfig(),
    val playerTheme: ScopedThemeConfig = ScopedThemeConfig(
        accentHex = "#00E5FF",
        backgroundTintHex = "#05070B",
        glassOpacity = 0.62f,
        neonGlowIntensity = 0.75f,
    ),
)

sealed class ThemeImportResult {
    data class Success(val state: ScopedThemeState) : ThemeImportResult()
    data class Error(val message: String) : ThemeImportResult()
}

object ThemeBundleCodec {
    private const val SCHEMA_VERSION = 1
    private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

    fun exportJson(state: ScopedThemeState, bundleName: String = "YTX Custom Theme"): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("name", bundleName)
        root.put("separatePlayerTheme", state.separatePlayerTheme)
        root.put("homeTheme", configToJson(state.homeTheme))
        root.put("playerTheme", configToJson(state.playerTheme))
        return root.toString(2)
    }

    fun importJson(raw: String): ThemeImportResult {
        if (raw.isBlank()) return ThemeImportResult.Error("Theme JSON is empty.")
        return runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("schemaVersion", -1)
            if (version < 1 || version > SCHEMA_VERSION) {
                return ThemeImportResult.Error("Unsupported theme schema version: $version")
            }
            val homeObj = root.optJSONObject("homeTheme")
                ?: return ThemeImportResult.Error("Missing 'homeTheme' object.")
            val playerObj = root.optJSONObject("playerTheme") ?: homeObj

            val home = jsonToConfig(homeObj)
                ?: return ThemeImportResult.Error("Invalid hex color in 'homeTheme'.")
            val player = jsonToConfig(playerObj)
                ?: return ThemeImportResult.Error("Invalid hex color in 'playerTheme'.")

            ThemeImportResult.Success(
                ScopedThemeState(
                    separatePlayerTheme = root.optBoolean("separatePlayerTheme", true),
                    homeTheme = home,
                    playerTheme = player,
                ),
            )
        }.getOrElse { ex ->
            ThemeImportResult.Error("Malformed JSON: ${ex.message ?: "parse error"}")
        }
    }

    private fun configToJson(c: ScopedThemeConfig): JSONObject = JSONObject()
        .put("accentHex", c.accentHex)
        .put("backgroundTintHex", c.backgroundTintHex)
        .put("glassOpacity", c.glassOpacity.toDouble())
        .put("neonGlowIntensity", c.neonGlowIntensity.toDouble())
        .put("useDynamicAccent", c.useDynamicAccent)

    private fun jsonToConfig(o: JSONObject): ScopedThemeConfig? {
        val accent = o.optString("accentHex", "#7C4DFF")
        val bg = o.optString("backgroundTintHex", "#0B0D14")
        if (!HEX_REGEX.matches(accent) || !HEX_REGEX.matches(bg)) return null
        return ScopedThemeConfig(
            accentHex = accent.uppercase(),
            backgroundTintHex = bg.uppercase(),
            glassOpacity = o.optDouble("glassOpacity", 0.72).toFloat().coerceIn(0.15f, 1.0f),
            neonGlowIntensity = o.optDouble("neonGlowIntensity", 0.55).toFloat().coerceIn(0f, 1.0f),
            useDynamicAccent = o.optBoolean("useDynamicAccent", false),
        )
    }
}

object ScopedThemeManager {
    private const val PREFS_NAME = "scoped_theme_prefs"
    private const val KEY_JSON = "scoped_theme_json"
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(ScopedThemeState())
    val state: StateFlow<ScopedThemeState> = _state.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        val saved = p.getString(KEY_JSON, null)
        if (!saved.isNullOrBlank()) {
            val result = ThemeBundleCodec.importJson(saved)
            if (result is ThemeImportResult.Success) {
                _state.value = result.state
            }
        }
    }

    fun activeConfigFor(scope: ThemeScope): ScopedThemeConfig {
        val current = _state.value
        return if (scope == ThemeScope.PLAYER && current.separatePlayerTheme) {
            current.playerTheme
        } else {
            current.homeTheme
        }
    }

    fun setSeparatePlayerTheme(enabled: Boolean) {
        save(_state.value.copy(separatePlayerTheme = enabled))
    }

    fun updateScopeAccent(scope: ThemeScope, accentHex: String) {
        val current = _state.value
        val updated = when (scope) {
            ThemeScope.HOME -> current.copy(homeTheme = current.homeTheme.copy(accentHex = accentHex))
            ThemeScope.PLAYER -> current.copy(playerTheme = current.playerTheme.copy(accentHex = accentHex))
        }
        save(updated)
    }

    fun importFromText(rawJson: String): ThemeImportResult {
        val result = ThemeBundleCodec.importJson(rawJson)
        if (result is ThemeImportResult.Success) {
            save(result.state)
        }
        return result
    }

    fun exportToText(): String = ThemeBundleCodec.exportJson(_state.value)

    private fun save(newState: ScopedThemeState) {
        _state.value = newState
        prefs?.edit()?.putString(KEY_JSON, ThemeBundleCodec.exportJson(newState))?.apply()
    }
}

@Composable
fun ScopedThemeSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by ScopedThemeManager.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var importText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val quickAccents = listOf("#7C4DFF", "#00E5FF", "#FF4081", "#00E676", "#FFAB40")

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
                text = "Scoped Themes & Import/Export",
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
                Text("Separate Player Theme", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Use a dedicated accent & glass tint inside the video/music player",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.separatePlayerTheme,
                onCheckedChange = { ScopedThemeManager.setSeparatePlayerTheme(it) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        ScopeAccentPicker(
            title = "Home Theme Accent",
            selectedHex = state.homeTheme.accentHex,
            accents = quickAccents,
            onSelect = { ScopedThemeManager.updateScopeAccent(ThemeScope.HOME, it) },
        )

        if (state.separatePlayerTheme) {
            Spacer(modifier = Modifier.height(10.dp))
            ScopeAccentPicker(
                title = "Player Theme Accent",
                selectedHex = state.playerTheme.accentHex,
                accents = quickAccents,
                onSelect = { ScopedThemeManager.updateScopeAccent(ThemeScope.PLAYER, it) },
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val exported = ScopedThemeManager.exportToText()
                    clipboard.setText(AnnotatedString(exported))
                    importText = exported
                    statusMessage = "Theme JSON copied to clipboard."
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export JSON")
            }
            Button(
                onClick = {
                    when (val res = ScopedThemeManager.importFromText(importText)) {
                        is ThemeImportResult.Success -> statusMessage = "Theme imported and applied."
                        is ThemeImportResult.Error -> statusMessage = res.message
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import JSON")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it },
            label = { Text("Paste Theme JSON") },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ScopeAccentPicker(
    title: String,
    selectedHex: String,
    accents: List<String>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(glassFill(0.45f))
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            accents.forEach { hex ->
                val color = Color(android.graphics.Color.parseColor(hex))
                val selected = selectedHex.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape,
                        )
                        .clickable { onSelect(hex) },
                )
            }
        }
    }
}