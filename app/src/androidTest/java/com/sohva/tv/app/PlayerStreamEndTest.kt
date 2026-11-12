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
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * A live channel whose provider closes the connection (spec 30 PLAY-FR-91, decision "A live stream
 * that ends"). A live stream has no length, so Media3 reads the closed connection as the end of the
 * media: the buffering indicator goes away and the last frame stays on screen with no error. The
 * player must open the channel again, as it does after a dropped connection.
 */
@RunWith(AndroidJUnit4::class)
class PlayerStreamEndTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val requests = ConcurrentLinkedQueue<String>()
    private val server = MockWebServer()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val serverRule = object : ExternalResource() {
        override fun before() {
            // A few seconds of a live audio stream, sent without a length (chunked), then a clean close.
            val stream = TestMedia.aacSegments(count = 1, seconds = STREAM_SECONDS).single()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    requests += path
                    if (!path.startsWith("/live/")) return MockResponse.Builder().code(404).build()
                    return MockResponse.Builder().addHeader("Content-Type", "audio/aac").chunkedBody(Buffer().write(stream), CHUNK_BYTES).build()
                }
            }
            server.start()
            GuideFixture.seed(graph, groups = 1, perGroup = 2, stream = { _, i -> server.url("/live/$i").toString() })
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private fun press(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun opened(): Int = requests.count { it == "/live/0" }

    private fun logged(text: String): Boolean = graph.diagnostics.snapshot().any { it.contains(text) }

    @Test
    fun aLiveStreamThatEndsIsOpenedAgain() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { opened() == 1 }
        // The stream plays out, ends, and the channel opens again after the first attempt's delay.
        compose.waitUntil(STREAM_SECONDS * 1_000L + 15_000) { opened() >= 2 }
        // Counted and named like a dropped connection.
        compose.waitUntil(5_000) { logged("STATE_ENDED, attempt 1") }
    }

    private companion object {
        const val STREAM_SECONDS = 4
        const val CHUNK_BYTES = 4_096
    }
}
