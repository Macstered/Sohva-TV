package com.sohva.tv.feature.trakt

import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktKind
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.shelf.TraktRowSource
import com.sohva.tv.feature.trakt.store.TraktAccount
import com.sohva.tv.feature.trakt.sync.TraktSync
import com.sohva.tv.feature.trakt.sync.TraktSyncLoop
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Home's added Trakt rows (spec 02 HOME-FR-94): public charts, watchlists, lifetimes and the loop. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TraktRowsTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private val server = MockWebServer()
    private val bodies = ConcurrentHashMap<String, String>()

    /** Each request's path, its Authorization header (or "-") and its query. */
    private val seen = ConcurrentLinkedQueue<Triple<String, String, String>>()
    private val clock = FakeClock()

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath.removePrefix("/")
                seen += Triple(path, request.headers["Authorization"] ?: "-", request.url.query.orEmpty())
                val body = bodies[path] ?: return MockResponse.Builder().code(503).body("{}").build()
                return MockResponse.Builder().code(200).body(body).build()
            }
        }
        server.start()
    }

    @After
    fun stop() = server.close()

    private fun host(allowed: Boolean = true, rows: List<String> = emptyList(), scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined), io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined) = TraktHost(
        TraktCredentials("fictional-client-id", "not-a-real-secret"), { OkHttpClient() }, store(), clock,
        TraktDispatchers(io, Dispatchers.Unconfined), { allowed }, FakeLog(), scope,
        monotonic = { 0L }, forgetCache = {}, rowsShown = { rows }, authOrigin = server.url("/"), apiOrigin = server.url("/"),
    )

    private fun signIn(h: TraktHost, profile: String) =
        h.store.saveAccount(profile, TraktAccount("viewer", "u-$profile", TraktTokens("acc", "ref", Long.MAX_VALUE / 2, TraktHost.DAY_MS * 90), false))

    private fun charts() {
        // Trending wraps each title with its watchers; popular is the title itself; box office wraps with revenue.
        bodies["movies/trending"] = """[{"watchers":40,"movie":{"title":"Film A","year":2026,"ids":{"trakt":1,"tmdb":11},"images":{"poster":["img.example/a.jpg"]}}},
            {"watchers":20,"movie":{"title":"No id"}},{"watchers":9,"movie":{"title":"Film B","ids":{"trakt":2}}}]"""
        bodies["shows/popular"] = """[{"title":"Show A","year":2020,"ids":{"trakt":5,"tmdb":55}}]"""
        bodies["movies/boxoffice"] = """[{"revenue":1000,"movie":{"title":"Film C","ids":{"trakt":3}}}]"""
    }

    private fun titles(h: TraktHost, profile: String, source: TraktRowSource) = runBlocking { h.rowLists.read(profile, source)?.cards?.map { it.title } }

    @Test
    fun chartsAreReadWithoutASignInAndWrappedTitlesAreUnwrapped() = runBlocking {
        charts()
        val h = host()
        TraktSync(h, FakeTable(), h.shelves).rows("p", listOf(HomeLayout.TRAKT_TRENDING_MOVIES, HomeLayout.TRAKT_POPULAR_SHOWS, HomeLayout.TRAKT_BOX_OFFICE))
        assertEquals(listOf("Film A", "Film B"), titles(h, "p", TraktRowSource.TRENDING_MOVIES))
        assertEquals("https://img.example/a.jpg", h.rowLists.read("p", TraktRowSource.TRENDING_MOVIES)!!.cards.first().poster)
        assertEquals(TraktKind.SHOW, h.rowLists.read("p", TraktRowSource.POPULAR_SHOWS)!!.cards.single().kind)
        assertEquals(listOf("Film C"), titles(h, "p", TraktRowSource.BOX_OFFICE))
        // A profile without an account: no Authorization header at all, and at most 30 titles asked for.
        assertEquals(3, seen.size)
        seen.forEach { (path, auth, query) ->
            assertEquals(path, "-", auth)
            assertTrue(query, "limit=30" in query && "extended=full,images" in query)
        }
    }

    @Test
    fun aRowNeverHoldsMoreThanThirtyTitles() = runBlocking {
        bodies["movies/popular"] = (1..40).joinToString(",", "[", "]") { """{"title":"Film $it","ids":{"trakt":$it}}""" }
        val h = host()
        TraktSync(h, FakeTable(), h.shelves).rows("p", listOf(HomeLayout.TRAKT_POPULAR_MOVIES))
        assertEquals(TraktRowSource.TITLES, titles(h, "p", TraktRowSource.POPULAR_MOVIES)!!.size)
    }

    @Test
    fun watchlistsNeedTheAccountAndSendItsToken() = runBlocking {
        bodies["sync/watchlist/shows/rank"] = """[{"rank":1,"id":77,"listed_at":"2026-09-01T10:00:00.000Z","type":"show","show":{"title":"Show W","ids":{"trakt":9}}}]"""
        val h = host()
        val sync = TraktSync(h, FakeTable(), h.shelves)
        sync.rows("q", listOf(HomeLayout.TRAKT_WATCHLIST_SHOWS))
        assertTrue("no account, no request: $seen", seen.isEmpty())
        signIn(h, "p")
        sync.rows("p", listOf(HomeLayout.TRAKT_WATCHLIST_SHOWS))
        assertEquals(listOf("Show W"), titles(h, "p", TraktRowSource.WATCHLIST_SHOWS))
        assertEquals("Bearer acc", seen.single().second)
    }

    @Test
    fun listsAreFetchedAgainOnlyWhenOlderThanTheirLifetime() = runBlocking {
        charts()
        bodies["sync/watchlist/movies/rank"] = """[{"rank":1,"type":"movie","movie":{"title":"Film W","ids":{"trakt":8}}}]"""
        val h = host()
        signIn(h, "p")
        val sync = TraktSync(h, FakeTable(), h.shelves)
        val ids = listOf(HomeLayout.TRAKT_TRENDING_MOVIES, HomeLayout.TRAKT_WATCHLIST_MOVIES)
        sync.rows("p", ids)
        assertEquals(2, seen.size)
        seen.clear()
        clock.now += 50 * 60 * 1000L
        sync.rows("p", ids)
        assertTrue(seen.toString(), seen.isEmpty())
        // A request (a row just added, a sign-in) refetches the watchlist, never a fresh chart.
        sync.rows("p", ids, force = true)
        assertEquals(listOf("sync/watchlist/movies/rank"), seen.map { it.first })
        seen.clear()
        // An hour after that the watchlist is old; the chart (almost two hours) is not.
        clock.now += 61 * 60 * 1000L
        sync.rows("p", ids)
        assertEquals(listOf("sync/watchlist/movies/rank"), seen.map { it.first })
        seen.clear()
        clock.now += 6 * 60 * 60 * 1000L
        sync.rows("p", ids)
        assertEquals(2, seen.size)
    }

    @Test
    fun aFailureKeepsTheStoredList() = runBlocking {
        charts()
        val h = host()
        val sync = TraktSync(h, FakeTable(), h.shelves)
        sync.rows("p", listOf(HomeLayout.TRAKT_TRENDING_MOVIES))
        bodies.remove("movies/trending")
        clock.now += 7 * 60 * 60 * 1000L
        sync.rows("p", listOf(HomeLayout.TRAKT_TRENDING_MOVIES))
        assertEquals(listOf("Film A", "Film B"), titles(h, "p", TraktRowSource.TRENDING_MOVIES))
    }

    @Test
    fun chartsAreSharedAndWatchlistsGoWithTheirAccount() = runBlocking {
        charts()
        bodies["sync/watchlist/movies/rank"] = """[{"rank":1,"type":"movie","movie":{"title":"Film W","ids":{"trakt":8}}}]"""
        val h = host()
        signIn(h, "p")
        val sync = TraktSync(h, FakeTable(), h.shelves)
        sync.rows("p", listOf(HomeLayout.TRAKT_TRENDING_MOVIES, HomeLayout.TRAKT_WATCHLIST_MOVIES))
        seen.clear()
        // Another profile reads the same chart without a request.
        sync.rows("q", listOf(HomeLayout.TRAKT_TRENDING_MOVIES))
        assertTrue(seen.toString(), seen.isEmpty())
        assertEquals(listOf("Film A", "Film B"), titles(h, "q", TraktRowSource.TRENDING_MOVIES))
        h.disconnect("p")
        assertNull(h.store.secret(TraktRowSource.WATCHLIST_MOVIES.key("p")))
        h.rowLists.clear()
        assertNull(titles(h, "p", TraktRowSource.WATCHLIST_MOVIES))
        assertEquals(listOf("Film A", "Film B"), titles(h, "p", TraktRowSource.TRENDING_MOVIES))
    }

    /** HOME-FR-99: a public list mixes films and series; its name is read once when the row has none. */
    @Test
    fun aPublicListMixesKindsAndKeepsItsName() = runBlocking {
        bodies["lists/26421/items/movie,show"] = """[{"rank":1,"type":"movie","movie":{"title":"Film L","ids":{"trakt":11}}},
            {"rank":2,"type":"show","show":{"title":"Show L","ids":{"trakt":12}}}]"""
        bodies["lists/26421"] = """{"name":"Fictional favourites","privacy":"public","item_count":2,"likes":5,"ids":{"trakt":26421},"user":{"username":"viewer-one"}}"""
        val h = host()
        val sync = TraktSync(h, FakeTable(), h.shelves)
        val source = TraktRowSource.list(26421)
        sync.rows("p", listOf(source.id))
        val shelf = h.rowLists.read("p", source)!!
        assertEquals(listOf(TraktKind.MOVIE, TraktKind.SHOW), shelf.cards.map { it.kind })
        assertEquals("Fictional favourites", shelf.title)
        assertEquals(listOf("lists/26421/items/movie,show", "lists/26421"), seen.map { it.first })
        assertTrue(seen.all { it.second == "-" })
        // Six hours later only the items are asked; the name is kept.
        seen.clear()
        clock.now += 7 * 60 * 60 * 1000L
        sync.rows("p", listOf(source.id))
        assertEquals(listOf("lists/26421/items/movie,show"), seen.map { it.first })
        assertEquals("Fictional favourites", h.rowLists.read("p", source)!!.title)
    }

    @Test
    fun listSummariesAndSearchReadPublicListsOnly() = runBlocking {
        bodies["users/viewer-one/lists/best-films"] = """{"name":"Best films","privacy":"public","item_count":40,"likes":3,"ids":{"trakt":77,"slug":"best-films"},"user":{"username":"viewer-one"}}"""
        bodies["lists/78"] = """{"name":"Secret","privacy":"private","ids":{"trakt":78}}"""
        bodies["search/list"] = """[{"type":"list","score":10,"list":{"name":"Nordic noir","privacy":"public","item_count":25,"likes":9,"ids":{"trakt":90},"user":{"username":"someone"}}},
            {"type":"list","list":{"name":"Hidden","privacy":"private","ids":{"trakt":91}}}]"""
        val h = host()
        val info = h.api.listSummary(com.sohva.tv.feature.trakt.protocol.TraktListRef.ByUser("viewer-one", "best-films"))!!
        assertEquals(com.sohva.tv.feature.trakt.protocol.TraktListInfo(77, "Best films", "viewer-one", 40, 3), info)
        assertNull("a private list is not offered", h.api.listSummary(com.sohva.tv.feature.trakt.protocol.TraktListRef.ById(78)))
        assertEquals(listOf("Nordic noir"), h.api.searchLists("nordic").map { it.name })
        assertTrue(seen.all { it.second == "-" })
    }

    @Test
    fun aRestrictedProfileFetchesNothing() = runBlocking {
        charts()
        val h = host(allowed = false)
        TraktSync(h, FakeTable(), h.shelves).rows("p", listOf(HomeLayout.TRAKT_TRENDING_MOVIES))
        assertTrue(seen.toString(), seen.isEmpty())
    }

    @Test
    fun theLoopRefreshesShownChartsWithoutAnAccountButNotDuringPlayback() {
        charts()
        val scheduler = StandardTestDispatcher()
        val scope = TestScope(scheduler)
        val h = host(rows = listOf(HomeLayout.TRAKT_TRENDING_MOVIES), scope = scope, io = scheduler)
        val playing = MutableStateFlow(true)
        val loop = TraktSyncLoop(h, TraktSync(h, FakeTable(), h.shelves), h.shelves, playing, scope)
        loop.start("p")
        scope.runCurrent()
        assertTrue(seen.toString(), seen.isEmpty())
        playing.value = false
        scope.runCurrent()
        val deadline = System.currentTimeMillis() + 5_000
        while (seen.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            scope.runCurrent()
        }
        assertEquals(listOf("movies/trending"), seen.map { it.first })
        loop.stop()
        repeat(20) {
            Thread.sleep(10)
            scope.advanceUntilIdle()
        }
    }
}
