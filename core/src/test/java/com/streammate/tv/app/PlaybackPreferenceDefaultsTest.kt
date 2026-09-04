package com.streammate.tv.app

import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPreferenceDefaultsTest {
    @Test
    fun `next episode autoplay is enabled by default`() {
        assertTrue(AppPreferences().autoPlayNextEpisodeEnabled)
    }
}
