package com.sohva.tv.ui.design.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The keys of a row picked up in a reorderable list (Discover's catalogue order, spec 50 FR-55;
 * Settings › Home, spec 02 HOME-FR-90): Up and Down move it one place, Page Up and Page Down by
 * [page] places, Home and End to either end, and held keys repeat. OK places it on its first
 * press only, never on a repeat. Left and Right do nothing, so focus cannot leave the list in the
 * middle of a move. Back belongs to the screen, which cancels the move.
 */
fun Modifier.moveKeys(page: () -> Int, onMove: (delta: Int, to: Int?) -> Unit, onPlace: () -> Unit): Modifier = onPreviewKeyEvent { k ->
    if (k.type != KeyEventType.KeyDown) return@onPreviewKeyEvent k.key == Key.DirectionCenter || k.key == Key.Enter
    when (k.key) {
        Key.DirectionUp -> onMove(-1, null)
        Key.DirectionDown -> onMove(1, null)
        Key.PageUp -> onMove(-page(), null)
        Key.PageDown -> onMove(page(), null)
        Key.MoveHome -> onMove(0, 0)
        Key.MoveEnd -> onMove(0, Int.MAX_VALUE)
        Key.DirectionLeft, Key.DirectionRight -> Unit
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (k.nativeKeyEvent.repeatCount == 0) onPlace()
        else -> return@onPreviewKeyEvent false
    }
    true
}
