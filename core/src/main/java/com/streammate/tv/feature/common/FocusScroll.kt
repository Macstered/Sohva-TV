package com.streammate.tv.feature.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlin.math.abs

/**
 * Gives one scroll container the non-TV bring-into-view policy.
 *
 * Compose normally installs a pivot policy on Leanback devices: every newly
 * focused child is moved to 30% of its scroll container, even when the child is
 * already completely visible. That is useful for rails, but on a vertically
 * scrolling screen it means moving sideways between two buttons also scrolls
 * the whole screen vertically.
 *
 * Compose the vertical scroll container directly inside [content]. Descendant
 * horizontal rails can retain the inherited TV policy by wrapping their
 * content in [InheritedFocusScrollBehavior].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeepFocusedChildVisibleForScrollContainer(
    content: @Composable (inheritedBehavior: BringIntoViewSpec) -> Unit,
) {
    val inheritedBehavior = LocalBringIntoViewSpec.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides KeepVisibleBringIntoViewSpec) {
        content(inheritedBehavior)
    }
}

/**
 * Applies visibility-only focus relocation to an arbitrary scroll container.
 *
 * Use this when the caller owns the scrollable itself (for example a lazy
 * grid). Focus can still bring a child that left the viewport back on screen,
 * but moving between children that are already fully visible does not pivot
 * the entire container around the newly focused item.
 */
@Composable
fun KeepFocusedChildVisibleScrollBehavior(content: @Composable () -> Unit) {
    KeepFocusedChildVisibleForScrollContainer { content() }
}

/** Restores a scroll behavior captured by [KeepFocusedChildVisibleForScrollContainer]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InheritedFocusScrollBehavior(
    behavior: BringIntoViewSpec,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides behavior, content = content)
}

/** A [Column] whose vertical scroll modifier uses visibility-only focus relocation. */
@Composable
fun KeepFocusedChildVisibleColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    KeepFocusedChildVisibleForScrollContainer { inheritedBehavior ->
        Column(modifier = modifier) {
            InheritedFocusScrollBehavior(inheritedBehavior) { content() }
        }
    }
}

/**
 * A [LazyColumn] that passes its inherited behavior to [content], allowing
 * nested horizontal rails to opt back into the TV pivot policy.
 */
@Composable
fun KeepFocusedChildVisibleLazyColumn(
    state: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.(inheritedBehavior: BringIntoViewSpec) -> Unit,
) {
    KeepFocusedChildVisibleForScrollContainer { inheritedBehavior ->
        LazyColumn(
            state = state,
            modifier = modifier,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
        ) {
            content(inheritedBehavior)
        }
    }
}

/** The usual phone/tablet policy: do nothing while the requested child fits. */
private object KeepVisibleBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leadingEdge = offset
        val trailingEdge = offset + size
        return when {
            leadingEdge >= 0f && trailingEdge <= containerSize -> 0f
            leadingEdge < 0f && trailingEdge > containerSize -> 0f
            abs(leadingEdge) < abs(trailingEdge - containerSize) -> leadingEdge
            else -> trailingEdge - containerSize
        }
    }
}

/**
 * Brings the whole top of a screen back once when focus enters the block above
 * the fold.
 *
 * Home, the movie page and the series page all put a brand, a breadcrumb or a
 * title above the first control that can take focus. Compose scrolls only far
 * enough to show the control itself, so everything above it stays off the top
 * edge - on arrival, and again every time focus comes back up from the rows.
 *
 * The frame wait is the whole trick. Scrolling straight from the focus callback
 * puts the request in before Compose does its own bring-into-view, which then
 * scrolls right back down and undoes it; the symptom is a screen that stays
 * short of the top until some later focus move happens to make it stick.
 * Waiting a frame puts this after that, where it holds. The surrounding scroll
 * container must use [KeepFocusedChildVisibleColumn] or
 * [KeepFocusedChildVisibleLazyColumn], otherwise the Leanback pivot policy
 * will scroll again on every move within the block.
 */
@Composable
fun Modifier.scrollsToTopWhenFocused(
    /** Zero while the screen is scrolled right up; anything else means it is not. */
    offset: () -> Int,
    scrollToTop: suspend () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        // Let the focused control's own bring-into-view request run first, then
        // restore the non-focusable header above it. This only runs on entry;
        // sideways moves keep the group focused and must not need correction.
        withFrameNanos { }
        if (offset() != 0) scrollToTop()
    }
    // The group is what gives this a focus node of its own to report on. On a
    // bare layout the callback never hears about the focusables inside it.
    return onFocusChanged { focused = it.hasFocus }.focusGroup()
}
