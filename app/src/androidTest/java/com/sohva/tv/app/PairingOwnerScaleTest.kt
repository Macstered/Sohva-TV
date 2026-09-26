package com.sohva.tv.app

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.PairingDao
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.TeamVariants
import com.sohva.tv.feature.sport.pairing.PairingCache
import com.sohva.tv.feature.sport.pairing.PairingScan
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 60 §11 "Performance" (mirrors beta 23's `SportsMatchingMemoryTest`): paged pairing on the
 * owner-scale synthetic guide — 56,164 channels and 112,328 programmes with 2,150-character
 * descriptions — completes with a Java heap peak under 16 MB, the main thread keeps ticking,
 * cancellation stops further queries within one page, and a second run is a cache hit without a
 * channel query. Runs only when asked: `-Pandroid.testInstrumentationRunnerArguments.ownerScale=true`.
 */
@RunWith(AndroidJUnit4::class)
class PairingOwnerScaleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val asked = InstrumentationRegistry.getArguments().getString("ownerScale") == "true"
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val now = System.currentTimeMillis()
    private val kickOff = (now / HOUR + 2) * HOUR

    /** Thirty games two hours from now, fifteen minutes apart; channel names and titles name a few of them. */
    private val games = (0 until GAMES).map { g ->
        SportEvent(
            "api-sports:football:$g", SportType.FOOTBALL, "39", "Premier League", null, Side("Northbridge $g", null), Side("Harbor $g", null),
            kickOff + g * 15 * MINUTE, 20 * 60, EventStatus.SCHEDULED, null, null, null,
        )
    }

    private val seed = object : ExternalResource() {
        override fun before() {
            if (!asked) return
            val db = graph.data.database
            val description = "A long synthetic programme description for the pairing measurement. ".repeat(32).take(2_150)
            val group = db.groupImport().insert(
                ContentGroupEntity(sourceId = SOURCE, room = "LIVE", groupKey = "all", name = "All", providerOrder = 0, itemCount = CHANNELS, shown = true, position = 0, sortMode = null),
            )
            val sealed = graph.data.cipher.encrypt("http://192.0.2.10/live/owner.ts")
            (0 until CHANNELS).chunked(CHUNK).forEach { part ->
                db.channelImport().insert(
                    part.map { i ->
                        // Every 2,000th channel's name carries a game: name matches spread over the whole scan.
                        val name = if (i % 2_000 == 7) "EVENT: Northbridge ${(i / 2_000) % GAMES} - Harbor ${(i / 2_000) % GAMES}" else "Channel $i HD"
                        ChannelEntity(
                            key = "$SOURCE:c$i", sourceId = SOURCE, groupId = group, name = name, sortName = name.lowercase(), providerName = name,
                            providerGroupId = group, providerLogoUrl = null, tvgId = "e$i", epgId = "e$i", logoUrl = null, streamUrlEnc = sealed,
                            userAgent = null, referrer = null, playlistOrder = i, providerNumber = i + 1, number = i + 1, displayRank = i.toLong(),
                            visible = true, catchupType = null, catchupSource = null, catchupDays = null, catchupTz = null, xtreamStreamId = null,
                            contentHash = 1, generation = 1,
                        )
                    },
                )
            }
            (0 until PROGRAMMES).chunked(CHUNK).forEach { part ->
                db.guideImport().insertProgrammes(
                    part.map { p ->
                        val channel = p / 2
                        val start = kickOff - HOUR + (p % 2) * 2 * HOUR
                        // Every 1,000th programme is one of the games; the rest never mention a team.
                        val title = if (p % 1_000 == 3) "Football: Northbridge ${(p / 1_000) % GAMES} v Harbor ${(p / 1_000) % GAMES}" else "Evening programme $p"
                        ProgrammeEntity(
                            sourceId = SOURCE, snapshot = 1, epgId = "e$channel", startAt = start, stopAt = start + 2 * HOUR, title = title,
                            subtitle = null, description = description, categories = null, programmeKey = "%016x".format(p.toLong()),
                        )
                    },
                )
            }
            db.sourceStatus().upsert(SourceStatusEntity(SOURCE, "epg", "success", now, now, null, null, null, PROGRAMMES, 0, 1, 1, 2 * HOUR))
            db.sourceStatus().upsert(SourceStatusEntity(SOURCE, "playlist", "success", now, now, null, null, null, CHANNELS, 0, 1, null, null))
            runBlocking { db.sources().upsert(SourceEntity(SOURCE, "Owner scale", "M3U", true, 0, 1, "LIVE_TV", 0, now, now)) }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed)

    /** Counts the scan's queries, so cancellation can be checked page by page. */
    private class Counting(private val dao: PairingDao) : PairingDao by dao {
        val channelPages = AtomicInteger()
        val programmePages = AtomicInteger()

        override suspend fun channels(after: Long, limit: Int): List<PairingDao.ScanChannel> {
            channelPages.incrementAndGet()
            return dao.channels(after, limit)
        }

        override suspend fun programmes(sourceId: String, snapshot: Long, epgIds: List<String>, from: Long, to: Long, after: Long, limit: Int): List<PairingDao.ScanProgramme> {
            programmePages.incrementAndGet()
            return dao.programmes(sourceId, snapshot, epgIds, from, to, after, limit)
        }
    }

    @Test
    fun pairingAnOwnerScaleGuideStaysSmallAndCancels() {
        assumeTrue(asked)
        val aliases = TeamVariants.aliases(emptyMap())
        val heartbeat = Heartbeat().also { it.start() }
        Runtime.getRuntime().gc()
        SystemClock.sleep(500)
        val runtime = Runtime.getRuntime()
        val baseline = runtime.totalMemory() - runtime.freeMemory()
        val heap = HeapSampler().also { it.start() }
        val dao = Counting(graph.data.pairingDao)
        val scan = PairingScan(dao)
        val started = SystemClock.elapsedRealtime()
        val results = runBlocking(graph.dispatchers.bulk) { scan.run(games, aliases, emptyMap()) }
        val scanMs = SystemClock.elapsedRealtime() - started
        heap.finish()
        heartbeat.finish()
        val available = results.values.sumOf { list -> list.count { it.confidence == Confidence.AVAILABLE } }
        Log.i(
            TAG,
            "scan ${scanMs} ms; ${dao.channelPages.get()} channel and ${dao.programmePages.get()} programme pages; " +
                "${scan.lastStats.candidates} candidates; $available available; Java heap baseline ${baseline / KB} KB, " +
                "peak ${heap.max() / KB} KB (+${(heap.max() - baseline) / KB} KB); main-thread longest gap ${heartbeat.longestGapMs()} ms",
        )
        assertEquals(GAMES, results.size)
        // The channel names carry games 0–27 and both teams: each of those has an Available stream.
        assertTrue("name matches", (0 until 28).all { g -> results.getValue("api-sports:football:$g").any { it.confidence == Confidence.AVAILABLE } })
        assertTrue("Java heap peak ${heap.max() / KB} KB", heap.max() < BUDGET_BYTES)
        assertTrue("main thread stalled ${heartbeat.longestGapMs()} ms", heartbeat.longestGapMs() < STALL_MS)

        // Cancellation: once cancelled, at most the page in flight finishes.
        val cancelled = Counting(graph.data.pairingDao)
        runBlocking(graph.dispatchers.bulk) {
            val job = async { PairingScan(cancelled).run(games, aliases, emptyMap()) }
            while (cancelled.channelPages.get() < 20) kotlinx.coroutines.delay(5)
            job.cancel()
            val atCancel = cancelled.channelPages.get() + cancelled.programmePages.get()
            runCatching { job.await() }
            val after = cancelled.channelPages.get() + cancelled.programmePages.get()
            Log.i(TAG, "cancelled: $atCancel pages at cancel, $after after")
            assertTrue("queries after cancel: ${after - atCancel}", after - atCancel <= 1)
        }

        // A second run for the same generation is a cache hit: no channel query at all.
        val cache = PairingCache(File(instrumentation.targetContext.cacheDir, "pairing-owner-scale.bin"))
        cache.write("generation", games, results, now)
        val hit = cache.read("generation", games, now)
        assertEquals(results.mapValues { (_, l) -> l.map { it.withDecision(null) } }, hit)
        cache.clear()
    }

    private companion object {
        const val TAG = "PairingOwnerScale"
        const val SOURCE = "owner"
        const val CHANNELS = 56_164
        const val PROGRAMMES = 112_328
        const val GAMES = 30
        const val CHUNK = 2_000
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val KB = 1024L
        const val BUDGET_BYTES = 16L * 1024 * 1024
        const val STALL_MS = 250L
    }
}
