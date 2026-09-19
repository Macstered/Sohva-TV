package com.streammate.tv.feature.today

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.*
import com.streammate.tv.core.model.*
import com.streammate.tv.matching.*
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Opt-in disposable-emulator stress test. Never seeds a user's library. */
class SportsMatchingMemoryTest {
    @Test fun largeEpgMatchingKeepsMemoryBoundedAndCanBeCancelled() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(arguments.getString("sportsMemoryStress") == "true")
        check(Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu")) {
            "This large synthetic fixture is for the disposable emulator only"
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "sports-memory-${System.nanoTime()}.db")
        val channelQueries = AtomicInteger()
        val programmeQueries = AtomicInteger()
        val heapPeak = AtomicLong()
        val database = Room.databaseBuilder(context, StreamMateDatabase::class.java, file.absolutePath)
            .setQueryCallback({ sql, _ ->
                if (sql.contains("sports-channel-page")) channelQueries.incrementAndGet()
                if (sql.contains("sports-programme-page")) programmeQueries.incrementAndGet()
            }, Executor { it.run() }).build()
        val dao = database.guideDao()
        val runtime = Runtime.getRuntime()
        val sample = launch(Dispatchers.Default) {
            while (isActive) {
                heapPeak.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory(), ::maxOf)
                delay(25)
            }
        }
        try {
            dao.upsertSourceState(IptvSourceStateEntity("memory", "Synthetic", "M3U", true, 1, 0, 1))
            val count = 56_164
            val description = "Synthetic unrelated programme description. ".repeat(50)
            for (first in 0 until count step 128) {
                val indices = first until minOf(first + 128, count)
                dao.upsertChannels(indices.map { index ->
                    IptvChannelEntity("memory", "playlist", "ch-$index", "xml-$index",
                        "Channel $index", "channel $index", "Sport", null, "synthetic", null, null, 1)
                })
                dao.insertProgrammes(indices.flatMap { index ->
                    (0..1).map { slot ->
                        TvProgrammeEntity("memory", "epg", "p-$index-$slot", "xml-$index",
                            KICKOFF + slot * 60_000, KICKOFF + (slot + 1) * 60_000,
                            if (index % 200 == 0 && slot == 1) "Real Betis vs Getafe" else "Unrelated programme $index",
                            null, description, "")
                    }
                })
                if (first % (128 * 100) == 0) Log.i(TAG, "seeded=$first")
            }
            dao.activatePlaylistSnapshot("memory", "playlist", count, 1)
            dao.activateEpgSnapshot("memory", "epg", count * 2, 1)
            val event = TodayEvent("memory-event", SportType.FOOTBALL, competition = "Synthetic",
                home = "Real Betis", away = "Getafe", startEpochMillis = KICKOFF,
                startMinuteOfDay = 19 * 60, startLabel = "19:00", status = TodayEventStatus.SCHEDULED,
                statusLabel = "Upcoming", score = null, matchingChannels = 0)
            val repository = EventChannelMatchingRepository(dao)
            heapPeak.set(runtime.totalMemory() - runtime.freeMemory())
            val before = SystemClock.elapsedRealtime()
            // Start from Main, as the real view model does. The repository must
            // move parsing/scoring off it and leave its heartbeat responsive.
            val heartbeat = AtomicInteger()
            val ticker = launch(Dispatchers.Main) { while (isActive) { heartbeat.incrementAndGet(); delay(50) } }
            val matches = try {
                withContext(Dispatchers.Main) { repository.matchesFor(listOf(event)) }
            } finally { ticker.cancelAndJoin() }
            val elapsed = SystemClock.elapsedRealtime() - before
            assertEquals((count + 199) / 200, matches.getValue(event.id).size)
            assertTrue(matches.getValue(event.id).all { it.confidence == ChannelMatchConfidence.AVAILABLE })
            Log.i(TAG, "channels=$count programmes=${count * 2} elapsedMs=$elapsed heapPeakBytes=${heapPeak.get()} heapLimitBytes=${runtime.maxMemory()} channelPages=${channelQueries.get()} programmePages=${programmeQueries.get()} mainHeartbeats=${heartbeat.get()}")
            // Leaves ample room for playback and the visible guide in a 192 MiB
            // process. The old complete description list alone exceeds that heap.
            assertTrue("heap peak ${heapPeak.get()}", heapPeak.get() < 128L * 1024 * 1024)
            assertTrue("main heartbeat stalled", heartbeat.get() >= elapsed / 250)
            val queriesAfterScan = channelQueries.get() + programmeQueries.get()
            assertEquals(matches, repository.matchesFor(listOf(event)))
            assertEquals(queriesAfterScan, channelQueries.get() + programmeQueries.get())

            val startedQueries = channelQueries.get()
            val cancelled = launch(Dispatchers.Main) { repository.matchesFor(listOf(event.copy(id = "cancelled"))) }
            withTimeout(10_000) { while (channelQueries.get() == startedQueries) delay(10) }
            withTimeout(2_000) { cancelled.cancelAndJoin() }
            val stoppedQueries = channelQueries.get() + programmeQueries.get()
            delay(250)
            assertEquals(stoppedQueries, channelQueries.get() + programmeQueries.get())
            Log.i(TAG, "cache-reuse=passed cancellation=passed")
        } finally {
            sample.cancelAndJoin()
            database.close()
            context.deleteDatabase(file.absolutePath)
        }
    }

    private companion object {
        const val KICKOFF = 1_000_000_000L
        const val TAG = "SportsMemoryTest"
    }
}
