package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * A reply the player cannot read as a stream is named in the diagnostics (spec 30 PLAY-FR-94), and
 * an HLS playlist served at an address without `.m3u8` is retried once as HLS.
 */
@RunWith(AndroidJUnit4::class)
class PlayerStreamReplyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val requests = ConcurrentLinkedQueue<String>()
    private val server = MockWebServer()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val serverRule = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    requests += path
                    return when (path) {
                        // Channel 0: a playlist at a plain address; its segment proves the HLS retry.
                        "/live/0" -> MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl")
                            .body("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:4.0,\nseg0.ts\n").build()
                        // Channel 1: the provider's web page instead of the stream.
                        "/live/1" -> MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8")
                            .body("<!DOCTYPE html><html><body>Service unavailable</body></html>").build()
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
            server.start()
            GuideFixture.seed(graph, groups = 1, perGroup = 4, stream = { _, i -> server.url("/live/$i").toString() })
        }
        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTagExists(tag)
    private fun press(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }
    private fun logged(text: String): Boolean = graph.diagnostics.snapshot().any { it.contains(text) }

    private fun play(row: Int) {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        repeat(row) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        awaitFocus("guide-row-$row")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    @Test
    fun aPlaylistAtAPlainAddressIsRetriedAsHls() {
        play(0)
        compose.waitUntil(15_000) { requests.contains("/live/seg0.ts") }
        compose.waitUntil(5_000) { logged("reply: playlist, type application/vnd.apple.mpegurl, same host") }
        // The playlist is fetched again by the HLS retry, not by a visible reconnect.
        assertTrue(requests.count { it == "/live/0" } >= 2)
        // The withheld failure never reached the screen, which logs every failure it shows.
        compose.waitForIdle()
        assertFalse(logged("ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"))
        assertFalse(graph.diagnostics.snapshot().any { it.contains("/live/") || it.contains(server.hostName + ":" + server.port) })
    }

    @Test
    fun aWebPageInsteadOfTheStreamIsNamedInTheDiagnostics() {
        play(1)
        compose.waitUntil(15_000) { exists("player-banner") }
        compose.waitUntil(5_000) { logged("reply: web page, type text/html, same host") }
        // A web page is not retried as a playlist.
        assertFalse(requests.any { it.startsWith("/live/seg") })
    }
}
