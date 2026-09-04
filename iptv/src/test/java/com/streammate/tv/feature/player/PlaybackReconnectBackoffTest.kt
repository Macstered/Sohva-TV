package com.streammate.tv.feature.player

import com.streammate.tv.app.PlaybackReconnectPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackReconnectBackoffTest {
    @Test
    fun `standard policy preserves three attempts at two four and six seconds`() {
        assertEquals(3, reconnectMaxAutomaticAttempts(PlaybackReconnectPolicy.STANDARD))
        assertArrayEquals(
            longArrayOf(2_000L, 4_000L, 6_000L),
            delaysFor(PlaybackReconnectPolicy.STANDARD),
        )
    }

    @Test
    fun `persistent policy uses eight bounded exponential attempts`() {
        assertEquals(8, reconnectMaxAutomaticAttempts(PlaybackReconnectPolicy.PERSISTENT))
        assertArrayEquals(
            longArrayOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L),
            delaysFor(PlaybackReconnectPolicy.PERSISTENT),
        )
    }

    private fun delaysFor(policy: PlaybackReconnectPolicy): LongArray =
        LongArray(reconnectMaxAutomaticAttempts(policy)) { index ->
            reconnectDelayMillis(policy, index + 1)
        }
}
