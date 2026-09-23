package com.streammate.tv.feature.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The group the guide opens on is the first one its rail lists. */
class GuideFirstGroupTest {
    @Test
    fun withoutAnOrderOfTheViewersOwnItIsThePlaylistsFirstGroup() {
        assertEquals("News", firstGuideGroup(listOf("News", "Sport", "Films"), emptyMap()))
    }

    @Test
    fun theViewersOwnOrderDecidesAndUnplacedGroupsComeAfterThePlacedOnes() {
        val positions = mapOf("group:Sport" to 2L, "group:Films" to 1L, "favourites" to 0L)
        assertEquals("Films", firstGuideGroup(listOf("News", "Sport", "Films"), positions))
        // Two unplaced groups keep the playlist's order between them, as the rail's stable sort does.
        assertEquals("News", firstGuideGroup(listOf("News", "Sport"), mapOf("recent" to 0L)))
    }

    @Test
    fun aSourceWithoutGroupsHasNone() {
        assertNull(firstGuideGroup(emptyList(), mapOf("group:Gone" to 1L)))
    }
}
