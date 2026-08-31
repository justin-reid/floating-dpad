package com.floatingdpad.input

import android.view.KeyEvent

/**
 * The six things the pad can press. The keycode each one actually sends is a
 * preference, not a constant: TiviMate builds differ on whether "select" wants
 * DPAD_CENTER or ENTER, and finding that out should not require a rebuild.
 */
enum class DpadButton(
    val prefKey: String,
    val label: String,
    val defaultKeyCode: Int,
    /** Holding an arrow should scroll; holding select should not re-fire. */
    val repeatable: Boolean,
) {
    UP("keycode_up", "Up", KeyEvent.KEYCODE_DPAD_UP, true),
    DOWN("keycode_down", "Down", KeyEvent.KEYCODE_DPAD_DOWN, true),
    LEFT("keycode_left", "Left", KeyEvent.KEYCODE_DPAD_LEFT, true),
    RIGHT("keycode_right", "Right", KeyEvent.KEYCODE_DPAD_RIGHT, true),
    SELECT("keycode_select", "Select", KeyEvent.KEYCODE_DPAD_CENTER, false),
    BACK("keycode_back", "Back", KeyEvent.KEYCODE_BACK, false),
}

/** The shortlist offered in the per-button override dropdown. */
object KeyCodeCatalog {

    data class Entry(val code: Int, val label: String)

    val entries: List<Entry> = listOf(
        Entry(KeyEvent.KEYCODE_DPAD_UP, "D-pad Up"),
        Entry(KeyEvent.KEYCODE_DPAD_DOWN, "D-pad Down"),
        Entry(KeyEvent.KEYCODE_DPAD_LEFT, "D-pad Left"),
        Entry(KeyEvent.KEYCODE_DPAD_RIGHT, "D-pad Right"),
        Entry(KeyEvent.KEYCODE_DPAD_CENTER, "D-pad Center"),
        Entry(KeyEvent.KEYCODE_ENTER, "Enter"),
        Entry(KeyEvent.KEYCODE_NUMPAD_ENTER, "Numpad Enter"),
        Entry(KeyEvent.KEYCODE_BACK, "Back"),
        Entry(KeyEvent.KEYCODE_ESCAPE, "Escape"),
        Entry(KeyEvent.KEYCODE_MENU, "Menu"),
        Entry(KeyEvent.KEYCODE_INFO, "Info"),
        Entry(KeyEvent.KEYCODE_GUIDE, "Guide"),
        Entry(KeyEvent.KEYCODE_CHANNEL_UP, "Channel Up"),
        Entry(KeyEvent.KEYCODE_CHANNEL_DOWN, "Channel Down"),
        Entry(KeyEvent.KEYCODE_PAGE_UP, "Page Up"),
        Entry(KeyEvent.KEYCODE_PAGE_DOWN, "Page Down"),
        Entry(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play / Pause"),
        Entry(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "Fast Forward"),
        Entry(KeyEvent.KEYCODE_MEDIA_REWIND, "Rewind"),
        Entry(KeyEvent.KEYCODE_MEDIA_NEXT, "Next"),
        Entry(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous"),
        Entry(KeyEvent.KEYCODE_VOLUME_UP, "Volume Up"),
        Entry(KeyEvent.KEYCODE_VOLUME_DOWN, "Volume Down"),
        Entry(KeyEvent.KEYCODE_VOLUME_MUTE, "Mute"),
    )

    fun labelFor(code: Int): String =
        entries.firstOrNull { it.code == code }?.label ?: "Keycode $code"
}
