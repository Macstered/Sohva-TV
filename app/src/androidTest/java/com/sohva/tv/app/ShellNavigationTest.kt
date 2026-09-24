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

    // Destination, and the control that takes focus there (placeholders focus their Back button).
    private val items = listOf(
        Triple(RailItem.LIVE_TV, "screen-guide", "placeholder-back"),
        Triple(RailItem.SPORT, "screen-today", "placeholder-back"),
        Triple(RailItem.MOVIES, "screen-movies", "placeholder-back"),
        Triple(RailItem.SERIES, "screen-series", "placeholder-back"),
        Triple(RailItem.SEARCH, "screen-search", "placeholder-back"),
        Triple(RailItem.DISCOVER, "screen-discover", "placeholder-back"),
        // Settings opens on Playlists; with no source its first control is "+ Add M3U source" (SET-FR-02).
        Triple(RailItem.SETTINGS, "screen-settings", "source-add-m3u"),
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
        val tops = items.map { (item, _, _) -> compose.onNodeWithTag(item.tag).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun backFromEveryDestinationReturnsFocusToItsRailItem() {
        awaitHome()
        items.forEachIndexed { index, (item, screen, first) ->
            // Down walks the rail from the item focused now (the previous one, after Back).
            press(KeyEvent.KEYCODE_DPAD_DOWN, if (index == 0) 0 else 1)
            compose.onNodeWithTag(item.tag).assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(5_000) { compose.onAllNodesWithTagExists(screen) }
            compose.waitUntil(5_000) { compose.onAllNodesWithTagExists(first) }
            compose.waitForIdle()
            compose.onNodeWithTag(first).assertIsFocused()
            press(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { compose.onAllNodesWithTagExists(item.tag) }
            compose.waitForIdle()
            compose.onNodeWithTag(item.tag).assertIsFocused()
        }
    }
}
