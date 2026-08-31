package com.floatingdpad.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.floatingdpad.input.DpadButton
import com.floatingdpad.settings.DpadConfig
import com.floatingdpad.settings.Prefs
import kotlin.math.abs
import kotlin.math.min

/**
 * The pad itself: plain Canvas drawing, no Compose.
 *
 * Compose inside a WindowManager window needs ViewTreeLifecycleOwner and
 * ViewTreeSavedStateRegistryOwner shimmed in, and adds recomposition to the touch-to-key
 * path of a latency-sensitive control. A single custom View draws six buttons and does
 * its own hit testing in far less code than that costs.
 */
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class DpadView(
    context: Context,
    private var config: DpadConfig,
) : View(context) {

    /** Where a press goes. Returns false when the backend cannot deliver it. */
    var onKey: ((keyCode: Int, action: Int, repeatCount: Int, downTime: Long) -> Boolean)? = null

    var onDragStart: (() -> Unit)? = null
    var onDragBy: ((dx: Int, dy: Int) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    /** Collapse to a bubble, or expand back out. */
    var onToggleCollapsed: (() -> Unit)? = null

    /** A press was made while the key backend was down. */
    var onNotReady: (() -> Unit)? = null

    private sealed interface Slot {
        data class Key(val button: DpadButton) : Slot
        data object Handle : Slot
        data object Collapse : Slot
        data object Bubble : Slot
    }

    private class Placed(val slot: Slot, val rect: RectF)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(214, 20, 21, 24)
    }
    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(255, 229, 87, 78)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 56, 58, 64)
    }
    private val buttonPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 88, 152, 240)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val glyphStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
    }

    private val path = Path()
    private val scratchRect = RectF()
    private val backgroundRect = RectF()

    private var cellPx = 0f
    private var padPx = 0f
    private var gapPx = 0f
    private var cornerPx = 0f
    private var buttonCornerPx = 0f

    private var cells: List<Placed> = emptyList()
    private var gridCols = 1
    private var gridRows = 1

    private var ready = true

    private var pressedSlot: Slot? = null
    private var pressedButton: DpadButton? = null
    private var pressDownTime = 0L
    private var repeatCount = 0

    private var dragging = false
    private var dragAnchorX = 0f
    private var dragAnchorY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val button = pressedButton ?: return
            repeatCount++
            // Incrementing repeatCount is what makes the receiving app's own scroll
            // acceleration engage, so it must climb rather than stay at 1.
            send(button, KeyEvent.ACTION_DOWN, repeatCount)
            postDelayed(this, config.repeatIntervalMs.toLong())
        }
    }

    init {
        isHapticFeedbackEnabled = true
        rebuild()
    }

    fun applyConfig(next: DpadConfig) {
        config = next
        cancelPress()
        rebuild()
    }

    fun setReady(next: Boolean) {
        if (ready == next) return
        ready = next
        invalidate()
    }

    // --- layout ------------------------------------------------------------------

    private fun rebuild() {
        cellPx = dp(config.buttonSizeDp.toFloat())
        padPx = dp(6f)
        gapPx = dp(3f)
        cornerPx = dp(18f)
        buttonCornerPx = cellPx * 0.22f
        glyphStrokePaint.strokeWidth = cellPx * 0.075f
        warningPaint.strokeWidth = dp(2f)
        alpha = config.opacityPercent / 100f

        val slots = slotLayout()
        gridCols = slots.cols
        gridRows = slots.rows
        cells = slots.entries.map { (slot, position) ->
            val (col, row) = position
            Placed(
                slot,
                RectF(
                    padPx + col * cellPx,
                    padPx + row * cellPx,
                    padPx + (col + 1) * cellPx,
                    padPx + (row + 1) * cellPx,
                ),
            )
        }
        requestLayout()
        invalidate()
    }

    private class SlotLayout(
        val cols: Int,
        val rows: Int,
        val entries: List<Pair<Slot, Pair<Int, Int>>>,
    )

    private fun slotLayout(): SlotLayout {
        if (config.collapsed) {
            return SlotLayout(1, 1, listOf(Slot.Bubble to (0 to 0)))
        }
        return when (config.preset) {
            Prefs.Preset.CROSS -> {
                val entries = buildList<Pair<Slot, Pair<Int, Int>>> {
                    if (!config.locked) add(Slot.Handle to (0 to 0))
                    add(Slot.Key(DpadButton.UP) to (1 to 0))
                    add(Slot.Collapse to (2 to 0))
                    add(Slot.Key(DpadButton.LEFT) to (0 to 1))
                    add(Slot.Key(DpadButton.SELECT) to (1 to 1))
                    add(Slot.Key(DpadButton.RIGHT) to (2 to 1))
                    add(Slot.Key(DpadButton.BACK) to (0 to 2))
                    add(Slot.Key(DpadButton.DOWN) to (1 to 2))
                }
                SlotLayout(3, 3, entries)
            }

            Prefs.Preset.ROW -> linear(horizontal = true)
            Prefs.Preset.COLUMN -> linear(horizontal = false)
        }
    }

    /** ROW and COLUMN pack the same ordered strip; only the axis differs. */
    private fun linear(horizontal: Boolean): SlotLayout {
        val order = buildList<Slot> {
            if (!config.locked) add(Slot.Handle)
            add(Slot.Key(DpadButton.LEFT))
            add(Slot.Key(DpadButton.UP))
            add(Slot.Key(DpadButton.SELECT))
            add(Slot.Key(DpadButton.DOWN))
            add(Slot.Key(DpadButton.RIGHT))
            add(Slot.Key(DpadButton.BACK))
            add(Slot.Collapse)
        }
        val entries = order.mapIndexed { index, slot ->
            slot to if (horizontal) (index to 0) else (0 to index)
        }
        return if (horizontal) {
            SlotLayout(order.size, 1, entries)
        } else {
            SlotLayout(1, order.size, entries)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            (gridCols * cellPx + 2 * padPx).toInt(),
            (gridRows * cellPx + 2 * padPx).toInt(),
        )
    }

    // --- drawing -----------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        backgroundRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(backgroundRect, cornerPx, cornerPx, backgroundPaint)

        if (!ready) {
            val inset = warningPaint.strokeWidth / 2f
            scratchRect.set(backgroundRect)
            scratchRect.inset(inset, inset)
            canvas.drawRoundRect(scratchRect, cornerPx, cornerPx, warningPaint)
        }

        for (cell in cells) {
            scratchRect.set(cell.rect)
            scratchRect.inset(gapPx, gapPx)

            when (val slot = cell.slot) {
                is Slot.Key -> {
                    val paint =
                        if (pressedSlot == slot) buttonPressedPaint else buttonPaint
                    canvas.drawRoundRect(
                        scratchRect,
                        buttonCornerPx,
                        buttonCornerPx,
                        paint,
                    )
                    drawGlyph(canvas, scratchRect, slot.button)
                }

                Slot.Handle -> drawHandle(canvas, scratchRect)

                Slot.Collapse -> {
                    if (pressedSlot == slot) {
                        canvas.drawRoundRect(
                            scratchRect,
                            buttonCornerPx,
                            buttonCornerPx,
                            buttonPressedPaint,
                        )
                    }
                    drawCollapse(canvas, scratchRect)
                }

                Slot.Bubble -> {
                    canvas.drawRoundRect(
                        scratchRect,
                        scratchRect.width() / 2f,
                        scratchRect.height() / 2f,
                        if (pressedSlot == slot) buttonPressedPaint else buttonPaint,
                    )
                    drawMiniPad(canvas, scratchRect)
                }
            }
        }
    }

    private fun drawGlyph(canvas: Canvas, r: RectF, button: DpadButton) {
        val cx = r.centerX()
        val cy = r.centerY()
        val s = min(r.width(), r.height()) * 0.30f
        when (button) {
            DpadButton.UP, DpadButton.DOWN, DpadButton.LEFT, DpadButton.RIGHT ->
                drawTriangle(canvas, cx, cy, s, button)

            DpadButton.SELECT -> {
                canvas.drawCircle(cx, cy, s * 0.82f, glyphStrokePaint)
                canvas.drawCircle(cx, cy, s * 0.34f, glyphPaint)
            }

            DpadButton.BACK -> drawBack(canvas, cx, cy, s)
        }
    }

    private fun drawTriangle(canvas: Canvas, cx: Float, cy: Float, s: Float, dir: DpadButton) {
        path.reset()
        when (dir) {
            DpadButton.UP -> {
                path.moveTo(cx, cy - s)
                path.lineTo(cx + s * 0.92f, cy + s * 0.62f)
                path.lineTo(cx - s * 0.92f, cy + s * 0.62f)
            }

            DpadButton.DOWN -> {
                path.moveTo(cx, cy + s)
                path.lineTo(cx + s * 0.92f, cy - s * 0.62f)
                path.lineTo(cx - s * 0.92f, cy - s * 0.62f)
            }

            DpadButton.LEFT -> {
                path.moveTo(cx - s, cy)
                path.lineTo(cx + s * 0.62f, cy - s * 0.92f)
                path.lineTo(cx + s * 0.62f, cy + s * 0.92f)
            }

            else -> {
                path.moveTo(cx + s, cy)
                path.lineTo(cx - s * 0.62f, cy - s * 0.92f)
                path.lineTo(cx - s * 0.62f, cy + s * 0.92f)
            }
        }
        path.close()
        canvas.drawPath(path, glyphPaint)
    }

    /** A U-turn arrow, so Back never reads as another left chevron. */
    private fun drawBack(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        scratchRect.set(cx - s * 0.30f, cy - s * 0.95f, cx + s * 0.80f, cy + s * 0.15f)
        canvas.drawArc(scratchRect, -90f, 180f, false, glyphStrokePaint)

        val tailY = scratchRect.bottom
        val headTip = cx - s * 0.95f
        canvas.drawLine(scratchRect.centerX(), tailY, headTip + s * 0.35f, tailY, glyphStrokePaint)

        path.reset()
        path.moveTo(headTip, tailY)
        path.lineTo(headTip + s * 0.55f, tailY - s * 0.42f)
        path.lineTo(headTip + s * 0.55f, tailY + s * 0.42f)
        path.close()
        canvas.drawPath(path, glyphPaint)
    }

    private fun drawHandle(canvas: Canvas, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        val step = min(r.width(), r.height()) * 0.18f
        val dot = step * 0.30f
        for (col in 0..1) {
            for (row in -1..1) {
                canvas.drawCircle(
                    cx + (col - 0.5f) * step,
                    cy + row * step,
                    dot,
                    handlePaint,
                )
            }
        }
    }

    private fun drawCollapse(canvas: Canvas, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        val s = min(r.width(), r.height()) * 0.22f
        canvas.drawLine(cx - s, cy, cx + s, cy, glyphStrokePaint)
    }

    private fun drawMiniPad(canvas: Canvas, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        val s = min(r.width(), r.height()) * 0.13f
        val offset = s * 1.9f
        drawTriangle(canvas, cx, cy - offset, s, DpadButton.UP)
        drawTriangle(canvas, cx, cy + offset, s, DpadButton.DOWN)
        drawTriangle(canvas, cx - offset, cy, s, DpadButton.LEFT)
        drawTriangle(canvas, cx + offset, cy, s, DpadButton.RIGHT)
        canvas.drawCircle(cx, cy, s * 0.55f, glyphPaint)
    }

    // --- touch -------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTest(event.x, event.y) ?: return true
                dragAnchorX = event.rawX
                dragAnchorY = event.rawY
                dragging = false
                when (val slot = hit.slot) {
                    is Slot.Key -> press(slot)

                    Slot.Handle -> {
                        pressedSlot = slot
                        beginDrag()
                    }

                    Slot.Collapse, Slot.Bubble -> {
                        // Tap toggles, but a drag from the bubble still moves the pad --
                        // otherwise a collapsed pad would be stuck where it sits.
                        pressedSlot = slot
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragAnchorX)
                val dy = (event.rawY - dragAnchorY)

                if (dragging) {
                    onDragBy?.invoke(dx.toInt(), dy.toInt())
                    return true
                }

                when (pressedSlot) {
                    Slot.Handle -> onDragBy?.invoke(dx.toInt(), dy.toInt())

                    Slot.Bubble -> if (!config.locked &&
                        (abs(dx) > touchSlop || abs(dy) > touchSlop)
                    ) {
                        beginDrag()
                        onDragBy?.invoke(dx.toInt(), dy.toInt())
                    }

                    else -> {
                        // Sliding off a button releases it and presses whatever is under
                        // the finger now, so you can run a finger along the arrows.
                        val hit = hitTest(event.x, event.y)
                        val slot = hit?.slot
                        if (slot != pressedSlot) {
                            releasePress()
                            pressedSlot = null
                            if (slot is Slot.Key) press(slot)
                            invalidate()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasDragging = dragging
                val slot = pressedSlot
                endTouch()
                if (wasDragging) {
                    onDragEnd?.invoke()
                } else if (slot == Slot.Collapse || slot == Slot.Bubble) {
                    onToggleCollapsed?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasDragging = dragging
                endTouch()
                if (wasDragging) onDragEnd?.invoke()
                return true
            }
        }
        return true
    }

    private fun beginDrag() {
        if (config.locked) return
        dragging = true
        onDragStart?.invoke()
        haptic()
    }

    private fun press(slot: Slot.Key) {
        pressedSlot = slot
        pressedButton = slot.button
        pressDownTime = SystemClock.uptimeMillis()
        repeatCount = 0
        haptic()
        val delivered = send(slot.button, KeyEvent.ACTION_DOWN, 0)
        if (!delivered) onNotReady?.invoke()
        if (slot.button.repeatable) {
            postDelayed(repeatRunnable, config.repeatDelayMs.toLong())
        }
        invalidate()
    }

    private fun releasePress() {
        val button = pressedButton ?: return
        removeCallbacks(repeatRunnable)
        send(button, KeyEvent.ACTION_UP, 0)
        pressedButton = null
        repeatCount = 0
    }

    private fun endTouch() {
        releasePress()
        pressedSlot = null
        dragging = false
        invalidate()
    }

    private fun cancelPress() {
        removeCallbacks(repeatRunnable)
        pressedButton = null
        pressedSlot = null
        dragging = false
    }

    private fun hitTest(x: Float, y: Float): Placed? =
        cells.firstOrNull { it.rect.contains(x, y) }

    /** downTime is generated once per press and reused for every event of that press. */
    private fun send(button: DpadButton, action: Int, repeat: Int): Boolean {
        val keyCode = config.keyCodes[button] ?: button.defaultKeyCode
        return onKey?.invoke(keyCode, action, repeat, pressDownTime) ?: false
    }

    private fun haptic() {
        if (!config.haptics) return
        performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    override fun onDetachedFromWindow() {
        cancelPress()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    )
}
