package com.floatingdpad.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.floatingdpad.input.DpadButton

/**
 * SharedPreferences rather than DataStore, deliberately: the overlay reads these from
 * inside a touch handler, where a synchronous read is exactly what you want, and the
 * change listener is all the reactivity a six-button pad needs.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Layout of the button cluster. */
    enum class Preset { CROSS, ROW, COLUMN }

    var overlayX: Int
        get() = sp.getInt(KEY_X, UNSET)
        set(value) = sp.edit { putInt(KEY_X, value) }

    var overlayY: Int
        get() = sp.getInt(KEY_Y, UNSET)
        set(value) = sp.edit { putInt(KEY_Y, value) }

    /** Whether the user wants the pad on screen; survives reboot for the boot receiver. */
    var overlayEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit { putBoolean(KEY_ENABLED, value) }

    var buttonSizeDp: Int
        get() = sp.getInt(KEY_SIZE, 56).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
        set(value) = sp.edit { putInt(KEY_SIZE, value.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)) }

    var opacityPercent: Int
        get() = sp.getInt(KEY_OPACITY, 80).coerceIn(MIN_OPACITY, 100)
        set(value) = sp.edit { putInt(KEY_OPACITY, value.coerceIn(MIN_OPACITY, 100)) }

    var preset: Preset
        get() = runCatching { Preset.valueOf(sp.getString(KEY_PRESET, "") ?: "") }
            .getOrDefault(Preset.CROSS)
        set(value) = sp.edit { putString(KEY_PRESET, value.name) }

    /** Locked hides the drag handle and ignores drags, so the pad cannot wander. */
    var locked: Boolean
        get() = sp.getBoolean(KEY_LOCKED, false)
        set(value) = sp.edit { putBoolean(KEY_LOCKED, value) }

    var collapsed: Boolean
        get() = sp.getBoolean(KEY_COLLAPSED, false)
        set(value) = sp.edit { putBoolean(KEY_COLLAPSED, value) }

    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(value) = sp.edit { putBoolean(KEY_HAPTICS, value) }

    var repeatDelayMs: Int
        get() = sp.getInt(KEY_REPEAT_DELAY, 400).coerceIn(100, 1000)
        set(value) = sp.edit { putInt(KEY_REPEAT_DELAY, value.coerceIn(100, 1000)) }

    var repeatIntervalMs: Int
        get() = sp.getInt(KEY_REPEAT_INTERVAL, 50).coerceIn(20, 300)
        set(value) = sp.edit { putInt(KEY_REPEAT_INTERVAL, value.coerceIn(20, 300)) }

    var startOnBoot: Boolean
        get() = sp.getBoolean(KEY_BOOT, false)
        set(value) = sp.edit { putBoolean(KEY_BOOT, value) }

    fun keyCodeFor(button: DpadButton): Int =
        sp.getInt(button.prefKey, button.defaultKeyCode)

    fun setKeyCode(button: DpadButton, code: Int) =
        sp.edit { putInt(button.prefKey, code) }

    fun resetKeyCodes() = sp.edit {
        DpadButton.entries.forEach { remove(it.prefKey) }
    }

    fun snapshot(): DpadConfig = DpadConfig(
        buttonSizeDp = buttonSizeDp,
        opacityPercent = opacityPercent,
        preset = preset,
        locked = locked,
        collapsed = collapsed,
        haptics = haptics,
        repeatDelayMs = repeatDelayMs,
        repeatIntervalMs = repeatIntervalMs,
        keyCodes = DpadButton.entries.associateWith(::keyCodeFor),
    )

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val UNSET = Int.MIN_VALUE
        const val MIN_SIZE_DP = 36
        const val MAX_SIZE_DP = 96
        const val MIN_OPACITY = 20

        const val KEY_X = "overlay_x"
        const val KEY_Y = "overlay_y"

        /** Written on every drag frame; the overlay ignores change callbacks for these. */
        val POSITION_KEYS = setOf(KEY_X, KEY_Y)

        private const val NAME = "floating_dpad"
        private const val KEY_ENABLED = "overlay_enabled"
        private const val KEY_SIZE = "button_size_dp"
        private const val KEY_OPACITY = "opacity_percent"
        private const val KEY_PRESET = "preset"
        private const val KEY_LOCKED = "locked"
        private const val KEY_COLLAPSED = "collapsed"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_REPEAT_DELAY = "repeat_delay_ms"
        private const val KEY_REPEAT_INTERVAL = "repeat_interval_ms"
        private const val KEY_BOOT = "start_on_boot"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}

/** Immutable view of everything the overlay needs in order to draw and behave. */
data class DpadConfig(
    val buttonSizeDp: Int,
    val opacityPercent: Int,
    val preset: Prefs.Preset,
    val locked: Boolean,
    val collapsed: Boolean,
    val haptics: Boolean,
    val repeatDelayMs: Int,
    val repeatIntervalMs: Int,
    val keyCodes: Map<DpadButton, Int>,
)
