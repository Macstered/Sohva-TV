package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.BufferedSink
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * A live channel whose provider stops sending but keeps the connection open (spec 30 PLAY-FR-91,
 * decision "A live stream that stalls"). The player shows "Buffering…" and, left alone, waits for
 * the 90-second read timeout and then asks for the rest of the same stream by byte position.
 * The channel must be opened again from the start, as the viewer would by switching away and back.
 */
@RunWith(AndroidJUnit4::class)
class PlayerStreamStallTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val requests = ConcurrentLinkedQueue<RecordedRequest>()
    private val release = CountDownLatch(1)
    private val server = MockWebServer()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val serverRule = object : ExternalResource() {
        override fun before() {
            val stream = TestMedia.aacSegments(count = 1, seconds = STREAM_SECONDS).single()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    if (!request.url.encodedPath.startsWith("/live/")) return MockResponse.Builder().code(404).build()
                    return MockResponse.Builder().addHeader("Content-Type", "audio/aac").body(StallingBody(stream, release)).build()
                }
            }
            server.start()
            GuideFixture.seed(graph, groups = 1, perGroup = 2, stream = { _, i -> server.url("/live/$i").toString() })
        }

        override fun after() {
            release.countDown()
            server.close()
        }
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

    private fun opens(): List<RecordedRequest> = requests.filter { it.url.encodedPath == "/live/0" }

    private fun logged(text: String): Boolean = graph.diagnostics.snapshot().any { it.contains(text) }

    @Test
    fun aLiveStreamThatStallsIsOpenedAgainFromTheStart() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { opens().size == 1 }
        // The audio plays out, the player buffers, and well before the read timeout the channel opens again.
        compose.waitUntil(STREAM_SECONDS * 1_000L + REOPEN_WITHIN_MS) { opens().size >= 2 }
        compose.waitUntil(5_000) { logged("STATE_BUFFERING, attempt 1") }
        // A fresh open from the live edge, not a request for the rest of the stalled stream.
        assertNull("The second request must start the stream again", opens()[1].headers["Range"])
    }

    /** Sends [first] at once, then keeps the connection open with no more bytes until [release]. */
    private class StallingBody(private val first: ByteArray, private val release: CountDownLatch) : MockResponseBody {
        override val contentLength: Long = -1L

        override fun writeTo(sink: BufferedSink) {
            sink.write(first)
            sink.flush()
            release.await(HOLD_S, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val STREAM_SECONDS = 4

        /** The default profile's stall time (5 s rebuffer + 10 s) and the first attempt's 2 s, with margin; far below 90 s. */
        const val REOPEN_WITHIN_MS = 30_000L

        /** Longer than the test; the rule releases it at the end. */
        const val HOLD_S = 180L
    }
}
