package com.sohva.tv.feature.sport

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportServiceState
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.sport.feed.SportFeed
import com.sohva.tv.feature.sport.provider.CacheState
import com.sohva.tv.feature.sport.provider.SportDay
import com.sohva.tv.feature.sport.provider.SportDays
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 60 §11 "Feed combination" and "Polling policy", on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SportFeedTest {
    private val start = 1_790_400_000_000L

    private class Days(private val scope: TestScope, private val start: Long) : SportDays {
        val asked = ArrayList<SportType>()
        var failing = setOf<SportType>()
        var status = EventStatus.SCHEDULED

        override suspend fun savedDay(sport: SportType, date: LocalDate, zone: ZoneId, competitions: Set<String>?): SportDay? = null

        override suspend fun day(sport: SportType, date: LocalDate, zone: ZoneId, competitions: Set<String>?): SportDay {
            asked += sport
            if (sport in failing) throw SportsException(SportsProblem.QUOTA_EXHAUSTED)
            val event = SportEvent(
                "api-sports:${sport.provider}:1", sport, competitions?.firstOrNull() ?: "", "C", null, Side("H", null), Side("A", null),
                start + 12 * 60 * 60_000L, 20 * 60, status, null, null, null,
            )
            return SportDay(sport, listOf(event), CacheState.MISS, 90)
        }

        override suspend fun quotas(): Map<SportType, Int> = emptyMap()
    }

    private fun feed(scope: TestScope, days: Days, follows: SportFollows, automatic: Boolean = true): SportFeed {
        val clock = object : Clock {
            override fun wallMillis(): Long = start + scope.testScheduler.currentTime
            override fun monotonicNanos(): Long = 0
        }
        val log = object : DiagnosticsLog {
            override fun info(event: String, message: String) = Unit
            override fun error(event: String, message: String?, error: Throwable?) = Unit
            override fun snapshot(): List<String> = emptyList()
        }
        return SportFeed(days, MutableStateFlow(follows), MutableStateFlow("Europe/Helsinki"), clock, scope.backgroundScope, log, automatic)
    }

    @Test
    fun sportsWithoutCompetitionsAreRequestedAndAPartialFailureKeepsTheRest() = runTest {
        val days = Days(this, start).apply { failing = setOf(SportType.FOOTBALL) }
        val follows = SportFollows(setOf(SportType.FOOTBALL, SportType.MMA, SportType.FORMULA_1, SportType.NBA, SportType.RUGBY), setOf("FOOTBALL:39"))
        val feed = feed(this, days, follows)
        feed.start()
        runCurrent()
        // MMA, Formula 1 and NBA have no competition keys and are still fetched (SPORT-FR-21 regression);
        // rugby has no followed competition and is not.
        assertEquals(setOf(SportType.FOOTBALL, SportType.MMA, SportType.FORMULA_1, SportType.NBA), days.asked.toSet())
        val s = feed.state.value
        assertEquals(3, s.events.size)
        assertTrue(s.partial)
        assertEquals(SportsProblem.QUOTA_EXHAUSTED, s.cause)
        assertEquals(SportServiceState.UPDATED, s.service)
    }

    @Test
    fun aWholeFailureKeepsTheListAndNamesTheCause() = runTest {
        val days = Days(this, start)
        val feed = feed(this, days, SportFollows(setOf(SportType.MMA), emptySet()))
        feed.start()
        runCurrent()
        assertEquals(1, feed.state.value.events.size)
        days.failing = setOf(SportType.MMA)
        feed.refresh()
        runCurrent()
        assertEquals(1, feed.state.value.events.size)
        assertEquals(SportsProblem.QUOTA_EXHAUSTED, feed.state.value.failure)
    }

    @Test
    fun pollingRunsOnlyWhileVisibleAtTheIntervalOfTheList() = runTest {
        val days = Days(this, start)
        val feed = feed(this, days, SportFollows(setOf(SportType.MMA), emptySet()))
        feed.start()
        runCurrent()
        assertEquals(1, days.asked.size)
        // Hidden: nothing for hours.
        advanceTimeBy(3 * 60 * 60_000L)
        assertEquals(1, days.asked.size)
        // Returning to an old list refreshes once, then every 30 minutes (nothing live or soon).
        feed.setVisible(true)
        runCurrent()
        assertEquals(2, days.asked.size)
        advanceTimeBy(29 * 60_000L)
        assertEquals(2, days.asked.size)
        advanceTimeBy(2 * 60_000L)
        assertEquals(3, days.asked.size)
        // A live game makes it 5 minutes.
        days.status = EventStatus.LIVE
        advanceTimeBy(31 * 60_000L)
        assertEquals(4, days.asked.size)
        advanceTimeBy(6 * 60_000L)
        assertEquals(5, days.asked.size)
        // Leaving stops it; coming straight back costs nothing.
        feed.setVisible(false)
        advanceTimeBy(60_000L)
        feed.setVisible(true)
        runCurrent()
        assertEquals(5, days.asked.size)
        feed.setVisible(false)
    }

    @Test
    fun theLabBuildLoadsNothingByItselfButRefreshWorks() = runTest {
        val days = Days(this, start)
        val feed = feed(this, days, SportFollows(setOf(SportType.MMA), emptySet()), automatic = false)
        feed.start()
        feed.setVisible(true)
        advanceTimeBy(60 * 60_000L)
        assertTrue(days.asked.isEmpty())
        feed.refresh()
        runCurrent()
        assertEquals(1, days.asked.size)
        assertFalse(feed.state.value.loading)
    }
}
