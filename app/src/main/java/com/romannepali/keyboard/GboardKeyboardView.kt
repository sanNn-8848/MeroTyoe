package com.romannepali.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.media.SoundPool
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.spatial.KeyQuadTree
import com.romannepali.keyboard.spatial.KeySpatialTarget

class GboardKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // Functional key tokens (never sent as typed text).
    private val SHIFT = "KEY_SHIFT"
    private val BACKSPACE = "KEY_BACKSPACE"
    private val SPACE = "KEY_SPACE"
    private val SYMBOLS = "KEY_SYMBOLS"
    private val DOT = "KEY_DOT"
    private val ENTER = "KEY_ENTER"
    private val COMMA = "KEY_COMMA"

    private data class KeyDef(val label: String?, val weight: Float)

    private val lettersRows: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef("q", 1f), KeyDef("w", 1f), KeyDef("e", 1f), KeyDef("r", 1f),
            KeyDef("t", 1f), KeyDef("y", 1f), KeyDef("u", 1f), KeyDef("i", 1f),
            KeyDef("o", 1f), KeyDef("p", 1f)
        ),
        listOf(
            KeyDef(null, 0.5f), KeyDef("a", 1f), KeyDef("s", 1f), KeyDef("d", 1f),
            KeyDef("f", 1f), KeyDef("g", 1f), KeyDef("h", 1f), KeyDef("j", 1f),
            KeyDef("k", 1f), KeyDef("l", 1f), KeyDef(null, 0.5f)
        ),
        listOf(
            KeyDef(SHIFT, 1.5f), KeyDef("z", 1f), KeyDef("x", 1f), KeyDef("c", 1f),
            KeyDef("v", 1f), KeyDef("b", 1f), KeyDef("n", 1f), KeyDef("m", 1f),
            KeyDef(BACKSPACE, 1.5f)
        ),
        listOf(
            KeyDef(SYMBOLS, 1.5f), KeyDef(COMMA, 1f), KeyDef(SPACE, 5.0f),
            KeyDef(DOT, 1f), KeyDef(ENTER, 1.5f)
        )
    )

    private val symbolRows: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef("1", 1f), KeyDef("2", 1f), KeyDef("3", 1f), KeyDef("4", 1f),
            KeyDef("5", 1f), KeyDef("6", 1f), KeyDef("7", 1f), KeyDef("8", 1f),
            KeyDef("9", 1f), KeyDef("0", 1f)
        ),
        listOf(
            KeyDef("!", 1f), KeyDef("@", 1f), KeyDef("#", 1f), KeyDef("$", 1f),
            KeyDef("%", 1f), KeyDef("^", 1f), KeyDef("&", 1f), KeyDef("*", 1f),
            KeyDef("(", 1f), KeyDef(")", 1f)
        ),
        listOf(
            KeyDef("-", 1f), KeyDef("_", 1f), KeyDef("=", 1f), KeyDef("+", 1f),
            KeyDef("[", 1f), KeyDef("]", 1f), KeyDef("{", 1f), KeyDef("}", 1f),
            KeyDef("\\", 1f), KeyDef("|", 1f)
        ),
        listOf(
            KeyDef(SYMBOLS, 1.5f), KeyDef(COMMA, 1f), KeyDef(SPACE, 5.0f),
            KeyDef(DOT, 1f), KeyDef(ENTER, 1.5f)
        )
    )

    /** Long-press secondary characters shown top-right and inserted on a 250ms hold. */
    private val ALT_SYMBOLS = mapOf(
        'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
        'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
        'a' to "@", 's' to "#", 'd' to "–", 'f' to "%", 'g' to "&",
        'h' to "'", 'j' to "\"", 'k' to "(", 'l' to ")",
        'z' to "!", 'x' to "?", 'c' to "/", 'v' to ":", 'b' to ";",
        'n' to "€", 'm' to "-"
    )

    private class KeyParts(
        val container: FrameLayout,
        val main: TextView,
        val tag: TextView?,
        val popup: TextView
    )

    // Remember letter keys so we can re-render upper/lower case without a full rebuild.
    private val letterKeys = mutableListOf<Pair<KeyParts, String>>()

    // Every key knows its row so a pressed key's popup can be raised above neighbours.
    private val keyRows = HashMap<View, View>()

    // Spatial Index QuadTree for low-latency multi-touch key hit-testing
    private val quadTree = KeyQuadTree()
    private val spatialTargets = ArrayList<KeySpatialTarget>()

    private class ActivePointer(
        val pointerId: Int,
        val target: KeySpatialTarget,
        val initialX: Float,
        val initialY: Float,
        val downTime: Long,
        var longPressRunnable: Runnable? = null,
        var repeatRunnable: Runnable? = null,
        var longPressFired: Boolean = false,
        var wordsDeletedByDrag: Int = 0,
        var isDraggingLeft: Boolean = false
    )

    private val activePointers = SparseArray<ActivePointer>()

    // Track every key's delayed touch work so a rebuild/detach can cancel it
    // before the dying views are discarded.
    private val pendingKeyRunnables = mutableListOf<Pair<View, Runnable>>()

    // Number row sits slightly shorter than letter rows (Gboard style).
    private val NUMBER_ROW_WEIGHT = 0.92f
    // Landscape: slimmer number row so the compacted keyboard leaves more room.
    private val NUMBER_ROW_WEIGHT_LANDSCAPE = 0.70f

    // ---- Production layout spec (dp) ----
    private val SIDE_MARGIN_MIN = 4
    private val SIDE_MARGIN_DEFAULT = 6
    private val KEY_MIN = 30
    private val KEY_MAX = 55
    private val KEY_EDGE = 2          // slot gap math for keyboardSideMargin(); keys themselves paint a 2dp/side <inset>
    private val LETTER_ROW = 44
    private val LETTER_ROW_LANDSCAPE = 34

    // User keyboard-size slider: 0..100 -> 0.80x .. 1.25x (50 normal = 1.0x).
    private val SIZE_MIN = 0.80f
    private val SIZE_SPAN = 0.45f

    private fun sizeFactor(): Float =
        SIZE_MIN + Prefs.keyboardSize(context) / 100f * SIZE_SPAN
    private val CHIN = 20             // transparent safety chin for nav gestures
    private val CHIN_LANDSCAPE = 12   // landscape: reclaim height from the chin

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun numberRowWeight(): Float =
        if (isLandscape()) NUMBER_ROW_WEIGHT_LANDSCAPE else NUMBER_ROW_WEIGHT

    private fun chinPx(): Int = dp(if (isLandscape()) CHIN_LANDSCAPE else CHIN)

    // Tactile spec: 250ms long-press, tap vs 40ms hold-and-repeat for backspace.
    private val LONG_PRESS_TIMEOUT = 250L
    /** Comma -> Emoji is a deliberate gesture: hold well past the repeat
     *  threshold so typing a normal comma never triggers it. */
    private val EMOJI_HOLD_TIMEOUT_MS = 600L
    private val REPEAT_INTERVAL = 40L

    // Triple-pitch audio: letter (high), space (mid), backspace/enter (low).
    private val SOUND_LETTER = 0
    private val SOUND_SPACE = 1
    private val SOUND_DELETE = 2

    var isShifted = false
        private set
    private var isShiftLocked = false
    private var lastShiftTapAt = 0L

    private var isSymbols = false
    private var darkTheme = true

    private var lastAppliedSize = Prefs.keyboardSize(context)

    private val hapticManager = HapticFeedbackManager(context)

    private var soundPool: SoundPool? = null
    private var soundLetter = 0
    private var soundSpace = 0
    private var soundDelete = 0

    private val readySounds = mutableSetOf<Int>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        isMotionEventSplittingEnabled = true
        darkTheme = Prefs.darkTheme(context)
        buildKeyboard()
    }

    var onKeyPressed: ((String) -> Unit)? = null

    /** Long-press events the IME may want to act on (e.g. the comma key). */
    var onEmojiRequested: (() -> Unit)? = null
    private var commaKeyHint: Button? = null
    private var emojiModeActive = false
    var onKeyLongPressed: ((String) -> Unit)? = null

    // ------------------------------------------------------------------
    // Sound: 3 short synthesized clicks (high / mid / low pitch).
    // ------------------------------------------------------------------

    private fun ensureSound() {
        if (soundPool != null) return

        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .build()

        pool.setOnLoadCompleteListener { _, sampleId, status ->
            post {
                if (soundPool !== pool) return@post
                if (status == 0) {
                    readySounds.add(sampleId)
                } else {
                    readySounds.remove(sampleId)
                }
            }
        }

        soundPool = pool
        soundLetter = pool.load(context, R.raw.key_click_letter, 1)
        soundSpace = pool.load(context, R.raw.key_click_space, 1)
        soundDelete = pool.load(context, R.raw.key_click_delete, 1)
    }

    private fun playSound(kind: Int) {
        if (!Prefs.sound(context)) return
        ensureSound()
        val pool = soundPool ?: return
        val stream = when (kind) {
            SOUND_SPACE -> soundSpace
            SOUND_DELETE -> soundDelete
            else -> soundLetter
        }
        if (stream == 0 || stream !in readySounds) return
        // Original click: single fixed pitch/volume, matching v1.1.16 and earlier.
        pool.play(stream, 0.8f, 0.8f, 1, 0, 1f)
    }

    // Haptics: fine-tuned tactile feedback tailored for each key type.
    private fun haptic(view: View, type: HapticFeedbackManager.FeedbackType) {
        hapticManager.performKeyHaptic(view, type)
    }

    private fun hapticStrong(view: View) {
        haptic(view, HapticFeedbackManager.FeedbackType.KEY_ACTION)
    }

    // Quick press-down squash; the release springs back over the top (Gboard feel).
    private fun pressScaleOn(view: View) {
        if (view.width == 0) return
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.animate().cancel()
        view.animate().scaleX(0.94f).scaleY(0.94f)
            .setDuration(60)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun pressScaleOff(view: View) {
        if (view.width == 0) return
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.animate().cancel()
        view.animate().scaleX(1f).scaleY(1f)
            .setDuration(250)
            .setInterpolator(OvershootInterpolator(1.8f))
            .start()
    }

    private fun postKeyDelayed(
        key: View,
        runnable: Runnable,
        delay: Long
    ) {
        pendingKeyRunnables.add(key to runnable)
        key.postDelayed(runnable, delay)
    }

    private fun cancelKeyRunnable(
        key: View,
        runnable: Runnable
    ) {
        key.removeCallbacks(runnable)
        pendingKeyRunnables.removeAll { it.first === key && it.second === runnable }
    }

    private fun cancelAllKeyWork() {
        pendingKeyRunnables.forEach { (key, runnable) ->
            key.removeCallbacks(runnable)
        }
        pendingKeyRunnables.clear()

        // Cancel popup/key animations as part of the same lifecycle boundary.
        keyRows.keys.forEach { key ->
            key.animate().cancel()
        }
    }

    /**
     * Cleans up all active touch interactions, key-press visual states,
     * animations, and floating popups during configuration changes or focus shifts.
     */
    fun clearActiveStates() {
        for (i in 0 until activePointers.size()) {
            val active = activePointers.valueAt(i)
            active.longPressRunnable?.let { cancelKeyRunnable(active.target.view, it) }
            active.repeatRunnable?.let { cancelKeyRunnable(active.target.view, it) }
        }
        activePointers.clear()
        cancelAllKeyWork()
        keyRows.keys.forEach { key ->
            key.isPressed = false
            key.translationZ = 0f
            key.animate().cancel()
            key.scaleX = 1f
            key.scaleY = 1f
        }
        keyRows.values.forEach { row ->
            row.translationZ = 0f
        }
        letterKeys.forEach { (parts, _) ->
            parts.popup.animate().cancel()
            parts.popup.visibility = View.GONE
            parts.popup.scaleX = 1f
            parts.popup.scaleY = 1f
            parts.popup.translationY = 0f
            parts.popup.alpha = 1f
        }
    }

    // ------------------------------------------------------------------
    // Keyboard construction
    // ------------------------------------------------------------------

    private fun buildKeyboard() {
        clearActiveStates()
        cancelAllKeyWork()
        removeAllViews()
        letterKeys.clear()
        keyRows.clear()
        spatialTargets.clear()
        quadTree.clear()
        shiftKey = null
        enterKey = null
        setBackgroundColor(themeBackground())
        // Outer horizontal inset computed from the real screen width so a weight-1
        // key lands inside [KEY_MIN, KEY_MAX] dp; any slack becomes the side margin.
        val sideMargin = keyboardSideMargin()
        setPadding(sideMargin, 0, sideMargin, 0)

        val rows = if (isSymbols) symbolRows else lettersRows

        // Always-on number row (main layout, matching the symbol page's digits).
        if (!isSymbols) {
            val digitRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                isMotionEventSplittingEnabled = true
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    0,
                    numberRowWeight()
                )
            }
            "1234567890".forEach { c ->
                addKey(
                    digitRow,
                    c.toString(),
                    1f,
                    isLetter = false,
                    onPress = { onKeyPressed?.invoke(c.toString()) }
                )
            }
            addView(digitRow)
        }

        rows.forEach { rowEntries ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                isMotionEventSplittingEnabled = true
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            rowEntries.forEach { entry ->
                if (entry.label != null) {
                    addEntry(row, entry)
                } else {
                    addSpacer(row, entry.weight)
                }
            }

            addView(row)
        }

        // Transparent safety chin isolates the keys from system nav gestures.
        addView(
            View(context),
            LayoutParams(LayoutParams.MATCH_PARENT, chinPx())
        )

        refreshKeyLabels()

        // Height is resolved by onMeasure from the window/wrapper constraints,
        // so no explicit layoutParams height is set here (responsive sizing).
    }

    private fun keyboardHeightPx(): Int {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Landscape layout policy: 34dp letter rows (portrait keeps LETTER_ROW).
        // At 34dp: 34*4.70 + 12 chin = ~172dp keyboard body -> total ~222dp with
        // the compacted landscape carousel/suggestion strips (layout-land xml).
        val letterRowDp = if (landscape) LETTER_ROW_LANDSCAPE else LETTER_ROW
        val letterRow = dp(letterRowDp) * sizeFactor()
        val withNumber = !isSymbols
        val totalWeight = 4f + if (withNumber) numberRowWeight() else 0f
        return (letterRow * totalWeight).toInt() + chinPx()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation must not tear down the key hierarchy: keeping it alive lets
        // shift/symbol state, the current word, and suggestions survive. Just
        // re-apply the side padding for the new width; onMeasure re-fits the
        // height to the new orientation on the next layout pass.
        post {
            setPadding(keyboardSideMargin(), 0, keyboardSideMargin(), 0)
            requestLayout()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        cancelAllKeyWork()

        soundPool?.release()
        soundPool = null

        readySounds.clear()

        soundLetter = 0
        soundSpace = 0
        soundDelete = 0
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        if (widthSize > 0) {
            val desiredMargin = keyboardSideMargin(widthSize)
            if (paddingLeft != desiredMargin || paddingRight != desiredMargin) {
                setPadding(desiredMargin, 0, desiredMargin, 0)
            }
        }
        val preferred = keyboardHeightPx()
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val targetHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(preferred, heightSize)
            else -> preferred
        }
        val heightSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, heightSpec)
        setMeasuredDimension(measuredWidth, targetHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        rebuildSpatialIndex()
    }

    private fun rebuildSpatialIndex() {
        if (width <= 0 || height <= 0 || spatialTargets.isEmpty()) return
        for (i in 0 until spatialTargets.size) {
            val target = spatialTargets[i]
            val parentRow = keyRows[target.view] ?: target.view.parent as? View
            val rowLeft = parentRow?.left ?: 0
            val rowTop = parentRow?.top ?: 0
            val left = (rowLeft + target.view.left).toFloat()
            val top = (rowTop + target.view.top).toFloat()
            val right = (rowLeft + target.view.right).toFloat()
            val bottom = (rowTop + target.view.bottom).toFloat()
            target.bounds.set(left, top, right, bottom)
        }
        quadTree.build(spatialTargets, width.toFloat(), height.toFloat())
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Intercept all touches for spatial QuadTree multi-pointer processing
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIdx = event.actionIndex
                val pointerId = event.getPointerId(actionIdx)
                val x = event.getX(actionIdx)
                val y = event.getY(actionIdx)
                handlePointerDown(pointerId, x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handlePointerMove(event)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val actionIdx = event.actionIndex
                val pointerId = event.getPointerId(actionIdx)
                handlePointerUp(pointerId)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleAllPointersUp()
                return true
            }
        }
        return true
    }

    private fun handlePointerDown(pointerId: Int, x: Float, y: Float) {
        val target = quadTree.findKey(x, y) ?: return

        activePointers[pointerId]?.let { cleanupActivePointer(it) }

        val active = ActivePointer(
            pointerId = pointerId,
            target = target,
            initialX = x,
            initialY = y,
            downTime = SystemClock.uptimeMillis()
        )
        activePointers.put(pointerId, active)

        // 1. Commit action FIRST for immediate response during rapid typing
        target.onDown()

        // 2. Haptic and sound feedback
        val hapticType = when {
            target.isBackspace -> HapticFeedbackManager.FeedbackType.KEY_DELETE
            target.isSpace -> HapticFeedbackManager.FeedbackType.KEY_SPACE
            target.isAction -> HapticFeedbackManager.FeedbackType.KEY_ACTION
            else -> HapticFeedbackManager.FeedbackType.KEY_LETTER
        }
        haptic(target.view, hapticType)
        playSound(target.soundKind)

        // 3. Visual pressed state & popup preview
        target.view.isPressed = true
        pressScaleOn(target.view)
        if (target.hasPopup && target.popupView is TextView) {
            val popupText = (target.view as? FrameLayout)?.let {
                (it.getChildAt(0) as? TextView)?.text?.toString()
            } ?: target.label ?: ""
            showPopup(target.popupView, popupText, target.view)
        }

        // 4. Long press / repeat scheduling
        if (target.onAltPress != null) {
            val altRunnable = Runnable {
                active.longPressFired = true
                hapticStrong(target.view)
                if (target.hasPopup && target.popupView is TextView && target.altLabel != null) {
                    target.popupView.text = target.altLabel
                }
                target.onAltPress.invoke()
            }
            active.longPressRunnable = altRunnable
            postKeyDelayed(target.view, altRunnable, target.holdTimeoutMs)
        } else if (target.onLongPress != null || target.repeat) {
            val repeatRunnable = object : Runnable {
                override fun run() {
                    if (active.longPressFired && target.repeat) {
                        playSound(target.soundKind)
                        target.onDown()
                        val repType = if (target.soundKind == SOUND_DELETE) {
                            HapticFeedbackManager.FeedbackType.KEY_DELETE
                        } else {
                            HapticFeedbackManager.FeedbackType.KEY_ACTION
                        }
                        haptic(target.view, repType)
                        postKeyDelayed(target.view, this, target.repeatIntervalMs)
                    }
                }
            }
            active.repeatRunnable = repeatRunnable

            val longPressRunnable = Runnable {
                active.longPressFired = true
                hapticStrong(target.view)
                if (target.onLongPress != null) {
                    target.onLongPress.invoke()
                } else if (target.repeat) {
                    target.onDown()
                    postKeyDelayed(target.view, repeatRunnable, target.repeatIntervalMs)
                }
            }
            active.longPressRunnable = longPressRunnable
            postKeyDelayed(target.view, longPressRunnable, target.holdTimeoutMs)
        }
    }

    private fun handlePointerMove(event: MotionEvent) {
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            val active = activePointers.get(pointerId) ?: continue
            val x = event.getX(i)
            val y = event.getY(i)
            val deltaX = x - active.initialX
            val deltaY = y - active.initialY

            if (active.target.isBackspace && Prefs.swipeDeleteWord(context)) {
                if (deltaX < -dp(10) && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 0.7f) {
                    active.isDraggingLeft = true
                    active.longPressRunnable?.let { cancelKeyRunnable(active.target.view, it) }
                    active.repeatRunnable?.let { cancelKeyRunnable(active.target.view, it) }

                    val maxShift = -dp(42).toFloat()
                    val shift = (deltaX * 0.45f).coerceIn(maxShift, 0f)
                    active.target.view.translationX = shift

                    val dragDist = -deltaX
                    val threshold1 = dp(32).toFloat()
                    val stepSize = dp(38).toFloat()
                    val targetWords = if (dragDist >= threshold1) {
                        1 + ((dragDist - threshold1) / stepSize).toInt()
                    } else 0

                    while (active.wordsDeletedByDrag < targetWords) {
                        active.wordsDeletedByDrag++
                        onKeyPressed?.invoke("DELETE_WORD")
                        haptic(active.target.view, HapticFeedbackManager.FeedbackType.KEY_DELETE)
                        playSound(SOUND_DELETE)
                    }
                    continue
                } else if (active.isDraggingLeft) {
                    val maxShift = -dp(42).toFloat()
                    active.target.view.translationX = (deltaX * 0.45f).coerceIn(maxShift, 0f)
                    continue
                }
            }

            val slop = dp(14).toFloat()
            val inside = x >= active.target.bounds.left - slop &&
                    x <= active.target.bounds.right + slop &&
                    y >= active.target.bounds.top - slop &&
                    y <= active.target.bounds.bottom + slop

            if (!inside) {
                active.target.view.isPressed = false
                active.longPressRunnable?.let { cancelKeyRunnable(active.target.view, it) }
                if (active.target.hasPopup && active.target.popupView is TextView) {
                    hidePopup(active.target.popupView, active.target.view)
                }
            } else {
                active.target.view.isPressed = true
            }
        }
    }

    private fun cleanupActivePointer(active: ActivePointer) {
        active.longPressRunnable?.let { cancelKeyRunnable(active.target.view, it) }
        active.repeatRunnable?.let { cancelKeyRunnable(active.target.view, it) }

        active.target.view.isPressed = false
        pressScaleOff(active.target.view)
        if (active.target.hasPopup && active.target.popupView is TextView) {
            hidePopup(active.target.popupView, active.target.view)
        }

        if (active.target.isBackspace && active.isDraggingLeft) {
            active.target.view.animate().translationX(0f).setDuration(120).start()
            active.isDraggingLeft = false
            active.wordsDeletedByDrag = 0
        }

        active.target.onUp?.invoke()
    }

    private fun handlePointerUp(pointerId: Int) {
        val active = activePointers.get(pointerId) ?: return
        cleanupActivePointer(active)
        activePointers.remove(pointerId)
    }

    private fun handleAllPointersUp() {
        for (i in 0 until activePointers.size()) {
            val active = activePointers.valueAt(i)
            cleanupActivePointer(active)
        }
        activePointers.clear()
        clearActiveStates()
    }

    private fun keyboardSideMargin(containerWidth: Int = 0): Int {
        val screenW = if (containerWidth > 0) containerWidth else resources.displayMetrics.widthPixels
        val defaultMargin = dp(SIDE_MARGIN_DEFAULT)
        val slots = 10 // all rows carry exactly 10 slots
        val edgePx = dp(KEY_EDGE) * 2
        val available = screenW - 2 * defaultMargin
        if (available <= 0) return dp(SIDE_MARGIN_MIN)
        val naturalSlot = available / slots
        val visible = naturalSlot - edgePx
        val minUnit = (dp(KEY_MIN) * sizeFactor()).toInt()
        val maxUnit = (dp(KEY_MAX) * sizeFactor()).toInt()
        val unit = visible.coerceIn(minUnit, maxUnit)
        if (unit == visible) return defaultMargin
        val wanted = (screenW - slots * (unit + edgePx)) / 2
        return wanted.coerceIn(dp(SIDE_MARGIN_MIN), (screenW * 0.25f).toInt())
    }

    private fun addEntry(row: LinearLayout, entry: KeyDef) {
        when (entry.label) {
            SHIFT -> addActionKey(
                row,
                entry.weight,
                iconRes = R.drawable.ic_shift,
                click = { toggleShift() }
            )
            BACKSPACE -> addActionKey(
                row,
                entry.weight,
                iconRes = R.drawable.ic_backspace,
                click = { onKeyPressed?.invoke("⌫") },
                repeat = true,
                strong = true,
                sound = SOUND_DELETE,
                isBackspace = true
            )
            SPACE -> addSpaceKey(row, entry.weight)
            SYMBOLS -> addActionKey(
                row,
                entry.weight,
                label = if (isSymbols) "ABC" else "?123",
                click = { toggleSymbols() }
            )
            COMMA -> {
                val commaKey = addActionKey(
                    row,
                    entry.weight,
                    label = ",",
                    click = { onKeyPressed?.invoke(",") },
                    onLongPress = {
                        // Undo the comma typed on touch-down, then hand the gesture
                        // to the IME (opens the Emoji search pane).
                        onKeyPressed?.invoke("⌫")
                        onEmojiRequested?.invoke()
                    },
                    // Longer than the repeat threshold so ordinary typing of a comma
                    // never opens the Emoji pane by accident.
                    holdTimeoutMs = EMOJI_HOLD_TIMEOUT_MS
                )
                commaKeyHint = commaKey as? Button
                styleCommaHint()
            }
            DOT -> addActionKey(
                row,
                entry.weight,
                label = ".",
                click = { onKeyPressed?.invoke(".") }
            )
            ENTER -> addActionKey(
                row,
                entry.weight,
                iconRes = R.drawable.ic_enter,
                tint = accentColor(),
                click = { onKeyPressed?.invoke("↵") },
                strong = true,
                sound = SOUND_DELETE
            ).also { enterKey = it }.also { applyEnterActionIcon() }

            else -> {
                val text = entry.label ?: return
                val isLetter = text.length == 1 && text[0] in 'a'..'z'
                val parts = addKey(
                    row,
                    labelFor(text),
                    entry.weight,
                    isLetter = true,
                    altTag = ALT_SYMBOLS[text[0]]?.toString(),
                    onAltPress = {
                        // The letter already committed on touch-down; replace it with
                        // the secondary character so a long-press inserts the tag.
                        ALT_SYMBOLS[text[0]]?.let { alt ->
                            onKeyPressed?.invoke("⌫")
                            onKeyPressed?.invoke(alt.toString())
                        }
                    },
                    onPress = {
                        // Any letter press breaks a shift double-tap window.
                        lastShiftTapAt = 0L
                        val upper = isShifted || isShiftLocked
                        val sent = if (isLetter && upper) text.uppercase() else text
                        onKeyPressed?.invoke(sent)
                        if (isLetter && upper && !isShiftLocked) {
                            isShifted = false
                            refreshKeyLabels()
                            styleShiftIcon()
                        }
                    }
                )
                if (isLetter) {
                    letterKeys.add(parts to text)
                }
            }
        }
    }

    private var shiftKey: View? = null

    private var enterKey: View? = null

    private var enterAction: Int = 0

    private fun addKey(
        row: LinearLayout,
        label: String,
        weight: Float,
        isLetter: Boolean,
        altTag: String? = null,
        onAltPress: (() -> Unit)? = null,
        onPress: () -> Unit
    ): KeyParts {
        val key = FrameLayout(context)
        key.clipChildren = false
        key.clipToPadding = false
        key.background = resources.getDrawable(
            if (darkTheme) R.drawable.key_bg else R.drawable.key_bg_light,
            null
        )
        keyRows[key] = row
        // Full-cell hit area: the views tile edge-to-edge so every pixel
        // between keys belongs to a key (no mistouch dead zones). The visible
        // 4dp gap is painted by the background's <inset>, which keeps the
        // slot-width math in keyboardSideMargin() honest.
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        )

        val main = TextView(context).apply {
            text = label
            textSize = (if (isLetter) 18f else 17f) * sizeFactor()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(letterTextColor())
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
            // 2dp downward offset so the top-right tag keeps the glyph visually centered.
            translationY = dp(1).toFloat()
        }
        key.addView(main, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val tag: TextView? = altTag?.let { alt ->
            TextView(context).apply {
                text = alt
                textSize = 10f * sizeFactor()
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(secondaryTextColor())
                alpha = 0.5f
                gravity = Gravity.CENTER
                setIncludeFontPadding(false)
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                )
                lp.setMargins(0, dp(3), dp(4), 0)
                key.addView(this, lp)
            }
        }

        // Press pop-up: 130% height cap rising ~8dp above the finger, 15ms fade-out.
        val popup = TextView(context).apply {
            text = label
            textSize = 20f * sizeFactor()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(letterTextColor())
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
            setBackgroundResource(R.drawable.key_bg_action)
            elevation = dp(10).toFloat()
            val lp = FrameLayout.LayoutParams(
                dp(46),
                dp(46),
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            )
            lp.bottomMargin = dp(4)
            key.addView(this, lp)
            pivotY = dp(46).toFloat()   // grows upward from its bottom edge
            visibility = View.GONE
            alpha = 0f
        }

        val target = KeySpatialTarget(
            id = label,
            view = key,
            isAction = false,
            isLetter = isLetter,
            label = label,
            altLabel = altTag,
            popupView = popup,
            onDown = onPress,
            onAltPress = onAltPress,
            holdTimeoutMs = LONG_PRESS_TIMEOUT,
            soundKind = SOUND_LETTER,
            hasPopup = true
        )
        spatialTargets.add(target)

        row.addView(key)
        return KeyParts(key, main, tag, popup)
    }

    private fun bumpRow(key: View) {
        keyRows[key]?.translationZ = dp(18).toFloat()
        key.translationZ = dp(30).toFloat()
    }

    private fun showPopup(popup: TextView, label: CharSequence, key: View) {
        popup.text = label
        popup.pivotX = popup.width / 2f
        popup.pivotY = popup.height.toFloat()
        popup.animate().cancel()
        popup.scaleX = 1f
        popup.scaleY = 1.15f
        popup.translationY = -dp(44).toFloat()
        popup.alpha = 1f
        bumpRow(key)
        popup.visibility = View.VISIBLE
    }

    private fun hidePopup(popup: TextView, key: View) {
        key.translationZ = 0f
        keyRows[key]?.translationZ = 0f
        if (popup.visibility != View.VISIBLE) return
        popup.animate().cancel()
        popup.visibility = View.GONE
        popup.scaleX = 1f
        popup.scaleY = 1f
        popup.translationY = 0f
        popup.alpha = 1f
    }

    private fun addActionKey(
        row: LinearLayout,
        weight: Float,
        label: String = "",
        iconRes: Int? = null,
        tint: Int? = null,
        click: () -> Unit,
        onLongPress: (() -> Unit)? = null,
        repeat: Boolean = false,
        holdTimeoutMs: Long = LONG_PRESS_TIMEOUT,
        strong: Boolean = false,
        sound: Int = SOUND_LETTER,
        hStart: Int = 0,
        hEnd: Int = 0,
        isBackspace: Boolean = false
    ): View {
        val key: View = if (iconRes != null) {
            val container = FrameLayout(context)
            container.background = resources.getDrawable(
                if (darkTheme) R.drawable.key_bg_action else R.drawable.key_bg_action_light,
                null
            )
            val icon = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dp(38),
                    dp(38),
                    Gravity.CENTER
                )
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(tint ?: iconColor())
                isClickable = false
                isFocusable = false
            }
            container.addView(icon)
            container
        } else {
            styleKey(Button(context), false).apply { text = label }
        }
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            setMargins(dp(hStart), 0, dp(hEnd), 0)
        }
        keyRows[key] = row

        if (iconRes == R.drawable.ic_shift) {
            shiftKey = key
            styleShiftIcon()
        }

        val target = KeySpatialTarget(
            id = if (iconRes != null) "action_$iconRes" else label,
            view = key,
            isAction = true,
            isLetter = false,
            label = label,
            onDown = click,
            onLongPress = onLongPress,
            repeat = repeat,
            repeatIntervalMs = REPEAT_INTERVAL,
            holdTimeoutMs = holdTimeoutMs,
            soundKind = sound,
            isBackspace = isBackspace,
            hasPopup = false
        )
        spatialTargets.add(target)

        row.addView(key)
        return key
    }

    private fun addSpaceKey(row: LinearLayout, weight: Float) {
        val key = styleKey(Button(context), false)
        key.text = context.getString(R.string.space_label)
        key.textSize = 13f * sizeFactor()
        key.setTextColor(spaceLabelColor())
        key.gravity = Gravity.CENTER
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            setMargins(0, 0, 0, 0)
        }
        keyRows[key] = row

        val target = KeySpatialTarget(
            id = "KEY_SPACE",
            view = key,
            isAction = true,
            isLetter = false,
            label = " ",
            onDown = {
                onKeyPressed?.invoke(" ")
            },
            onLongPress = {
                onKeyLongPressed?.invoke(" ")
            },
            holdTimeoutMs = LONG_PRESS_TIMEOUT,
            soundKind = SOUND_SPACE,
            isSpace = true,
            hasPopup = false
        )
        spatialTargets.add(target)

        row.addView(key)
    }

    private fun addSpacer(row: LinearLayout, weight: Float) {
        val spacer = View(context)
        spacer.visibility = View.INVISIBLE
        spacer.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        )
        row.addView(spacer)
    }

    private fun styleKey(button: Button, isLetter: Boolean): Button {
        button.isAllCaps = false
        button.setPadding(0, 0, 0, 0)
        button.minHeight = 0
        button.minWidth = 0
        button.stateListAnimator = null
        button.gravity = Gravity.CENTER
        button.setIncludeFontPadding(false)
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.textSize = (if (isLetter) 18f else 15f) * sizeFactor()
        button.setTextColor(letterTextColor())
        button.background = resources.getDrawable(
            if (isLetter) {
                if (darkTheme) R.drawable.key_bg else R.drawable.key_bg_light
            } else {
                if (darkTheme) R.drawable.key_bg_action else R.drawable.key_bg_action_light
            },
            null
        )
        return button
    }

    private fun applyIcon(key: View, iconRes: Int, tint: Int) {
        val icon = (key as? FrameLayout)?.getChildAt(0) as? ImageView
        if (icon != null) {
            icon.setImageResource(iconRes)
            icon.imageTintList = ColorStateList.valueOf(tint)
        }
    }

    private fun styleShiftIcon() {
        shiftKey?.let { key ->
            applyIcon(
                key,
                if (isShiftLocked) R.drawable.ic_shift_locked else R.drawable.ic_shift,
                if (isShifted || isShiftLocked) accentColor() else iconColor()
            )
        }
    }

    /**
     * Reflects the target editor's IME action on the Enter key:
     * SEARCH -> magnifier, GO/NEXT/SEND -> right arrow, everything else (incl.
     * DONE and multiline) keeps the plain return arrow. Only the icon changes;
     * the "↵" event pipeline is untouched (RomanNepaliIME decides behavior).
     */
    fun setEnterAction(action: Int) {
        enterAction = action
        applyEnterActionIcon()
    }

    private fun applyEnterActionIcon() {
        val iconRes = when (enterAction and android.view.inputmethod.EditorInfo.IME_MASK_ACTION) {
            android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
            android.view.inputmethod.EditorInfo.IME_ACTION_GO -> R.drawable.ic_go
            android.view.inputmethod.EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_go
            android.view.inputmethod.EditorInfo.IME_ACTION_SEND -> R.drawable.ic_go
            else -> R.drawable.ic_enter
        }
        enterKey?.let { key ->
            applyIcon(key, iconRes, accentColor())
        }
    }

    private fun labelFor(entry: String): String {
        return if ((isShifted || isShiftLocked) && entry.length == 1 && entry[0] in 'a'..'z') {
            entry.uppercase()
        } else {
            entry
        }
    }

    private fun refreshKeyLabels() {
        letterKeys.forEach { (parts, entry) ->
            val label = labelFor(entry)
            parts.main.text = label
            parts.popup.text = label
        }
        for (i in 0 until spatialTargets.size) {
            val target = spatialTargets[i]
            if (target.isLetter) {
                target.label = (target.view as? FrameLayout)?.let {
                    ((it.getChildAt(0) as? TextView)?.text?.toString())
                } ?: target.label
            }
        }
        styleShiftIcon()
    }

    private fun toggleShift() {
        val now = android.os.SystemClock.uptimeMillis()
        val isDoubleTap =
            now - lastShiftTapAt <= android.view.ViewConfiguration.getDoubleTapTimeout()
        lastShiftTapAt = now
        when {
            // Tapping while locked releases it (back to plain lowercase).
            isShiftLocked -> {
                isShiftLocked = false
                isShifted = false
            }
            // Second quick tap: caps lock, stays on across letters/symbols.
            isDoubleTap -> {
                isShiftLocked = true
                isShifted = true
            }
            else -> isShifted = !isShifted
        }
        refreshKeyLabels()
        styleShiftIcon()
        onShiftToggled?.invoke(isShifted)
    }

    /** External auto-capitalization (sentence start). Keeps the visual state in sync. */
    fun setShifted(shifted: Boolean) {
        isShifted = shifted
        refreshKeyLabels()
        styleShiftIcon()
    }

    /** Fired only when the USER taps the shift key (never for auto-capitalization). */
    var onShiftToggled: ((Boolean) -> Unit)? = null

    private fun toggleSymbols() {
        isSymbols = !isSymbols
        isShifted = false
        buildKeyboard()
    }

    fun rebuild() {
        post {
            buildKeyboard()
            requestLayout()
            invalidate()
        }
    }

    fun applyThemeIfChanged() {
        val preferred = Prefs.darkTheme(context)
        val size = Prefs.keyboardSize(context)
        if (preferred != darkTheme || size != lastAppliedSize) {
            darkTheme = preferred
            lastAppliedSize = size
            rebuild()
        }
    }

    private fun themeBackground(): Int =
        if (darkTheme) themedColor(R.color.keyboard_bg_dark) else themedColor(R.color.keyboard_bg_light)

    private fun letterTextColor(): Int =
        if (darkTheme) themedColor(R.color.letter_text_dark) else themedColor(R.color.letter_text_light)

    private fun secondaryTextColor(): Int =
        if (darkTheme) themedColor(R.color.secondary_text_dark) else themedColor(R.color.secondary_text_light)

    private fun iconColor(): Int =
        if (darkTheme) themedColor(R.color.icon_dark) else themedColor(R.color.icon_light)

    /** Marks the comma key as the Emoji entry point (hold to open) and makes the
     *  marker fully opaque while the Emoji pane is the active mode. Kept in the
     *  letter colour so it reads as part of the key, not as a red badge. */
    fun setEmojiMode(active: Boolean) {
        emojiModeActive = active
        styleCommaHint()
    }

    private fun styleCommaHint() {
        val hint = commaKeyHint ?: return
        val icon = resources.getDrawable(R.drawable.ic_emoji_hint, null)
        icon?.setTint(letterTextColor())
        icon?.alpha = if (emojiModeActive) 255 else 150
        hint.setCompoundDrawablesWithIntrinsicBounds(null, null, null, icon)
        hint.compoundDrawablePadding = dp(2)
    }

    private fun accentColor(): Int = themedColor(R.color.accent)

    private fun spaceLabelColor(): Int =
        if (darkTheme) themedColor(R.color.space_label_dark) else themedColor(R.color.space_label_light)

    private fun themedColor(resId: Int): Int = resources.getColor(resId, null)

    private fun dp(value: Int): Int =
        (resources.displayMetrics.density * value).toInt()
}