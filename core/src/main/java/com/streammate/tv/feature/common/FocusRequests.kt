package com.streammate.tv.feature.common

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus as soon as the requester is actually attached, returning
 * whether it succeeded.
 *
 * A [FocusRequester] wired to a lazy-list row is not attached until that row has
 * been composed and laid out, and `requestFocus()` on an unattached requester
 * throws. Screens worked around this either by firing the request immediately
 * and hoping, or by sleeping a fixed 80 ms first. Both are bets on layout
 * finishing in time. The fixed sleep in particular is lost instantly under a
 * Compose test clock, which advances the delay without advancing layout, so
 * initial focus never landed in UI tests even though it looks fine on a device.
 *
 * Retrying across frames waits for the thing that actually matters - the next
 * frame - and is bounded, so it cannot keep the composition busy forever.
 */
suspend fun FocusRequester.requestFocusWhenAttached(
    attempts: Int = DEFAULT_FOCUS_ATTEMPTS,
): Boolean {
    repeat(attempts) {
        withFrameNanos { }
        // requestFocus() throws while unattached and returns false when the
        // node is attached but not yet focusable, so both have to be retried -
        // treating a non-throwing call as success would stop on the second.
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
    }
    return false
}

// Roughly half a second of frames. The old budget of twelve was two hundred
// milliseconds, which is under the length of a container's entry animation and
// shorter than a lazy list takes to lay out its first row on a cold screen.
private const val DEFAULT_FOCUS_ATTEMPTS = 30
