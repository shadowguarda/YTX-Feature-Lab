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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Upload
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.UUID

/**
 * Feature F: Subscription folders/groups and OPML 2.0 + JSON import/export.
 * Complements [ChannelRef] (Account.kt) and [DiscoveryAndSubsManager] (SmartDiscoveryAndSubs.kt).
 */
data class ChannelFolder(
    val id: String,
    val name: String,
    val channelIds: List<String> = emptyList(),
    val sortOrder: Int = 0,
)

data class OpmlSubscriptionEntry(
    val title: String,
    val channelId: String,
    val xmlUrl: String,
    val folderName: String? = null,
)

sealed class OpmlImportResult {
    data class Success(val entries: List<OpmlSubscriptionEntry>) : OpmlImportResult()
    data class Error(val message: String) : OpmlImportResult()
}

object SubscriptionOpmlCodec {

    fun extractChannelIdFromFeedUrl(url: String): String {
        val marker = "channel_id="
        val idx = url.indexOf(marker)
        if (idx >= 0) {
            return url.substring(idx + marker.length).substringBefore("&").trim()
        }
        return url.substringAfterLast("/").trim()
    }

    fun exportOpml(
        channels: List<ChannelRef>,
        folders: List<ChannelFolder>,
    ): String {
        val folderByChannel = mutableMapOf<String, String>()
        folders.forEach { folder ->
            folder.channelIds.forEach { cid -> folderByChannel[cid] = folder.name }
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head><title>YTXTube Subscriptions</title></head>\n")
        sb.append("  <body>\n")

        val grouped = channels.groupBy { folderByChannel[it.id] }
        grouped.forEach { (folderName, list) ->
            if (folderName != null) {
                sb.append("    <outline text=\"${escapeXml(folderName)}\" title=\"${escapeXml(folderName)}\">\n")
            }
            val indent = if (folderName != null) "      " else "    "
            list.forEach { ch ->
                val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=${escapeXml(ch.id)}"
                val title = escapeXml(ch.name)
                sb.append("${indent}<outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"$feedUrl\" />\n")
            }
            if (folderName != null) {
                sb.append("    </outline>\n")
            }
        }
        sb.append("  </body>\n")
        sb.append("</opml>")
        return sb.toString()
    }

    fun importOpml(xml: String): OpmlImportResult {
        if (xml.isBlank()) return OpmlImportResult.Error("OPML content is empty.")
        return runCatching {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            val results = mutableListOf<OpmlSubscriptionEntry>()
            val folderStack = ArrayDeque<String?>()
            var event = parser.eventType

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name.equals("outline", ignoreCase = true)) {
                            val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                            val text = parser.getAttributeValue(null, "title")
                                ?: parser.getAttributeValue(null, "text")
                                ?: "Channel"
                            if (!xmlUrl.isNullOrBlank()) {
                                val cid = extractChannelIdFromFeedUrl(xmlUrl)
                                if (cid.isNotBlank()) {
                                    results.add(
                                        OpmlSubscriptionEntry(
                                            title = text,
                                            channelId = cid,
                                            xmlUrl = xmlUrl,
                                            folderName = folderStack.lastOrNull(),
                                        ),
                                    )
                                }
                                folderStack.addLast(folderStack.lastOrNull())
                            } else {
                                folderStack.addLast(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("outline", ignoreCase = true) && folderStack.isNotEmpty()) {
                            folderStack.removeLast()
                        }
                    }
                }
                event = parser.next()
            }
            OpmlImportResult.Success(results.distinctBy { it.channelId })
        }.getOrElse { ex ->
            OpmlImportResult.Error("Invalid OPML XML: ${ex.message ?: "parse error"}")
        }
    }

    internal fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

object SubscriptionFoldersManager {
    private const val PREFS_NAME = "subscription_folders_prefs"
    private const val KEY_JSON = "folders_json"
    private var prefs: SharedPreferences? = null

    private val _folders = MutableStateFlow<List<ChannelFolder>>(emptyList())
    val folders: StateFlow<List<ChannelFolder>> = _folders.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
        _folders.value = deserialize(p.getString(KEY_JSON, null))
    }

    fun createFolder(name: String): ChannelFolder {
        val folder = ChannelFolder(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Folder" },
            sortOrder = _folders.value.size,
        )
        save(_folders.value + folder)
        return folder
    }

    fun deleteFolder(folderId: String) {
        save(_folders.value.filterNot { it.id == folderId })
    }

    fun assignChannel(folderId: String, channelId: String) {
        val updated = _folders.value.map { f ->
            when {
                f.id == folderId && channelId !in f.channelIds -> f.copy(channelIds = f.channelIds + channelId)
                f.id != folderId && channelId in f.channelIds -> f.copy(channelIds = f.channelIds - channelId)
                else -> f
            }
        }
        save(updated)
    }

    fun importOpmlAndMergeFolders(xml: String): OpmlImportResult {
        val result = SubscriptionOpmlCodec.importOpml(xml)
        if (result is OpmlImportResult.Success) {
            val grouped = result.entries.filter { !it.folderName.isNullOrBlank() }.groupBy { it.folderName!! }
            var current = _folders.value
            grouped.forEach { (folderName, entries) ->
                val existing = current.find { it.name.equals(folderName, ignoreCase = true) }
                val newIds = entries.map { it.channelId }
                current = if (existing != null) {
                    current.map {
                        if (it.id == existing.id) it.copy(channelIds = (it.channelIds + newIds).distinct()) else it
                    }
                } else {
                    current + ChannelFolder(
                        id = UUID.randomUUID().toString(),
                        name = folderName,
                        channelIds = newIds.distinct(),
                        sortOrder = current.size,
                    )
                }
            }
            save(current)
        }
        return result
    }

    internal fun serialize(list: List<ChannelFolder>): String {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("sortOrder", f.sortOrder)
                    .put("channelIds", JSONArray(f.channelIds)),
            )
        }
        return arr.toString()
    }

    internal fun deserialize(raw: String?): List<ChannelFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ids = o.optJSONArray("channelIds") ?: JSONArray()
                val channelIds = (0 until ids.length()).mapNotNull { j -> ids.optString(j).takeIf { it.isNotBlank() } }
                ChannelFolder(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "Folder"),
                    channelIds = channelIds,
                    sortOrder = o.optInt("sortOrder", i),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(newList: List<ChannelFolder>) {
        _folders.value = newList
        prefs?.edit()?.putString(KEY_JSON, serialize(newList))?.apply()
    }
}

@Composable
fun SubscriptionFoldersSheet(
    channels: List<ChannelRef>,
    onImportChannels: (List<OpmlSubscriptionEntry>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folders by SubscriptionFoldersManager.folders.collectAsState()
    val clipboard = LocalClipboardManager.current
    var newFolderName by remember { mutableStateOf("") }
    var opmlInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

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
                text = "Subscription Folders & OPML",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onDismiss) { Text("Done") }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                label = { Text("New folder name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newFolderName.isNotBlank()) {
                        SubscriptionFoldersManager.createFolder(newFolderName)
                        newFolderName = ""
                    }
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create folder")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            folders.forEach { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassFill(0.5f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.name, fontWeight = FontWeight.SemiBold)
                        Text("${folder.channelIds.size} channels", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { SubscriptionFoldersManager.deleteFolder(folder.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete folder")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val xml = SubscriptionOpmlCodec.exportOpml(channels, folders)
                    clipboard.setText(AnnotatedString(xml))
                    opmlInput = xml
                    status = "OPML exported (${channels.size} channels) to clipboard."
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export OPML")
            }
            Button(
                onClick = {
                    when (val res = SubscriptionFoldersManager.importOpmlAndMergeFolders(opmlInput)) {
                        is OpmlImportResult.Success -> {
                            onImportChannels(res.entries)
                            status = "Imported ${res.entries.size} subscriptions."
                        }
                        is OpmlImportResult.Error -> status = res.message
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import OPML")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = opmlInput,
            onValueChange = { opmlInput = it },
            label = { Text("Paste OPML XML") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        status?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}