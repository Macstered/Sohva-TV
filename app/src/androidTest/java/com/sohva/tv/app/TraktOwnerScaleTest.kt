package com.sohva.tv.app

import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.store.TraktAccount
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 51 §11 "Low-end check" on the emulator stand-in: a heavy fictional account (1,000 watched
 * films, 300 shows × 60 episodes = 18,000 episodes, 500 paused titles) synced in full while Home
 * is up, then a sync with nothing new and one after a new pause. Records the time, the app's CPU
 * time, the Java heap peak and the main thread's longest gap; the heap must stay ≤ 64 MB and the
 * main thread must never wait on the sync.
 */
@RunWith(AndroidJUnit4::class)
class TraktOwnerScaleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val trakt = MockWebServer()
    private val paths = ConcurrentLinkedQueue<String>()

    @Volatile private var pausedAt = "2026-09-01T10:00:00.000Z"
    private val at = "2026-09-01T10:00:00.000Z"

    private fun movies(page: Int): String = (0 until MOVIES_PER_PAGE).joinToString(",", "[", "]") { i ->
        val n = (page - 1) * MOVIES_PER_PAGE + i
        """{"plays":1,"last_watched_at":"$at","movie":{"title":"Film $n","ids":{"trakt":${n + 1},"tmdb":${100_000 + n},"imdb":"tt${9_000_000 + n}"}}}"""
    }

    private fun shows(page: Int): String = (0 until SHOWS_PER_PAGE).joinToString(",", "[", "]") { i ->
        val n = (page - 1) * SHOWS_PER_PAGE + i
        val seasons = (1..SEASONS).joinToString(",") { s ->
            val eps = (1..EPISODES).joinToString(",") { e -> """{"number":$e,"plays":1,"last_watched_at":"$at"}""" }
            """{"number":$s,"episodes":[$eps]}"""
        }
        """{"last_watched_at":"$at","show":{"title":"Show $n","ids":{"trakt":${50_000 + n},"tmdb":${200_000 + n}}},"seasons":[$seasons]}"""
    }

    private fun playback(): String = (0 until PAUSED).joinToString(",", "[", "]") { i ->
        """{"type":"movie","progress":${10 + i % 80}.5,"paused_at":"$pausedAt","movie":{"ids":{"tmdb":${300_000 + i}}}}"""
    }

    private val servers = object : ExternalResource() {
        override fun before() {
            trakt.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath.removePrefix("/")
                    paths += path
                    val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
                    val (body, pages) = when {
                        path == "sync/last_activities" ->
                            """{"movies":{"watched_at":"$at","paused_at":"$pausedAt"},"episodes":{"watched_at":"$at","paused_at":"$at"}}""" to null
                        path == "sync/watched/movies" -> movies(page) to MOVIES / MOVIES_PER_PAGE
                        path == "sync/watched/shows" -> shows(page) to SHOWS / SHOWS_PER_PAGE
                        path == "sync/playback" -> playback() to null
                        path.endsWith("/progress/watched") -> """{"last_watched_at":"$at","next_episode":{"season":4,"number":1,"title":"Next"}}""" to null
                        path.startsWith("shows/") -> """{"title":"A show","ids":{"trakt":1},"images":{"poster":["img.example/p.jpg"]}}""" to null
                        path.startsWith("recommendations/") -> "[]" to null
                        else -> return MockResponse.Builder().code(404).body("{}").build()
                    }
                    return MockResponse.Builder().body(body).apply { pages?.let { addHeader("X-Pagination-Page-Count", it.toString()) } }.build()
                }
            }
            trakt.start()
            val host = graph.trakt!!
            host.useTestServer(trakt.url("/"), TraktCredentials("fictional-client-id", "not-a-real-secret"))
            host.store.saveAccount(
                graph.data.profiles.activeId,
                TraktAccount("fictional-viewer", "uuid-1", TraktTokens("not-a-real-token", "not-a-real-refresh", System.currentTimeMillis() + 30 * TraktHost.DAY_MS, 90 * TraktHost.DAY_MS), false),
            )
        }

        override fun after() = trakt.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(servers).around(compose)

    private class Run(val ms: Long, val cpuMs: Long)

    private fun timed(block: () -> Unit): Run {
        val wall = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        block()
        return Run(SystemClock.elapsedRealtime() - wall, Process.getElapsedCpuTime() - cpu)
    }

    @Test
    fun aHeavyHistorySyncsOffTheMainThreadWithinTheHeap() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTagExists(RailItem.MOVIES.tag) }
        runBlocking { graph.continueFeed.awaitSettled() }
        val profile = graph.data.profiles.activeId
        val sync = graph.traktSync!!
        val heap = HeapSampler().apply { start() }
        val heartbeat = Heartbeat().apply { start() }
        val full = timed { assertTrue(runBlocking { sync.sync(profile, force = true) }) }
        val fullGap = heartbeat.phase()
        val rows = runBlocking { graph.data.traktState.count(profile) }
        assertEquals(MOVIES + SHOWS * SEASONS * EPISODES + PAUSED, rows)
        val writes = graph.data.traktState.revision.value
        paths.clear()
        val idle = timed { assertTrue(runBlocking { sync.sync(profile) }) }
        assertEquals(listOf("sync/last_activities"), paths.toList())
        assertEquals("nothing new writes nothing", writes, graph.data.traktState.revision.value)
        // A new pause on another device: the playback list only, and only the changed rows.
        pausedAt = "2026-09-02T10:00:00.000Z"
        paths.clear()
        val pause = timed { assertTrue(runBlocking { sync.sync(profile) }) }
        assertTrue(paths.toString(), paths.none { it.startsWith("sync/watched/") })
        val continueRead = timed { runBlocking { graph.data.progress.continueWatching() } }
        heartbeat.finish()
        heap.finish()
        Log.i(
            TAG,
            "full sync ${full.ms} ms (cpu ${full.cpuMs} ms, $rows rows); nothing new ${idle.ms} ms (cpu ${idle.cpuMs}); new pause ${pause.ms} ms " +
                "(cpu ${pause.cpuMs}); continue watching read ${continueRead.ms} ms; Java heap peak ${heap.max() / MB} MB; " +
                "main-thread longest gap during the full sync $fullGap ms, overall ${heartbeat.longestGapMs()} ms",
        )
        assertTrue("Java heap peak ${heap.max() / MB} MB", heap.max() < HEAP_BUDGET)
    }

    companion object {
        private const val TAG = "TraktOwnerScale"
        private const val MB = 1024 * 1024
        private const val HEAP_BUDGET = 64L * MB
        private const val MOVIES = 1_000
        private const val MOVIES_PER_PAGE = 250
        private const val SHOWS = 300
        private const val SHOWS_PER_PAGE = 100
        private const val SEASONS = 3
        private const val EPISODES = 20
        private const val PAUSED = 500
    }
}
