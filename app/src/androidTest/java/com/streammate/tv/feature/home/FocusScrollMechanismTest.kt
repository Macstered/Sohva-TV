package com.streammate.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.feature.common.KeepFocusedChildVisibleColumn
import com.streammate.tv.feature.common.KeepFocusedChildVisibleLazyColumn
import com.streammate.tv.feature.common.KeepFocusedChildVisibleScrollBehavior
import com.streammate.tv.feature.common.scrollsToTopWhenFocused
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home, the movie page and the series page all put a block of text above the
 * first thing that takes focus. Walking down into the rows scrolls that block
 * off the top, and walking back up has to bring all of it back - Compose only
 * scrolls far enough to show the focused control itself, which leaves the text
 * above it off the screen.
 *
 * The screens are hard to fill with enough content from a test, so the shape is
 * checked here instead: a header nobody can focus, a control under it, and a
 * row below. What passes here is what those screens are wired to do.
 */
@RunWith(AndroidJUnit4::class)
class FocusScrollMechanismTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusReturningToTheTopItemBringsBackWhatSitsAboveIt() {
        composeRule.setContent {
            val listState = rememberLazyListState()
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocus() }
            KeepFocusedChildVisibleLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                item {
                    Column(
                        modifier = Modifier.scrollsToTopWhenFocused(
                            offset = {
                                if (listState.firstVisibleItemIndex == 0 &&
                                    listState.firstVisibleItemScrollOffset == 0
                                ) {
                                    0
                                } else {
                                    1
                                }
                            },
                            scrollToTop = { listState.scrollToItem(0) },
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().height(320.dp).testTag("header"))
                        Row {
                            Box(
                                Modifier
                                    .width(200.dp)
                                    .height(60.dp)
                                    .focusRequester(first)
                                    .focusable()
                                    .testTag("top-control"),
                            )
                            Box(
                                Modifier
                                    .width(200.dp)
                                    .height(60.dp)
                                    .focusable()
                                    .testTag("top-control-2"),
                            )
                        }
                    }
                }
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .focusable()
                            .testTag("row-control"),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val resting = headerTop()

        pressOnFocused(Key.DirectionDown)
        composeRule.waitForIdle()
        assertTrue(
            "going down did not scroll: the header stayed at $resting",
            headerTop() < resting,
        )

        pressOnFocused(Key.DirectionUp)
        composeRule.waitForIdle()

        assertEquals("coming back up left the header above the top edge", resting, headerTop())

        // Stepping along the controls does not re-enter the group, so the
        // arrival correction must not be what keeps the top in place.
        pressOnFocused(Key.DirectionRight)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top-control-2").assertIsFocused()
        assertEquals(
            "stepping sideways along the top block threw the header off again",
            resting,
            headerTop(),
        )
    }

    @Test
    fun sidewaysMoveInTopRowDoesNotPivotTheVerticalScreen() {
        composeRule.setContent {
            val scrollState = rememberScrollState()
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocus() }
            KeepFocusedChildVisibleColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .scrollsToTopWhenFocused(
                        offset = { scrollState.value },
                        scrollToTop = { scrollState.scrollTo(0) },
                    ),
            ) {
                Box(Modifier.fillMaxWidth().height(260.dp).testTag("header"))
                Row {
                    Box(
                        Modifier
                            .width(200.dp)
                            .height(60.dp)
                            .focusRequester(first)
                            .focusable()
                            .testTag("top-control"),
                    )
                    Box(
                        Modifier
                            .width(200.dp)
                            .height(60.dp)
                            .focusable()
                            .testTag("top-control-2"),
                    )
                }
                Box(Modifier.fillMaxWidth().height(400.dp))
            }
        }
        composeRule.waitForIdle()

        val resting = headerTop()
        pressOnFocused(Key.DirectionRight)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("top-control-2").assertIsFocused()
        assertEquals(
            "the TV focus pivot scrolled a vertical screen for a sideways move",
            resting,
            headerTop(),
        )
    }

    @Test
    fun movingToAnAlreadyVisibleGridRowDoesNotScrollTheGrid() {
        lateinit var gridState: LazyGridState
        composeRule.setContent {
            gridState = rememberLazyGridState()
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocus() }
            KeepFocusedChildVisibleScrollBehavior {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items((0 until 8).toList()) { index ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                .focusable()
                                .testTag("grid-$index"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val restingIndex = gridState.firstVisibleItemIndex
        val restingOffset = gridState.firstVisibleItemScrollOffset

        pressOnFocused(Key.DirectionDown)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("grid-2").assertIsFocused()
        assertEquals(restingIndex, gridState.firstVisibleItemIndex)
        assertEquals(restingOffset, gridState.firstVisibleItemScrollOffset)
    }

    private fun headerTop(): Int = composeRule
        .onNodeWithTag("header")
        .fetchSemanticsNode()
        .positionInRoot
        .y
        .toInt()

    private fun pressOnFocused(key: Key) {
        composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
    }
}

@androidx.compose.runtime.Composable
private fun Box(modifier: Modifier) {
    androidx.compose.foundation.layout.Box(modifier.background(Color.DarkGray))
}
