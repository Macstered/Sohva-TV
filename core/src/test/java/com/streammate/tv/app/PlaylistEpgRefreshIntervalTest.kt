package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistEpgRefreshIntervalTest {
    @Test
    fun `offers only supported refresh intervals in display order`() {
        assertEquals(
            listOf(1L, 2L, 4L, 10L, 24L),
            PlaylistEpgRefreshInterval.entries.map { it.hours },
        )
    }

    @Test
    fun `missing or unknown stored value uses twenty four hours`() {
        assertEquals(
            PlaylistEpgRefreshInterval.TWENTY_FOUR_HOURS,
            PlaylistEpgRefreshInterval.fromStoredValue(null),
        )
        assertEquals(
            PlaylistEpgRefreshInterval.TWENTY_FOUR_HOURS,
            PlaylistEpgRefreshInterval.fromStoredValue("unsupported"),
        )
    }

    @Test
    fun `known stored value is restored`() {
        assertEquals(
            PlaylistEpgRefreshInterval.FOUR_HOURS,
            PlaylistEpgRefreshInterval.fromStoredValue("FOUR_HOURS"),
        )
    }
}
