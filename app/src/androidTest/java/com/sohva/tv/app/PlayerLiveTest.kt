package com.sohva.tv.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Window
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 30 §11 "Emulator playback": live channels play through the session service from the tests'
 * own server with Sohva's user agent; zapping, Back to the guide, and the reconnect banner.
 */
@RunWith(AndroidJUnit4::class)
class PlayerLiveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val requests = ConcurrentLinkedQueue<Pair<String, String>>()

    @Volatile
    private var failing = false

    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request.url.encodedPath to request.headers["User-Agent"].orEmpty()
                    if (failing) return MockResponse.Builder().code(503).build()
                    return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip.readBytes())).build()
                }
            }
            server.start()
            val app = instrumentation.targetContext.applicationContext as SohvaApplication
            GuideFixture.seed(app.graph, groups = 1, perGroup = 6, stream = { _, i -> server.url("/live/$i.mp4").toString() })
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

    private fun awaitFocus(tag: String, timeout: Long = 5_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun awaitRequest(path: String, timeout: Long = 15_000) {
        compose.waitUntil(timeout) { requests.any { it.first == path } }
    }

    private fun openPlayerOnFirstChannel() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0", 10_000)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists("screen-player") }
    }

    @Test
    fun playsWithSohvasAgentAndTheLiveBoxHidesAfterFiveSeconds() {
        openPlayerOnFirstChannel()
        awaitRequest("/live/0.mp4")
        assertTrue(requests.first { it.first == "/live/0.mp4" }.second.startsWith("Sohva TV/"))
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("player-live-box") }
        compose.waitUntil(8_000) { !compose.onAllNodesWithTagExists("player-live-box") }
        awaitFocus("player-video")
    }

    @Test
    fun channelKeysZapAndBackReturnsToTheGuideOnTheWatchedChannel() {
        openPlayerOnFirstChannel()
        awaitRequest("/live/0.mp4")
        // CH− steps to the next channel (REMOTE-FR-13).
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        awaitRequest("/live/1.mp4")
        // Back hides the box first, then leaves (spec 30 §3.2).
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("player-live-box") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !compose.onAllNodesWithTagExists("player-live-box") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists("screen-guide") }
        awaitFocus("guide-row-1", 10_000)
    }

    /**
     * Spec 30 §9 "Nothing composed while hidden": with the box gone, a playing stream draws no UI
     * frames at all (the video is a hardware overlay; the UI thread should be idle).
     */
    @Test
    fun hiddenOverlaysDrawNoFramesWhilePlaying() {
        openPlayerOnFirstChannel()
        awaitRequest("/live/0.mp4")
        compose.waitUntil(10_000) { !compose.onAllNodesWithTagExists("player-live-box") }
        compose.waitForIdle()
        SystemClock.sleep(1_000)
        val frames = AtomicInteger()
        val listener = Window.OnFrameMetricsAvailableListener { _, _, _ -> frames.incrementAndGet() }
        val handler = Handler(Looper.getMainLooper())
        compose.activityRule.scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, handler) }
        // Wait by pumping the test's frame clock, not by sleeping: a sleep would stop Compose too and prove nothing.
        val end = SystemClock.uptimeMillis() + 15_000
        compose.waitUntil(20_000) { SystemClock.uptimeMillis() >= end }
        compose.activityRule.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        assertTrue("UI frames drawn while overlays were hidden: ${frames.get()}", frames.get() <= 1)
    }

    @Test
    fun aFailingStreamCountsItsAttemptsAndReconnectRecovers() {
        failing = true
        openPlayerOnFirstChannel()
        compose.waitUntil(10_000) { compose.onAllNodesWithTextExists("Reconnecting 1/3", substring = true) }
        compose.waitUntil(30_000) { compose.onAllNodesWithTextExists("Automatic reconnection stopped", substring = true) }
        awaitFocus("player-reconnect")
        failing = false
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { !compose.onAllNodesWithTagExists("player-banner") }
    }
}
