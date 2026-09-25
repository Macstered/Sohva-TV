package com.sohva.tv.core.sync

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.DataGraph
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.concurrent.AppDispatchers
import com.sohva.tv.core.model.concurrent.PauseGate
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.core.model.time.SystemClock
import com.sohva.tv.core.net.http.ProviderHttp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The owner-scale import of M1's exit criteria (spec 10 §9, plan/07): the whole fixture from the
 * host's fixture server (`python tools/fixture/serve_fixture.py`), through the real runner, cipher
 * and file database, on the low-end stand-in, with the Java heap sampled every 250 ms.
 *
 * Runs only when asked: `-Pandroid.testInstrumentationRunnerArguments.ownerScale=true`. Results go
 * to logcat under the tag `SohvaMeasure`.
 */
@RunWith(AndroidJUnit4::class)
class OwnerScaleImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun importsTheOwnerScaleFixtureWithinBudget() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ownerScale") == "true")
        context.deleteDatabase(SohvaDatabase.FILE_NAME)
        val threads = BackgroundDispatchers()
        val data = DataGraph(context, threads)
        val log = object : DiagnosticsLog {
            override fun info(event: String, message: String) {
                Log.i(TAG, "$event $message")
            }
            override fun error(event: String, message: String?, error: Throwable?) {
                Log.e(TAG, "$event $message", error)
            }
            override fun snapshot(): List<String> = emptyList()
        }
        val env = ImportEnvironment(
            db = data.database,
            http = ProviderHttp(ProviderHttp.client(ProviderHttp.userAgent("measure", "11")), log),
            sealer = StreamSealer { data.cipher.encrypt(it) },
            clock = SystemClock,
            dispatchers = threads,
            pauseGate = PauseGate(MutableStateFlow(false), MutableStateFlow(false)),
            log = log,
            names = object : FallbackNames {
                override fun channel(number: Int) = "Channel $number"
                override fun episode(number: Int) = "Episode $number"
            },
        )
        val runner = ImportRunner(env, data.sources, CoroutineScope(SupervisorJob() + threads.io))

        suspend fun save(config: SourceConfig) = assertTrue((data.sources.save(config) as? Outcome.Ok) != null)
        save(SourceConfig(Source("live", "Live", SourceType.M3U, importScope = ImportScope.BOTH), SourceSecrets(m3uUrl = "$BASE/playlist.m3u")))
        save(SourceConfig(Source("films", "Films", SourceType.M3U, importScope = ImportScope.VOD), SourceSecrets(m3uUrl = "$BASE/vod.m3u")))
        save(
            SourceConfig(
                Source("panel", "Panel", SourceType.XTREAM),
                SourceSecrets(xtreamBaseUrl = BASE, xtreamUsername = "fixture", xtreamPassword = "fixture"),
            ),
        )

        val heap = HeapSampler().also { it.start() }
        val results = LinkedHashMap<String, Long>()
        suspend fun step(name: String, source: String, kind: RefreshKind) {
            val begun = System.nanoTime()
            runner.sync(source, setOf(kind), WorkOrigin.VIEWER).join()
            results[name] = (System.nanoTime() - begun) / 1_000_000
            val row = data.database.openHelper.readableDatabase
                .query("SELECT status, error_code, item_count FROM source_status WHERE source_id = ? AND kind = ?", arrayOf(source, kind.id))
                .use { c -> if (c.moveToFirst()) "${c.getString(0)} ${c.getString(1)} ${c.getInt(2)}" else "none" }
            Log.i(TAG, "$name: ${results[name]} ms, $row, heap max so far ${heap.maxMb()} MB")
        }
        step("m3u playlist", "live", RefreshKind.PLAYLIST)
        step("m3u guide", "live", RefreshKind.EPG)
        step("m3u playlist unchanged", "live", RefreshKind.PLAYLIST)
        step("m3u catalogue (films, series)", "films", RefreshKind.CATALOGUE)
        step("m3u catalogue unchanged", "films", RefreshKind.CATALOGUE)
        step("xtream playlist", "panel", RefreshKind.PLAYLIST)
        step("xtream guide", "panel", RefreshKind.EPG)
        step("xtream catalogue", "panel", RefreshKind.CATALOGUE)
        step("xtream catalogue unchanged", "panel", RefreshKind.CATALOGUE)
        heap.finish()

        // Search at owner scale (spec 03 §9: each group within 100 ms on the stand-in), warm, median of 5.
        for (term in listOf("ha", "north", "signal kitchen", "zz")) {
            val groups = linkedMapOf<String, suspend () -> Int>(
                "live" to { data.search.live(term).let { it.channels.size + it.programmes.size } },
                "films" to { data.search.films(term).size },
                "series" to { data.search.series(term).size },
                "episodes" to { data.search.episodes(term).size },
            )
            for ((group, run) in groups) {
                var found = 0
                val times = (1..5).map {
                    val begun = System.nanoTime()
                    found = run()
                    (System.nanoTime() - begun) / 1_000_000
                }.sorted()
                Log.i(TAG, "search '$term' $group: median ${times[2]} ms, max ${times.last()} ms, $found rows")
            }
        }

        val counts = listOf("channel", "programme", "movie", "series", "episode").associateWith { table ->
            data.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        }
        Log.i(TAG, "rows $counts; Java heap max ${heap.maxMb()} MB of ${Runtime.getRuntime().maxMemory() / MB} MB")
        // 200,000 films each from the M3U VOD playlist and the Xtream panel; 1,500 series each.
        assertEquals(400_000, counts.getValue("movie"))
        assertEquals(3_000, counts.getValue("series"))
        assertEquals(18_000, counts.getValue("episode"))
        assertTrue("Java heap ${heap.maxMb()} MB", heap.maxMb() < 128)
    }

    /** Samples used Java heap on its own thread; the maximum is what the budget limits. */
    private class HeapSampler : Thread("HeapSampler") {
        private val max = AtomicLong()

        @Volatile private var running = true

        override fun run() {
            val runtime = Runtime.getRuntime()
            while (running) {
                max.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory()) { a, b -> maxOf(a, b) }
                sleep(250)
            }
        }

        fun finish() {
            running = false
            join()
        }

        fun maxMb(): Long = max.get() / MB
    }

    /** The app's dispatchers: two background-priority import threads, as `AndroidDispatchers`. */
    private class BackgroundDispatchers : AppDispatchers {
        private fun thread(name: String): CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }, name)
        }.asCoroutineDispatcher()

        private val pool = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        override val main: CoroutineDispatcher = pool
        override val ui: CoroutineDispatcher = pool
        override val io: CoroutineDispatcher = pool
        override val bulk: CoroutineDispatcher = thread("SohvaBulk")
        override val bulkWrite: CoroutineDispatcher = thread("SohvaBulkWrite")
    }

    private companion object {
        const val TAG = "SohvaMeasure"
        const val BASE = "http://10.0.2.2:8780"
        const val MB = 1024L * 1024
    }
}
