package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.feature.home.RailItem
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
 * Spec 20 GUIDE-FR-62…65 and spec 41 §11 "Guide hero": with TMDB on, the selected programme is
 * looked up once the selection rests, and the hero shows the year, the "TMDB 7.8" chip (read from
 * the search result, spec 41 Q1), the overview and "Source: TMDB". TMDB is the test's own server.
 */
@RunWith(AndroidJUnit4::class)
class GuideHeroMetadataTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val queries = ConcurrentLinkedQueue<String>()
    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    // Every programme is a film of its own name, so each lookup matches.
                    val query = request.url.queryParameter("query").orEmpty()
                    queries += query
                    val body = """{"results":[{"media_type":"movie","id":${700 + query.length},"title":"$query","release_date":"2010-05-01",""" +
                        """"overview":"A tale from the test server.","vote_average":7.84,"popularity":2.0}]}"""
                    return MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()
                }
            }
            server.start()
            graph.metadata.useEndpoints(server.url("/3/"), server.url("/tvmaze/"))
            GuideFixture.seed(graph, sources = 1)
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

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    /** A chip carries its text itself; a button's text is merged up from its label. */
    private fun text(tag: String): String = listOf(true, false).firstNotNullOfOrNull { unmerged ->
        runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }.orEmpty()

    private fun awaitFocus(tag: String) = compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }

    @Test
    fun theSelectedProgrammeGetsItsYearRatingOverviewAndSource() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        awaitFocus(RailItem.LIVE_TV.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("guide-row-0") }
        awaitFocus("guide-row-0")
        compose.waitUntil(10_000) { exists("guide-hero-rating") }
        assertEquals("TMDB 7.8", text("guide-hero-rating"))
        assertTrue(text("guide-metadata-attribution").contains("TMDB"))
        assertTrue(compose.onAllNodes(hasText("A tale from the test server."), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("2010", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // Rows passed on the way at key-repeat pace are not looked up: only where the selection rests.
        val before = queries.size
        repeat(4) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.waitForIdle()
        compose.waitUntil(10_000) { exists("guide-hero-rating") }
        Thread.sleep(1_000)
        assertTrue("lookups: ${queries.toList()}", queries.size - before <= 2)
    }
}
