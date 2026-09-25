package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 02 §11 "Instrumentation": Continue watching and Recently watched channels on Home, focus on
 * entry and back from the rail, the hero following focus, hold OK, and the routes of §3.2. Keys go
 * through the window like a remote's.
 */
@RunWith(AndroidJUnit4::class)
class HomeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            LibraryFixture.seed(graph, perGroup = 10)
            GuideFixture.seed(graph, groups = 1, perGroup = 4)
            runBlocking {
                // Three films paused, the newest last: Drama 0002 first on Home.
                for (id in listOf(0, 1, 2)) {
                    graph.data.progress.save(LibraryFixture.key(id), (10 + id) * MINUTE, 90 * MINUTE)
                    Thread.sleep(5)
                }
                graph.data.live.recordWatched("fixture-0:c1")
            }
            // The process's Continue watching row reads this test's progress before Home opens.
            graph.continueFeed.retry()
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

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun card(id: Int) = "home-resume-vod:${LibraryFixture.key(id)}"

    /** Spec 02 §3.4, HOME-FR-28: the first card of the first row, and the hero after focus rests. */
    @Test
    fun homeOpensOnTheNewestPausedFilmAndTheHeroFollowsFocus() {
        awaitFocus(card(2))
        compose.waitUntil(5_000) { text("home-hero-title") == "Drama 0002" }
        assertTrue(exists("home-channel-1") || compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-row-1")).fetchSemanticsNodes().isNotEmpty())
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(card(1))
        compose.waitUntil(5_000) { text("home-hero-title") == "Drama 0001" }
    }

    /** HOME-FR-83: Left from the first card opens the rail; Right returns to the card last focused. */
    @Test
    fun theRailReturnsToTheCardLastFocused() {
        awaitFocus(card(2))
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(card(2))
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        // Left from the first card of the channels row, then Back: the channel card again.
        val channel = compose.onAllNodes(isFocused()).fetchSemanticsNodes().first().config.getOrNull(SemanticsProperties.TestTag)!!
        assertTrue(channel, channel.startsWith("home-channel-"))
        compose.focusRail(RailItem.SEARCH)
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus(channel)
        assertTrue(exists("screen-home"))
    }

    /** HOME-FR-40, -41: hold OK opens the actions with Resume focused; Remove takes the card away and focus stays in the row. */
    @Test
    fun holdOkRemovesACardAndFocusStaysInTheRow() {
        awaitFocus(card(2))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        instrumentation.sendKeySync(KeyEvent.changeTimeRepeat(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER), System.currentTimeMillis(), 1))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        compose.waitForIdle()
        awaitFocus("home-resume-action-continue")
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        awaitFocus("home-resume-action-remove")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { !exists(card(2)) }
        awaitFocus(card(1))
        assertEquals(null, runBlocking { graph.data.progress.of(LibraryFixture.key(2)) })
    }

    /** Spec 02 §3.2: OK resumes with the film's pages under the player; Back walks them to Home. */
    @Test
    fun okResumesAndBackWalksTheFilmsPages() {
        awaitFocus(card(2))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-player") }
        // The player's first Back may only hide its controls (spec 30): Back until the film's page.
        repeat(3) { if (!exists("screen-film")) press(KeyEvent.KEYCODE_BACK) }
        compose.waitUntil(10_000) { exists("screen-film") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("screen-movies") }
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("screen-home") }
        // A fresh Home: the first card again.
        awaitFocus(card(2))
    }

    /** HOME-FR-27: a recent channel plays live; Back goes to the guide on that channel. */
    @Test
    fun aRecentChannelPlaysAndBackLandsInTheGuide() {
        awaitFocus(card(2))
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        val channel = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("channel card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("home-channel-") == true
        }).fetchSemanticsNodes().first().config.getOrNull(SemanticsProperties.TestTag)!!
        awaitFocus(channel)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-player") }
        repeat(3) { if (!exists("screen-guide")) press(KeyEvent.KEYCODE_BACK) }
        compose.waitUntil(10_000) { exists("screen-guide") }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
