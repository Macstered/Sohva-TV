package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.metadata.MetadataSettings
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.feature.home.RailItem
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
 * Spec 40 §4.10–4.11 and spec 41 §11 "Film page", "Series page": with TMDB on, the film page takes
 * the details' title, score, runtime and synopsis, shows "Source: TMDB", Versions, Cast and the
 * Similar films the library has, repairs the missing poster, and a Similar card opens that film's
 * page on top. The series page takes the show's title and cast line, and the selected episode's
 * runtime once the selection rests. "Wrong details?" pins the chosen record into the page and the
 * library and undoes it, and focus returns to "Wrong details?" every time the picker closes
 * (spec 40 §4.14, lessons 4.1). TMDB is the test's own server.
 */
@RunWith(AndroidJUnit4::class)
class TitleMetadataTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.url.encodedPath.endsWith("/search/movie") && request.url.queryParameter("query") == "Drama 0000" -> json(SEARCH)
                    request.url.encodedPath.endsWith("/movie/949") -> json(DETAILS)
                    request.url.encodedPath.endsWith("/search/movie") && request.url.queryParameter("query") == "Drama 0001" -> json(PICKER_SEARCH)
                    request.url.encodedPath.endsWith("/movie/555") -> json(PICKER_DETAILS)
                    request.url.encodedPath.endsWith("/search/tv") && request.url.queryParameter("query") == "Northern Line 0" -> json(SHOW_SEARCH)
                    request.url.encodedPath.endsWith("/tv/77") -> json(SHOW)
                    request.url.encodedPath.endsWith("/tv/77/season/1/episode/1") -> json(EPISODE.format(1, 48))
                    request.url.encodedPath.endsWith("/tv/77/season/1/episode/2") -> json(EPISODE.format(2, 52))
                    else -> json("""{"results":[]}""")
                }
            }
            server.start()
            graph.metadata.useEndpoints(server.url("/3/"), server.url("/tvmaze/"))
            LibraryFixture.seed(graph, perGroup = 6, series = 1, episodes = 2)
            val db = graph.data.database.openHelper.writableDatabase
            // Drama 0000 and Comedy 0000 are two copies of one film (VOD-FR-68).
            db.execSQL("UPDATE movie SET work_key = 'tmdb:949' WHERE key IN ('${LibraryFixture.key(0)}', '${LibraryFixture.key(6)}')")
            runBlocking {
                graph.data.secrets.write(ServiceKeys.TMDB_TOKEN, "fictional-token")
                graph.data.secrets.write(ServiceKeys.TMDB_ENABLED, "true")
                graph.metadata.settings.reload()
            }
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private fun json(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun focusAndPress(tag: String) {
        compose.waitUntil(10_000) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(tag)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
    }

    @Test
    fun theDetailsFillThePageAndSimilarOpensAnotherFilm() {
        focusAndPress(RailItem.MOVIES.tag)
        focusAndPress("library-row-group:drama")
        focusAndPress("library-card-${LibraryFixture.key(0)}")
        compose.waitUntil(10_000) { exists("screen-film") }
        awaitFocus("details-watch")
        compose.waitUntil(15_000) { text("details-title") == "Harbour Lights" }
        assertTrue(text("details-source"), text("details-source").contains("TMDB"))
        assertTrue(exists("details-cast"))
        assertTrue(exists("details-version-${LibraryFixture.key(0)}") && exists("details-version-${LibraryFixture.key(6)}"))
        // Data arriving never moves focus (AGENTS 5.2).
        awaitFocus("details-watch")
        // The film had no poster: the details' poster stands in for it (META-FR-71).
        val repaired = graph.data.database.openHelper.readableDatabase
            .query("SELECT replacement_poster, replace_poster FROM movie WHERE key = '${LibraryFixture.key(0)}'")
            .use { it.moveToFirst(); it.getString(0) to it.getInt(1) }
        assertEquals("/harbour.jpg" to 1, repaired)
        // TMDB names Drama 0003 (2003) as similar; the library has it. Drama 0004 has another year.
        val similar = "details-similar-${LibraryFixture.key(3)}"
        compose.waitUntil(10_000) { exists(similar) }
        assertTrue(!exists("details-similar-${LibraryFixture.key(4)}"))
        focusAndPress(similar)
        compose.waitUntil(10_000) { text("details-title") == "Drama 0003" }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { text("details-title") == "Harbour Lights" }
    }

    private fun shows(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theSeriesPageTakesTheShowAndTheSelectedEpisode() {
        runBlocking {
            graph.data.secrets.write(MetadataSettings.TVMAZE_ENABLED, "true")
            graph.metadata.settings.reload()
        }
        focusAndPress(RailItem.SERIES.tag)
        // With TVmaze on, the Series wall credits it (spec 41 Q9).
        compose.waitUntil(10_000) { exists("library-tvmaze-credit") }
        focusAndPress("library-row-group:crime")
        focusAndPress("library-card-${LibraryFixture.seriesKey(0)}")
        compose.waitUntil(10_000) { exists("screen-series-page") }
        compose.waitUntil(15_000) { text("details-title") == "Northern Lights" }
        compose.waitUntil(10_000) { text("details-cast-line").contains("Aino Example, Otto Sample") }
        assertTrue(text("details-source").contains("TMDB"))
        awaitFocus("details-watch")
        // The first episode is selected: its runtime replaces the 20-second duration once looked up.
        compose.waitUntil(10_000) { shows("48 min") }
        compose.onNodeWithTag("series-episode-${LibraryFixture.episodeKey(0, 2)}").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(10_000) { shows("52 min") }
        assertTrue(!shows("48 min"))
    }

    /** Spec 41 Q10: a card the focus rests on is looked up in the foreground, and the wall shows the new title. */
    @Test
    fun aRestingCardIsLookedUpWhileTheAppIsInFront() {
        focusAndPress(RailItem.MOVIES.tag)
        focusAndPress("library-row-group:drama")
        val card = "library-card-${LibraryFixture.key(0)}"
        compose.waitUntil(10_000) { exists(card) }
        compose.onNodeWithTag(card).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(card)
        try {
            compose.waitUntil(15_000) { replacementTitle(LibraryFixture.key(0)) == "Harbour Lights" }
        } catch (e: Throwable) {
            throw AssertionError("requests: ${server.requestCount}; match: " + graph.data.database.metadata().match(LibraryFixture.key(0)), e)
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(card) and hasText("Harbour Lights", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("Harbour Lights", replacementTitle(LibraryFixture.key(0)))
        // Data arriving never moves focus (AGENTS 5.2).
        awaitFocus(card)
    }

    private fun replacementTitle(key: String): String? = graph.data.database.openHelper.readableDatabase
        .query("SELECT replacement_title FROM movie WHERE key = '$key'").use { it.moveToFirst(); it.getString(0) }

    @Test
    fun wrongDetailsPinsTheChoiceAndUndoGivesItBack() {
        val film = LibraryFixture.key(1)
        focusAndPress(RailItem.MOVIES.tag)
        focusAndPress("library-row-group:drama")
        focusAndPress("library-card-$film")
        compose.waitUntil(10_000) { exists("screen-film") }
        // TMDB's answer for "Drama 0001" is another title: automatic matching leaves the page alone.
        compose.waitUntil(10_000) { text("details-title") == "Drama 0001" }
        focusAndPress("details-wrong")
        compose.waitUntil(10_000) { exists("match-picker") }
        awaitFocus("match-picker-search")
        assertTrue(!exists("match-picker-clear"))
        // The search ran on open with the cleaned provider name.
        focusAndPress("match-result-555")
        compose.waitUntil(10_000) { !exists("match-picker") }
        awaitFocus("details-wrong")
        compose.waitUntil(10_000) { text("details-title") == "Quiet Harbour" }
        assertEquals("Quiet Harbour", replacementTitle(film))
        // Open again: the choice can be undone.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("match-picker-clear") }
        focusAndPress("match-picker-clear")
        compose.waitUntil(10_000) { !exists("match-picker") }
        awaitFocus("details-wrong")
        compose.waitUntil(10_000) { text("details-title") == "Drama 0001" }
        assertEquals(null, replacementTitle(film))
        // Back closes the picker the same way.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("match-picker-search")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { !exists("match-picker") }
        awaitFocus("details-wrong")
        assertTrue(exists("screen-film"))
    }

    private companion object {
        const val PICKER_SEARCH = """{"results":[{"id":555,"title":"Quiet Harbour","release_date":"2001-04-01","overview":"A harbour at night.","popularity":1.0}]}"""
        const val PICKER_DETAILS = """{"id":555,"title":"Quiet Harbour","overview":"A harbour at night.","release_date":"2001-04-01","runtime":95,"genres":[{"id":18}]}"""
        const val SEARCH = """{"results":[{"id":949,"title":"Harbour Lights","original_title":"Drama 0000","release_date":"2000-03-01",""" +
            """"popularity":12.5,"genre_ids":[18]}]}"""
        const val DETAILS = """{"id":949,"title":"Harbour Lights","overview":"A keeper waits for a ship that never comes.",""" +
            """"poster_path":"/harbour.jpg","release_date":"2000-03-01","runtime":125,"vote_average":7.84,"genres":[{"id":18}],""" +
            """"credits":{"cast":[{"name":"Aino Example","character":"Keeper"},{"name":"Otto Sample","character":"Captain"}]},""" +
            """"similar":{"results":[{"id":1003,"title":"Drama 0003","release_date":"2003-06-01"},""" +
            """{"id":1004,"title":"Drama 0004","release_date":"1990-06-01"}]}}"""
        const val SHOW_SEARCH = """{"results":[{"id":77,"name":"Northern Lights","original_name":"Northern Line 0","first_air_date":"2020-02-01","popularity":3.0}]}"""
        const val SHOW = """{"id":77,"name":"Northern Lights","original_name":"Northern Line 0","overview":"A night train and its passengers.","first_air_date":"2020-02-01",""" +
            """"episode_run_time":[45],"vote_average":8.3,"genres":[{"id":80}],""" +
            """"credits":{"cast":[{"name":"Aino Example","character":"Driver"},{"name":"Otto Sample","character":"Guard"}]}}"""
        const val EPISODE = """{"id":%1${'$'}d,"name":"Chapter %1${'$'}d","season_number":1,"episode_number":%1${'$'}d,"runtime":%2${'$'}d}"""
    }
}
