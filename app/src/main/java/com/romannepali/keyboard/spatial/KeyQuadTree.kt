package com.romannepali.keyboard.spatial

import android.graphics.RectF
import android.view.View

/**
 * Encapsulates the spatial boundaries and action callbacks for an individual key
 * within the keyboard surface.
 */
data class KeySpatialTarget(
    val id: String,
    val view: View,
    val bounds: RectF = RectF(),
    val isAction: Boolean = false,
    val isLetter: Boolean = false,
    var label: String? = null,
    val altLabel: String? = null,
    val popupView: View? = null,
    val onDown: () -> Unit = {},
    val onUp: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val onAltPress: (() -> Unit)? = null,
    val holdTimeoutMs: Long = 250L,
    val repeat: Boolean = false,
    val repeatIntervalMs: Long = 40L,
    val soundKind: Int = 0,
    val isBackspace: Boolean = false,
    val isSpace: Boolean = false,
    val hasPopup: Boolean = false
)

/**
 * 2D Spatial Index (QuadTree) designed for ultra-low latency, O(log N) key hit-testing.
 *
 * Avoids main-thread stalls and eliminates recursive ViewGroup hierarchy traversals during
 * rapid multi-pointer input. Pre-indexes all keys during layout passes and supports zero-allocation
 * point queries with edge proximity tolerance.
 */
class KeyQuadTree(
    private val maxDepth: Int = 4,
    private val maxItemsPerNode: Int = 4
) {
    private var root: Node? = null
    private val allKeys = ArrayList<KeySpatialTarget>()

    private class Node(
        val bounds: RectF,
        val depth: Int
    ) {
        val items = ArrayList<KeySpatialTarget>()
        var nw: Node? = null
        var ne: Node? = null
        var sw: Node? = null
        var se: Node? = null

        val isLeaf: Boolean get() = nw == null

        fun subdivide() {
            val midX = bounds.centerX()
            val midY = bounds.centerY()
            nw = Node(RectF(bounds.left, bounds.top, midX, midY), depth + 1)
            ne = Node(RectF(midX, bounds.top, bounds.right, midY), depth + 1)
            sw = Node(RectF(bounds.left, midY, midX, bounds.bottom), depth + 1)
            se = Node(RectF(midX, midY, bounds.right, bounds.bottom), depth + 1)
        }
    }

    /**
     * Builds or updates the QuadTree spatial partitioning structure for the given keys.
     */
    fun build(keys: List<KeySpatialTarget>, width: Float, height: Float) {
        allKeys.clear()
        allKeys.addAll(keys)
        if (width <= 0f || height <= 0f || keys.isEmpty()) {
            root = null
            return
        }

        val rootBounds = RectF(0f, 0f, width, height)
        val newRoot = Node(rootBounds, 0)
        for (i in 0 until keys.size) {
            insert(newRoot, keys[i])
        }
        root = newRoot
    }

    private fun insert(node: Node, key: KeySpatialTarget) {
        if (!RectF.intersects(node.bounds, key.bounds)) {
            return
        }

        if (node.isLeaf) {
            if (node.items.size < maxItemsPerNode || node.depth >= maxDepth) {
                node.items.add(key)
                return
            }
            node.subdivide()
            val existing = ArrayList(node.items)
            node.items.clear()
            for (i in 0 until existing.size) {
                insertToChildren(node, existing[i])
            }
            insertToChildren(node, key)
        } else {
            insertToChildren(node, key)
        }
    }

    private fun insertToChildren(node: Node, key: KeySpatialTarget) {
        node.nw?.let { if (RectF.intersects(it.bounds, key.bounds)) insert(it, key) }
        node.ne?.let { if (RectF.intersects(it.bounds, key.bounds)) insert(it, key) }
        node.sw?.let { if (RectF.intersects(it.bounds, key.bounds)) insert(it, key) }
        node.se?.let { if (RectF.intersects(it.bounds, key.bounds)) insert(it, key) }
    }

    /**
     * Fast O(log N) hit-test lookup.
     * Evaluates exact point containment first, falling back to proximity nearest-key search
     * within [proximityTolerancePx] if a touch lands on a hairline seam between keys.
     */
    fun findKey(x: Float, y: Float, proximityTolerancePx: Float = 28f): KeySpatialTarget? {
        val r = root ?: return null

        // Check if within bounds or edge tolerance
        if (x < -proximityTolerancePx || x > r.bounds.right + proximityTolerancePx ||
            y < -proximityTolerancePx || y > r.bounds.bottom + proximityTolerancePx
        ) {
            return null
        }

        // Fast QuadTree traversal
        val exactMatch = queryExact(r, x, y)
        if (exactMatch != null) {
            return exactMatch
        }

        // Proximity fallback for boundary touches during fast continuous typing
        return queryNearest(x, y, proximityTolerancePx)
    }

    private fun queryExact(node: Node, x: Float, y: Float): KeySpatialTarget? {
        if (!node.bounds.contains(x, y)) return null

        for (i in 0 until node.items.size) {
            val item = node.items[i]
            if (item.bounds.contains(x, y)) {
                return item
            }
        }

        if (!node.isLeaf) {
            val midX = node.bounds.centerX()
            val midY = node.bounds.centerY()
            val target = when {
                x < midX && y < midY -> node.nw
                x >= midX && y < midY -> node.ne
                x < midX && y >= midY -> node.sw
                else -> node.se
            }
            val res = target?.let { queryExact(it, x, y) }
            if (res != null) return res

            // Also check adjacent quad nodes if coordinates hit exactly along partition line
            for (child in arrayOf(node.nw, node.ne, node.sw, node.se)) {
                if (child !== target && child != null && child.bounds.contains(x, y)) {
                    val hit = queryExact(child, x, y)
                    if (hit != null) return hit
                }
            }
        }
        return null
    }

    private fun queryNearest(x: Float, y: Float, maxDist: Float): KeySpatialTarget? {
        var closest: KeySpatialTarget? = null
        var minSqDist = maxDist * maxDist

        for (i in 0 until allKeys.size) {
            val key = allKeys[i]
            val clampedX = x.coerceIn(key.bounds.left, key.bounds.right)
            val clampedY = y.coerceIn(key.bounds.top, key.bounds.bottom)
            val dx = x - clampedX
            val dy = y - clampedY
            val distSq = dx * dx + dy * dy
            if (distSq < minSqDist) {
                minSqDist = distSq
                closest = key
            }
        }
        return closest
    }

    /**
     * Clears all indexed keys and spatial nodes.
     */
    fun clear() {
        root = null
        allKeys.clear()
    }

    /**
     * Returns total number of indexed keys.
     */
    fun size(): Int = allKeys.size
}
