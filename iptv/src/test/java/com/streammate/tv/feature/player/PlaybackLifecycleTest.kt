package com.streammate.tv.feature.player

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLifecycleTest {

    @Test
    fun `leaving the app ends the stream`() {
        // Home, another app, or the screen going off. A stream left running
        // holds one of the account's connections and keeps making noise.
        assertEquals(
            PlaybackLifecycleAction.STOP,
            PlaybackLifecycle.actionFor(Lifecycle.Event.ON_STOP, hasMedia = true),
        )
    }

    @Test
    fun `coming back picks the stream up again`() {
        assertEquals(
            PlaybackLifecycleAction.RESUME,
            PlaybackLifecycle.actionFor(Lifecycle.Event.ON_START, hasMedia = true),
        )
    }

    @Test
    fun `there is nothing to resume before anything has been chosen`() {
        assertEquals(
            PlaybackLifecycleAction.NONE,
            PlaybackLifecycle.actionFor(Lifecycle.Event.ON_START, hasMedia = false),
        )
    }

    @Test
    fun `moving around inside the app leaves playback alone`() {
        listOf(
            Lifecycle.Event.ON_CREATE,
            Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_DESTROY,
        ).forEach { event ->
            assertEquals(
                event.name,
                PlaybackLifecycleAction.NONE,
                PlaybackLifecycle.actionFor(event, hasMedia = true),
            )
        }
    }
}
