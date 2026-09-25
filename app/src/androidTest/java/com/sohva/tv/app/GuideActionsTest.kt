package com.sohva.tv.app

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 20 §11: OK on a future programme offers its actions; OK held opens them once and plays
 * nothing; closing returns to the block it came from; Find programme filters the list.
 */
@RunWith(AndroidJUnit4::class)
class GuideActionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val seed = object : ExternalResource() {
        override fun before() {
            val app = instrumentation.targetContext.applicationContext as SohvaApplication
            GuideFixture.seed(app.graph, groups = 1, perGroup = 12)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun focused(tag: String) = runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess

    private fun awaitFocus(tag: String, timeout: Long = 8_000) = compose.waitUntil(timeout) { focused(tag) }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun openGuide() {
        compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }
        awaitFocus(RailItem.LIVE_TV.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0", 10_000)
        // Let the first rows' programmes arrive.
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("1 Northstar 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        SystemClock.sleep(500)
        compose.waitForIdle()
    }

    /**
     * A block that surely starts after now: page forward 90 minutes (the window then starts at
     * least 60 minutes ahead), then move past the block that may cover the window's start.
     */
    private fun walkToAFutureBlock() {
        press(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        SystemClock.sleep(500)
        compose.waitForIdle()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    @Test
    fun okOnAFutureProgrammeOpensItsActionsAndCloseReturnsToTheBlock() {
        openGuide()
        walkToAFutureBlock()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { exists("guide-actions") }
        awaitFocus("guide-actions-watch")
        // The favourite row toggles in place and the dialog stays.
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Favourite") }
        assertTrue(exists("guide-actions"))
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("guide-actions") }
        awaitFocus("guide-row-0")
        assertFalse(exists("screen-player"))
    }

    @Test
    fun okHeldOpensTheActionsOncePlaysNothing() {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        val down = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        for (r in 1..8) {
            SystemClock.sleep(50)
            instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, r))
        }
        instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        compose.waitForIdle()
        compose.waitUntil(5_000) { exists("guide-actions") }
        SystemClock.sleep(500)
        compose.waitForIdle()
        assertFalse("the release played the channel", exists("screen-player"))
        assertTrue(exists("guide-actions"))
    }

    @Test
    fun findProgrammeFiltersTheRowsByChannelName() {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus("guide-hero-watch")
        press(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        awaitFocus("guide-hero-search")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // The rail opens with focus in the search field (GUIDE-FR-90).
        awaitFocus("guide-search")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("summit")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        compose.waitUntil(8_000) {
            compose.onAllNodes(hasContentDescription("Summit 4", substring = true)).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasContentDescription("Northstar 1", substring = true)).fetchSemanticsNodes().isEmpty()
        }
    }
}
