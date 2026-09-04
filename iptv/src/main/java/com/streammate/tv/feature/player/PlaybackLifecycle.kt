package com.streammate.tv.feature.player

import androidx.lifecycle.Lifecycle

/** What should happen to playback when the app moves between foreground states. */
enum class PlaybackLifecycleAction { STOP, RESUME, NONE }

/**
 * Decides what a lifecycle event means for a playing stream.
 *
 * A media session is built to outlive its screen, which is right for music and
 * wrong here. Two reasons: a provider counts an open stream against the
 * account's connection limit whether or not anyone is watching, so a forgotten
 * background playback can lock the household out of its own subscription; and
 * on a television, audio continuing after Home reads as an app that refuses to
 * close.
 *
 * Stopping releases the stream but keeps the item, so returning can prepare
 * again and pick live up where it now is - coming back to a dead player would
 * be its own bug.
 */
object PlaybackLifecycle {

    fun actionFor(event: Lifecycle.Event, hasMedia: Boolean): PlaybackLifecycleAction = when (event) {
        Lifecycle.Event.ON_STOP -> PlaybackLifecycleAction.STOP
        // Nothing to resume before anything has been chosen to play, and asking
        // an empty controller to prepare would only raise an error.
        Lifecycle.Event.ON_START -> if (hasMedia) PlaybackLifecycleAction.RESUME else PlaybackLifecycleAction.NONE
        else -> PlaybackLifecycleAction.NONE
    }
}
