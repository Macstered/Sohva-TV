package com.sohva.tv.app

import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 22 §11 "Guide" and spec 30's transport controls: a `shift` channel with seven days of
 * archive plays a programme from its start; a channel without catch-up plays live.
 */
@RunWith(AndroidJUnit4::class)
class PlayerArchiveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Path and query of every request, e.g. `/live/0.mp4?utc=…&lutc=…`. */
    private val requests = ConcurrentLinkedQueue<String>()
    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                    return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip.readBytes())).build()
                }
            }
            server.start()
            val app = instrumentation.targetContext.applicationContext as SohvaApplication
            // Even channels keep a seven-day archive (`shift`), odd ones have none.
            GuideFixture.seed(
                app.graph, groups = 1, perGroup = 6, stream = { _, i -> server.url("/live/$i.mp4").toString() },
                catchupType = { if (it % 2 == 0) "shift" else null },
            )
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

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    private fun openGuide() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        // The first block of the window is the airing programme once its programmes are drawn.
        compose.waitUntil(8_000) { compose.onAllNodes(hasContentDescription("1 Northstar 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(500)
        compose.waitForIdle()
    }

    @Test
    fun okOnTheAiringBlockPlaysItFromTheStartAndBackReturnsToTheGuide() {
        openGuide()
        compose.waitUntil(5_000) { exists("guide-hero-archive") }
        assertTrue(text("guide-hero-archive") == "Watch from start")
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.any { it.startsWith("/live/0.mp4?utc=") && "&lutc=" in it } }
        // Catch-up opens with the transport controls, unfocused, titled after the archive (PLAY-FR-08).
        compose.waitUntil(5_000) { exists("player-transport") }
        assertTrue(compose.onAllNodesWithTextExists("Archive · Northstar 1"))
        assertFalse(exists("player-live-box"))
        // Back hides the controls, then pops to the guide on the channel.
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("player-transport") }
        assertTrue(exists("screen-player"))
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("screen-guide") }
        awaitFocus("guide-row-0")
    }

    /** CATCH-12: catch-up of a locked channel meets the PIN first, and the channel counts as recently watched. */
    @Test
    fun aLockedChannelsArchiveAsksForThePinAndIsRecordedAsWatched() {
        val graph = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
        kotlinx.coroutines.runBlocking {
            graph.data.profiles.setPin("2468")
            graph.data.profiles.setLocked(CHANNEL, true)
        }
        openGuide()
        compose.waitUntil(5_000) { exists("guide-hero-archive") }
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-pin") }
        assertTrue("nothing plays before the PIN", requests.none { it.startsWith("/live/0.mp4") })
        // The PIN field takes text once it is opened, as a viewer's OK does.
        compose.onNodeWithTag("parental-pin").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("2468")
        compose.onNodeWithTag("parental-unlock").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(15_000) { requests.any { it.startsWith("/live/0.mp4?utc=") } }
        compose.waitUntil(5_000) {
            graph.data.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM recent_channel WHERE channel_key = ?", arrayOf(CHANNEL))
                .use { it.moveToFirst() && it.getInt(0) == 1 }
        }
    }

    @Test
    fun aChannelWithoutCatchUpPlaysLiveAndShowsNoArchiveButton() {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("guide-row-1")
        compose.waitForIdle()
        assertFalse(exists("guide-hero-archive"))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/live/1.mp4") }
        assertTrue(requests.none { it.startsWith("/live/1.mp4?") })
        compose.waitUntil(5_000) { exists("player-live-box") }
        assertFalse(exists("player-transport"))
    }

    @Test
    fun transportControlsTakeFocusPauseAndSkip() {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.any { it.startsWith("/live/0.mp4?utc=") } }
        compose.waitUntil(5_000) { exists("player-transport") }
        // Up enters the controls on Play/Pause (PLAY-FR-49).
        // A network request and composed controls do not yet guarantee the key host has focus.
        awaitFocus("player-video")
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus("player-transport-play")
        compose.waitUntil(10_000) { text("player-transport-play") == "Pause" }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { text("player-transport-play") == "Play" }
        // Focused controls do not hide; Back hides them and the clean screen's Left skips back.
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("player-transport") }
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitUntil(3_000) { exists("player-skip-feedback") && text("player-skip-feedback") == "−10 s" }
        // Digits are not dialled in catch-up.
        press(KeyEvent.KEYCODE_5)
        assertFalse(exists("player-dial"))
    }

    private companion object {
        const val CHANNEL = "fixture-0:c0"
    }
}
