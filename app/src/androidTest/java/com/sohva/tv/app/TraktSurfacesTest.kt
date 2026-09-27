package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.database.TraktStateEntity
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktKind
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.shelf.TraktCard
import com.sohva.tv.feature.trakt.shelf.TraktShelf
import com.sohva.tv.feature.trakt.shelf.TraktShelfKind
import com.sohva.tv.feature.trakt.store.TraktAccount
import java.net.InetAddress
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
 * Spec 51 §11 "Instrumented" for what Trakt's history shows: bars and ticks on the library, a
 * library title paused on Trakt in Continue watching, Discover posters and episode cards, a
 * Discover title resuming from Trakt, and Home's Watch next and Recommended rows with their routes.
 * The account, titles and ids are fictional; nothing talks to Trakt (the rows are written here).
 */
@RunWith(AndroidJUnit4::class)
class TraktSurfacesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val profile get() = graph.data.profiles.activeId
    private val addon = MockWebServer()
    private val now = System.currentTimeMillis()

    private val manifest = """{"id":"org.example.trakt","version":"1.0.0","name":"Fictional provider","types":["movie","series"],
        "resources":["catalog","meta"],"idPrefixes":["tt","tmdb:"],
        "catalogs":[{"type":"movie","id":"films","name":"Films"},{"type":"series","id":"shows","name":"Shows"}]}"""

    private val seed = object : ExternalResource() {
        override fun before() {
            addon.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    val body = when {
                        path.endsWith("/manifest.json") -> manifest
                        path.contains("/catalog/movie/films") -> """{"metas":[{"type":"movie","id":"tt0000001","name":"Fictional Film"}]}"""
                        path.contains("/catalog/series/shows") -> """{"metas":[{"type":"series","id":"tt0000002","name":"Fictional Show"}]}"""
                        path.contains("/meta/movie/tt0000001") -> """{"meta":{"type":"movie","id":"tt0000001","name":"Fictional Film"}}"""
                        path.contains("/meta/movie/tmdb:777") -> """{"meta":{"type":"movie","id":"tmdb:777","name":"Found Film"}}"""
                        path.contains("/meta/series/tt0000002") -> """{"meta":{"type":"series","id":"tt0000002","name":"Fictional Show","videos":[
                            {"id":"tt0000002:1:1","title":"Pilot","season":1,"episode":1},{"id":"tt0000002:1:2","title":"Second","season":1,"episode":2}]}}"""
                        else -> return MockResponse.Builder().code(404).build()
                    }
                    return MockResponse.Builder().body(body).build()
                }
            }
            addon.start(InetAddress.getByName("127.0.0.1"), 0)
            graph.trakt!!.store.saveAccount(
                profile,
                TraktAccount("fictional-viewer", "uuid-1", TraktTokens("not-a-real-token", "not-a-real-refresh", now + 30 * TraktHost.DAY_MS, 90 * TraktHost.DAY_MS), false),
            )
            // First sync done, so no note (HOME-FR-48).
            graph.trakt!!.store.putLong("activity:$profile", now)
        }

        override fun after() = addon.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun row(key: String, kind: String, tmdb: Long?, imdb: String?, progress: Double, watched: Boolean, season: Int? = null, number: Int? = null) =
        TraktStateEntity(profile, key, kind, tmdb, imdb, season, number, progress, watched, if (watched) 1 else 0, now)

    private fun write(vararg rows: TraktStateEntity) = runBlocking { graph.data.traktState.write(profile, emptyList(), rows.toList()) }

    private fun sql(statement: String, vararg args: Any?) = graph.data.database.openHelper.writableDatabase.execSQL(statement, args)

    private fun tagged(prefix: String, suffix: String) = SemanticsMatcher("tag $prefix…$suffix") { n ->
        n.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith(prefix) && it.endsWith(suffix) } == true
    }

    private fun present(matcher: SemanticsMatcher) = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun libraryTitlesShowTraktMarksAndAPausedFilmJoinsContinueWatching() {
        LibraryFixture.seed(graph, groups = listOf("Drama"), perGroup = 4, series = 1, episodes = 3)
        // Films 0 and 1 are TMDB 603 and 604; the series is TMDB 1399, matched by TMDB.
        sql("UPDATE movie SET work_key = 'tmdb:603' WHERE key = ?", LibraryFixture.key(0))
        sql("UPDATE movie SET work_key = 'tmdb:604' WHERE key = ?", LibraryFixture.key(1))
        sql(
            "INSERT OR REPLACE INTO metadata_match (content_key, media_type, status, provider, external_id, genre, genres_version, replacement_title, replacement_poster, replace_provider_poster, updated_at) " +
                "VALUES (?, 'series', 'matched', 'tmdb', '1399', NULL, 0, NULL, NULL, 0, 0)",
            LibraryFixture.seriesKey(0),
        )
        write(
            row("movie:tmdb:603", "movie", 603, null, 0.0, true),
            row("movie:tmdb:604", "movie", 604, null, 40.0, false),
            row("episode:tmdb:1399:1:1", "episode", 1399, null, 0.0, true, 1, 1),
            row("episode:tmdb:1399:1:2", "episode", 1399, null, 50.0, false, 1, 2),
        )
        // Continue watching gains the film paused on Trakt (FR-34).
        compose.waitUntil(15_000) { exists("home-resume-vod:${LibraryFixture.key(1)}") }
        // A series page reads Trakt's marks per episode, the pause placed by the playlist's runtime (FR-33).
        val series = runBlocking { graph.data.progress.ofSeries(LibraryFixture.seriesKey(0)) }
        assertEquals(true, series[LibraryFixture.episodeKey(0, 1)]?.completed)
        assertEquals(10_000L, series[LibraryFixture.episodeKey(0, 2)]?.positionMs)
        // The movie wall ticks the film Trakt says is watched (FR-32).
        compose.focusRail(RailItem.MOVIES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val group = "library-row-group:drama"
        compose.waitUntil(10_000) { exists(group) }
        compose.onNodeWithTag(group).performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val card = "library-card-${LibraryFixture.key(0)}"
        compose.waitUntil(10_000) { exists(card) }
        compose.waitUntil(10_000) { present(hasTestTag("library-tick") and hasAnyAncestor(hasTestTag(card))) }
        assertTrue(!present(hasTestTag("library-tick") and hasAnyAncestor(hasTestTag("library-card-${LibraryFixture.key(2)}"))))
    }

    @Test
    fun discoverCardsShowTraktMarksAndATitleResumesFromTrakt() {
        write(
            row("movie:imdb:tt0000001", "movie", null, "tt0000001", 50.0, false),
            row("episode:imdb:tt0000002:1:1", "episode", null, "tt0000002", 0.0, true, 1, 1),
        )
        graph.discover!!.testAllowHttp = true
        runBlocking { graph.discover!!.manager.install(profile, "http://127.0.0.1:${addon.port}/cfg/manifest.json") }
        compose.waitUntil(15_000) { exists(RailItem.DISCOVER.tag) }
        compose.onNodeWithTag(RailItem.DISCOVER.tag).performSemanticsAction(SemanticsActions.OnClick)
        // The film's poster carries Trakt's bar (ADDON-FR-71).
        val film = tagged("discover-card-", "-tt0000001")
        compose.waitUntil(15_000) { present(film) }
        compose.waitUntil(10_000) { present(hasTestTag("discover-progress") and hasAnyAncestor(film)) }
        // The series page's first episode carries the tick.
        val show = tagged("discover-card-", "-tt0000002")
        compose.waitUntil(10_000) { present(show) }
        compose.onAllNodes(show)[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { exists("discover-episode-tt0000002:1:1") }
        compose.waitUntil(10_000) { present(hasTestTag("discover-watched") and hasAnyAncestor(hasTestTag("discover-episode-tt0000002:1:1"))) }
        assertTrue(!present(hasTestTag("discover-watched") and hasAnyAncestor(hasTestTag("discover-episode-tt0000002:1:2"))))
        // The film's page offers Continue watching from Trakt's pause alone (FR-35, ADDON-FR-82).
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { present(film) }
        compose.onAllNodes(film)[0].performSemanticsAction(SemanticsActions.OnClick)
        val resume = instrumentation.targetContext.getString(com.sohva.tv.ui.design.R.string.home_hero_resume)
        compose.waitUntil(15_000) { present(hasText(resume, substring = true) and hasAnyAncestor(hasTestTag("discover-title-primary"))) }
    }

    @Test
    fun homeRowsRenderAndOpenTheLibraryOrTheLookup() {
        LibraryFixture.seed(graph, groups = listOf("Drama"), perGroup = 2)
        sql("UPDATE movie SET work_key = 'tmdb:603' WHERE key = ?", LibraryFixture.key(0))
        val inLibrary = TraktCard(TraktKind.MOVIE, TraktIds(trakt = 1, tmdb = 603), "A library film", 1999, null, null, null)
        val elsewhere = TraktCard(TraktKind.MOVIE, TraktIds(trakt = 2, tmdb = 777), "Found Film", 2001, null, null, null)
        val next = TraktCard(TraktKind.SHOW, TraktIds(trakt = 10, tmdb = 999), "A fictional show", 2011, null, null, null, 1, 2, "Second")
        runBlocking {
            graph.trakt!!.shelves.save(profile, TraktShelfKind.WATCH_NEXT, TraktShelf(now, listOf(next)))
            graph.trakt!!.shelves.save(profile, TraktShelfKind.RECOMMENDED, TraktShelf(now, listOf(inLibrary, elsewhere)))
        }
        graph.discover!!.testAllowHttp = true
        runBlocking { graph.discover!!.manager.install(profile, "http://127.0.0.1:${addon.port}/cfg/manifest.json") }
        compose.waitUntil(15_000) { exists("home-trakt-${next.key}") && exists("home-trakt-${inLibrary.key}") }
        // A card with a library copy opens its details page (FR-30 step 1).
        compose.onNodeWithTag("home-trakt-${inLibrary.key}").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(5_000) { present(hasTestTag("home-trakt-${inLibrary.key}") and isFocused()) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-film") }
        press(KeyEvent.KEYCODE_BACK)
        // A card without one looks the title up in the addons and opens its Discover page (step 2).
        compose.waitUntil(10_000) { exists("home-trakt-${elsewhere.key}") }
        compose.onNodeWithTag("home-trakt-${elsewhere.key}").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(5_000) { present(hasTestTag("home-trakt-${elsewhere.key}") and isFocused()) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { exists("screen-discover-title") }
        compose.waitUntil(10_000) { present(hasTestTag("discover-title-name") or hasText("Found Film")) }
        press(KeyEvent.KEYCODE_BACK)
        // A show no addon knows: the lookup says so and Back to home leaves. Watch next is above the
        // restored focus, out of the lazy list's window, so scroll to it first.
        compose.waitUntil(10_000) { exists("home-rows") }
        compose.onNodeWithTag("home-rows").performScrollToKey("watch-next")
        compose.waitUntil(10_000) { exists("home-trakt-${next.key}") }
        compose.onNodeWithTag("home-trakt-${next.key}").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(5_000) { present(hasTestTag("home-trakt-${next.key}") and isFocused()) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val missing = instrumentation.targetContext.getString(com.sohva.tv.ui.design.R.string.trakt_title_unavailable)
        compose.waitUntil(20_000) { present(hasTestTag("trakt-title-status") and hasText(missing)) }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("screen-home") }
    }
}
