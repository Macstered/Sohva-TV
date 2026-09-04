package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackBufferPolicyTest {
    @Test
    fun `default profile leaves Media3 load control untouched`() {
        assertNull(PlaybackBufferPolicy.durations(PlaybackBufferProfile.DEFAULT))
    }

    @Test
    fun `low latency profile uses a bounded responsive buffer`() {
        assertEquals(
            PlaybackBufferDurations(
                minBufferMs = 5_000,
                maxBufferMs = 15_000,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_000,
            ),
            PlaybackBufferPolicy.durations(PlaybackBufferProfile.LOW_LATENCY),
        )
    }

    @Test
    fun `stability profile uses a larger recovery buffer`() {
        assertEquals(
            PlaybackBufferDurations(
                minBufferMs = 60_000,
                maxBufferMs = 120_000,
                bufferForPlaybackMs = 5_000,
                bufferForPlaybackAfterRebufferMs = 10_000,
            ),
            PlaybackBufferPolicy.durations(PlaybackBufferProfile.STABILITY),
        )
    }
}
