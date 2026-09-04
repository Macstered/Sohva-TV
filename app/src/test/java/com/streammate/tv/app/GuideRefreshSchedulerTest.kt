package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideRefreshSchedulerTest {
    @Test
    fun `selected interval controls playlist and epg jobs`() {
        PlaylistEpgRefreshInterval.entries.forEach { interval ->
            assertEquals(interval.hours, GuideRefreshScheduler.repeatHoursFor(RefreshKind.PLAYLIST, interval))
            assertEquals(interval.hours, GuideRefreshScheduler.repeatHoursFor(RefreshKind.EPG, interval))
        }
    }

    @Test
    fun `catalogue refresh remains daily`() {
        PlaylistEpgRefreshInterval.entries.forEach { interval ->
            assertEquals(24L, GuideRefreshScheduler.repeatHoursFor(RefreshKind.CATALOGUE, interval))
        }
    }

    @Test
    fun `automatic refresh yields while the app is foreground`() {
        assertTrue(GuideRefreshScheduler.shouldDeferAutomaticRefresh(isAppForeground = true))
        assertFalse(GuideRefreshScheduler.shouldDeferAutomaticRefresh(isAppForeground = false))
    }
}
