package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 01 §3.3–3.4 and the M0 exit criterion: every rail destination opens, and Back — sent
 * through the window like a remote's, not as Compose key input — returns to Home with focus on
 * the rail item that opened it.
 */
@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {
    private val clearState = ClearStateRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(clearState).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private val items = listOf(
        RailItem.LIVE_TV to "screen-guide",
        RailItem.SPORT to "screen-today",
        RailItem.MOVIES to "screen-movies",
        RailItem.SERIES to "screen-series",
        RailItem.SEARCH to "screen-search",
        RailItem.DISCOVER to "screen-discover",
        RailItem.SETTINGS to "screen-settings",
    )

    private fun awaitHome() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag)
        }
    }

    @Test
    fun homeOpensWithFocusOnTheFirstRailItem() {
        awaitHome()
        compose.onNodeWithTag(RailItem.LIVE_TV.tag).assertIsFocused()
        compose.onNodeWithTag("home-nav-home").assertExists()
    }

    @Test
    fun railItemsAreInSpecOrderTopToBottom() {
        awaitHome()
        val tops = items.map { (item, _) -> compose.onNodeWithTag(item.tag).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun backFromEveryDestinationReturnsFocusToItsRailItem() {
        awaitHome()
        items.forEachIndexed { index, (item, screen) ->
            // Down walks the rail from the item focused now (the previous one, after Back).
            press(KeyEvent.KEYCODE_DPAD_DOWN, if (index == 0) 0 else 1)
            compose.onNodeWithTag(item.tag).assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(5_000) { compose.onAllNodesWithTagExists(screen) }
            compose.onNodeWithTag("placeholder-back").assertIsFocused()
            press(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { compose.onAllNodesWithTagExists(item.tag) }
            compose.waitForIdle()
            compose.onNodeWithTag(item.tag).assertIsFocused()
        }
    }
}
