package com.merotype.keyboard.views

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GestureEvaluatorTest {

    private lateinit var evaluator: GestureEvaluator

    @Before
    fun setUp() {
        // touchSlop = 8f, wordEraseThreshold = 30f, scrollHoldDelayMs = 150L
        evaluator = GestureEvaluator(
            touchSlop = 8f,
            wordEraseThreshold = 30f,
            scrollHoldDelayMs = 150L
        )
    }

    @Test
    fun backspaceDrag_belowThreshold_returnsSingleDelete() {
        val action = evaluator.evaluateBackspaceDrag(initialX = 100f, currentX = 85f) // deltaX = -15f
        assertEquals(GestureEvaluator.GestureAction.SINGLE_DELETE, action)
    }

    @Test
    fun backspaceDrag_pastThreshold_returnsWordErase() {
        val action = evaluator.evaluateBackspaceDrag(initialX = 100f, currentX = 60f) // deltaX = -40f
        assertEquals(GestureEvaluator.GestureAction.WORD_ERASE, action)
    }

    @Test
    fun spacebarGesture_horizontal_returnsCursorMovement() {
        val action = evaluator.evaluateSpacebarGesture(
            deltaX = 25f,
            deltaY = 4f,
            elapsedTimeMs = 50L
        )
        assertEquals(GestureEvaluator.GestureAction.HORIZONTAL_CURSOR, action)
    }

    @Test
    fun spacebarGesture_verticalScroll_afterHoldDelay() {
        val action = evaluator.evaluateSpacebarGesture(
            deltaX = 3f,
            deltaY = 25f,
            elapsedTimeMs = 200L
        )
        assertEquals(GestureEvaluator.GestureAction.VERTICAL_SCROLL, action)
    }

    @Test
    fun spacebarGesture_tooSmall_returnsNone() {
        val action = evaluator.evaluateSpacebarGesture(
            deltaX = 2f,
            deltaY = 3f,
            elapsedTimeMs = 50L
        )
        assertEquals(GestureEvaluator.GestureAction.NONE, action)
    }
}
