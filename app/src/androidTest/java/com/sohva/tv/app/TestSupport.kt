package com.sohva.tv.app

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag

/** True once a node with [tag] is in the tree; for `waitUntil` conditions. */
fun ComposeTestRule.onAllNodesWithTagExists(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

/** True once a node with [text] (whole or [substring]) is in the tree. */
fun ComposeTestRule.onAllNodesWithTextExists(text: String, substring: Boolean = false, unmerged: Boolean = false): Boolean =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
