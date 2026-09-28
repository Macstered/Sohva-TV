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
import org.junit.Assert.assertFalse
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

    /**
     * Home as the viewer presses it: below Android 12 the app's leave hint enters the corner; from 12
     * the system does, on the Home key itself (auto-enter). The emulator only: never a real TV.
     */
    private fun leave() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            instrumentation.runOnMainSync { instrumentation.callActivityOnUserLeaving(compose.activity) }
        }
        instrumentation.waitForIdleSync()
    }

    /** Polls without Compose: a window in the corner has no hierarchy the rule can wait on. */
    private fun waitFor(timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in $timeout ms" }
            Thread.sleep(100)
        }
    }

    /** PLAY-26: a changed buffer profile applies to the next playback (the idle player is rebuilt). */
    @Test
    fun aChangedBufferProfileAppliesToTheNextPlayback() {
        openPlayerOnFirstChannel()
        val graph = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
        kotlinx.coroutines.runBlocking { graph.data.preferences.setBuffer(com.sohva.tv.core.model.player.BufferProfile.STABILITY) }
        press(KeyEvent.KEYCODE_CHANNEL_UP)
        compose.waitUntil(10_000) { graph.diagnostics.snapshot().any { it.contains("buffer profile STABILITY") } }
    }

    /**
     * Seen on the owner's Shield: with the buffer profile changed, the first playback had sound and a
     * black picture. The service rebuilt its idle player for the profile, and the picture's surface
     * stayed with the old one. The rebuilt player must draw frames: the session reports each first
     * frame to every controller, so a controller of the test's own counts them.
     */
    @Test
    fun theRebuiltPlayerForAChangedBufferProfileShowsThePicture() {
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as SohvaApplication).graph
        val frames = AtomicInteger()
        var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null
        instrumentation.runOnMainSync {
            val token = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, com.sohva.tv.core.player.PlaybackService::class.java))
            future = androidx.media3.session.MediaController.Builder(context, token).buildAsync()
        }
        val watcher = future!!.get(10, java.util.concurrent.TimeUnit.SECONDS)
        instrumentation.runOnMainSync {
            watcher.addListener(object : androidx.media3.common.Player.Listener {
                override fun onRenderedFirstFrame() {
                    frames.incrementAndGet()
                }
            })
        }
        try {
            openPlayerOnFirstChannel()
            compose.waitUntil(15_000) { frames.get() >= 1 }
            kotlinx.coroutines.runBlocking { graph.data.preferences.setBuffer(com.sohva.tv.core.model.player.BufferProfile.STABILITY) }
            press(KeyEvent.KEYCODE_CHANNEL_UP)
            compose.waitUntil(10_000) { graph.diagnostics.snapshot().any { it.contains("buffer profile STABILITY") } }
            val before = frames.get()
            try {
                compose.waitUntil(15_000) { frames.get() > before }
            } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("no picture after the player was rebuilt for the profile", e)
            }
        } finally {
            instrumentation.runOnMainSync { watcher.release() }
        }
    }

    private fun openPlayerOnFirstChannel() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0", 10_000)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists("screen-player") }
    }

    /**
     * Spec 30 PLAY-FR-110, -111: with "Keep watching in a corner" on, Home while the player is on
     * top shrinks it to the corner, drawing the picture only; full screen brings the box back; the
     * corner's Close finishes the app. Off, nothing shrinks.
     */
    @Test
    fun homeShrinksThePlayerToTheCornerOnlyWhenTheSettingIsOn() {
        // Some TV images (the API 34 emulator) have no corner windows at all; the app then ignores the setting.
        org.junit.Assume.assumeTrue(instrumentation.targetContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE))
        val graph = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
        openPlayerOnFirstChannel()
        awaitRequest("/live/0.mp4")
        // Off: no corner (from Android 12 Home would really leave the app, so it is checked below 12).
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            leave()
            Thread.sleep(1_000)
            assertFalse("off: no corner", compose.activity.isInPictureInPictureMode)
        }
        kotlinx.coroutines.runBlocking { graph.data.preferences.setPictureInPicture(true) }
        compose.waitUntil(5_000) { graph.pictureInPictureOn.value }
        leave()
        // The window is the corner now; the screen draws the picture only while the flag is set.
        val activity = compose.activity
        waitFor { activity.isInPictureInPictureMode && graph.inPictureInPicture.value }
        activity.sendBroadcast(android.content.Intent(MainActivity.ACTION_CLOSE_CORNER).setPackage(activity.packageName))
        waitFor { activity.isFinishing || activity.isDestroyed }
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
