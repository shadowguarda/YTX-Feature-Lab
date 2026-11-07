package com.tensei.ytxtube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-look presets and your own saved ones. A profile is a snapshot of the YTX Tweaks, the ambient
 * light settings and the theme mode and colour (keys start with "t:" and "a:"). Applying one only
 * changes what it contains. The same snapshot can be copied out and pasted back, which doubles as
 * a settings backup.
 */
object Profiles {
    private fun profile(vararg pairs: Pair<String, Any>) = JSONObject().also { o -> pairs.forEach { (k, v) -> o.put(k, v) } }

    val builtIn: List<Pair<String, JSONObject>> = listOf(
        "Minimal" to profile(
            "t:hide_views" to true, "t:hide_duration" to true, "t:hide_progress" to true, "t:thumb_width_dp" to 110,
            "t:title_lines" to 1, "t:hide_download" to true, "t:extra_copy_buttons" to false, "a:ambient_light" to false,
        ),
        "Liquid Glass" to profile(
            "t:hide_views" to false, "t:hide_duration" to false, "t:hide_progress" to false, "t:thumb_width_dp" to 170,
            "t:title_lines" to 2, "a:ambient_light" to true, "a:ambient_intensity" to 0.9, "a:ambient_reach" to 1.2,
            "a:ambient_status_bar" to true, "a:ambient_fill_bands" to true,
        ),
        "AMOLED" to profile(
            "a:mode" to "amoled", "a:ambient_light" to false, "t:thumb_width_dp" to 140, "t:title_lines" to 2,
        ),
        "Cinematic" to profile(
            "a:mode" to "dark", "a:ambient_light" to true, "a:ambient_intensity" to 1.0, "a:ambient_reach" to 1.5,
            "a:ambient_saturation" to 1.8, "a:ambient_fill_bands" to true, "a:ambient_fill_dim" to 0.8,
            "t:thumb_width_dp" to 200, "t:fullscreen_clock" to true, "t:hide_views" to true,
        ),
        "Music" to profile(
            "t:start_tab" to 1, "t:hide_shorts" to true, "t:thumb_width_dp" to 110, "t:title_lines" to 1,
            "a:ambient_light" to false,
        ),
    )

    fun snapshot(context: Context): JSONObject {
        val out = JSONObject()
        context.getSharedPreferences("tweaks", Context.MODE_PRIVATE).all.forEach { (k, v) -> if (v != null) out.put("t:$k", v) }
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).all
            .filterKeys { it == "mode" || it == "seed" || it.startsWith("ambient") }
            .forEach { (k, v) -> if (v != null) out.put("a:$k", v) }
        return out
    }

    fun apply(context: Context, json: JSONObject): Int {
        val tweaks = context.getSharedPreferences("tweaks", Context.MODE_PRIVATE).edit()
        val look = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
        var count = 0
        json.keys().forEach { key ->
            val editor = when {
                key.startsWith("t:") -> tweaks
                key.startsWith("a:") -> look
                else -> return@forEach
            }
            val name = key.substring(2)
            when (val v = json.get(key)) {
                is Boolean -> editor.putBoolean(name, v)
                is Int -> editor.putInt(name, v)
                is Long -> editor.putInt(name, v.toInt())
                is Double -> editor.putFloat(name, v.toFloat())
                is String -> editor.putString(name, v)
                else -> return@forEach
            }
            count++
        }
        tweaks.apply()
        look.apply()
        Tweaks.init(context)
        AmbientSettings.init(context)
        ThemeSettings.init(context)
        return count
    }

    private fun store(context: Context) = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun custom(context: Context): List<Pair<String, JSONObject>> = runCatching {
        val arr = JSONArray(store(context).getString("list", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("name") to o.getJSONObject("data") } }
    }.getOrDefault(emptyList())

    fun saveCurrent(context: Context, name: String) {
        val list = custom(context).filterNot { it.first.equals(name, ignoreCase = true) } + (name to snapshot(context))
        write(context, list)
    }

    fun delete(context: Context, name: String) = write(context, custom(context).filterNot { it.first == name })

    private fun write(context: Context, list: List<Pair<String, JSONObject>>) {
        val arr = JSONArray()
        list.take(20).forEach { (n, d) -> arr.put(JSONObject().put("name", n).put("data", d)) }
        store(context).edit().putString("list", arr.toString()).apply()
    }

    fun isHexColour(text: String) = Regex("^#?[0-9a-fA-F]{6}$").matches(text.trim())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var customs by remember { mutableStateOf(Profiles.custom(context)) }
    var newName by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf(ThemeSettings.seed.orEmpty()) }

    FloatingSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 22.dp)) {
            Text("Profiles and backup", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            if (message.isNotBlank()) Text(message, color = c.primary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))

            ProfileHeader("Look presets")
            Profiles.builtIn.forEach { (name, data) ->
                ProfileRow(name, "Apply") { message = "Applied $name (${Profiles.apply(context, data)} settings)" }
            }

            ProfileHeader("My profiles")
            OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(24) }, singleLine = true,
                label = { Text("Name for the current setup") }, modifier = Modifier.fillMaxWidth(),
            )
            ProfileRow("Save current setup", "Save") {
                if (newName.isBlank()) message = "Give it a name first" else {
                    Profiles.saveCurrent(context, newName.trim())
                    customs = Profiles.custom(context)
                    message = "Saved ${newName.trim()}"
                    newName = ""
                }
            }
            customs.forEach { (name, data) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Pill("Apply") { message = "Applied $name"; Profiles.apply(context, data) }
                    Spacer(Modifier.width(8.dp))
                    Pill("Delete") { Profiles.delete(context, name); customs = Profiles.custom(context) }
                }
            }

            ProfileHeader("Accent colour")
            OutlinedTextField(
                value = hex, onValueChange = { hex = it.take(7) }, singleLine = true,
                label = { Text("Hex colour, like #7B4DFF") }, modifier = Modifier.fillMaxWidth(),
            )
            ProfileRow("Use this colour", "Apply") {
                if (Profiles.isHexColour(hex)) {
                    ThemeSettings.choose("#" + hex.trim().removePrefix("#").uppercase())
                    message = "Accent colour applied"
                } else message = "Enter 6 hex digits, like #7B4DFF"
            }

            ProfileHeader("Backup")
            ProfileRow("Copy my settings", "Copy") {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("YTXTube settings", Profiles.snapshot(context).toString()))
                message = "Settings copied. Paste them anywhere to keep them."
            }
            ProfileRow("Paste settings from clipboard", "Paste") {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                val n = json?.let { Profiles.apply(context, it) } ?: 0
                message = if (n > 0) "Restored $n settings" else "The clipboard does not hold YTXTube settings"
            }
            Spacer(Modifier.height(10.dp))
            Text("Backups cover the YTX Tweaks, ambient light, theme mode and accent colour.", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileHeader(text: String) {
    Text(
        text.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProfileRow(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Pill(action, onClick)
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Text(
        text, color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.primaryContainer)
            .pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
