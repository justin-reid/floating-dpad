package com.floatingdpad.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import com.floatingdpad.input.KeySender
import com.floatingdpad.settings.Prefs

/**
 * Owns the WindowManager window that the pad lives in.
 *
 * The window is WRAP_CONTENT around the button cluster rather than a fullscreen
 * transparent layer. Since Android 12 the system blocks touches passing *through* an
 * overlay anyway, and this app never wants pass-through -- its buttons consume the touch
 * and inject a key instead -- so a fullscreen window would only put us in the touch path
 * of everything else and burn battery for nothing.
 */
class OverlayWindow(
    private val context: Context,
    private val prefs: Prefs,
    private val keySender: KeySender,
) {

    /** Raised when a press could not be delivered, so the service can say so out loud. */
    var onNotReady: (() -> Unit)? = null

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: DpadView? = null
    private var params: WindowManager.LayoutParams? = null

    private var dragOriginX = 0
    private var dragOriginY = 0
    private var awaitingInitialPlacement = false

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return

        val dpad = DpadView(context, prefs.snapshot())

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE is non-negotiable. If this window takes input focus the
            // injected key events land here instead of on the app underneath, and
            // nothing works.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            awaitingInitialPlacement = prefs.overlayX == Prefs.UNSET
            x = if (awaitingInitialPlacement) 0 else prefs.overlayX
            y = if (awaitingInitialPlacement) 0 else prefs.overlayY
        }

        dpad.onKey = { keyCode, action, repeatCount, downTime ->
            keySender.send(keyCode, action, repeatCount, downTime)
        }
        dpad.onNotReady = { onNotReady?.invoke() }
        dpad.onDragStart = {
            dragOriginX = layoutParams.x
            dragOriginY = layoutParams.y
        }
        dpad.onDragBy = { dx, dy ->
            layoutParams.x = dragOriginX + dx
            layoutParams.y = dragOriginY + dy
            clampAndApply()
        }
        dpad.onDragEnd = {
            prefs.overlayX = layoutParams.x
            prefs.overlayY = layoutParams.y
        }
        dpad.onToggleCollapsed = {
            // Flip the pref only; the service's preference listener rebuilds the view.
            prefs.collapsed = !prefs.collapsed
        }

        try {
            windowManager.addView(dpad, layoutParams)
        } catch (t: Throwable) {
            Log.e(TAG, "addView failed -- is the overlay permission granted?", t)
            return
        }

        view = dpad
        params = layoutParams
        dpad.post { settlePosition() }
    }

    fun hide() {
        val current = view ?: return
        runCatching { windowManager.removeView(current) }
            .onFailure { Log.w(TAG, "removeView failed", it) }
        view = null
        params = null
    }

    /** Re-reads preferences and re-lays out the cluster in place. */
    fun refreshConfig() {
        val current = view ?: return
        current.applyConfig(prefs.snapshot())
        current.post { settlePosition() }
    }

    fun setReady(ready: Boolean) {
        view?.setReady(ready)
    }

    /** Keeps the pad on screen after a rotation or a size change. */
    fun onConfigurationChanged() {
        view?.post { settlePosition() }
    }

    private fun settlePosition() {
        val current = view ?: return
        val layoutParams = params ?: return
        if (current.width == 0 || current.height == 0) return

        if (awaitingInitialPlacement) {
            awaitingInitialPlacement = false
            val bounds = screenBounds()
            val margin = dp(12f)
            layoutParams.x = bounds.width() - current.width - margin
            layoutParams.y = (bounds.height() - current.height) / 2
            prefs.overlayX = layoutParams.x
            prefs.overlayY = layoutParams.y
        }
        clampAndApply()
    }

    private fun clampAndApply() {
        val current = view ?: return
        val layoutParams = params ?: return
        val bounds = screenBounds()

        val maxX = (bounds.width() - current.width).coerceAtLeast(0)
        val maxY = (bounds.height() - current.height).coerceAtLeast(0)
        layoutParams.x = layoutParams.x.coerceIn(0, maxX)
        layoutParams.y = layoutParams.y.coerceIn(0, maxY)

        runCatching { windowManager.updateViewLayout(current, layoutParams) }
            .onFailure { Log.w(TAG, "updateViewLayout failed", it) }
    }

    private fun screenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(windowManager.currentWindowMetrics.bounds)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    ).toInt()

    private companion object {
        const val TAG = "OverlayWindow"
    }
}
