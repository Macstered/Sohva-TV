package com.sohva.tv.app

import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertTrue
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 20 §11 instrumentation: the guide opens on its first group with focus on the first row,
 * the rail, dialling, the hero and the options sheet. Keys go through the window like a remote's.
 */
@RunWith(AndroidJUnit4::class)
class GuideScreenTest {
    private val clearState = ClearStateRule()
    private val seed = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SohvaApplication
            GuideFixture.seed(app.graph, sources = 2)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(clearState).around(seed).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
    }

    private fun openGuide() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists("guide-row-0") }
        awaitFocus("guide-row-0")
    }

    @Test
    fun opensOnTheFirstGroupWithFocusOnItsFirstRow() {
        openGuide()
        compose.onNodeWithTag("guide-row-0").assertIsFocused()
        // Left from the channel column opens the rail on the selected entry: the first group.
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("guide-rail-group:g0")
        // Right closes it and returns to the row.
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("guide-row-0")
        compose.waitUntil(3_000) { !compose.onAllNodesWithTagExists("guide-rail") }
    }

    private fun rowDescription(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    /** GUIDE-15: channel numbers show by default and not at all when switched off in Settings › General. */
    @Test
    fun channelNumbersShowUnlessSwitchedOff() {
        openGuide()
        assertTrue(rowDescription("guide-row-0"), rowDescription("guide-row-0").startsWith("1 "))
        kotlinx.coroutines.runBlocking {
            (instrumentation.targetContext.applicationContext as SohvaApplication).graph.data.preferences.setShowChannelNumbers(false)
        }
        press(KeyEvent.KEYCODE_BACK)
        openGuide()
        compose.waitUntil(5_000) { !rowDescription("guide-row-0").first().isDigit() }
    }

    @Test
    fun choosingAllChannelsShowsEveryChannelOfTheSource() {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("guide-rail-group:g0")
        // Favourites, All channels, Recently watched, then the groups.
        press(KeyEvent.KEYCODE_DPAD_UP, 2)
        awaitFocus("guide-rail-all")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        // Rows state their text in one node each (GUIDE-52).
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Northstar 1", substring = true)).assertExists()
    }

    @Test
    fun dialledNumberFocusesItsChannel() {
        openGuide()
        press(KeyEvent.KEYCODE_2)
        compose.waitUntil(2_000) { compose.onAllNodesWithTextExists("Channel 2") }
        compose.waitUntil(5_000) { !compose.onAllNodesWithTagExists("guide-dial") }
        awaitFocus("guide-row-1")
    }

    @Test
    fun upFromTheTopRowReachesTheHeroAndDownReturns() {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus("guide-hero-watch")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("guide-row-0")
    }

    @Test
    fun nextDayChangesTheDayLabelAndPreviousReturns() {
        openGuide()
        // The header has no semantics of its own (guide.md §2): read it from the unmerged tree.
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Now", unmerged = true) }
        press(KeyEvent.KEYCODE_MEDIA_NEXT)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Tomorrow", unmerged = true) }
        press(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Now", unmerged = true) }
        awaitFocus("guide-row-0")
    }

    @Test
    fun optionsCloseReturnsToTheRowAndSourceSwitchLandsOnTheFirstRow() {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        awaitFocus("guide-row-3")
        press(KeyEvent.KEYCODE_MENU)
        awaitFocus("guide-options-source")
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("guide-row-3")
        // A switch lands on the other source's first group, first channel, when the sheet closes.
        press(KeyEvent.KEYCODE_MENU)
        awaitFocus("guide-options-source")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { compose.onAllNodesWithTextExists("Source: Fixture B", substring = true) }
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("guide-row-0")
    }
}
