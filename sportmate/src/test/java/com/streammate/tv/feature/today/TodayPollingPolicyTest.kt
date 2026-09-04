package com.streammate.tv.feature.today

import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayPollingPolicyTest {
    private val now = 1_000_000L

    @Test
    fun `polls live events every five minutes`() {
        assertEquals(5, TodayPollingPolicy.intervalMinutes(listOf(event(TodayEventStatus.LIVE, now)), now))
    }

    @Test
    fun `polls imminent scheduled events every ten minutes`() {
        val kickoff = now + 90 * 60_000L
        assertEquals(
            10,
            TodayPollingPolicy.intervalMinutes(listOf(event(TodayEventStatus.SCHEDULED, kickoff)), now),
        )
    }

    @Test
    fun `polls idle days every thirty minutes`() {
        val kickoff = now + 3 * 60 * 60_000L
        assertEquals(
            30,
            TodayPollingPolicy.intervalMinutes(listOf(event(TodayEventStatus.SCHEDULED, kickoff)), now),
        )
    }

    @Test
    fun `only polls while the sports screen is the thing being looked at`() {
        assertTrue(TodayPollingPolicy.shouldPoll(onSportsScreen = true, appInForeground = true))
        // Watching a stream is its own destination.
        assertFalse(TodayPollingPolicy.shouldPoll(onSportsScreen = false, appInForeground = true))
        // The app left for the launcher, or an external player took over. The
        // screen is still "on" as far as navigation knows.
        assertFalse(TodayPollingPolicy.shouldPoll(onSportsScreen = true, appInForeground = false))
        assertFalse(TodayPollingPolicy.shouldPoll(onSportsScreen = false, appInForeground = false))
    }

    @Test
    fun `coming back to stale scores costs one call`() {
        val interval = 5
        assertTrue(
            "never loaded",
            TodayPollingPolicy.shouldRefreshOnResume(null, now, interval),
        )
        assertTrue(
            "away longer than an interval",
            TodayPollingPolicy.shouldRefreshOnResume(now - 5 * 60_000L, now, interval),
        )
    }

    @Test
    fun `coming straight back does not`() {
        assertFalse(
            TodayPollingPolicy.shouldRefreshOnResume(now - 30_000L, now, intervalMinutes = 5),
        )
    }

    private fun event(status: TodayEventStatus, startEpochMillis: Long) = TodayEvent(
        id = status.name,
        sport = SportType.FOOTBALL,
        competition = "Example League",
        home = "Home",
        away = "Away",
        startEpochMillis = startEpochMillis,
        startMinuteOfDay = 0,
        startLabel = "00:00",
        status = status,
        statusLabel = status.name,
        score = null,
        matchingChannels = 0,
    )
}
