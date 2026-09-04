package com.streammate.tv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMateNavigationTest {
    @Test
    fun `live playback returns to guide`() {
        assertTrue(shouldReturnPlaybackToGuide(null, null))
    }

    @Test
    fun `catchup playback retains its previous destination`() {
        assertFalse(shouldReturnPlaybackToGuide(1_000L, 2_000L))
        assertFalse(shouldReturnPlaybackToGuide(1_000L, null))
        assertFalse(shouldReturnPlaybackToGuide(null, 2_000L))
    }
}
