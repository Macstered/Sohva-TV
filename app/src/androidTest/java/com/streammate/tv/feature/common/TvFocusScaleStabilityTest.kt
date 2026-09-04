package com.streammate.tv.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.testing.awaitUntil
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared focusable surfaces lift on focus. That lift must stay a drawing
 * effect: if it enlarges the bounds the component reports, the scrollable it
 * sits in scrolls to fit the newly focused item and everything else shifts,
 * which is what made the library grid twitch on every press.
 *
 * Rows of buttons inside a scrolling list are the everyday case - the settings
 * screen is built from them - and there a sideways move must not scroll at all.
 */
@RunWith(AndroidJUnit4::class)
class TvFocusScaleStabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun movingAcrossARowDoesNotScrollTheListItSitsIn() {
        composeRule.setContent {
            val first = remember { FocusRequester() }
            StreamMateTheme {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items((0 until ROWS).toList()) { row ->
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            (0 until COLUMNS).forEach { column ->
                                TvActionButton(
                                    label = "Cell $row-$column",
                                    onClick = {},
                                    testTag = tagOf(row, column),
                                    focusRequester = if (row == 0 && column == 0) first else null,
                                )
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
        }

        composeRule.awaitUntil {
            composeRule.onAllNodes(hasTestTag(tagOf(0, 0)) and isFocused())
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Walk down until the focused row is against the bottom of the list.
        // With the list resting at the top there is nothing to scroll and the
        // defect cannot show.
        repeat(ROWS_DOWN) {
            pressOnFocused(Key.DirectionDown)
            composeRule.waitForIdle()
        }

        listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionLeft).forEachIndexed { step, key ->
            assertListHoldsStill(step, key)
        }
    }

    private fun assertListHoldsStill(step: Int, key: Key) {
        val before = visibleCellTops()
        val movedFrom = focusedTag()

        pressOnFocused(key)
        composeRule.waitForIdle()
        composeRule.awaitUntil { focusedTag() != movedFrom }
        val movedTo = focusedTag()
        val after = visibleCellTops()

        val drifted = before
            .filterKeys { it != movedFrom && it != movedTo && it in after }
            .mapNotNull { (tag, y) ->
                val moved = after.getValue(tag) - y
                if (abs(moved) > 0.5f) "$tag moved ${moved}px" else null
            }
        assertTrue(
            "step $step ($key, $movedFrom -> $movedTo) scrolled the list: $drifted",
            drifted.isEmpty(),
        )
    }

    private fun visibleCellTops(): Map<String, Float> = buildMap {
        for (row in 0 until ROWS) {
            for (column in 0 until COLUMNS) {
                val tag = tagOf(row, column)
                composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
                    .firstOrNull()
                    ?.let { put(tag, it.positionInRoot.y) }
            }
        }
    }

    private fun focusedTag(): String? = composeRule
        .onAllNodes(isFocused())
        .fetchSemanticsNodes()
        .firstNotNullOfOrNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)
                ?.takeIf { it.startsWith(TAG_PREFIX) }
        }

    private fun pressOnFocused(key: Key) {
        composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
    }

    private fun tagOf(row: Int, column: Int) = "$TAG_PREFIX-$row-$column"

    private companion object {
        const val TAG_PREFIX = "cell"
        const val ROWS = 14
        const val COLUMNS = 4
        const val ROWS_DOWN = 5
    }
}
