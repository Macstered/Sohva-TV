package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CompletableDeferred
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
 * The info box's now and next belong to the playing channel (spec 30 PLAY-FR-40): a channel change
 * while the box is showing reads the new channel at once, and a slow read for the channel before
 * never lands on the new one.
 */
@RunWith(AndroidJUnit4::class)
class PlayerNowNextTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val requests = ConcurrentLinkedQueue<String>()
    private val boxReads = ConcurrentLinkedQueue<String>()

    /** While set, a now/next read of [heldId] waits here. */
    @Volatile private var hold: CompletableDeferred<Unit>? = null
    @Volatile private var heldId: String? = null
    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext).readBytes()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request.url.encodedPath
                    return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip)).build()
                }
            }
            server.start()
            val graph = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
            GuideFixture.seed(graph, groups = 1, perGroup = 4, stream = { _, i -> server.url("/live/$i.mp4").toString() })
            val base = graph.data.live
            graph.liveReadsOverride = object : LiveReads by base {
                // The box asks for one channel; its programme is named after that channel's guide id.
                override suspend fun schedules(source: LiveSource, epgIds: Collection<String>, windowStart: Long): Map<String, List<GuideProgramme>> {
                    val id = epgIds.singleOrNull() ?: return base.schedules(source, epgIds, windowStart)
                    boxReads += id
                    if (id == heldId) hold?.await()
                    val now = System.currentTimeMillis()
                    return mapOf(id to listOf(GuideProgramme(1, now - 600_000, now + 3_000_000, "On $id", null, null, null, false, 0, "k-$id")))
                }
            }
        }
        override fun after() {
            hold?.complete(Unit)
            server.close()
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTagExists(tag)
    private fun shows(text: String): Boolean = compose.onAllNodesWithTextExists(text)
    private fun press(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun openPlayer(): String {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/live/0.mp4") && boxReads.isNotEmpty() }
        val first = boxReads.first()
        compose.waitUntil(5_000) { shows("On $first") }
        return first
    }

    @Test
    fun changingChannelWhileTheBoxShowsReadsTheNewChannelAtOnce() {
        val first = openPlayer()
        // The box from entry is still up (5 s); the next channel's programme must replace it before it hides.
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        compose.waitUntil(15_000) { requests.contains("/live/1.mp4") }
        compose.waitUntil(3_000) { boxReads.any { it != first } }
        val second = boxReads.first { it != first }
        compose.waitUntil(3_000) { exists("player-live-box") && shows("On $second") }
        assertFalse(shows("On $first"))
    }

    @Test
    fun aSlowReadForTheChannelBeforeNeverLandsOnTheNewOne() {
        val first = openPlayer()
        compose.waitUntil(8_000) { !exists("player-live-box") }
        // OK shows the box again; its read for the first channel is held, as a busy database would.
        heldId = first
        hold = CompletableDeferred()
        val before = boxReads.size
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { boxReads.size > before }
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        compose.waitUntil(15_000) { requests.contains("/live/1.mp4") }
        compose.waitUntil(3_000) { boxReads.any { it != first } }
        val second = boxReads.first { it != first }
        compose.waitUntil(3_000) { shows("On $second") }
        hold!!.complete(Unit)
        compose.waitForIdle()
        assertTrue(exists("player-live-box"))
        assertTrue(shows("On $second"))
        assertFalse("the previous channel's programme came back", shows("On $first"))
    }
}
