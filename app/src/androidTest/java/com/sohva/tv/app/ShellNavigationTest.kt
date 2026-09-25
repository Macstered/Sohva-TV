package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.getOrNull
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
        // With no source the guide shows its empty card, "Open settings" focused (GUIDE-FR-101).
        Triple(RailItem.LIVE_TV, "screen-guide", "guide-empty-settings"),
        Triple(RailItem.SPORT, "screen-today", "placeholder-back"),
        // The walls open on History (VOD-FR-49).
        Triple(RailItem.MOVIES, "screen-movies", "library-row-history"),
        Triple(RailItem.SERIES, "screen-series", "library-row-history"),
        // Search opens on its field (SEARCH-FR-50).
        Triple(RailItem.SEARCH, "screen-search", "unified-search-field"),
        Triple(RailItem.DISCOVER, "screen-discover", "placeholder-back"),
        // Settings opens on Playlists; with no source its first control is "+ Add M3U source" (SET-FR-02).
        Triple(RailItem.SETTINGS, "screen-settings", "source-add-m3u"),
    )

    private fun focused(tag: String) =
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag) and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitHome() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag)
        }
    }

    /** Spec 02 §3.4: an empty Home (no sources) focuses the Welcome hero's Guide button, not the rail. */
    @Test
    fun anEmptyHomeOpensWithFocusOnTheGuideButton() {
        awaitHome()
        try {
            compose.waitUntil(10_000) { focused("home-hero-primary") }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val now = compose.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
                .map { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) }
            throw AssertionError("focused: $now; guide button: ${compose.onAllNodesWithTagExists("home-hero-primary")}, status: ${compose.onAllNodesWithTagExists("home-resume-status")}, rows: ${compose.onAllNodesWithTagExists("home-rows")}, hero: ${compose.onAllNodesWithTagExists("home-hero-title")}", e)
        }
        compose.onNodeWithTag("home-nav-home").assertExists()
    }

    @Test
    fun railItemsAreInSpecOrderTopToBottom() {
        awaitHome()
        val tops = items.map { (item, _, _) -> compose.onNodeWithTag(item.tag).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun backFromEveryDestinationReturnsToAFreshHome() {
        awaitHome()
        items.forEach { (item, screen, first) ->
            compose.focusRail(item)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(5_000) { compose.onAllNodesWithTagExists(screen) }
            compose.waitUntil(5_000) { compose.onAllNodesWithTagExists(first) }
            compose.waitForIdle()
            compose.onNodeWithTag(first).assertIsFocused()
            press(KeyEvent.KEYCODE_BACK)
            // Search's first Back may only close the keyboard (spec 03 §3).
            if (compose.onAllNodesWithTagExists(screen)) press(KeyEvent.KEYCODE_BACK)
            // Home is rebuilt on return and focuses its content, here the Welcome button (spec 01 §3.4).
            compose.waitUntil(5_000) { focused("home-hero-primary") }
        }
    }
}
