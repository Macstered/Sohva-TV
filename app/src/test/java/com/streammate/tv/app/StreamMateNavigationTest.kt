package com.streammate.tv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMateNavigationTest {
    @Test
    fun `live playback returns to guide`() {
        assertTrue(shouldReturnPlaybackToGuide(null, null))
        assertTrue(shouldReturnPlaybackToGuide(null, null, launchedForGuide = true))
    }

    @Test
    fun `catchup playback retains its previous destination`() {
        assertFalse(shouldReturnPlaybackToGuide(1_000L, 2_000L))
        assertFalse(shouldReturnPlaybackToGuide(1_000L, null))
        assertFalse(shouldReturnPlaybackToGuide(null, 2_000L))
    }

    @Test
    fun `playback chosen on a match card returns to the match card`() {
        // Sohva Sport plays a stream without telling the guide about it, and
        // back from that stream belongs on the card it was chosen from.
        assertFalse(shouldReturnPlaybackToGuide(null, null, launchedForGuide = false))
        assertFalse(shouldReturnPlaybackToGuide(1_000L, 2_000L, launchedForGuide = false))
    }
}
