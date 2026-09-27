package com.sohva.tv.feature.trakt

import com.sohva.tv.core.data.database.TraktStateEntity
import com.sohva.tv.core.data.trakt.TraktStateTable
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.shelf.TraktShelf
import com.sohva.tv.feature.trakt.shelf.TraktShelfKind
import com.sohva.tv.feature.trakt.shelf.TraktShelves
import com.sohva.tv.feature.trakt.store.TraktAccount
import com.sohva.tv.feature.trakt.sync.TraktStateDiff
import com.sohva.tv.feature.trakt.sync.TraktSync
import com.sohva.tv.feature.trakt.sync.TraktSyncLoop
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** The cache in a sorted map, keyed like the table's primary key. */
class FakeTable : TraktStateTable {
    val rows = TreeMap<Pair<String, String>, TraktStateEntity>(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
    var writes = 0

    override suspend fun page(profile: String, kind: String, after: String, limit: Int): List<TraktStateEntity> = synchronized(rows) {
        rows.values.filter { it.profileId == profile && it.kind == kind && it.key > after }.take(limit)
    }

    override suspend fun write(profile: String, deletes: List<String>, upserts: List<TraktStateEntity>) {
        if (deletes.isEmpty() && upserts.isEmpty()) return
        synchronized(rows) {
            deletes.forEach { rows.remove(profile to it) }
            upserts.forEach { rows[profile to it.key] = it }
            writes++
        }
    }

    override suspend fun count(profile: String): Int = synchronized(rows) { rows.keys.count { it.first == profile } }

    override suspend fun paused(profile: String): List<TraktStateEntity> = synchronized(rows) {
        rows.values.filter { it.profileId == profile && it.progress > 0 && it.progress < 100 }
    }

    override suspend fun byKeys(profile: String, keys: List<String>): List<TraktStateEntity> = synchronized(rows) { keys.mapNotNull { rows[profile to it] } }

    fun row(key: String): TraktStateEntity? = synchronized(rows) { rows["p" to key] }
}

/** Spec 51 §11 "Sync" and "Home rows", against a fake Trakt. The account and titles are fictional. */
@OptIn(ExperimentalCoroutinesApi::class)
class TraktSyncTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private val server = MockWebServer()
    private val bodies = ConcurrentHashMap<String, String>()
    private val paths = ConcurrentLinkedQueue<String>()

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath.removePrefix("/")
                paths += path
                val body = bodies[path] ?: return MockResponse.Builder().code(404).body("{}").build()
                return MockResponse.Builder().code(200).body(body).build()
            }
        }
        server.start()
    }

    @After
    fun stop() = server.close()

    private val clock = FakeClock()

    private fun host(prefs: FakePrefs = FakePrefs()) = TraktHost(
        TraktCredentials("fictional-client-id", "not-a-real-secret"), { OkHttpClient() }, store(prefs), clock,
        TraktDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined), { true }, FakeLog(), CoroutineScope(Dispatchers.Unconfined),
        monotonic = { 0L }, forgetCache = {}, authOrigin = server.url("/"), apiOrigin = server.url("/"),
    ).also { it.store.saveAccount("p", TraktAccount("viewer", "u", TraktTokens("acc", "ref", Long.MAX_VALUE / 2, TraktHost.DAY_MS * 90), false)) }

    private fun activities(mw: String, mp: String, ew: String, ep: String) {
        bodies["sync/last_activities"] =
            """{"movies":{"watched_at":"$mw","paused_at":"$mp"},"episodes":{"watched_at":"$ew","paused_at":"$ep"}}"""
    }

    private val t1 = "2026-09-01T10:00:00.000Z"
    private val t2 = "2026-09-02T10:00:00.000Z"
    private val t3 = "2026-09-03T10:00:00.000Z"

    private fun fullAccount() {
        activities(t1, t1, t1, t1)
        bodies["sync/watched/movies"] = """[{"plays":2,"last_watched_at":"$t1","movie":{"ids":{"trakt":1,"tmdb":603,"imdb":"tt0133093"}}},
            {"plays":1,"last_watched_at":"$t1","movie":{"ids":{"trakt":2,"imdb":"tt0000002"}}},
            {"plays":1,"last_watched_at":"$t1","movie":{"ids":{"trakt":3}}}]"""
        bodies["sync/watched/shows"] = """[{"last_watched_at":"$t1","show":{"ids":{"trakt":10,"tmdb":1399}},
            "seasons":[{"number":1,"episodes":[{"number":1,"plays":1,"last_watched_at":"$t1"},{"number":2,"plays":1,"last_watched_at":"$t1"}]}]}]"""
        bodies["sync/playback"] = """[{"type":"movie","progress":40.0,"paused_at":"$t1","movie":{"ids":{"tmdb":604}}},
            {"type":"episode","progress":10.0,"paused_at":"$t1","show":{"ids":{"tmdb":1399}},"episode":{"season":1,"number":3}}]"""
        bodies["shows/10/progress/watched"] = """{"last_watched_at":"$t1","next_episode":{"season":1,"number":3,"title":"Episode three"}}"""
        bodies["shows/10"] = """{"title":"A fictional show","year":2011,"ids":{"trakt":10,"tmdb":1399},"images":{"poster":["img.example/p.jpg"],"fanart":["img.example/f.jpg"]}}"""
    }

    @Test
    fun mergeKeepsWatchedAndPausedSidesApart() {
        val watched = TraktStateEntity("p", "movie:tmdb:1", "movie", 1, null, null, null, 0.0, true, 3, 100)
        val paused = TraktStateEntity("p", "movie:tmdb:1", "movie", 1, null, null, null, 55.0, false, 0, 200)
        // A rewatch in progress: both sides, later time.
        assertEquals(watched.copy(progress = 55.0, updatedAt = 200), TraktStateDiff.merge(null, watched, paused, true, true))
        // Only the playback list changed: the stored watched side stays, the pause goes.
        val stored = watched.copy(progress = 55.0, updatedAt = 200)
        assertEquals(stored.copy(progress = 0.0), TraktStateDiff.merge(stored, null, null, false, true))
        // Unwatched and no longer paused: the row goes.
        assertNull(TraktStateDiff.merge(paused, null, null, true, true))
    }

    @Test
    fun aFirstSyncFetchesEverythingSkipsIdlessTitlesAndBuildsWatchNext() = runBlocking {
        fullAccount()
        val h = host()
        val table = FakeTable()
        assertTrue(TraktSync(h, table, h.shelves).sync("p"))
        assertEquals(true, table.row("movie:tmdb:603")?.watched)
        assertEquals(2, table.row("movie:tmdb:603")?.plays)
        assertEquals("tt0000002", table.row("movie:imdb:tt0000002")?.imdb)
        assertEquals(40.0, table.row("movie:tmdb:604")?.progress)
        assertEquals(true, table.row("episode:tmdb:1399:1:2")?.watched)
        assertEquals(10.0, table.row("episode:tmdb:1399:1:3")?.progress)
        // A title with only a Trakt id is skipped.
        assertEquals(6, table.count("p"))
        val next = h.shelves.read("p", TraktShelfKind.WATCH_NEXT)!!.cards.single()
        assertEquals("Episode three", next.episodeTitle)
        assertEquals("https://img.example/p.jpg", next.poster)
        assertEquals(1L, h.store.long("format:p"))
    }

    @Test
    fun nothingNewFetchesNothingAndOnlyWhatMovedIsFetched() = runBlocking {
        fullAccount()
        val h = host()
        val table = FakeTable()
        val sync = TraktSync(h, table, h.shelves)
        sync.sync("p")
        paths.clear()
        val writes = table.writes
        assertTrue(sync.sync("p"))
        assertEquals(listOf("sync/last_activities"), paths.toList())
        assertEquals(writes, table.writes)
        // A new pause: the playback list only; the watched marks stay.
        activities(t1, t2, t1, t1)
        bodies["sync/playback"] = """[{"type":"movie","progress":70.0,"paused_at":"$t2","movie":{"ids":{"tmdb":603}}}]"""
        paths.clear()
        assertTrue(sync.sync("p"))
        assertEquals(listOf("sync/last_activities", "sync/playback", "sync/last_activities"), paths.toList())
        assertEquals(70.0, table.row("movie:tmdb:603")?.progress)
        assertEquals(true, table.row("movie:tmdb:603")?.watched)
        assertNull(table.row("movie:tmdb:604"))
        assertEquals(0.0, table.row("episode:tmdb:1399:1:2")?.progress)
        assertNull(table.row("episode:tmdb:1399:1:3"))
    }

    @Test
    fun aFailedPageLeavesTheCacheAndStampsAsTheyWere() = runBlocking {
        fullAccount()
        val h = host()
        val table = FakeTable()
        val sync = TraktSync(h, table, h.shelves)
        sync.sync("p")
        val before = table.rows.toMap()
        activities(t3, t1, t1, t1)
        bodies.remove("sync/watched/movies")
        assertFalse(sync.sync("p"))
        assertEquals(before, table.rows.toMap())
        assertEquals(java.time.Instant.parse(t1).toEpochMilli(), h.store.long("activity-mw:p"))
        // The next cycle fetches the films again once Trakt answers.
        bodies["sync/watched/movies"] = """[{"plays":3,"last_watched_at":"$t3","movie":{"ids":{"tmdb":603}}}]"""
        assertTrue(sync.sync("p"))
        assertEquals(3, table.row("movie:tmdb:603")?.plays)
        assertNull(table.row("movie:imdb:tt0000002"))
    }

    @Test
    fun anOlderFormatForcesAFullWalk() = runBlocking {
        fullAccount()
        val prefs = FakePrefs()
        val h = host(prefs)
        val table = FakeTable()
        val sync = TraktSync(h, table, h.shelves)
        sync.sync("p")
        h.store.putLong("format:p", 0)
        paths.clear()
        sync.sync("p")
        assertTrue(paths.toString(), paths.contains("sync/watched/movies") && paths.contains("sync/watched/shows"))
    }

    @Test
    fun recommendationsAreFetchedOnlyWhenOlderThanTwelveHours() = runBlocking {
        bodies["recommendations/movies"] = """[{"title":"Film A","year":2020,"ids":{"trakt":1}},{"title":"Film B","ids":{"trakt":2}}]"""
        bodies["recommendations/shows"] = """[{"title":"Show A","ids":{"trakt":3}}]"""
        val h = host()
        val sync = TraktSync(h, FakeTable(), h.shelves)
        sync.recommendations("p")
        assertEquals(listOf("Film A", "Show A", "Film B"), h.shelves.read("p", TraktShelfKind.RECOMMENDED)!!.cards.map { it.title })
        paths.clear()
        clock.now += 11 * 60 * 60 * 1000L
        sync.recommendations("p")
        assertTrue(paths.isEmpty())
        clock.now += 2 * 60 * 60 * 1000L
        sync.recommendations("p")
        assertEquals(2, paths.size)
    }

    @Test
    fun theDemoSeedsAnOfflineAccountOnceAndNeverCallsTrakt() = runBlocking {
        val h = TraktHost(
            TraktCredentials("fictional-client-id", "not-a-real-secret"), { OkHttpClient() }, store(), clock,
            TraktDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined), { true }, FakeLog(), CoroutineScope(Dispatchers.Unconfined),
            offline = true, monotonic = { 0L }, forgetCache = {}, authOrigin = server.url("/"), apiOrigin = server.url("/"),
        )
        com.sohva.tv.feature.trakt.demo.DemoTraktSeed.seed(h, "p")
        assertEquals("sohva-demo", h.account("p")?.username)
        assertEquals(3, h.shelves.read("p", TraktShelfKind.WATCH_NEXT)!!.cards.size)
        assertFalse(TraktSync(h, FakeTable(), h.shelves).sync("p", force = true))
        // A disconnect sticks: the next start seeds nothing.
        h.disconnect("p")
        com.sohva.tv.feature.trakt.demo.DemoTraktSeed.seed(h, "p")
        assertNull(h.account("p"))
        assertTrue(paths.toString(), paths.isEmpty())
    }

    @Test
    fun shelvesSurviveTheirCodec() {
        val shelf = TraktShelf(5, listOf(com.sohva.tv.feature.trakt.shelf.TraktCard(com.sohva.tv.feature.trakt.protocol.TraktKind.SHOW, com.sohva.tv.feature.trakt.protocol.TraktIds(trakt = 10, tmdb = 1399), "A \"fictional\" show", 2011, "Text", "https://img.example/p.jpg", null, 1, 3, "Three", 9)))
        assertEquals(shelf, TraktShelves.decode(TraktShelves.encode(shelf)))
    }

    @Test
    fun theLoopWaitsWhileVideoPlaysAndIdlesWithoutAnAccount() {
        val scheduler = StandardTestDispatcher()
        val scope = TestScope(scheduler)
        activities(t1, t1, t1, t1)
        val h = TraktHost(
            TraktCredentials("fictional-client-id", "not-a-real-secret"), { OkHttpClient() }, store(), clock,
            TraktDispatchers(scheduler, Dispatchers.Unconfined), { true }, FakeLog(), scope,
            monotonic = { 0L }, forgetCache = {}, authOrigin = server.url("/"), apiOrigin = server.url("/"),
        )
        val playing = MutableStateFlow(true)
        val sync = TraktSync(h, FakeTable(), h.shelves)
        val loop = TraktSyncLoop(h, sync, h.shelves, playing, scope)
        // No account: nothing is asked of Trakt, even after a cycle.
        loop.start("p")
        playing.value = false
        scope.runCurrent()
        scope.advanceTimeBy(TraktSyncLoop.CYCLE_MS + 1)
        scope.runCurrent()
        assertTrue(paths.toString(), paths.isEmpty())
        loop.stop()
        // With an account, the loop waits for playback to end before its first cycle.
        h.store.saveAccount("q", TraktAccount("viewer", "u", TraktTokens("acc", "ref", Long.MAX_VALUE / 2, TraktHost.DAY_MS * 90), false))
        playing.value = true
        loop.start("q")
        scope.runCurrent()
        assertTrue(paths.toString(), paths.isEmpty())
        playing.value = false
        scope.runCurrent()
        val deadline = System.currentTimeMillis() + 5_000
        while (!paths.contains("sync/last_activities") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            scope.runCurrent()
        }
        assertTrue(paths.toString(), paths.contains("sync/last_activities"))
        loop.stop()
        // Let the cancelled cycle finish on the test scheduler: its reads hold the app-wide permits.
        repeat(50) {
            Thread.sleep(20)
            scope.advanceUntilIdle()
        }
    }
}
