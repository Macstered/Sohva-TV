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
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 42 §11 "Instrumentation": the library manager from the wall's Options › Edit and from
 * Settings, the keys of both panes, Back innermost first, a move and a toggle the wall follows.
 * Keys go through the window like a remote's.
 */
@RunWith(AndroidJUnit4::class)
class LibraryManagerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val seed = object : ExternalResource() {
        override fun before() {
            LibraryFixture.seed(graph, perGroup = 30)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String, timeout: Long = 10_000) {
        try {
            compose.waitUntil(timeout) { focused(tag) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val now = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("$tag not focused; focused: $now", e)
        }
    }

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    /** Every text of a row, merged: mark, title and subtitle. */
    private fun rowText(tag: String): String = runCatching {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    }.getOrDefault("")

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun item(id: Int) = "manager-item-${LibraryFixture.key(id)}"

    private val drama = "manager-group-name:drama"
    private val comedy = "manager-group-name:comedy"

    /** Home → Movies → Drama → Options › Edit, as the viewer does. */
    private fun openFromWall() {
        compose.waitUntil(15_000) { exists(RailItem.MOVIES.tag) }
        compose.onNodeWithTag(RailItem.MOVIES.tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(RailItem.MOVIES.tag)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("library-row-group:drama") }
        compose.onNodeWithTag("library-row-group:drama").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("library-row-group:drama")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { text("library-label") == "Drama  ·  30" }
        compose.onNodeWithTag("library-options").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("library-options")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("library-refresh")
        compose.onNodeWithTag("library-edit").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("library-edit")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-manager") }
    }

    /** ORG-FR-01, -02, -47, -48, -03: opens on the wall's group; a toggle hides the film on the wall. */
    @Test
    fun editFromTheWallOpensOnItsGroupAndATogglePassesToTheWall() {
        openFromWall()
        awaitFocus(drama)
        assertTrue(rowText(drama), rowText(drama).contains("30 / 30"))
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        awaitFocus(item(0))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { rowText(item(0)).startsWith("—") }
        // The row stays where it was and keeps focus (ORG-FR-55); Undo is offered.
        awaitFocus(item(0))
        assertTrue(exists("manager-undo"))
        // Left returns to the group, whose count followed.
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus(drama)
        compose.waitUntil(5_000) { rowText(drama).contains("29 / 30") }
        back()
        compose.waitUntil(10_000) { exists("screen-movies") }
        // Back on the wall, on Options (spec 42 §3).
        awaitFocus("library-options")
        compose.waitUntil(10_000) { text("library-label") == "Drama  ·  29" }
        assertFalse(exists("library-card-${LibraryFixture.key(0)}"))
    }

    /** ORG-FR-03: menu, move and selection each close with Back before the manager does. */
    @Test
    fun backClosesTheInnermostThingFirst() {
        openFromWall()
        awaitFocus(drama)
        // The group menu, then Back: focus is back on the group.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("manager-menu-shown")
        back()
        awaitFocus(drama)
        // Move mode, then Back: nothing moved.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("manager-menu-shown")
        compose.onNodeWithTag("manager-menu-move").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("manager-menu-move")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { text("manager-footer").startsWith("Move:") }
        awaitFocus(drama)
        press(KeyEvent.KEYCODE_DPAD_UP)
        back()
        compose.waitUntil(5_000) { !text("manager-footer").startsWith("Move:") }
        awaitFocus(drama)
        assertTrue(top(comedy) < top(drama))
        // Selection mode, then Back: selection ends, the manager stays.
        compose.onNodeWithTag("manager-bulk").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("manager-bulk")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("manager-bulk-mode")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { text("manager-footer").startsWith("OK: select") }
        back()
        compose.waitUntil(5_000) { !text("manager-footer").startsWith("OK: select") }
        assertTrue(exists("screen-manager"))
        // Nothing open: Back leaves to the wall.
        back()
        compose.waitUntil(10_000) { exists("screen-movies") }
    }

    /** ORG-FR-52: Move › Up › OK places the group before Comedy (A–Z put it after) and the order becomes manual. */
    @Test
    fun aMovedGroupKeepsItsNewPlace() {
        openFromWall()
        awaitFocus(drama)
        assertTrue(top(comedy) < top(drama))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("manager-menu-shown")
        compose.onNodeWithTag("manager-menu-move").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("manager-menu-move")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus(drama)
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.waitUntil(5_000) { top(drama) < top(comedy) }
        awaitFocus(drama)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("manager-undo") }
        awaitFocus(drama)
        assertTrue(top(drama) < top(comedy))
        val rules = runBlocking { graph.data.organization.groups(OrgRoom.MOVIES, null) }.map { it.key }
        assertEquals(listOf("@history", "name:drama", "name:comedy"), rules)
    }

    /** Spec 42 §3: Settings › Library › "Manage groups & content" opens Live TV; Back returns to the row. */
    @Test
    fun settingsOpensTheLiveRoomAndBackReturnsToTheRow() {
        compose.waitUntil(15_000) { exists(RailItem.SETTINGS.tag) }
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        // With a source the pane opens on its row (the fixture's), not on "Add".
        awaitFocus("source-vod-0")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        repeat(30) { if (!focused("settings-section-metadata")) press(KeyEvent.KEYCODE_DPAD_DOWN) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-metadata-tmdb-enabled")
        repeat(20) { if (!focused("settings-manage-groups")) press(KeyEvent.KEYCODE_DPAD_DOWN) }
        awaitFocus("settings-manage-groups")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-manager") }
        compose.waitUntil(10_000) { exists("manager-advanced") }
        back()
        compose.waitUntil(10_000) { !exists("screen-manager") }
        awaitFocus("settings-manage-groups")
    }
}
