package com.sohva.tv.app

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.feature.home.RailItem
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 20 §11 timing tests with a gated programme read: programmes arriving late never move focus,
 * and a held Right pages once, waits on the channel, and lands on the new window's first block
 * (old `GuideHeldKeyPagingTest`; it must fail when repeats are not consumed).
 */
@RunWith(AndroidJUnit4::class)
class GuideTimingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SohvaApplication
    private lateinit var gated: GatedReads

    private val seed = object : ExternalResource() {
        override fun before() {
            GuideFixture.seed(app.graph, groups = 2, perGroup = 30)
            gated = GatedReads(app.graph.data.live)
            app.graph.liveReadsOverride = gated
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun focused(tag: String): Boolean = runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess

    private fun awaitFocus(tag: String, timeout: Long = 5_000) = compose.waitUntil(timeout) { focused(tag) }

    private fun openGuide() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        awaitFocus(RailItem.LIVE_TV.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0", 10_000)
    }

    /** A real held key: one down, [repeats] auto-repeats about 50 ms apart, then the up. */
    private fun hold(keyCode: Int, repeats: Int) {
        val down = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, keyCode, 0))
        for (r in 1..repeats) {
            SystemClock.sleep(50)
            instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, r))
        }
        instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        compose.waitForIdle()
    }

    private fun rulerStartsAt(start: Long): Boolean {
        val label = TimeLabels(TimeLabels.zoneOf(null), Locale.getDefault()).guideTime(start)
        return compose.onAllNodesWithTextExists(label, unmerged = true)
    }

    @Test
    fun programmesArrivingLateNeverMoveFocus() {
        gated.open.value = false
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        awaitFocus("guide-row-3")
        // Right on an unread row is consumed: focus stays on the channel (GUIDE-FR-57).
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(focused("guide-row-3"))
        compose.waitUntil(5_000) { gated.waiting.get() > 0 }
        gated.open.value = true
        compose.waitUntil(5_000) { gated.waiting.get() == 0 }
        compose.waitForIdle()
        SystemClock.sleep(500)
        compose.waitForIdle()
        assertTrue(focused("guide-row-3"))
    }

    @Test
    fun heldRightPagesOnceWaitsOnTheChannelAndLandsOnTheNextWindow() {
        openGuide()
        val anchor = GuideWindow.anchor(System.currentTimeMillis())
        compose.waitUntil(5_000) { rulerStartsAt(anchor) }
        // Walk right until the window pages once (the gate is open): focus is then on the new window's first block.
        var presses = 0
        while (!rulerStartsAt(anchor + GuideWindow.PAGE_MS) && presses < 20) {
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            presses++
        }
        assertTrue(rulerStartsAt(anchor + GuideWindow.PAGE_MS))
        compose.waitUntil(5_000) { gated.waiting.get() == 0 }
        // Now close the gate and hold Right: it walks the blocks, pages once more, then waits.
        gated.open.value = false
        val readsBefore = gated.scheduleReads.get()
        hold(KeyEvent.KEYCODE_DPAD_RIGHT, repeats = 25)
        compose.waitUntil(5_000) { rulerStartsAt(anchor + 2 * GuideWindow.PAGE_MS) }
        assertTrue(focused("guide-row-0"))
        compose.waitForIdle()
        // One page only: a further page would have started a second read.
        assertEquals("repeats started overlapping reads", readsBefore + 1, gated.scheduleReads.get())
        gated.open.value = true
        compose.waitUntil(5_000) { gated.waiting.get() == 0 }
        compose.waitForIdle()
        assertTrue(rulerStartsAt(anchor + 2 * GuideWindow.PAGE_MS))
        assertTrue(focused("guide-row-0"))
        // Normal navigation works after the release: Left walks back inside the new window.
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue(focused("guide-row-0"))
    }
}
