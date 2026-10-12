package com.merotype.keyboard.views

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageButton
import androidx.preference.PreferenceManager
import com.merotype.keyboard.services.MeroTypeIME

class BackspaceKeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    private var initialX = 0f
    private var isWordEraseTriggered = false
    private var wordEraseThresholdPx = 30f

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val dp = prefs.getInt("pref_backspace_erase_dp", 30)
        wordEraseThresholdPx = dp * context.resources.displayMetrics.density
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
                initialX = event.x
                isWordEraseTriggered = false
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - initialX
                if (deltaX < -wordEraseThresholdPx && !isWordEraseTriggered) {
                    isWordEraseTriggered = true
                    // Trigger word erase on the active IME service
                    (findImeService() ?: (context as? MeroTypeIME))?.performWordBackspace()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isWordEraseTriggered) {
                    // Standard single character delete
                    (findImeService() ?: (context as? MeroTypeIME))?.performSingleBackspace()
                }
                isWordEraseTriggered = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
