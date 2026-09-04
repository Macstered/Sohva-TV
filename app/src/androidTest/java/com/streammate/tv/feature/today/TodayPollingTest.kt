package com.streammate.tv.feature.today

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.matching.EventChannelMatchingRepository
import com.streammate.tv.sports.repository.FootballIncidentsSnapshot
import com.streammate.tv.sports.repository.SportsEventsSnapshot
import com.streammate.tv.sports.repository.SportsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API-Sports free tier is a daily allowance, so a call made while nobody is
 * looking is one unavailable during a match. Polling stops when the screen is
 * not being watched - and the price of that is coming back to scores as old as
 * the absence, which is why returning refreshes when the data has gone stale.
 */
@RunWith(AndroidJUnit4::class)
class TodayPollingTest {

    private lateinit var database: StreamMateDatabase
    private val clock = MutableClock(Instant.parse("2026-08-29T18:00:00Z"))
    private var loadCount = 0

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() = database.close()

    private fun viewModel(): TodayViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return TodayViewModel(
            repository = CountingRepository(),
            matchingRepository = EventChannelMatchingRepository(database.guideDao()),
            preferencesRepository = AppPreferencesRepository(context),
            clock = clock,
        )
    }

    private fun awaitFirstLoad(model: TodayViewModel) = runBlocking {
        model.uiState.first { !it.isLoading && it.events.isNotEmpty() }
    }

    /**
     * Waits until nothing else is loading.
     *
     * The preferences flow can fire its own refresh moments after the first
     * one, so measuring straight after the initial load reads a moving number -
     * and whether it has settled depends on what ran before in the same
     * process.
     */
    private fun awaitQuiet(model: TodayViewModel) {
        val deadline = System.currentTimeMillis() + 10_000
        var stableSince = 0L
        var seen = -1
        while (System.currentTimeMillis() < deadline) {
            val loading = model.uiState.value.isLoading
            if (!loading && loadCount == seen) {
                if (System.currentTimeMillis() - stableSince > 400) return
            } else {
                seen = loadCount
                stableSince = System.currentTimeMillis()
            }
            Thread.sleep(50)
        }
        throw AssertionError("the view model never stopped loading")
    }

    private fun awaitLoadCount(target: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (loadCount >= target) return
            Thread.sleep(50)
        }
        throw AssertionError("expected $target loads, saw $loadCount")
    }

    @Test
    fun returningAfterTimeAwayRefreshesRatherThanShowingStaleScores() {
        val model = viewModel()
        awaitFirstLoad(model)
        awaitQuiet(model)
        val afterFirstLoad = loadCount

        // Away for longer than the live polling interval.
        clock.instant = clock.instant.plusSeconds(20 * 60)
        model.setAutoRefreshEnabled(true)

        awaitLoadCount(afterFirstLoad + 1)
        awaitQuiet(model)
        assertEquals("coming back stale should cost exactly one call", afterFirstLoad + 1, loadCount)
    }

    @Test
    fun returningStraightAwayCostsNothing() {
        val model = viewModel()
        awaitFirstLoad(model)
        awaitQuiet(model)
        val afterFirstLoad = loadCount

        clock.instant = clock.instant.plusSeconds(20)
        model.setAutoRefreshEnabled(true)

        awaitQuiet(model)
        assertEquals("data this fresh is worth no call", afterFirstLoad, loadCount)
    }

    private inner class CountingRepository : SportsRepository {
        override suspend fun footballEvents(
            date: LocalDate,
            zoneId: ZoneId,
            selectedCompetitionIds: Set<String>,
        ): SportsEventsSnapshot {
            loadCount++
            return SportsEventsSnapshot(
                events = listOf(liveEvent()),
                cacheState = "fresh",
                source = "test",
                quotaRemaining = 99,
            )
        }

        override suspend fun aflEvents(
            date: LocalDate,
            zoneId: ZoneId,
            selectedCompetitionIds: Set<String>,
        ) = SportsEventsSnapshot(emptyList(), "fresh", "test", 99)

        override suspend fun hockeyEvents(
            date: LocalDate,
            zoneId: ZoneId,
            selectedCompetitionIds: Set<String>,
        ) = SportsEventsSnapshot(emptyList(), "fresh", "test", 99)

        override suspend fun footballIncidents(eventId: String) =
            FootballIncidentsSnapshot(emptyList(), "fresh", "test", 99)
    }

    private fun liveEvent() = TodayEvent(
        id = "event-1",
        sport = SportType.FOOTBALL,
        competition = "Test League",
        home = "Home FC",
        away = "Away United",
        startEpochMillis = clock.millis(),
        startMinuteOfDay = 20 * 60,
        startLabel = "20:00",
        // Live, so the interval is the shortest the policy allows and the test
        // is not leaning on a generous idle-day window.
        status = TodayEventStatus.LIVE,
        statusLabel = "62",
        score = "1 - 0",
        matchingChannels = 0,
    )

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
}
