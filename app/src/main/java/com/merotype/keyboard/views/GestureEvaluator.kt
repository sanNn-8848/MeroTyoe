package com.merotype.keyboard.views

import kotlin.math.abs

class GestureEvaluator(
    private val touchSlop: Float,
    private val wordEraseThreshold: Float,
    private val scrollHoldDelayMs: Long
) {
    enum class GestureAction {
        NONE,
        SINGLE_DELETE,
        WORD_ERASE,
        HORIZONTAL_CURSOR,
        VERTICAL_SCROLL
    }

    fun evaluateBackspaceDrag(initialX: Float, currentX: Float): GestureAction {
        val deltaX = currentX - initialX
        return if (deltaX < -wordEraseThreshold) {
            GestureAction.WORD_ERASE
        } else {
            GestureAction.SINGLE_DELETE
        }
    }

    fun evaluateSpacebarGesture(
        deltaX: Float,
        deltaY: Float,
        elapsedTimeMs: Long
    ): GestureAction {
        val absX = abs(deltaX)
        val absY = abs(deltaY)

        return when {
            absX > touchSlop && absX > absY -> {
                GestureAction.HORIZONTAL_CURSOR
            }
            absY > touchSlop && elapsedTimeMs >= scrollHoldDelayMs && absY > absX -> {
                GestureAction.VERTICAL_SCROLL
            }
            else -> GestureAction.NONE
        }
    }
}
