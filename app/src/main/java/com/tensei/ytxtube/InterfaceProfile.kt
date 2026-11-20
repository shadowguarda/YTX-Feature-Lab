package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * A complete saved look for the app: home, navigation, glass, animation, player and accent.
 * Strings hold enum names so a profile survives renamed labels.
 */
data class InterfaceProfile(
    val name: String,
    val accentHex: String? = null,
    val primaryGlowHex: String = "#7C3AED",
    val secondaryGlowHex: String = "#0091FF",
    val homeCardStyle: String = FeedCardStyle.Comfortable.name,
    val homeShelves: String = "",
    val dock: String = "",
    val dockLabelMode: String = DockLabelMode.Always.name,
    val navIndicator: String = NavIndicatorStyle.LiquidPill.name,
    val animation: String = AnimationTimelinePreset.SilkyLiquid.name,
    val sheetRadiusDp: Int = 28,
    val sheetSurfaceAlpha: Float = 0.68f,
    val borderWidthDp: Float = 1f,
    val neonBorder: Boolean = true,
    val animatedGlow: Boolean = true,
    val playerBlurDp: Int = 20,
    val navBlurDp: Int = 10,
    val sheetBlurDp: Int = 26,
    val refractionHeightDp: Int = 14,
    val refractionAmountDp: Int = 22,
    val chromatic: Boolean = true,
    val playerEngine: String = PlayerEngineMode.Classic.name,
    val playerStyle: String = PlayerStylePreset.KawaiiGlass.name,
    val seekbarStyle: String = PlayerSeekbarStyle.WavyCute.name,
    val buttonShape: String = PlayerButtonShape.Squircle.name,
    val hudGlassAlpha: Float = 0.44f,
    val autoHideSeconds: Int = 4,
    val doubleTapSeconds: Int = 10,
)

data class ProfileParse(val profile: InterfaceProfile?, val errors: List<String>, val notes: List<String> = emptyList())

object InterfaceProfileLogic {
    const val TYPE = "ytx-interface-profile"
    const val VERSION = 1
    const val MAX_PROFILES = 20
    const val MAX_NAME = 40

    private inline fun <reified E : Enum<E>> enumName(raw: String?, default: E): String =
        enumValues<E>().firstOrNull { it.name == raw }?.name ?: default.name

    fun toJson(p: InterfaceProfile): String = JSONObject()
        .put("type", TYPE)
        .put("version", VERSION)
        .put("name", p.name)
        .put("accentHex", p.accentHex ?: JSONObject.NULL)
        .put("primaryGlowHex", p.primaryGlowHex)
        .put("secondaryGlowHex", p.secondaryGlowHex)
        .put("homeCardStyle", p.homeCardStyle)
        .put("homeShelves", p.homeShelves)
        .put("dock", p.dock)
        .put("dockLabelMode", p.dockLabelMode)
        .put("navIndicator", p.navIndicator)
        .put("animation", p.animation)
        .put("sheetRadiusDp", p.sheetRadiusDp)
        .put("sheetSurfaceAlpha", p.sheetSurfaceAlpha.toDouble())
        .put("borderWidthDp", p.borderWidthDp.toDouble())
        .put("neonBorder", p.neonBorder)
        .put("animatedGlow", p.animatedGlow)
        .put("playerBlurDp", p.playerBlurDp)
        .put("navBlurDp", p.navBlurDp)
        .put("sheetBlurDp", p.sheetBlurDp)
        .put("refractionHeightDp", p.refractionHeightDp)
        .put("refractionAmountDp", p.refractionAmountDp)
        .put("chromatic", p.chromatic)
        .put("playerEngine", p.playerEngine)
        .put("playerStyle", p.playerStyle)
        .put("seekbarStyle", p.seekbarStyle)
        .put("buttonShape", p.buttonShape)
        .put("hudGlassAlpha", p.hudGlassAlpha.toDouble())
        .put("autoHideSeconds", p.autoHideSeconds)
        .put("doubleTapSeconds", p.doubleTapSeconds)
        .toString(2)

    /** Reads a profile. Unknown choices fall back to defaults (and are reported in notes); numbers are clamped. */
    fun fromJson(raw: String): ProfileParse {
        val root = runCatching { JSONObject(raw.trim()) }.getOrNull()
            ?: return ProfileParse(null, listOf("That is not valid JSON."))
        if (root.optString("type") != TYPE) return ProfileParse(null, listOf("This is not a YTX interface profile."))
        val version = root.optInt("version", 0)
        if (version < 1 || version > VERSION) return ProfileParse(null, listOf("Unsupported profile version $version."))
        val errors = mutableListOf<String>()
        val notes = mutableListOf<String>()

        fun hex(key: String, fallback: String): String {
            val v = root.optString(key, fallback)
            val n = ThemeAccentLogic.normalizeHex(v)
            if (n == null) { notes += "$key was not a valid colour, kept $fallback."; return fallback }
            return n.uppercase()
        }
        val accentRaw = if (root.isNull("accentHex")) null else root.optString("accentHex")
        val accent = accentRaw?.let { ThemeAccentLogic.normalizeHex(it)?.uppercase() }
        if (accentRaw != null && accent == null) notes += "accentHex was not a valid colour and was ignored."
        val name = root.optString("name", "").trim().take(MAX_NAME)
        if (name.isEmpty()) errors += "The profile has no name."
        if (errors.isNotEmpty()) return ProfileParse(null, errors)

        val card = enumName(root.optString("homeCardStyle"), FeedCardStyle.Comfortable)
        val label = enumName(root.optString("dockLabelMode"), DockLabelMode.Always)
        val nav = enumName(root.optString("navIndicator"), NavIndicatorStyle.LiquidPill)
        val anim = enumName(root.optString("animation"), AnimationTimelinePreset.SilkyLiquid)
        val engine = enumName(root.optString("playerEngine"), PlayerEngineMode.Classic)
        val pstyle = enumName(root.optString("playerStyle"), PlayerStylePreset.KawaiiGlass)
        val seek = enumName(root.optString("seekbarStyle"), PlayerSeekbarStyle.WavyCute)
        val shape = enumName(root.optString("buttonShape"), PlayerButtonShape.Squircle)
        listOf("homeCardStyle" to card, "navIndicator" to nav, "animation" to anim, "playerEngine" to engine, "playerStyle" to pstyle)
            .forEach { (k, v) -> if (root.has(k) && root.optString(k) != v) notes += "$k '${root.optString(k)}' is unknown, used $v." }

        val profile = InterfaceProfile(
            name = name,
            accentHex = accent,
            primaryGlowHex = hex("primaryGlowHex", "#7C3AED"),
            secondaryGlowHex = hex("secondaryGlowHex", "#0091FF"),
            homeCardStyle = card,
            homeShelves = root.optString("homeShelves", ""),
            dock = root.optString("dock", ""),
            dockLabelMode = label,
            navIndicator = nav,
            animation = anim,
            sheetRadiusDp = root.optInt("sheetRadiusDp", 28).coerceIn(12, 42),
            sheetSurfaceAlpha = root.optDouble("sheetSurfaceAlpha", 0.68).toFloat().coerceIn(0.35f, 0.68f),
            borderWidthDp = root.optDouble("borderWidthDp", 1.0).toFloat().coerceIn(0.5f, 3f),
            neonBorder = root.optBoolean("neonBorder", true),
            animatedGlow = root.optBoolean("animatedGlow", true),
            playerBlurDp = root.optInt("playerBlurDp", 20).coerceIn(0, 40),
            navBlurDp = root.optInt("navBlurDp", 10).coerceIn(0, 40),
            sheetBlurDp = root.optInt("sheetBlurDp", 26).coerceIn(0, 40),
            refractionHeightDp = root.optInt("refractionHeightDp", 14).coerceIn(0, 36),
            refractionAmountDp = root.optInt("refractionAmountDp", 22).coerceIn(0, 44),
            chromatic = root.optBoolean("chromatic", true),
            playerEngine = engine,
            playerStyle = pstyle,
            seekbarStyle = seek,
            buttonShape = shape,
            hudGlassAlpha = root.optDouble("hudGlassAlpha", 0.44).toFloat().coerceIn(0.15f, 0.88f),
            autoHideSeconds = root.optInt("autoHideSeconds", 4).coerceIn(0, 10),
            doubleTapSeconds = root.optInt("doubleTapSeconds", 10).coerceIn(5, 30),
        )
        return ProfileParse(profile, emptyList(), notes)
    }

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    /** [base] if free, else "base 2", "base 3"... compared ignoring case. */
    fun uniqueName(existing: List<InterfaceProfile>, base: String): String {
        val b = cleanName(base).ifEmpty { "My profile" }
        val taken = existing.map { it.name.lowercase() }.toSet()
        if (b.lowercase() !in taken) return b
        var i = 2
        while ("${b.take(MAX_NAME - 4)} $i".lowercase() in taken) i++
        return "${b.take(MAX_NAME - 4)} $i"
    }

    /** Adds or replaces (same name, ignoring case); the list never grows past [MAX_PROFILES]. Null when full. */
    fun upsert(list: List<InterfaceProfile>, p: InterfaceProfile): List<InterfaceProfile>? {
        val idx = list.indexOfFirst { it.name.equals(p.name, ignoreCase = true) }
        if (idx >= 0) return list.toMutableList().also { it[idx] = p }
        if (list.size >= MAX_PROFILES) return null
        return list + p
    }

    fun remove(list: List<InterfaceProfile>, name: String): List<InterfaceProfile> =
        list.filterNot { it.name.equals(name, ignoreCase = true) }

    fun serializeList(list: List<InterfaceProfile>): String =
        JSONArray().also { arr -> list.forEach { arr.put(JSONObject(toJson(it))) } }.toString()

    fun parseList(raw: String?): List<InterfaceProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { fromJson(it.toString()).profile } }
    }
}

/** Reads the app's current look into a profile, applies one, and keeps the saved list. */
object InterfaceProfiles {
    private var prefs: SharedPreferences? = null

    var saved by mutableStateOf<List<InterfaceProfile>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("interface_profiles", Context.MODE_PRIVATE)
        prefs = p
        saved = InterfaceProfileLogic.parseList(p.getString("list", null))
    }

    private fun persist(list: List<InterfaceProfile>) {
        saved = list
        prefs?.edit()?.putString("list", InterfaceProfileLogic.serializeList(list))?.apply()
    }

    fun capture(name: String): InterfaceProfile = InterfaceProfile(
        name = name,
        accentHex = ThemeSettings.seed,
        primaryGlowHex = LiquidGlassStudio.primaryGlowHex,
        secondaryGlowHex = LiquidGlassStudio.secondaryGlowHex,
        homeCardStyle = UiUpgrades.homeCardStyle.name,
        homeShelves = OrderedLayoutLogic.serialize(UiUpgrades.homeShelves),
        dock = OrderedLayoutLogic.serialize(UiUpgrades.currentDock()),
        dockLabelMode = UiUpgrades.dockLabelMode.name,
        navIndicator = LiquidGlassStudio.navIndicatorStyle.name,
        animation = LiquidGlassStudio.animationPreset.name,
        sheetRadiusDp = LiquidGlassStudio.sheetCornerRadiusDp,
        sheetSurfaceAlpha = LiquidGlassStudio.sheetSurfaceAlpha,
        borderWidthDp = LiquidGlassStudio.borderWidthDp,
        neonBorder = LiquidGlassStudio.neonBorderEnabled,
        animatedGlow = LiquidGlassStudio.animatedBackgroundsEnabled,
        playerBlurDp = LiquidGlassStudio.playerBlurDp,
        navBlurDp = LiquidGlassStudio.navBarBlurDp,
        sheetBlurDp = LiquidGlassStudio.sheetBlurDp,
        refractionHeightDp = LiquidGlassStudio.refractionHeightDp,
        refractionAmountDp = LiquidGlassStudio.refractionAmountDp,
        chromatic = LiquidGlassStudio.chromaticAberration,
        playerEngine = PlayerEngine.mode.name,
        playerStyle = CutePlayerPreferences.style.name,
        seekbarStyle = CutePlayerPreferences.seekbarStyle.name,
        buttonShape = CutePlayerPreferences.buttonShape.name,
        hudGlassAlpha = CutePlayerPreferences.hudGlassAlpha,
        autoHideSeconds = CutePlayerPreferences.autoHideSeconds,
        doubleTapSeconds = CutePlayerPreferences.doubleTapSeekSeconds,
    )

    fun apply(p: InterfaceProfile) {
        p.accentHex?.let { ThemeSettings.choose(it) }
        UiUpgrades.updateHomeCardStyle(FeedCardLogic.parse(p.homeCardStyle))
        UiUpgrades.updateDockLabelMode(NavDockLogic.parseLabelMode(p.dockLabelMode))
        if (p.homeShelves.isNotBlank()) UiUpgrades.applyShelvesRaw(p.homeShelves)
        if (p.dock.isNotBlank()) UiUpgrades.applyDockRaw(p.dock)
        LiquidGlassStudio.updateGlowAndStyle(
            glow1 = p.primaryGlowHex,
            glow2 = p.secondaryGlowHex,
            animBg = p.animatedGlow,
            navStyle = NavIndicatorStyle.entries.firstOrNull { it.name == p.navIndicator } ?: NavIndicatorStyle.LiquidPill,
            animTimeline = AnimationTimelinePreset.entries.firstOrNull { it.name == p.animation } ?: AnimationTimelinePreset.SilkyLiquid,
            sheetRadius = p.sheetRadiusDp,
        )
        LiquidGlassStudio.updateDepthAndBorders(
            refHeight = p.refractionHeightDp,
            refAmount = p.refractionAmountDp,
            chromatic = p.chromatic,
            borderW = p.borderWidthDp,
            neonBorder = p.neonBorder,
        )
        LiquidGlassStudio.updateScreenBlurs(player = p.playerBlurDp, navBar = p.navBlurDp, sheet = p.sheetBlurDp)
        LiquidGlassStudio.updateSheetSurfaceAlpha(p.sheetSurfaceAlpha)
        PlayerEngine.updateMode(PlayerEngineLogic.parse(p.playerEngine))
        PlayerStylePreset.entries.firstOrNull { it.name == p.playerStyle }?.let { CutePlayerPreferences.updateStyle(it) }
        PlayerSeekbarStyle.entries.firstOrNull { it.name == p.seekbarStyle }?.let { CutePlayerPreferences.updateSeekbarStyle(it) }
        PlayerButtonShape.entries.firstOrNull { it.name == p.buttonShape }?.let { CutePlayerPreferences.updateButtonShape(it) }
        CutePlayerPreferences.updateHudGlassAlpha(p.hudGlassAlpha)
        CutePlayerPreferences.updateAutoHideSeconds(p.autoHideSeconds)
        CutePlayerPreferences.updateDoubleTapSeekSeconds(p.doubleTapSeconds)
    }

    /** Saves the current look under [name]; false when the list is full. */
    fun saveCurrent(name: String): Boolean {
        val clean = InterfaceProfileLogic.cleanName(name).ifEmpty { InterfaceProfileLogic.uniqueName(saved, "My profile") }
        val next = InterfaceProfileLogic.upsert(saved, capture(clean)) ?: return false
        persist(next)
        return true
    }

    fun saveImported(p: InterfaceProfile): Boolean {
        val unique = p.copy(name = InterfaceProfileLogic.uniqueName(saved, p.name))
        val next = InterfaceProfileLogic.upsert(saved, unique) ?: return false
        persist(next)
        return true
    }

    fun delete(name: String) = persist(InterfaceProfileLogic.remove(saved, name))
}
