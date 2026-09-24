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
 * Spec 30 §11 UI tests on a playing stream: the channel list, dialling, quick actions and the Back
 * order. Keys go through the window like a remote's.
 */
@RunWith(AndroidJUnit4::class)
class PlayerControlsTest {
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
            val app = instrumentation.targetContext.applicationContext as SohvaApplication
            GuideFixture.seed(app.graph, groups = 2, perGroup = 8, stream = { _, i -> server.url("/live/$i.mp4").toString() })
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

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun openPlayer() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        awaitFocus(RailItem.LIVE_TV.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0", 10_000)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/live/0.mp4") }
        // Let the entry box time out, so the clean screen's keys decide.
        compose.waitUntil(10_000) { !exists("player-live-box") }
    }

    @Test
    fun channelListOpensOnThePlayingChannelAndOkTunesTheSelectedOne() {
        openPlayer()
        // Up press = Channel list (REMOTE-FR-13).
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.waitUntil(5_000) { exists("player-channels") && exists("player-channel-0") }
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/live/2.mp4") }
        compose.waitUntil(5_000) { !exists("player-channels") }
    }

    @Test
    fun backClosesTheGroupListThenTheChannelList() {
        openPlayer()
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.waitUntil(5_000) { exists("player-channels") }
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("GROUPS") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !compose.onAllNodesWithTextExists("GROUPS") }
        compose.waitForIdle()
        assert(exists("player-channels"))
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("player-channels") }
        assert(exists("screen-player"))
    }

    @Test
    fun dialTunesTheChannelAndAnUnknownNumberSaysSo() {
        openPlayer()
        press(KeyEvent.KEYCODE_3)
        compose.waitUntil(2_000) { compose.onAllNodesWithTextExists("Channel 3") }
        compose.waitUntil(8_000) { requests.contains("/live/2.mp4") }
        press(KeyEvent.KEYCODE_9)
        press(KeyEvent.KEYCODE_9)
        compose.waitUntil(8_000) { compose.onAllNodesWithTextExists("No channel 99") }
        compose.waitUntil(5_000) { !exists("player-dial") }
    }

    @Test
    fun quickActionsCyclePictureInPlaceAndPlaybackInfoCloses() {
        openPlayer()
        // Menu press = Quick actions.
        press(KeyEvent.KEYCODE_MENU)
        compose.waitUntil(5_000) { exists("player-quick") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Fit", substring = true) }
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // The menu stays open and the value moves on (PLAY-FR-80 item 3).
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Fill", substring = true) }
        assert(exists("player-quick"))
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { !exists("player-quick") && exists("player-info-line") }
        // Back from the bare picture with the box hidden leaves to the guide.
        compose.waitUntil(10_000) { !exists("player-live-box") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("screen-guide") }
    }
}
