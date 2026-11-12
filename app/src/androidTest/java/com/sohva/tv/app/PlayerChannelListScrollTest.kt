package com.sohva.tv.app

import android.os.Debug
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.data.live.ChannelList
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * PLAY-FR-54 on a slow box: Up/Down in the player's channel list moves the highlight and scrolls
 * the list in one drawn frame, without waiting for a database read of rows already on screen.
 */
@RunWith(AndroidJUnit4::class)
class PlayerChannelListScrollTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val requests = ConcurrentLinkedQueue<String>()

    private val titleReads = ConcurrentLinkedQueue<List<String>>()
    @Volatile private var firstPage: List<LiveChannel> = emptyList()

    /** Once set, every page read waits: rows already loaded must not need one. */
    @Volatile private var pageGate: CompletableDeferred<Unit>? = null
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
            GuideFixture.seed(graph, groups = 1, perGroup = CHANNELS, stream = { _, i -> server.url("/live/$i.mp4").toString() })
            val base = graph.data.live
            graph.liveReadsOverride = object : LiveReads by base {
                override suspend fun page(list: ChannelList, page: Int): List<LiveChannel> {
                    pageGate?.await()
                    return base.page(list, page).also { if (page == 0) firstPage = it }
                }
                override suspend fun schedules(source: LiveSource, epgIds: Collection<String>, windowStart: Long): Map<String, List<GuideProgramme>> {
                    // The info box asks for one channel; the list asks for the rows it shows.
                    if (epgIds.size > 1) titleReads += epgIds.toList()
                    return base.schedules(source, epgIds, windowStart)
                }
            }
        }
        override fun after() {
            pageGate?.complete(Unit)
            server.close()
        }
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

    private fun openList() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/live/0.mp4") }
        compose.waitUntil(15_000) { exists("player-live-box") }
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("player-video")
        // The guide read its own rows on the way here; only the list's reads count from now on.
        titleReads.clear()
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.waitUntil(5_000) { selectedRow()?.first == 0 }
    }

    @Test
    fun aStaleReadOfAnotherPageDoesNotMoveTheHighlight() {
        openList()
        // Up wraps to the last channel, on the second page of rows; Down comes back to the first.
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.waitUntil(5_000) { selectedRow()?.first == CHANNELS - 1 }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitUntil(5_000) { selectedRow()?.first == 0 }
        // The second page's read is held while the viewer returns to the rows on screen.
        val gate = CompletableDeferred<Unit>()
        pageGate = gate
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        pageGate = null
        gate.complete(Unit)
        compose.waitForIdle()
        SystemClock.sleep(500)
        compose.waitForIdle()
        assertEquals(0, selectedRow()?.first)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitUntil(2_000) { selectedRow()?.first == 1 }
    }

    /**
     * Spec 30 §9: now titles only for the rows shown. A group of hundreds of channels must not read
     * every channel's programmes before the first title appears; scrolling reads the rows it reaches.
     */
    @Test
    fun programmeTitlesAreReadForTheRowsAroundTheHighlight() {
        openList()
        compose.waitUntil(5_000) { titleReads.isNotEmpty() }
        val first = titleReads.first()
        assertTrue("the first read asked for ${first.size} channels", first.size <= 30)
        assertTrue(first.contains(firstPage[0].epgId))
        repeat(20) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.waitUntil(5_000) { selectedRow()?.first == 20 }
        // The rows around the new highlight get their titles too, again a small read.
        compose.waitUntil(5_000) { titleReads.any { it.contains(firstPage[24].epgId) } }
        assertTrue(titleReads.all { it.size <= 30 })
    }

    /** Main-thread CPU of one Down press in the list, from the key to the idle UI, after warm-up. */
    @Test
    fun compareListPressCpu() {
        openList()
        repeat(4) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        val samples = ArrayList<Long>()
        repeat(20) {
            var start = 0L
            instrumentation.runOnMainSync { start = Debug.threadCpuTimeNanos() }
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitUntil(2_000) { selectedRow()?.first == 5 + it }
            var cpu = 0L
            instrumentation.runOnMainSync { cpu = (Debug.threadCpuTimeNanos() - start) / 1_000 }
            samples += cpu
        }
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "List press mainCpuUs=$samples" + System.lineSeparator()) })
    }

    @Test
    fun highlightAndScrollMoveInOneFrameWithoutWaitingForAPageRead() {
        openList()
        pageGate = CompletableDeferred()

        // Compose frames one at a time, each frame's highlighted row as laid out.
        val frames = ArrayList<Pair<Int, Float>>()
        compose.mainClock.autoAdvance = false
        try {
            for (i in 1..PRESSES) {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                // The rows are already loaded: the gated page read must not hold the highlight back.
                var waited = 0
                do {
                    compose.mainClock.advanceTimeByFrame()
                    val row = selectedRow()
                    row?.let(frames::add)
                    assertTrue("The highlight did not reach row $i", ++waited < 30)
                } while (row?.first != i)
            }
            repeat(3) { compose.mainClock.advanceTimeByFrame(); selectedRow()?.let(frames::add) }
        } finally {
            compose.mainClock.autoAdvance = true
        }

        // The highlight walks down to the middle row and stays there while the list moves (PLAY-FR-54).
        val middle = frames.last().second
        val late = frames.filter { it.second > middle + 1f }
        assertTrue("The list scrolled a frame after the highlight moved: $late in $frames", late.isEmpty())
        assertTrue(frames.last().first == PRESSES)
    }

    private fun selectedRow(): Pair<Int, Float>? =
        compose.onAllNodes(isSelected(), useUnmergedTree = true).fetchSemanticsNodes().firstNotNullOfOrNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.startsWith(ROW) }?.let { it.removePrefix(ROW).toInt() to node.boundsInRoot.top }
        }

    private companion object {
        const val ROW = "player-channel-"
        const val PRESSES = 12

        /** More than one page of rows (ChannelList.PAGE is 200). */
        const val CHANNELS = 205
    }
}
