package com.sohva.tv.app

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction

/** True once a node with [tag] is in the tree; for `waitUntil` conditions. */
fun ComposeTestRule.onAllNodesWithTagExists(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

/** True once a node with [text] (whole or [substring]) is in the tree. */
fun ComposeTestRule.onAllNodesWithTextExists(text: String, substring: Boolean = false, unmerged: Boolean = false): Boolean =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

/**
 * Focus on a Home rail item: Home itself focuses its content (spec 02 §3.4), so tests that start
 * from the rail press Left as the viewer would, then move to the item and wait for it to hold focus.
 */
fun ComposeTestRule.focusRail(item: com.sohva.tv.feature.home.RailItem) {
    waitUntil(15_000) { onAllNodesWithTagExists(item.tag) }
    val rail = com.sohva.tv.feature.home.RailItem.entries.map { it.tag }
    val onRail = {
        onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().any { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) in rail
        }
    }
    // Home places focus on its content first; then Left, as the viewer opens the rail (HOME-FR-47).
    waitUntil(15_000) { onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty() }
    // Left walks a row to its first card, then enters the rail.
    repeat(20) {
        if (onRail()) return@repeat
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        waitForIdle()
    }
    waitUntil(10_000) { onRail() }
    onAllNodesWithTag(item.tag)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
    waitUntil(10_000) {
        onAllNodes(androidx.compose.ui.test.hasTestTag(item.tag) and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty()
    }
}
