package com.sohva.tv.app

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 31 §11 "Player driven by real key events": holds made of the platform's own repeats,
 * injected at the window like a remote's.
 */
@RunWith(AndroidJUnit4::class)
class PlayerRemoteTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
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
            GuideFixture.seed(graph, groups = 1, perGroup = 8, stream = { _, i -> server.url("/live/$i.mp4").toString() })
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    /** A held key: one down, [repeats] auto-repeats about 50 ms apart, one up (REMOTE-FR-05). */
    private fun hold(keyCode: Int, repeats: Int = 10) {
        val down = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, keyCode, 0))
        SystemClock.sleep(500)
        for (r in 1..repeats) {
            instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, r))
            SystemClock.sleep(50)
        }
        instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun openPlayer() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        awaitFocus(RailItem.LIVE_TV.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/live/0.mp4") }
        // The entry box times out, so the clean screen's keys decide.
        compose.waitUntil(10_000) { !exists("player-live-box") }
    }

    private fun count(path: String) = requests.count { it == path }

    @Test
    fun upHoldZapsExactlyOnceAndOpensNoList() {
        openPlayer()
        hold(KeyEvent.KEYCODE_DPAD_UP, repeats = 20)
        compose.waitUntil(15_000) { requests.contains("/live/1.mp4") }
        SystemClock.sleep(1_000)
        compose.waitForIdle()
        assertFalse("a held key zapped more than once", requests.contains("/live/2.mp4"))
        assertFalse(exists("player-channels"))
    }

    @Test
    fun okHoldOpensQuickActionsAndItsReleaseActivatesNothing() {
        openPlayer()
        hold(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("player-quick") }
        SystemClock.sleep(500)
        compose.waitForIdle()
        // The first row stays as it was: the release did not choose it.
        assertTrue(exists("player-quick"))
        assertFalse(exists("player-picker"))
    }

    @Test
    fun backHoldOnLiveZapsBackAndStaysInThePlayer() {
        openPlayer()
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        compose.waitUntil(15_000) { requests.contains("/live/1.mp4") }
        compose.waitUntil(10_000) { !exists("player-live-box") }
        val before = count("/live/0.mp4")
        hold(KeyEvent.KEYCODE_BACK, repeats = 5)
        compose.waitUntil(15_000) { count("/live/0.mp4") > before }
        SystemClock.sleep(500)
        compose.waitForIdle()
        assertTrue(exists("screen-player"))
    }

    @Test
    fun aChangedMappingAppliesToTheNextPress() {
        openPlayer()
        runBlocking { graph.data.preferences.setRemoteAction(RemoteButton.UP, Gesture.PRESS, RemoteAction.GO_HOME) }
        SystemClock.sleep(500)
        press(KeyEvent.KEYCODE_DPAD_UP)
        // Home: the player and its channel list are gone and the Home rail is back.
        compose.waitUntil(10_000) { !exists("screen-player") && exists(RailItem.LIVE_TV.tag) }
        assertEquals(false, exists("player-channels"))
    }
}
