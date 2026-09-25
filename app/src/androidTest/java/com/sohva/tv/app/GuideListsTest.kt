package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Spec 21 CHAN-28 / spec 20 GUIDE-12: a custom list is a rail entry with its channels in its own order. */
@RunWith(AndroidJUnit4::class)
class GuideListsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private var listId = ""
    private val seed = object : ExternalResource() {
        override fun before() {
            GuideFixture.seed(graph, groups = 1, perGroup = 8)
            runBlocking {
                listId = graph.data.channelLists.create("Evening")!!
                // Added out of playlist order: the list keeps its own.
                graph.data.channelLists.add(listId, "fixture-0:c5")
                graph.data.channelLists.add(listId, "fixture-0:c2")
            }
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
    }

    private fun described(text: String) = compose.onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aCustomListShowsItsChannelsInItsOwnOrder() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("guide-rail-group:g0")
        // Favourites, All channels, Recently watched, the lists, then the groups.
        press(KeyEvent.KEYCODE_DPAD_UP)
        awaitFocus("guide-rail-list:$listId")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("guide-row-0")
        compose.waitUntil(8_000) { described("Lumen 6") }
        // Row 0 is the first member (channel 6), row 1 the second (channel 3); nothing else.
        assertTrue(described("6 Lumen 6"))
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("guide-row-1")
        assertTrue(described("Pulse HD 3"))
        assertFalse(described("Northstar 1"))
    }
}
