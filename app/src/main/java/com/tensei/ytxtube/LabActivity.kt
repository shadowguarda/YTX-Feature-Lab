package com.tensei.ytxtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * YTX Lab: one screen, with its own launcher icon, that holds the extra features added in this
 * fork. It is separate from the main app screens so those stay untouched.
 */
class LabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) { LabScreen() }
        }
    }
}

@Composable
private fun LabScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(Backup.ConflictMode.Replace) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            status = runCatching { Backup.exportTo(context, uri); "Backup saved." }
                .getOrElse { "Backup failed: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            status = runCatching {
                val r = Backup.importFrom(context, uri, mode)
                buildString {
                    append("Restored ${r.applied} settings, skipped ${r.skipped}.")
                    if (r.ignoredFiles.isNotEmpty()) append(" Ignored: ${r.ignoredFiles.joinToString()}.")
                    if (r.needsRestart) append(" Restart the app to apply them.")
                }
            }.getOrElse { "Restore failed: ${it.message}" }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("YTX Lab", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Extra features by kairo-xy, version ${BuildConfig.VERSION_NAME}. " +
                    "Based on YTXTube by Koustubh12345 (GPLv3).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )

            Section("Dislike counts") { ReturnDislikeSetting() }

            Section("Audio") {
                Text("Equalizer", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.SemiBold)
                ChipRow {
                    AudioEffects.Preset.entries.forEach { p ->
                        LabChip(p.label, AudioEffects.preset == p) { AudioEffects.setPreset(p) }
                    }
                }
                Text("Loudness boost", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.SemiBold)
                ChipRow {
                    AudioEffects.boostSteps.forEachIndexed { i, mb ->
                        LabChip(if (mb == 0) "Off" else "+${mb / 100} dB", AudioEffects.boostIndex == i) {
                            AudioEffects.setBoost(i)
                        }
                    }
                }
                Text(
                    "Applies to the video or song that is playing, and to the next ones.",
                    Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            Section("Playback") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Skip silence", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(checked = SkipSilence.enabled, onCheckedChange = { SkipSilence.toggle() })
                }
                OutlinedButton(
                    onClick = { SleepTimer.cycle() },
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    Text(if (SleepTimer.minutes == 0) "Sleep timer: off" else "Sleep timer: ${SleepTimer.minutes} min")
                }
            }

            Section("Backup and restore") {
                Text(
                    "Saves your settings to a file you choose. Sign-in data is never included.",
                    Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Text("If a setting already exists", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.SemiBold)
                ChipRow {
                    Backup.ConflictMode.entries.forEach { m ->
                        LabChip(m.label, mode == m) { mode = m }
                    }
                }
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { exportLauncher.launch("ytx-backup.json") }) { Text("Back up") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Restore") }
                }
                status?.let {
                    Text(it, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                }
            }

            Section("Live player stats") { LiveStats() }
        }
    }
}

@Composable
private fun LiveStats() {
    val rows by produceState(initialValue = emptyList<Pair<String, String>>()) {
        while (true) {
            value = PlayerBridge.exo?.let(PlayerStats::read).orEmpty()
            delay(1_000L)
        }
    }
    if (rows.isEmpty()) {
        Text(
            "Play a video in YTXTube, then come back here to see its resolution, codecs, buffer and dropped frames.",
            Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        return
    }
    rows.forEach { (label, value) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun LabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (selected) c.onPrimaryContainer else c.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier)
    }
}
