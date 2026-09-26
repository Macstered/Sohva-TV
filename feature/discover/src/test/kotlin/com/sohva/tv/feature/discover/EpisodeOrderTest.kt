package com.sohva.tv.feature.discover

import com.sohva.tv.feature.discover.data.EpisodeOrder
import com.sohva.tv.feature.discover.protocol.AddonVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 50 §11 "Episode order": unordered metadata, season boundaries, specials, duplicates, unnumbered lists. */
class EpisodeOrderTest {
    private fun v(id: String, season: Int?, episode: Int?) = AddonVideo(id, id, season, episode, null, null)

    @Test
    fun numberedEpisodesAdvanceAcrossSeasonsWhateverTheListOrder() {
        val videos = listOf(v("s2e1", 2, 1), v("s1e2", 1, 2), v("s0e1", 0, 1), v("s1e1", 1, 1), v("s1e2-copy", 1, 2), v("s0e2", 0, 2))
        assertEquals("s1e2", EpisodeOrder.next(videos, "s1e1")!!.id)
        // The other copy of the same episode is not "next": the season ends and the next one starts.
        assertEquals("s2e1", EpisodeOrder.next(videos, "s1e2")!!.id)
        assertNull(EpisodeOrder.next(videos, "s2e1"))
        // Specials stay among specials; regular seasons never enter them.
        assertEquals("s0e2", EpisodeOrder.next(videos, "s0e1")!!.id)
        assertNull(EpisodeOrder.next(videos, "s0e2"))
    }

    @Test
    fun unnumberedListsFollowTheProviderOrder() {
        val videos = listOf(v("a", null, null), v("a", null, null), v("b", null, null))
        assertEquals("b", EpisodeOrder.next(videos, "a")!!.id)
        assertNull(EpisodeOrder.next(videos, "b"))
        assertNull(EpisodeOrder.next(videos, "unknown"))
    }
}
