package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** VOD-FR-82: an off-screen first episode must remain reachable after browsing later episodes. */
@RunWith(AndroidJUnit4::class)
class SeriesEpisodeFocusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            LibraryFixture.seed(graph, perGroup = 1, series = 1, episodes = 12, seasons = 5)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(key: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(key)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val focused = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
                .map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("$tag not focused; focused: $focused", e)
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun open(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun episode(number: Int, season: Int = 1): String =
        "series-episode-${LibraryFixture.episodeKey(0, number, season)}"

    private fun browseToEpisodeEight() {
        compose.focusRail(RailItem.SERIES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        open("library-row-group:crime")
        open("library-card-${LibraryFixture.seriesKey(0)}")
        awaitFocus("details-watch")
        compose.onNodeWithTag("series-season-1").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("series-season-1")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus(episode(1))
        press(KeyEvent.KEYCODE_DPAD_RIGHT, times = 7)
        awaitFocus(episode(8))
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus("series-season-1")
    }

    @Test
    fun choosingSeasonFiveAfterEpisodeEightFocusesItsFirstEpisode() {
        browseToEpisodeEight()
        press(KeyEvent.KEYCODE_DPAD_RIGHT, times = 4)
        awaitFocus("series-season-5")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus(episode(1, season = 5))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(episode(2, season = 5))
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus("series-season-5")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus(episode(1, season = 5))
    }

    @Test
    fun downFromSeasonAfterEpisodeEightReturnsToItsFirstEpisode() {
        browseToEpisodeEight()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus(episode(1))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(episode(2))
    }
}
