package com.example.ytxfeaturelab.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Portable copy of the seed / mode / AMOLED theme settings from YTXTube.
 * Drop this into the real app and let ThemeSettings drive `schemeFor`.
 */
object ThemeSettings {
    private const val PREFS = "appearance"
    private const val KEY_SEED = "seed"
    private const val KEY_MODE = "mode"

    private var prefs: SharedPreferences? = null

    var seed by mutableStateOf("#B5651D")
        private set
    var mode by mutableStateOf("system")
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        seed = p.getString(KEY_SEED, seed) ?: seed
        mode = p.getString(KEY_MODE, "system") ?: "system"
    }

    fun chooseSeed(hex: String) {
        seed = hex
        prefs?.edit()?.putString(KEY_SEED, hex)?.apply()
    }

    fun chooseMode(value: String) {
        mode = value
        prefs?.edit()?.putString(KEY_MODE, value)?.apply()
    }
}
