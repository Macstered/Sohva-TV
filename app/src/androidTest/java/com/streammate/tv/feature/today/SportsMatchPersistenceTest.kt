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
            .setQueryCallback({ sql, _ -> if (sql.contains("sports-channel-page") || sql.contains("sports-programme-page")) scans.incrementAndGet() }, java.util.concurrent.Executor { it.run() })
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
        assertEquals(1, scans.get()) // No programme query when no visible channels remain.
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

    @Test fun programmePagesKeepSharedXmltvMappingsOffsetsAndVisibilityWithoutDuplicates() = runBlocking {
        val dao = database.guideDao()
        dao.upsertSourceState(source.copy(epgOffsetMinutes = 60))
        dao.upsertChannels(listOf("channel", "second", "hidden").map { id ->
            IptvChannelEntity("fixture", "one", id, "original", "Name $id", id, "Sport", null, "synthetic", null, null, 1)
        })
        for (id in listOf("channel", "second", "hidden")) {
            dao.upsertChannelPreference(ChannelPreferenceEntity(id, "fixture", "Custom $id", null,
                id == "hidden", null, "shared", 1))
        }
        // A late row from an inactive snapshot must not leak into the channel scan.
        dao.upsertChannels(listOf(IptvChannelEntity("fixture", "staged", "inactive", "shared", "Inactive", "inactive", null, null, "synthetic", null, null, 1)))
        dao.insertProgrammes((0 until 100).map { index ->
            TvProgrammeEntity("fixture", "epg", "p-$index", "shared", event.startEpochMillis,
                event.startEpochMillis + 60_000, "Real Betis vs Getafe", null, "Description", "")
        })
        dao.activateEpgSnapshot("fixture", "epg", 100, 1)
        val channels = mutableListOf<ChannelNameCandidateRow>()
        var after = Long.MIN_VALUE
        while (true) {
            val page = dao.channelNameCandidatesPage(after, 1)
            if (page.isEmpty()) break
            channels += page
            after = page.last().channelRowId
        }
        assertEquals(setOf("channel", "second"), channels.map { it.channelId }.toSet())
        assertTrue(channels.all { it.channelName == "Custom ${it.channelId}" })
        val keys = mutableSetOf<Pair<String, String>>()
        var afterProgramme = Long.MIN_VALUE
        var afterChannel = Long.MIN_VALUE
        val adjusted = event.startEpochMillis + 60 * 60_000
        while (true) {
            // Odd page size deliberately divides two streams sharing one programme.
            val page = dao.programmeCandidatesPage(channels.map { it.channelRowId }, adjusted, adjusted,
                afterProgramme, afterChannel, 3)
            assertTrue(page.size <= 3)
            for (row in page) {
                assertEquals(adjusted, row.programmeStartEpochMillis)
                assertEquals("Custom ${row.channelId}", row.channelName)
                assertTrue("duplicate ${row.channelId}/${row.programmeId}", keys.add(row.channelId to row.programmeId))
            }
            if (page.isEmpty()) break
            afterProgramme = page.last().programmeRowId
            afterChannel = page.last().channelRowId
        }
        assertEquals(200, keys.size)
        assertTrue(dao.programmeCandidatesPage(channels.map { it.channelRowId }, adjusted + 1, adjusted + 2,
            Long.MIN_VALUE, Long.MIN_VALUE, 3).isEmpty())
        database.organizationDao().upsertRules(listOf(OrganizationRuleEntity("LIVE", "", "", "second", false, null, null)))
        assertEquals(listOf("channel"), dao.channelNameCandidatesPage(Long.MIN_VALUE, 10).map { it.channelId })
        assertEquals(setOf("channel"), dao.programmeCandidatesPage(channels.map { it.channelRowId }, adjusted, adjusted,
            Long.MIN_VALUE, Long.MIN_VALUE, 300).map { it.channelId }.toSet())
        dao.upsertSourceState(source.copy(enabled = false))
        assertTrue(dao.channelNameCandidatesPage(Long.MIN_VALUE, 10).isEmpty())
    }
}
