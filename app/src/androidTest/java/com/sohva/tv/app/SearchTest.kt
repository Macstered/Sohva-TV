package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 03 §11 "Instrumentation": the field is focused on entry; one character shows the hint, two
 * search; a channel result plays and Back lands in the guide; a film result opens its page and
 * Back returns to Search. Keys go through the window like a remote's.
 */
@RunWith(AndroidJUnit4::class)
class SearchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            GuideFixture.seed(graph, groups = 1, perGroup = 8)
            LibraryFixture.seed(graph, perGroup = 20)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(10_000) { focused(tag) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val now = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("$tag not focused; focused: $now", e)
        }
    }

    private fun status(): String = runCatching {
        compose.onNodeWithTag("search-status", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun openSearch() {
        compose.waitUntil(15_000) { exists(RailItem.SEARCH.tag) }
        compose.focusRail(RailItem.SEARCH)
        awaitFocus(RailItem.SEARCH.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-search") }
        awaitFocus("unified-search-field")
    }

    /** The tag of the first result row of [kind] ("channel", "movie"…): row ids change between tests. */
    private fun firstResult(kind: String): String? = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("result") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("search-result-$kind:") == true
    }).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.TestTag)

    private fun type(text: String) = compose.onNodeWithTag("unified-search-field").performTextReplacement(text)

    /** Done on the keyboard, as the viewer presses it before moving to the results: the keyboard takes the D-pad while it is up. */
    private fun done() {
        compose.onNodeWithTag("unified-search-field").performImeAction()
        compose.waitForIdle()
    }

    @Test
    fun oneCharacterShowsTheHintAndTwoSearch() {
        openSearch()
        assertEquals("Search covers all downloaded sources.", status())
        type("n")
        compose.waitForIdle()
        assertEquals("Search covers all downloaded sources.", status())
        type("no")
        // "Northstar 1" and "Northstar 9" are channels; "North Horizon" is a programme on several.
        compose.waitUntil(10_000) { status().endsWith("results") }
        assertTrue(firstResult("channel") != null)
        assertTrue(!exists("search-result-movie:${LibraryFixture.key(0)}"))
    }

    @Test
    fun aChannelResultPlaysAndBackLandsInTheGuide() {
        openSearch()
        type("northstar")
        compose.waitUntil(10_000) { firstResult("channel") != null }
        done()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus(firstResult("channel")!!)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-player") }
        // The first Back may only hide the player's controls.
        repeat(3) {
            if (!exists("screen-guide")) {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                runCatching { compose.waitUntil(3_000) { exists("screen-guide") } }
            }
        }
        compose.waitUntil(10_000) { exists("screen-guide") }
    }

    @Test
    fun aFilmResultOpensItsPageAndBackReturnsToSearch() {
        openSearch()
        type("drama 0003")
        val film = "search-result-movie:${LibraryFixture.key(3)}"
        compose.waitUntil(10_000) { exists(film) }
        done()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus(film)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-film") }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("screen-search") }
    }
}
