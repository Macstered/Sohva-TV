package com.sohva.tv.app

import android.content.ComponentName
import android.os.Debug
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.feature.home.RailItem
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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

/** A request alone is insufficient: each choice in the right-side list must draw a new picture. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerChannelSwitchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val requests = ConcurrentLinkedQueue<String>()
    private val frames = AtomicInteger()
    @Volatile private var slow = false
    @Volatile private var readGate: CompletableDeferred<Unit>? = null
    private val readStarted = CompletableDeferred<Unit>()
    private val readFinished = CompletableDeferred<Unit>()
    private val readCancelled = AtomicBoolean()
    private val gateNextPage = AtomicBoolean()
    @Volatile private var gatePosition = false
    private val server = MockWebServer()
    private val serverRule = object : ExternalResource() {
        override fun before() {
            val clip = TestMedia.mp4(instrumentation.targetContext).readBytes()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request.url.encodedPath
                    return MockResponse.Builder().addHeader("Content-Type", "video/mp4")
                        .headersDelay(if (slow) 1_200 else 0, TimeUnit.MILLISECONDS)
                        .body(Buffer().write(clip)).build()
                }
            }
            server.start()
            val graph = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
            GuideFixture.seed(graph, groups = 1, perGroup = 6, stream = { _, i -> server.url("/live/$i.mp4").toString() })
            val base = graph.data.live
            graph.liveReadsOverride = object : LiveReads by base {
                override suspend fun channel(key: String): LiveChannel? {
                    val gate = readGate?.takeIf { !gatePosition && key == "fixture-0:c1" }
                    if (gate != null) delayRead(gate)
                    return base.channel(key)
                }
                override suspend fun page(list: ChannelList, page: Int): List<LiveChannel> {
                    val gate = readGate?.takeIf { gateNextPage.compareAndSet(true, false) }
                    if (gate != null) delayRead(gate)
                    return base.page(list, page)
                }
                override suspend fun indexOf(list: ChannelList, channel: LiveChannel): Int {
                    val gate = readGate?.takeIf { gatePosition && channel.key == "fixture-0:c1" }
                    if (gate != null) delayRead(gate)
                    return base.indexOf(list, channel)
                }
            }
        }
        override fun after() { readGate?.complete(Unit); server.close() }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(serverRule).around(compose)

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTagExists(tag)
    private suspend fun delayRead(gate: CompletableDeferred<Unit>) {
        readStarted.complete(Unit)
        try { gate.await() }
        catch (cancelled: CancellationException) { readCancelled.set(true); throw cancelled }
        finally { readFinished.complete(Unit) }
    }
    private fun press(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
    private fun awaitFocus(tag: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun observe(action: (MediaController) -> Unit) {
        val context = instrumentation.targetContext
        var future: ListenableFuture<MediaController>? = null
        instrumentation.runOnMainSync {
            future = MediaController.Builder(context, SessionToken(context, ComponentName(context, com.sohva.tv.core.player.PlaybackService::class.java))).buildAsync()
        }
        val watcher = future!!.get(10, TimeUnit.SECONDS)
        instrumentation.runOnMainSync {
            watcher.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { frames.incrementAndGet() }
            })
        }
        try {
            action(watcher)
        } finally {
            instrumentation.runOnMainSync { watcher.release() }
        }
    }

    private fun openGuide() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
    }

    private fun openPlayer() {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { frames.get() > 0 && requests.contains("/live/0.mp4") }
    }

    private fun openList() {
        if (exists("player-live-box")) press(KeyEvent.KEYCODE_BACK)
        awaitFocus("player-video")
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.waitUntil(5_000) { exists("player-channels") }
    }

    private fun switching(slowResponses: Boolean, measure: Boolean = false) = observe {
        openPlayer()
        slow = slowResponses
        for ((sample, index) in listOf(1, 2, 3, 4, 5, 0).withIndex()) {
            openList()
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            val before = frames.get()
            val path = "/live/$index.mp4"
            val count = requests.count { it == path }
            var cpuStart = 0L
            if (measure) instrumentation.runOnMainSync { cpuStart = Debug.threadCpuTimeNanos() }
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(15_000) { requests.count { it == path } > count && frames.get() > before }
            if (measure) {
                var cpuMs = 0L
                instrumentation.runOnMainSync { cpuMs = (Debug.threadCpuTimeNanos() - cpuStart) / 1_000_000 }
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Sidebar switch sample=$sample mainCpuMs=$cpuMs\n") })
            }
            assertFalse("A successful sidebar switch must not leave an error", exists("player-banner"))
            assertFalse(exists("player-channels"))
        }
        if (exists("player-live-box")) press(KeyEvent.KEYCODE_BACK)
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("screen-guide") }
        awaitFocus("guide-row-0")
    }

    @Test fun everySidebarChoiceDrawsANewFrameAtConnectionLimitOne() = switching(slowResponses = false)
    @Test fun delayedResponsesStillDrawANewFrameAfterEachSidebarChoice() = switching(slowResponses = true)
    @Test fun compareSidebarChoiceCpuUntilItsNewFrame() = switching(slowResponses = false, measure = true)

    @Test fun channelKeysWorkWhenTheInitialInfoBoxArrivesBeforeTheFirstContentFrame() = observe {
        openGuide()
        // Hold composition after entry until the asynchronous player has shown its initial box.
        compose.mainClock.autoAdvance = false
        try {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                exists("screen-player")
            }
            compose.waitUntil(15_000) { requests.contains("/live/0.mp4") }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitUntil(15_000) { frames.get() > 0 && exists("player-live-box") }
        compose.onNodeWithTag("player-video").assertIsFocused()
        val before = frames.get()
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        compose.waitUntil(15_000) { requests.contains("/live/1.mp4") && frames.get() > before }
    }

    private fun latestChoiceWithDelayedRead(page: Boolean) = observe { watcher ->
        openPlayer()
        openList()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        readGate = CompletableDeferred()
        gateNextPage.set(page)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { readStarted.isCompleted }
        // Before the first selection's read finishes, the viewer makes another choice.
        openList()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val before = frames.get()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) { requests.contains("/live/2.mp4") && frames.get() > before }
        readGate!!.complete(Unit)
        compose.waitUntil(10_000) { readFinished.isCompleted && (readCancelled.get() || requests.contains("/live/1.mp4")) }
        assertFalse("The superseded choice must not replace the channel the viewer chose last", requests.contains("/live/1.mp4"))
        instrumentation.runOnMainSync { assertEquals("fixture-0:c2", watcher.currentMediaItem?.mediaId) }
    }

    @Test fun latestSidebarChoiceWinsWhileAnEarlierChannelReadIsDelayed() = latestChoiceWithDelayedRead(page = false)
    @Test fun latestSidebarChoiceWinsWhileAnEarlierListReadIsDelayed() = latestChoiceWithDelayedRead(page = true)

    @Test fun reselectingDuringADelayedPositionReadKeepsChannelKeysOnTheCorrectChannel() = observe {
        openPlayer()
        val before = frames.get()
        openList()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        gatePosition = true
        readGate = CompletableDeferred()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { readStarted.isCompleted }
        // The list still has its old position until the read finishes. Choose channel 1 again.
        openList()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { readCancelled.get() }
        readGate!!.complete(Unit)
        compose.waitUntil(15_000) { requests.contains("/live/1.mp4") && frames.get() > before }
        val selectedFrames = frames.get()
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        compose.waitUntil(15_000) { requests.contains("/live/2.mp4") && frames.get() > selectedFrames }
    }

    @Test fun choosingThePlayingChannelCancelsAnEarlierPendingChoiceWithoutRestartingIt() = observe { watcher ->
        openPlayer()
        openList()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        readGate = CompletableDeferred()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { readStarted.isCompleted }
        openList()
        // MP4 may make several range requests; only a new request after this choice is a restart.
        val currentRequests = requests.count { it == "/live/0.mp4" }
        val currentFrames = frames.get()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { readCancelled.get() }
        readGate!!.complete(Unit)
        assertFalse(requests.contains("/live/1.mp4"))
        assertEquals(currentRequests, requests.count { it == "/live/0.mp4" })
        assertEquals(currentFrames, frames.get())
        instrumentation.runOnMainSync {
            assertEquals("fixture-0:c0", watcher.currentMediaItem?.mediaId)
            assertTrue(watcher.playWhenReady)
        }
    }
}
