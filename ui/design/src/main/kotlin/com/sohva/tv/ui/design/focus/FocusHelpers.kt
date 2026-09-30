package com.sohva.tv.ui.design.focus

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Long OK (design/01 §12): the first key repeat of OK, Enter or numpad Enter fires once, and the
 * key-up after it is swallowed so the ordinary click does not follow.
 */
internal fun Modifier.longPress(gesture: LongPressGesture, onLongClick: () -> Unit): Modifier =
    onPreviewKeyEvent { gesture.handle(it, onLongClick) }

/** Remembered per surface; holds whether the current press already fired. */
internal class LongPressGesture {
    private var fired = false

    fun handle(event: KeyEvent, onLongClick: () -> Unit): Boolean {
        if (event.key.nativeKeyCode !in OK_KEYS) return false
        return when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount < 1) return false
                if (!fired) {
                    fired = true
                    onLongClick()
                }
                true
            }
            KeyEventType.KeyUp -> fired.also { fired = false }
            else -> false
        }
    }

    private companion object {
        val OK_KEYS = setOf(KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER)
    }
}

/**
 * Requests focus once the target is attached, retrying once per frame for up to [attempts]
 * frames (≈ half a second). Fixed sleeps never landed in UI tests and lost races with lazy
 * layouts (design/02 §20). Use for every initial focus and every focus return. [stillWanted]
 * cancels a pending request when the viewer has chosen somewhere else in the meantime.
 */
suspend fun FocusRequester.requestFocusWhenAttached(
    attempts: Int = 30,
    stillWanted: () -> Boolean = { true },
): Boolean {
    repeat(attempts) {
        withFrameNanos { }
        if (!stillWanted()) return false
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
    }
    return false
}

/**
 * Scrolls only as far as needed to show the focused child, and not at all when it is already
 * fully visible. The TV default moves every focused child to 30 % of its container, which made
 * sideways moves scroll whole pages (design/02 §20). Provide through `LocalBringIntoViewSpec`.
 */
@OptIn(ExperimentalFoundationApi::class)
object KeepVisibleBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            size > containerSize -> offset
            offset < 0f -> offset
            else -> trailing - containerSize
        }
    }
}
