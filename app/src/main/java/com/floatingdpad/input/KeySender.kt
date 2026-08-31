package com.floatingdpad.input

/**
 * The one seam between "a button was pressed" and "a key reached the foreground app".
 *
 * ShizukuKeySender is the only v1 implementation. Keeping this an interface is what
 * makes an AccessibilityService fallback backend possible later without the overlay
 * code knowing anything changed, so do not reach for Shizuku directly from a View.
 */
interface KeySender {

    val isReady: Boolean

    /**
     * @param action KeyEvent.ACTION_DOWN or ACTION_UP
     * @param downTime uptimeMillis of the ACTION_DOWN that began this press; constant
     *                 for the whole press, repeats included
     * @return true if the event was handed off, false if the backend is not ready
     */
    fun send(keyCode: Int, action: Int, repeatCount: Int, downTime: Long): Boolean
}
