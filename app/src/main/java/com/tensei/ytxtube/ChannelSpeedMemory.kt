package com.tensei.ytxtube

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

/**
 * Pure rules for remembering a playback speed per channel, so a podcast channel can stay at
 * 1.5x while music stays at 1x. No Android imports, so it is unit tested directly.
 */
object ChannelSpeedLogic {
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 4.0f
    const val MAX_ENTRIES = 300

    /** Clamps to the supported range and snaps to 0.05 steps. Returns null for NaN/infinite. */
    fun clean(speed: Float): Float? {
        if (speed.isNaN() || speed.isInfinite()) return null
        return (speed.coerceIn(MIN_SPEED, MAX_SPEED) * 20f).roundToInt() / 20f
    }

    /** The speed to use: the channel's saved speed if valid, else [defaultSpeed], else 1x. */
    fun resolve(channelSpeed: Float?, defaultSpeed: Float): Float =
        channelSpeed?.let { clean(it) } ?: clean(defaultSpeed) ?: 1f

    /**
     * Returns a new map with [speed] saved for [channelId]. A speed equal to [defaultSpeed] removes
     * the entry (nothing to remember). The most recently changed channel is kept last and the oldest
     * entries are dropped beyond [maxEntries].
     */
    fun remember(
        current: Map<String, Float>,
        channelId: String,
        speed: Float,
        defaultSpeed: Float = 1f,
        maxEntries: Int = MAX_ENTRIES,
    ): Map<String, Float> {
        if (!validId(channelId)) return current
        val cleaned = clean(speed) ?: return current
        val next = LinkedHashMap(current)
        next.remove(channelId)
        if (cleaned != (clean(defaultSpeed) ?: 1f)) next[channelId] = cleaned
        while (next.size > maxEntries) next.remove(next.keys.first())
        return next
    }

    /** Ids are stored as `id=speed;` pairs, so they must not contain the separators. */
    fun validId(id: String): Boolean = id.isNotBlank() && ';' !in id && '=' !in id

    fun encode(map: Map<String, Float>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    /** Reads what [encode] wrote; skips any malformed pair instead of failing. */
    fun decode(text: String?): Map<String, Float> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Float>()
        text.split(';').forEach { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) return@forEach
            val id = pair.substring(0, i)
            val speed = pair.substring(i + 1).toFloatOrNull()?.let { clean(it) } ?: return@forEach
            if (validId(id)) out[id] = speed
        }
        return out
    }
}

/** Saved per-channel playback speeds. Call [init] once at app start. */
object ChannelSpeedMemory {
    private const val PREFS = "channel_speed"
    private const val KEY = "speeds"

    private var prefs: SharedPreferences? = null
    private var speeds: Map<String, Float> = emptyMap()

    @Synchronized
    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        speeds = ChannelSpeedLogic.decode(p.getString(KEY, null))
    }

    /** Saved speed for the channel, or [defaultSpeed] when none is saved or the id is unknown. */
    @Synchronized
    fun speedFor(channelId: String?, defaultSpeed: Float = 1f): Float =
        ChannelSpeedLogic.resolve(channelId?.let { speeds[it] }, defaultSpeed)

    /** True when this channel has its own saved speed. */
    @Synchronized
    fun hasSpeed(channelId: String?): Boolean = channelId != null && speeds.containsKey(channelId)

    /** Saves [speed] for the channel. Setting it back to the default forgets the channel. */
    @Synchronized
    fun remember(channelId: String?, speed: Float, defaultSpeed: Float = 1f) {
        if (channelId == null) return
        speeds = ChannelSpeedLogic.remember(speeds, channelId, speed, defaultSpeed)
        persist()
    }

    @Synchronized
    fun forget(channelId: String?) {
        if (channelId == null || !speeds.containsKey(channelId)) return
        speeds = speeds - channelId
        persist()
    }

    @Synchronized
    fun clearAll() {
        speeds = emptyMap()
        persist()
    }

    @Synchronized
    fun count(): Int = speeds.size

    private fun persist() {
        prefs?.edit()?.putString(KEY, ChannelSpeedLogic.encode(speeds))?.apply()
    }
}
