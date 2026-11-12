package com.sohva.tv.feature.sport

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.ProfileAllowedGroupEntity
import com.sohva.tv.core.data.database.PairingDao
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.data.database.TeamAliasEntity
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.ProviderReference
import com.sohva.tv.core.model.sport.ReferenceKind
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.DictionaryNames
import com.sohva.tv.core.model.sport.pairing.EventNames
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.TeamVariants
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.SystemClock
import com.sohva.tv.feature.sport.pairing.PairingCache
import com.sohva.tv.feature.sport.pairing.PairingScan
import com.sohva.tv.feature.sport.pairing.StreamPairing
import com.sohva.tv.feature.sport.dictionary.PairingDictionary
import java.io.DataInputStream
import java.util.concurrent.atomic.AtomicInteger
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 60 §11 "Matcher" (streamed pages) and "Persistence" on a real schema: channel pages in row-id
 * order, a batch's programmes in the window with the source's offset, one guide id shared by
 * several channels, hidden channels and groups left out, and winners kept across page boundaries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PairingScanTest {
    @get:Rule
    val cacheDir = TemporaryFolder()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java).allowMainThreadQueries().build()
    private val kickOff = 1_790_445_600_000L // a round hour on 2026-09-26
    private val minute = 60_000L
    private val game = SportEvent(
        "api-sports:football:1", SportType.FOOTBALL, "39", "Premier League", null, Side("Northbridge", null), Side("Harbor", null),
        kickOff, 20 * 60, EventStatus.SCHEDULED, null, null, null,
    )

    @After
    fun close() = db.close()

    private fun channel(i: Int, name: String, epg: String?, group: Long?, visible: Boolean = true) = ChannelEntity(
        key = "s:c$i", sourceId = "s", groupId = group, name = name, sortName = name.lowercase(), providerName = name, providerGroupId = group,
        providerLogoUrl = null, tvgId = epg, epgId = epg, logoUrl = null, streamUrlEnc = "enc", userAgent = null, referrer = null,
        playlistOrder = i, providerNumber = null, number = null, displayRank = i.toLong(), visible = visible, catchupType = null,
        catchupSource = null, catchupDays = null, catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
    )

    private fun programme(epg: String, start: Long, title: String, snapshot: Long = 3) = ProgrammeEntity(
        sourceId = "s", snapshot = snapshot, epgId = epg, startAt = start, stopAt = start + 120 * minute, title = title,
        subtitle = null, description = "A long description ".repeat(100), categories = null, programmeKey = "p-$epg-$start",
    )

    /** 600 filler channels around the interesting ones, so pages of 256 break in between. */
    private fun seed(offsetMinutes: Int = 0) = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", offsetMinutes, 0, 0))
        db.sourceStatus().upsert(SourceStatusEntity("s", "epg", "success", null, null, null, null, null, 0, 0, 3, 3, null))
        val shown = db.groupImport().insert(ContentGroupEntity(sourceId = "s", room = "LIVE", groupKey = "sport", name = "Sport", providerOrder = 0, itemCount = 1, shown = true, position = 0, sortMode = null))
        val hidden = db.groupImport().insert(ContentGroupEntity(sourceId = "s", room = "LIVE", groupKey = "hidden", name = "Hidden", providerOrder = 1, itemCount = 1, shown = false, position = 1, sortMode = null))
        val rows = ArrayList<ChannelEntity>()
        for (i in 0 until 600) rows += channel(i, "Filler $i", "f$i", shown)
        rows += channel(600, "Sport One HD", "sport1", shown)
        rows += channel(601, "Sport One FI", "sport1", shown)
        rows += channel(602, "EVENT: Northbridge - Harbor", null, shown)
        rows += channel(603, "Northbridge v Harbor (hidden channel)", null, shown, visible = false)
        rows += channel(604, "Northbridge v Harbor (hidden group)", null, hidden)
        for (i in 605 until 900) rows += channel(i, "Filler $i", "f$i", shown)
        db.channelImport().insert(rows)
        val stored = kickOff - offsetMinutes * minute
        db.guideImport().insertProgrammes(
            listOf(
                programme("sport1", stored - 15 * minute, "Football: Northbridge v Harbor"),
                programme("sport1", stored - 60 * minute, "Harbor preview"),
                programme("sport1", stored + 200 * minute, "Northbridge v Harbor (repeat)"),
                programme("sport1", stored, "Northbridge v Harbor", snapshot = 2),
            ) + (0 until 900).map { programme("f${it % 600}", stored + (it % 7) * minute, "Unrelated $it") },
        )
    }

    private fun scan(decisions: Map<Pair<String, String>, Decision> = emptyMap()) = runBlocking {
        PairingScan(db.pairing()).let { it to it.run(listOf(game), TeamVariants.aliases(emptyMap()), decisions).getValue(game.id) }
    }

    @Test
    fun storedAliasesNeverLeakFromHockeyIntoAFootballFixture() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 0, 0, 0))
        db.channelImport().insert(listOf(channel(1, "Lake Wolves - Harbor", null, null)))
        db.sport().putAlias(TeamAliasEntity(SportType.ICE_HOCKEY.name, "Northbridge", "Lake Wolves"))
        val football = game.copy(startMillis = System.currentTimeMillis())
        val hockey = football.copy(id = "fixture:hockey", sport = SportType.ICE_HOCKEY)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val log = object : DiagnosticsLog {
            override fun info(event: String, message: String) = Unit
            override fun error(event: String, message: String?, error: Throwable?) = Unit
            override fun snapshot(): List<String> = emptyList()
        }
        val pairing = StreamPairing(
            db.pairing(), db.sport(), flowOf(Unit), flowOf(listOf(football, hockey)),
            PairingCache(cacheDir.newFile()), SystemClock, scope, Dispatchers.Default, log,
        )
        try {
            pairing.setActive(true)
            val results = withTimeout(5_000) { pairing.streams.first { it[hockey.id]?.size == 1 } }
            assertEquals(Confidence.AVAILABLE, results.getValue(hockey.id).single().automatic)
            assertTrue("hockey alias leaked into football", results.getValue(football.id).isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun oldNumericDateCacheIsRematchedWithoutLosingRejections() = runBlocking {
        val today = LocalDate.now(ZoneOffset.UTC)
        val title = "Northbridge - Harbor ${today.monthValue}/${today.dayOfMonth}"
        val current = game.copy(startMillis = today.atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli())
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 0, 0, 0))
        db.channelImport().insert(listOf(channel(1, title, null, null), channel(2, title, null, null)))
        val reads = AtomicInteger()
        val dao = object : PairingDao by db.pairing() {
            override suspend fun channels(after: Long, limit: Int): List<PairingDao.ScanChannel> {
                reads.incrementAndGet()
                return db.pairing().channels(after, limit)
            }
        }
        val file = cacheDir.newFile()
        val cache = PairingCache(file)
        val log = object : DiagnosticsLog {
            override fun info(event: String, message: String) = Unit
            override fun error(event: String, message: String?, error: Throwable?) { throw AssertionError(message, error) }
            override fun snapshot(): List<String> = emptyList()
        }
        fun controller(): Pair<StreamPairing, CoroutineScope> {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            return StreamPairing(dao, db.sport(), flowOf(Unit), flowOf(listOf(current)), cache, SystemClock,
                scope, Dispatchers.Default, log) to scope
        }
        var (pairing, scope) = controller()
        try {
            pairing.setActive(true)
            val initial = withTimeout(5_000) { pairing.streams.first { it[game.id]?.size == 2 } }.getValue(game.id)
            assertEquals(listOf(Confidence.AVAILABLE), initial.map { it.automatic }.distinct())
            assertTrue(pairing.decide(game.id, "s:c2", Decision.REJECTED))
            // Results publish before the atomic cache write; wait for that observable state.
            withTimeout(5_000) { while (file.length() == 0L) delay(10) }
            scope.cancel()
            val generation = DataInputStream(file.inputStream()).use { input ->
                input.readInt()
                input.readInt()
                input.readUTF()
            }
            // Recreate build 127's persisted Possible results; keep its generation except the rules.
            val oldGeneration = generation.substringBeforeLast("|rules:") + "|rules:4"
            cache.write(oldGeneration, listOf(current), mapOf(current.id to initial.map {
                it.copy(automatic = Confidence.POSSIBLE, decision = null)
            }), System.currentTimeMillis())
            val beforeReads = reads.get()
            controller().also { pairing = it.first; scope = it.second }
            pairing.setActive(true)
            val corrected = withTimeout(5_000) { pairing.streams.first { it[game.id]?.size == 2 } }.getValue(game.id)
            assertEquals(listOf(Confidence.AVAILABLE), corrected.map { it.automatic }.distinct())
            assertEquals(Confidence.REJECTED, corrected.single { it.channelKey == "s:c2" }.confidence)
            assertTrue("old date rules must query channels again", reads.get() > beforeReads)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun identityCorrectionsRescanScoresStayCheapAndRestartKeepsChoices() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 0, 0, 0))
        db.channelImport().insert(listOf(channel(1, "Northbridge - Harbor", null, null), channel(2, "Lake Wolves - Harbor", null, null)))
        val ref = ProviderReference("fixture", "football", ReferenceKind.TEAM, "1")
        val events = MutableStateFlow(listOf(game.copy(startMillis = System.currentTimeMillis(), home = game.home.copy(reference = ref))))
        val calls = AtomicInteger()
        val reads = AtomicInteger()
        val dao = object : PairingDao by db.pairing() {
            override suspend fun channels(after: Long, limit: Int): List<PairingDao.ScanChannel> {
                reads.incrementAndGet()
                return db.pairing().channels(after, limit)
            }
        }
        var revision = "one"
        val dictionary = PairingDictionary { current ->
            calls.incrementAndGet()
            DictionaryNames(revision, current.associate { event ->
                event.id to EventNames(homeId = event.home.reference?.externalId,
                    home = if (event.home.reference?.externalId == "2") setOf("lake wolves") else emptySet())
            })
        }
        val file = cacheDir.newFile()
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable?>()
        val log = object : DiagnosticsLog {
            override fun info(event: String, message: String) = Unit
            override fun error(event: String, message: String?, error: Throwable?) { errors += error }
            override fun snapshot(): List<String> = emptyList()
        }
        fun controller(): Pair<StreamPairing, CoroutineScope> {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            return StreamPairing(dao, db.sport(), flowOf(Unit), events, PairingCache(file), SystemClock, scope, Dispatchers.Default, log, dictionary) to scope
        }
        var (pairing, scope) = controller()
        try {
            pairing.setActive(true)
            withTimeout(5_000) { pairing.streams.first { it[game.id]?.size == 1 } }
            val firstReads = reads.get()
            val firstCalls = calls.get()
            events.value = events.value.map { it.copy(score = "2 - 1", minute = "67") }
            delay(600)
            assertEquals(firstReads, reads.get())
            assertEquals(firstCalls, calls.get())
            events.value = events.value.map { it.copy(home = it.home.copy(reference = ref.copy(externalId = "2"))) }
            withTimeout(5_000) { pairing.streams.first { it[game.id]?.size == 2 } }
            assertTrue(reads.get() > firstReads)
            assertTrue(pairing.decide(game.id, "s:c2", Decision.REJECTED))
            val savedReads = reads.get()
            scope.cancel()
            controller().also { pairing = it.first; scope = it.second }
            pairing.setActive(true)
            val restored = withTimeout(5_000) { pairing.streams.first { it[game.id]?.size == 2 } }
            assertEquals(Confidence.REJECTED, restored.getValue(game.id).single { it.channelKey == "s:c2" }.confidence)
            assertEquals("warm restart queried channels", savedReads, reads.get())
            scope.cancel()
            revision = "two"
            controller().also { pairing = it.first; scope = it.second }
            pairing.setActive(true)
            val rescored = withTimeout(5_000) { pairing.streams.first { it[game.id]?.size == 2 } }
            assertEquals(Confidence.REJECTED, rescored.getValue(game.id).single { it.channelKey == "s:c2" }.confidence)
            assertTrue("new dictionary must rescan", reads.get() > savedReads)
            assertTrue("controller errors $errors", errors.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun pagesFindTheGuideAndNameMatchesOfVisibleChannelsOnly() {
        seed()
        val (scan, streams) = scan()
        assertEquals(listOf("s:c600", "s:c601", "s:c602"), streams.map { it.channelKey }.sorted())
        val guide = streams.first { it.channelKey == "s:c600" }
        assertEquals(MatchSource.GUIDE, guide.source)
        assertEquals("Football: Northbridge v Harbor", guide.programmeTitle)
        assertEquals(-15L, guide.offsetMinutes)
        assertEquals(Confidence.AVAILABLE, guide.confidence)
        // One guide id, two channels: both get the programme.
        assertEquals(guide.programmeId, streams.first { it.channelKey == "s:c601" }.programmeId)
        assertEquals(MatchSource.NAME, streams.first { it.channelKey == "s:c602" }.source)
        // 900 visible channels in pages of 256 read four pages; the window keeps the programme reads small.
        assertEquals(4, scan.lastStats.channelQueries)
        assertTrue(scan.lastStats.programmeQueries in 4..40)
    }

    @Test
    fun theSourceOffsetShiftsTheWindowAndTheStart() {
        seed(offsetMinutes = 60)
        val (_, streams) = scan()
        val guide = streams.first { it.channelKey == "s:c600" }
        assertEquals(-15L, guide.offsetMinutes)
        assertEquals(kickOff - 15 * minute, guide.programmeStartMillis)
    }

    @Test
    fun aDecidedChannelWithoutEvidenceStaysListed() {
        seed()
        val (_, streams) = scan(mapOf((game.id to "s:c5") to Decision.REJECTED))
        assertEquals(Confidence.REJECTED, streams.first { it.channelKey == "s:c5" }.confidence)
    }

    @Test
    fun aRestrictedProfileIsOfferedOnlyItsGroups() = runBlocking {
        seed()
        db.profiles().allow(ProfileAllowedGroupEntity("kids", "LIVE", "news"))
        assertEquals(emptyList<String>(), db.pairing().allowed(listOf("s:c600", "s:c602"), "kids"))
        assertEquals(listOf("s:c600", "s:c602"), db.pairing().allowed(listOf("s:c600", "s:c602"), "adult").sorted())
    }

    @Test
    fun aCompletedPlaylistImportReplacesPartialCachedMatches() = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 0, 0, 0))
        val importing = SourceStatusEntity("s", "playlist", "running", 1, null, null, null, null, 0, 0, 7, null, null)
        db.sourceStatus().upsert(importing)
        val firstPage = listOf(channel(1, "Northbridge v Harbor 1", null, null))
        db.channelImport().insert(firstPage)
        val event = game.copy(startMillis = System.currentTimeMillis())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val log = object : DiagnosticsLog {
            override fun info(event: String, message: String) = Unit
            override fun error(event: String, message: String?, error: Throwable?) = Unit
            override fun snapshot(): List<String> = emptyList()
        }
        val pairing = StreamPairing(
            db.pairing(), db.sport(), db.invalidationTracker.createFlow("source_status").map { },
            flowOf(listOf(event)), PairingCache(cacheDir.newFile()), SystemClock, scope, Dispatchers.Default, log,
        )
        try {
            pairing.setActive(true)
            withTimeout(5_000) { pairing.streams.first { it[event.id]?.size == 1 } }
            // The generation was reserved before streaming the playlist. The final page and
            // visibility rules are applied before this same generation is marked complete.
            db.channelImport().insert((2..14).map { channel(it, "Northbridge v Harbor $it", null, null) })
            db.sourceStatus().upsert(importing.copy(status = "success", lastSuccessAt = 2, itemCount = 14))
            val completed = withTimeout(5_000) { pairing.streams.first { it[event.id]?.size == 14 } }
            assertEquals(14, completed.getValue(event.id).size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun datedCountryStreamsSurviveThePagedScanAndKeepViewerDecisions() = runBlocking {
        seed()
        val date = LocalDate.now(ZoneOffset.UTC)
        val event = game.copy(
            home = Side("Tappara", null), away = Side("KooKoo", null),
            startMillis = date.atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        // Place fourteen PPV listings beyond the existing 900 channels, across a page boundary.
        db.channelImport().insert((900 until 914).map { i ->
            val clock = listOf("19:15 EEST", "18:15 CEST", "06:00 UTC")[i % 3]
            channel(i, "NEXT | TAPPARA - KOOKOO | $date $clock | PPV $i", null, null)
        })
        val decisions = mapOf((event.id to "s:c913") to Decision.REJECTED)
        val scan = PairingScan(db.pairing())
        val results = scan.run(listOf(event), emptyMap(), decisions).getValue(event.id)
        assertEquals(14, results.size)
        assertTrue(results.all { it.automatic == Confidence.AVAILABLE })
        assertEquals(13, results.count { it.confidence == Confidence.AVAILABLE })
        assertEquals(Confidence.REJECTED, results.single { it.channelKey == "s:c913" }.confidence)
        assertEquals(4, scan.lastStats.channelQueries)
    }
}
