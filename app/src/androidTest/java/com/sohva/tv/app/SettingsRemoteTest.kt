package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Spec 31 §11 UI tests for Settings › Remote buttons. Keys go through the window like a remote's. */
@RunWith(AndroidJUnit4::class)
class SettingsRemoteTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun await(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(tag) }

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            val focused = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("waiting for $tag, focused: $focused", e)
        }
    }

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    /** Presses [keyCode] until [tag] has focus, like a viewer walking there with the remote. */
    private fun walkTo(tag: String, keyCode: Int = KeyEvent.KEYCODE_DPAD_DOWN, limit: Int = 30) {
        repeat(limit) {
            if (focused(tag)) return
            press(keyCode)
        }
        awaitFocus(tag)
    }

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    private fun cell(button: String, gesture: String) = "settings-remote-slot-$button-$gesture"

    private fun openRemote() {
        await(RailItem.SETTINGS.tag)
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        // Settings opens on Playlists with its first control focused; walk the rail like a remote.
        awaitFocus("source-add-m3u")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        awaitFocus("settings-section-remote")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        await(cell("up", "press"))
        awaitFocus(cell("up", "press"))
    }

    @Test
    fun theGridShowsTheDefaultsAndNoBackPressCell() {
        openRemote()
        assertEquals("Channel list", text(cell("up", "press")))
        assertEquals("Quick actions", text(cell("menu", "press")))
        assertEquals("Switch to previous channel", text(cell("back", "hold")))
        assertTrue(compose.onAllNodes(hasTestTag(cell("back", "press"))).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun choosingAnActionChangesOnlyThatCellAndFocusReturnsToIt() {
        openRemote()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // The list opens with the current action focused.
        awaitFocus("settings-remote-action-open_channel_browser")
        walkTo("settings-remote-action-go_home")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        await(cell("up", "press"))
        awaitFocus(cell("up", "press"))
        compose.waitUntil(5_000) { text(cell("up", "press")) == "Home" }
        assertEquals("Channel list", text(cell("down", "press")))
        val stored = runBlocking { graph.data.preferences.remoteMapping.first() }
        assertEquals(RemoteAction.GO_HOME, stored.action(RemoteButton.UP, Gesture.PRESS))
        // Back from the list changes nothing and returns to the cell.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-remote-action-go_home")
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus(cell("up", "press"))
        assertEquals("Home", text(cell("up", "press")))
    }

    @Test
    fun resetAsksFirstAndFocusLandsOnResetAfterEitherChoice() {
        runBlocking { graph.data.preferences.setRemoteAction(RemoteButton.UP, Gesture.PRESS, RemoteAction.GO_HOME) }
        openRemote()
        compose.waitUntil(5_000) { text(cell("up", "press")) == "Home" }
        walkTo("settings-remote-reset")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-remote-reset-confirm")
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus("settings-remote-reset-cancel")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-remote-reset")
        assertEquals("Home", text(cell("up", "press")))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-remote-reset-confirm")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-remote-reset")
        compose.waitUntil(5_000) { text(cell("up", "press")) == "Channel list" }
    }

    @Test
    fun theReadBackLineFollowsFocus() {
        openRemote()
        compose.waitUntil(5_000) { text("settings-remote-readback") == "Up, Press: Channel list" }
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(cell("up", "hold"))
        compose.waitUntil(5_000) { text("settings-remote-readback") == "Up, Hold: Next channel" }
    }
}
