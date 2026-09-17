package com.streammate.tv.feature.today

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.core.database.*
import com.streammate.tv.core.model.*
import com.streammate.tv.matching.*
import com.streammate.tv.sports.repository.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class SportsMatchPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var database: StreamMateDatabase
    private lateinit var file: File
    private val scans = AtomicInteger()
    private val source = IptvSourceStateEntity("fixture", "Fixture", "M3U", true, 1, 0, 1)
    private val event = TodayEvent("fixture-event", SportType.FOOTBALL, competition = "Test", home = "Real Betis", away = "Getafe", startEpochMillis = 1_000_000, startMinuteOfDay = 19 * 60, startLabel = "19:00", status = TodayEventStatus.SCHEDULED, statusLabel = "Upcoming", score = null, matchingChannels = 0)

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java)
            .setQueryCallback({ sql, _ -> if (sql.contains("FROM organization_visible_channels c")) scans.incrementAndGet() }, java.util.concurrent.Executor { it.run() })
            .build()
        file = File.createTempFile("sports-matches", ".bin", context.cacheDir)
        database.guideDao().upsertSourceState(source)
        database.guideDao().upsertChannels(listOf(IptvChannelEntity("fixture", "one", "channel", null, "ES - Real Betis vs Getafe", "real betis getafe", "Sport", null, "synthetic", null, null, 1)))
        database.guideDao().activatePlaylistSnapshot("fixture", "one", 1, 1)
    }

    @After fun close() { database.close(); file.delete() }
    private fun repository() = EventChannelMatchingRepository(database.guideDao(), cache = EventChannelMatchCache(file))

    @Test fun restartReusesMatchesWithoutScanningAndHonoursSavedDecisionsAndHiddenChannels() = runBlocking {
        val first = repository().matchesFor(listOf(event))
        assertEquals(1, first.getValue(event.id).size)
        assertEquals(2, scans.get())
        val restarted = repository()
        scans.set(0)
        assertEquals(first, restarted.matchesFor(listOf(event)))
        assertEquals(0, scans.get())
        restarted.setDecision(event.id, "channel", ManualMatchDecision.REJECTED)
        assertEquals(ChannelMatchConfidence.REJECTED, repository().cachedMatchesFor(listOf(event)).getValue(event.id).single().confidence)
        assertEquals(0, scans.get())
        // Startup touches source timestamps without changing the actual matching inputs.
        database.guideDao().upsertSourceState(source.copy(updatedAtEpochMillis = 99))
        assertTrue(repository().cachedMatchesFor(listOf(event)).isNotEmpty())
        database.guideDao().upsertChannelPreference(ChannelPreferenceEntity("channel", "fixture", null, null, true, null, null, 100))
        assertTrue(restarted.cachedMatchesFor(listOf(event)).isEmpty())
        assertTrue(restarted.matchesFor(listOf(event)).getValue(event.id).isEmpty())
        assertEquals(2, scans.get())
    }

    @Test fun cachedFeedHasChannelCountsBeforeNetworkCompletesAndDecisionsDoNotRescan() = runBlocking {
        repository().matchesFor(listOf(event))
        scans.set(0)
        val network = CompletableDeferred<Unit>()
        val store = ViewModelStore()
        lateinit var model: TodayViewModel
        val feed = object : SportsRepository {
            private fun snapshot(sport: SportType) = SportsEventsSnapshot(if (sport == SportType.FOOTBALL) listOf(event) else emptyList(), "hit", "fixture", null)
            override suspend fun cachedEvents(sport: SportType, date: LocalDate, zoneId: ZoneId, selectedCompetitionIds: Set<String>) = snapshot(sport)
            override suspend fun events(sport: SportType, date: LocalDate, zoneId: ZoneId, selectedCompetitionIds: Set<String>): SportsEventsSnapshot { network.await(); return snapshot(sport) }
            override suspend fun footballEvents(date: LocalDate, zoneId: ZoneId, selectedCompetitionIds: Set<String>) = snapshot(SportType.FOOTBALL)
            override suspend fun hockeyEvents(date: LocalDate, zoneId: ZoneId, selectedCompetitionIds: Set<String>) = snapshot(SportType.ICE_HOCKEY)
            override suspend fun aflEvents(date: LocalDate, zoneId: ZoneId, selectedCompetitionIds: Set<String>) = snapshot(SportType.AUSTRALIAN_FOOTBALL)
            override suspend fun footballIncidents(eventId: String) = FootballIncidentsSnapshot(emptyList(), "hit", "fixture", null)
        }
        try {
            instrumentation.runOnMainSync {
                model = TodayViewModel(feed, repository(), AppPreferencesRepository(context))
                store.put("test", model)
            }
            withTimeout(10_000) { model.uiState.first { it.events.any { e -> e.matchingChannels == 1 } } }
            assertFalse(network.isCompleted)
            assertEquals(0, scans.get())
            instrumentation.runOnMainSync { model.setMatchDecision(event.id, "channel", ManualMatchDecision.REJECTED) }
            withTimeout(10_000) { model.uiState.first { it.matches[event.id]?.singleOrNull()?.manualDecision == ManualMatchDecision.REJECTED } }
            assertEquals(0, model.uiState.value.events.single().matchingChannels)
            assertEquals(0, scans.get())
            network.complete(Unit)
            withTimeout(10_000) { model.uiState.first { !it.isLoading } }
            assertEquals(ManualMatchDecision.REJECTED, model.uiState.value.matches.getValue(event.id).single().manualDecision)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
