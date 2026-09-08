package com.streammate.tv.iptv.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedRuleTest {
    private val film = 100L * 60_000L

    @Test
    fun `a film is watched at nine tenths`() {
        assertFalse(WatchedRule.isWatched(89L * 60_000L, film))
        assertTrue(WatchedRule.isWatched(90L * 60_000L, film))
        assertTrue(WatchedRule.isWatched(91L * 60_000L, film))
    }

    @Test
    fun `an episode is watched with three minutes left, not four`() {
        // 25 minutes: three minutes from the end is 88 %, before the fraction bites.
        val episode = 25L * 60_000L
        assertFalse(WatchedRule.isWatched(episode - 4L * 60_000L, episode))
        assertTrue(WatchedRule.isWatched(episode - 3L * 60_000L, episode))
        assertTrue(WatchedRule.isWatched(episode - 2L * 60_000L - 59_000L, episode))
    }

    @Test
    fun `a short does not count its last minutes as credits`() {
        val short = 7L * 60_000L
        assertFalse(WatchedRule.isWatched(short - 3L * 60_000L, short))
        assertFalse(WatchedRule.isWatched((short * 0.89).toLong(), short))
        assertTrue(WatchedRule.isWatched((short * 0.9).toLong(), short))
    }

    @Test
    fun `no duration, no verdict`() {
        assertFalse(WatchedRule.isWatched(5_000L, 0L))
        assertFalse(WatchedRule.isWatched(5_000L, -1L))
        assertTrue(WatchedRule.isWatched(film + 60_000L, film))
    }
}
