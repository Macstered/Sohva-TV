package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 40 §11 and spec 30 §4.21: a film resumes from its saved place and, finished, returns to its
 * page marked watched; a finished episode continues to the next one. Real playback of the tests'
 * 20-second clip from their own server.
 */
@RunWith(AndroidJUnit4::class)
class LibraryPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val requests = ConcurrentLinkedQueue<String>()
    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request.url.encodedPath
                    return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip.readBytes())).build()
                }
            }
            server.start()
            LibraryFixture.seed(graph, perGroup = 6, stream = { server.url("/vod/$it.mp4").toString() }, series = 1, episodes = 2)
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun text(tag: String): String = compose.onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    private fun open(rail: RailItem, row: String, card: String) {
        compose.waitUntil(15_000) { exists(rail.tag) }
        compose.focusRail(rail)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists(row) }
        compose.onNodeWithTag(row).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(row)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists(card) }
        compose.onNodeWithTag(card).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(card)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    @Test
    fun aFilmResumesAndFinishedReturnsToItsPageMarkedWatched() {
        runBlocking { graph.data.progress.save(LibraryFixture.key(0), 12_000, 20_000) }
        open(RailItem.MOVIES, "library-row-group:drama", "library-card-${LibraryFixture.key(0)}")
        compose.waitUntil(10_000) { exists("screen-film") }
        awaitFocus("details-watch")
        assertTrue(text("details-watch"), text("details-watch").contains("Resume"))
        assertTrue(exists("details-progress"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/vod/0.mp4") }
        // Eight seconds of clip, then the page again with the film watched and Watch focused.
        compose.waitUntil(40_000) { exists("screen-film") && !exists("screen-player") }
        awaitFocus("details-watch")
        compose.waitUntil(10_000) { text("details-mark").contains("unwatched") }
        assertTrue(runBlocking { graph.data.progress.of(LibraryFixture.key(0)) }!!.completed)
        // Back returns to the wall on the same card, now ticked as watched (VOD-FR-37).
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitFocus("library-card-${LibraryFixture.key(0)}")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("library-tick", useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
    }

    @Test
    fun aFinishedEpisodeContinuesToTheNextThenReturnsToTheSeries() {
        runBlocking { graph.data.progress.save(LibraryFixture.episodeKey(0, 1), 12_000, 20_000) }
        open(RailItem.SERIES, "library-row-group:crime", "library-card-${LibraryFixture.seriesKey(0)}")
        compose.waitUntil(10_000) { exists("screen-series-page") }
        awaitFocus("details-watch")
        assertTrue(text("details-watch"), text("details-watch").contains("Continue"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/vod/10001.mp4") }
        // The first episode ends; the second starts from the beginning in its place.
        compose.waitUntil(40_000) { requests.contains("/vod/10002.mp4") }
        assertTrue(exists("screen-player"))
        compose.waitUntil(45_000) { exists("screen-series-page") && !exists("screen-player") }
        val progress = runBlocking { graph.data.progress.ofSeries(LibraryFixture.seriesKey(0)) }
        assertEquals(setOf(true), progress.values.map { it.completed }.toSet())
        assertEquals(2, progress.size)
    }
}
