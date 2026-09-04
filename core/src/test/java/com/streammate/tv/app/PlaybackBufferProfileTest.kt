package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBufferProfileTest {
    @Test
    fun `missing or unknown stored value preserves the Media3 default`() {
        assertEquals(PlaybackBufferProfile.DEFAULT, PlaybackBufferProfile.fromStoredValue(null))
        assertEquals(PlaybackBufferProfile.DEFAULT, PlaybackBufferProfile.fromStoredValue("unsupported"))
    }

    @Test
    fun `known optional profiles are restored`() {
        assertEquals(
            PlaybackBufferProfile.LOW_LATENCY,
            PlaybackBufferProfile.fromStoredValue("LOW_LATENCY"),
        )
        assertEquals(
            PlaybackBufferProfile.STABILITY,
            PlaybackBufferProfile.fromStoredValue("STABILITY"),
        )
    }
}
