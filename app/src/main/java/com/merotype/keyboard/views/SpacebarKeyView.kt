package com.merotype.keyboard.views

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton
import androidx.preference.PreferenceManager
import com.merotype.keyboard.services.MeroTypeIME

class SpacebarKeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var scrollHoldDelayMs = 150L

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        scrollHoldDelayMs = prefs.getInt("pref_space_scroll_delay_ms", 150).toLong()
    }

    private fun findImeService(): MeroTypeIME? {
        var ctx: Context? = context
        while (ctx != null) {
            if (ctx is MeroTypeIME) return ctx
            if (ctx is ContextWrapper) {
                ctx = ctx.baseContext
            } else {
                break
            }
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                startTime = System.currentTimeMillis()
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - startX
                val deltaY = event.y - startY
                val elapsedTime = System.currentTimeMillis() - startTime

                val evaluator = GestureEvaluator(8f, 30f, scrollHoldDelayMs)
                val ime = findImeService() ?: (context as? MeroTypeIME)
                when (evaluator.evaluateSpacebarGesture(deltaX, deltaY, elapsedTime)) {
                    GestureEvaluator.GestureAction.HORIZONTAL_CURSOR -> {
                        val direction = if (deltaX > 0) 1 else -1
                        ime?.moveCursor(direction)
                        startX = event.x // Reset anchor for continuous tracking
                    }
                    GestureEvaluator.GestureAction.VERTICAL_SCROLL -> {
                        val scrollAmount = if (deltaY > 0) 1 else -1
                        ime?.performScroll(scrollAmount)
                        startY = event.y
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP -> {
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < 150) {
                    val ime = findImeService() ?: (context as? MeroTypeIME)
                    ime?.commitSpace()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
