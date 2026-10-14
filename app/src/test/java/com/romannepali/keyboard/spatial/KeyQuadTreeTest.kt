package com.romannepali.keyboard.spatial

import android.graphics.RectF
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KeyQuadTreeTest {

    private lateinit var quadTree: KeyQuadTree
    private lateinit var dummyView: View

    @Before
    fun setUp() {
        quadTree = KeyQuadTree()
        dummyView = View(RuntimeEnvironment.getApplication())
    }

    @Test
    fun buildAndFindExactKeys() {
        val keys = listOf(
            KeySpatialTarget("q", dummyView, RectF(0f, 0f, 100f, 100f)),
            KeySpatialTarget("w", dummyView, RectF(100f, 0f, 200f, 100f)),
            KeySpatialTarget("a", dummyView, RectF(0f, 100f, 100f, 200f)),
            KeySpatialTarget("s", dummyView, RectF(100f, 100f, 200f, 200f))
        )
        quadTree.build(keys, 200f, 200f)

        val hitQ = quadTree.findKey(50f, 50f)
        assertNotNull(hitQ)
        assertEquals("q", hitQ?.id)

        val hitW = quadTree.findKey(150f, 50f)
        assertNotNull(hitW)
        assertEquals("w", hitW?.id)

        val hitA = quadTree.findKey(50f, 150f)
        assertNotNull(hitA)
        assertEquals("a", hitA?.id)

        val hitS = quadTree.findKey(150f, 150f)
        assertNotNull(hitS)
        assertEquals("s", hitS?.id)
    }

    @Test
    fun proximityHitTest_resolvesNearSeam() {
        val keys = listOf(
            KeySpatialTarget("key1", dummyView, RectF(10f, 10f, 50f, 50f)),
            KeySpatialTarget("key2", dummyView, RectF(60f, 10f, 100f, 50f))
        )
        quadTree.build(keys, 120f, 100f)

        // Point at (52f, 30f) is in the 10px gap between keys, closer to key1
        val hit = quadTree.findKey(52f, 30f, proximityTolerancePx = 20f)
        assertNotNull(hit)
        assertEquals("key1", hit?.id)
    }

    @Test
    fun outOfBounds_returnsNull() {
        val keys = listOf(
            KeySpatialTarget("key1", dummyView, RectF(0f, 0f, 50f, 50f))
        )
        quadTree.build(keys, 100f, 100f)

        val hitFar = quadTree.findKey(500f, 500f, proximityTolerancePx = 20f)
        assertNull(hitFar)
    }

    @Test
    fun emptyTree_returnsNull() {
        quadTree.build(emptyList(), 100f, 100f)
        assertNull(quadTree.findKey(10f, 10f))
    }
}
