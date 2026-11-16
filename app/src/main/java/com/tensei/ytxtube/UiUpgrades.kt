package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Settings and pure logic for the UI upgrades: the floating playback pill (Dynamic Island), the
 * adaptive navigation dock, custom home shelves, artwork-driven colour, the blurred wallpaper, mini
 * player swipes and the Quick Settings panel. The Compose parts are in UiUpgradesUi.kt.
 */
enum class DockLabelMode(val label: String) {
    Always("Always"),
    SelectedOnly("Selected only"),
    Never("Icons only"),
}

/** One row in an ordered, hideable list (a dock tab or a home shelf). */
data class LayoutItem(val name: String, val visible: Boolean = true)

object OrderedLayoutLogic {
    fun defaults(all: List<String>): List<LayoutItem> = all.map { LayoutItem(it) }

    /**
     * Brings a saved list in line with what exists now: unknown names are dropped, new ones are added
     * (visible) at the end, and a list that would leave too few items, or hide a locked one, is reset.
     */
    fun reconcile(saved: List<LayoutItem>, all: List<String>, minVisible: Int = 0, locked: String? = null): List<LayoutItem> {
        val kept = saved.filter { it.name in all }.distinctBy { it.name }
        val merged = kept + all.filter { n -> kept.none { it.name == n } }.map { LayoutItem(it) }
        val lockedHidden = locked != null && merged.any { it.name == locked && !it.visible }
        return if (merged.count { it.visible } < minVisible || lockedHidden) merged.map { it.copy(visible = true) } else merged
    }

    fun visibleNames(list: List<LayoutItem>): List<String> = list.filter { it.visible }.map { it.name }

    /** Shows or hides [name]. The locked item and the last [minVisible] visible items cannot be hidden. */
    fun toggle(list: List<LayoutItem>, name: String, minVisible: Int = 0, locked: String? = null): List<LayoutItem> {
        val item = list.firstOrNull { it.name == name } ?: return list
        if (name == locked) return list
        if (item.visible && list.count { it.visible } <= minVisible) return list
        return list.map { if (it.name == name) it.copy(visible = !it.visible) else it }
    }

    /** Moves [name] up (-1) or down (+1) one place; an edge is left alone. */
    fun move(list: List<LayoutItem>, name: String, delta: Int): List<LayoutItem> {
        val from = list.indexOfFirst { it.name == name }
        val to = from + delta
        if (from < 0 || to !in list.indices) return list
        val out = list.toMutableList()
        val moved = out.removeAt(from)
        out.add(to, moved)
        return out
    }

    fun serialize(list: List<LayoutItem>): String = list.joinToString(",") { "${it.name}:${if (it.visible) 1 else 0}" }

    fun deserialize(raw: String?, all: List<String>, minVisible: Int = 0, locked: String? = null): List<LayoutItem> {
        if (raw.isNullOrBlank()) return defaults(all)
        val parsed = raw.split(',').mapNotNull { part ->
            val bits = part.split(':')
            val name = bits.getOrNull(0)?.trim().orEmpty()
            if (name.isEmpty()) null else LayoutItem(name, bits.getOrNull(1)?.trim() != "0")
        }
        return reconcile(parsed, all, minVisible, locked)
    }
}

object NavDockLogic {
    const val LOCKED_TAB = "Home"
    const val MIN_VISIBLE_TABS = 2

    /** Whether a tab shows its text label: the old icon-only switch always wins. */
    fun showLabel(mode: DockLabelMode, selected: Boolean, iconOnly: Boolean): Boolean = when {
        iconOnly -> false
        mode == DockLabelMode.Always -> true
        mode == DockLabelMode.SelectedOnly -> selected
        else -> false
    }

    fun parseLabelMode(name: String?): DockLabelMode = DockLabelMode.entries.find { it.name == name } ?: DockLabelMode.Always
}

object HomeLayoutLogic {
    const val CONTINUE = "Continue"
    const val SUBSCRIPTIONS = "Subscriptions"
    const val PLANNED = "Planned"
    val ALL = listOf(CONTINUE, SUBSCRIPTIONS, PLANNED)

    fun label(name: String): String = when (name) {
        CONTINUE -> "Continue watching"
        SUBSCRIPTIONS -> "Latest from subscriptions"
        PLANNED -> "Watch plan"
        else -> name
    }

    fun blurb(name: String): String = when (name) {
        CONTINUE -> "Videos you started and did not finish"
        SUBSCRIPTIONS -> "Newest uploads from channels you subscribe to"
        PLANNED -> "Videos you scheduled for later"
        else -> ""
    }

    /** Started but not finished videos, newest first: between 2% and 95% watched. */
    fun continueWatching(recent: List<LoggedVideo>, progress: (String) -> Float?, max: Int = 12): List<LoggedVideo> =
        recent.filter { s -> progress(s.videoId)?.let { it in 0.02f..0.95f } == true }.take(max)
}

object IslandLogic {
    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

object ArtworkColorLogic {
    /**
     * The most vivid common colour in [pixels] (ARGB). Colours are grouped into 16 shades per channel
     * and weighted by how saturated and bright they are, so a grey or near-black frame does not win.
     * Falls back to the plain average, then to [fallback].
     */
    fun dominant(pixels: IntArray, fallback: Int): Int {
        val weight = HashMap<Int, Double>()
        val sums = HashMap<Int, DoubleArray>()
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (max == 0) continue
            val sat = (max - min).toDouble() / max
            val value = max / 255.0
            if (sat < 0.12 || value < 0.15) continue
            val w = sat * sat * (0.35 + 0.65 * value)
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            weight[key] = (weight[key] ?: 0.0) + w
            val s = sums.getOrPut(key) { DoubleArray(4) }
            s[0] += r * w; s[1] += g * w; s[2] += b * w; s[3] += w
        }
        val best = weight.maxByOrNull { it.value }?.key ?: return average(pixels, fallback)
        val s = sums.getValue(best)
        return argb((s[0] / s[3]).toInt(), (s[1] / s[3]).toInt(), (s[2] / s[3]).toInt())
    }

    fun average(pixels: IntArray, fallback: Int): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
        return if (n == 0L) fallback else argb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}

object UiUpgrades {
    private const val PREFS = "ui_upgrades"
    private var prefs: SharedPreferences? = null

    var islandEnabled by mutableStateOf(true)
        private set
    var wallpaperEnabled by mutableStateOf(false)
        private set
    var wallpaperAnimated by mutableStateOf(true)
        private set
    var dynamicArtwork by mutableStateOf(true)
        private set
    var miniSwipe by mutableStateOf(true)
        private set
    var dockLabelMode by mutableStateOf(DockLabelMode.Always)
        private set
    var dock by mutableStateOf(OrderedLayoutLogic.defaults(emptyList()))
        private set
    var homeShelves by mutableStateOf(OrderedLayoutLogic.defaults(HomeLayoutLogic.ALL))
        private set

    /** Not saved: whether the Quick Settings panel and the layout sheet are open right now. */
    var quickPanelOpen by mutableStateOf(false)
    var layoutSheetOpen by mutableStateOf(false)

    private fun tabNames(): List<String> = Tab.entries.map { it.name }

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        islandEnabled = p.getBoolean("island", true)
        wallpaperEnabled = p.getBoolean("wallpaper", false)
        wallpaperAnimated = p.getBoolean("wallpaper_anim", true)
        dynamicArtwork = p.getBoolean("dynamic_artwork", true)
        miniSwipe = p.getBoolean("mini_swipe", true)
        dockLabelMode = NavDockLogic.parseLabelMode(p.getString("dock_labels", null))
        dock = OrderedLayoutLogic.deserialize(p.getString("dock", null), tabNames(), NavDockLogic.MIN_VISIBLE_TABS, NavDockLogic.LOCKED_TAB)
        homeShelves = OrderedLayoutLogic.deserialize(p.getString("home_shelves", null), HomeLayoutLogic.ALL)
    }

    /** The dock as it should be drawn now, even before [init] ran. */
    fun dockTabs(): List<Tab> {
        val list = if (dock.isEmpty()) OrderedLayoutLogic.defaults(tabNames()) else dock
        val names = OrderedLayoutLogic.visibleNames(list)
        return names.mapNotNull { n -> Tab.entries.firstOrNull { it.name == n } }.ifEmpty { Tab.entries.toList() }
    }

    private fun putBool(key: String, value: Boolean) { prefs?.edit()?.putBoolean(key, value)?.apply() }

    fun updateIslandEnabled(value: Boolean) { islandEnabled = value; putBool("island", value) }
    fun updateWallpaperEnabled(value: Boolean) { wallpaperEnabled = value; putBool("wallpaper", value) }
    fun updateWallpaperAnimated(value: Boolean) { wallpaperAnimated = value; putBool("wallpaper_anim", value) }
    fun updateDynamicArtwork(value: Boolean) { dynamicArtwork = value; putBool("dynamic_artwork", value) }
    fun updateMiniSwipe(value: Boolean) { miniSwipe = value; putBool("mini_swipe", value) }

    fun updateDockLabelMode(value: DockLabelMode) {
        dockLabelMode = value
        prefs?.edit()?.putString("dock_labels", value.name)?.apply()
    }

    private fun saveDock(list: List<LayoutItem>) {
        dock = list
        prefs?.edit()?.putString("dock", OrderedLayoutLogic.serialize(list))?.apply()
    }

    fun toggleDockTab(name: String) =
        saveDock(OrderedLayoutLogic.toggle(currentDock(), name, NavDockLogic.MIN_VISIBLE_TABS, NavDockLogic.LOCKED_TAB))

    fun moveDockTab(name: String, delta: Int) = saveDock(OrderedLayoutLogic.move(currentDock(), name, delta))

    fun currentDock(): List<LayoutItem> = if (dock.isEmpty()) OrderedLayoutLogic.defaults(tabNames()) else dock

    private fun saveShelves(list: List<LayoutItem>) {
        homeShelves = list
        prefs?.edit()?.putString("home_shelves", OrderedLayoutLogic.serialize(list))?.apply()
    }

    fun toggleShelf(name: String) = saveShelves(OrderedLayoutLogic.toggle(homeShelves, name))
    fun moveShelf(name: String, delta: Int) = saveShelves(OrderedLayoutLogic.move(homeShelves, name, delta))

    fun resetLayout() {
        saveDock(OrderedLayoutLogic.defaults(tabNames()))
        saveShelves(OrderedLayoutLogic.defaults(HomeLayoutLogic.ALL))
        updateDockLabelMode(DockLabelMode.Always)
    }
}
