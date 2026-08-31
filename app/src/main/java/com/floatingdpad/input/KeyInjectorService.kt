package com.floatingdpad.input

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import kotlin.system.exitProcess

/**
 * Runs inside the Shizuku-forked process, as the shell UID.
 *
 * Note there is no HiddenApiBypass dependency here on purpose: hidden-API restrictions
 * are not applied to processes running as shell, which is where this code executes.
 */
class KeyInjectorService() : IKeyInjector.Stub() {

    /** Shizuku will use this constructor if present; we don't need the Context. */
    @Suppress("unused")
    constructor(context: Context) : this()

    /**
     * The injectInputEvent binder moved from InputManager to InputManagerGlobal in
     * Android 14, so resolve whichever one this device has.
     */
    private val inject: (InputEvent, Int) -> Unit = run {
        val (cls, instance) = try {
            val c = Class.forName("android.hardware.input.InputManagerGlobal")
            c to c.getMethod("getInstance").invoke(null)
        } catch (_: ClassNotFoundException) {
            val c = Class.forName("android.hardware.input.InputManager")
            c to c.getMethod("getInstance").invoke(null)
        }
        val method = cls.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
        ({ event: InputEvent, mode: Int -> method.invoke(instance, event, mode); Unit })
    }

    override fun injectKey(keyCode: Int, action: Int, repeatCount: Int, downTime: Long) {
        try {
            val event = KeyEvent(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                keyCode,
                repeatCount,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD,
            )
            inject(event, INJECT_INPUT_EVENT_MODE_ASYNC)
        } catch (t: Throwable) {
            // oneway, so nothing propagates back to the caller: log and carry on.
            Log.e(TAG, "injectKey($keyCode, $action) failed", t)
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    private companion object {
        const val TAG = "KeyInjectorService"
        const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
    }
}
