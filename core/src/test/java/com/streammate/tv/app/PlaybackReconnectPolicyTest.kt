package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackReconnectPolicyTest {
    @Test
    fun `missing or unknown stored value preserves the standard retry behavior`() {
        assertEquals(PlaybackReconnectPolicy.STANDARD, PlaybackReconnectPolicy.fromStoredValue(null))
        assertEquals(
            PlaybackReconnectPolicy.STANDARD,
            PlaybackReconnectPolicy.fromStoredValue("unsupported"),
        )
    }

    @Test
    fun `persistent policy is restored`() {
        assertEquals(
            PlaybackReconnectPolicy.PERSISTENT,
            PlaybackReconnectPolicy.fromStoredValue("PERSISTENT"),
        )
    }
}
