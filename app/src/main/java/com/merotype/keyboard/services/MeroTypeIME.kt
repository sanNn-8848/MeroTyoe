package com.merotype.keyboard.services

/**
 * Interface contract defining system-level keyboard actions triggered
 * by custom interactive views such as BackspaceKeyView and SpacebarKeyView.
 */
interface MeroTypeIME {
    fun performWordBackspace()
    fun performSingleBackspace()
    fun moveCursor(direction: Int)
    fun performScroll(amount: Int)
    fun commitSpace()
}
