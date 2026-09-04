package com.streammate.tv.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag

/**
 * Polls until [condition] holds, treating a thrown condition as "not yet".
 *
 * Compose's own `waitUntil` only retries while the block returns `false`; an
 * exception from inside the block escapes immediately. Every semantics query
 * throws `IllegalStateException("No compose hierarchies found in the app")`
 * until the activity has attached its Compose view, so
 *
 *     waitUntil { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
 *
 * fails instantly against a cold activity rather than waiting for it. That is
 * timing-dependent, which is why these tests pass when a class is run on its
 * own and fail in a full suite run.
 */
fun ComposeTestRule.awaitUntil(
    timeoutMillis: Long = DEFAULT_AWAIT_TIMEOUT_MILLIS,
    condition: () -> Boolean,
) {
    waitUntil(timeoutMillis) { runCatching(condition).getOrDefault(false) }
}

/**
 * Waits for the node tagged [testTag] to actually hold focus, then asserts it.
 *
 * Initial focus is requested from a `LaunchedEffect` once the node is attached,
 * so there is a window in which the node exists but focus has not landed on it
 * yet. Waiting only for the node to appear and asserting focus in the next
 * statement races that window, which is why these assertions failed
 * intermittently under load and passed when the class ran on its own.
 */
fun ComposeTestRule.awaitFocused(
    testTag: String,
    timeoutMillis: Long = DEFAULT_AWAIT_TIMEOUT_MILLIS,
) {
    try {
        awaitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(testTag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    } catch (timeout: ComposeTimeoutException) {
        // "Condition still not satisfied" says nothing about why. Whether the
        // tree had no focus at all or focus simply landed somewhere else is the
        // difference between a focus request that never fired and one that
        // targeted the wrong node, so name it in the failure.
        throw AssertionError(
            "Timed out after ${timeoutMillis}ms waiting for '$testTag' to take focus. " +
                describeFocus(),
            timeout,
        )
    }
    onNodeWithTag(testTag).assertIsFocused()
}

private fun ComposeTestRule.describeFocus(): String {
    val focused = runCatching {
        onAllNodes(isFocused()).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                ?: "<untagged>"
        }
    }.getOrElse { return "The semantics tree could not be read: ${it.message}" }
    return if (focused.isEmpty()) {
        "Nothing in the tree holds focus."
    } else {
        "Focus is on ${focused.joinToString()}."
    }
}

const val DEFAULT_AWAIT_TIMEOUT_MILLIS = 15_000L
