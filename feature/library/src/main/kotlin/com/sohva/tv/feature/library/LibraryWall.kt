package com.sohva.tv.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.text.genreLabel
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the wall asks of the screen around it: moving focus to the rail (VOD-FR-50). */
internal fun interface RailFocus {
    fun focusRail()
}

/**
 * The poster grid (spec 40 §5 "Wall", layout §1 "Grid"): fixed columns derived from the width and
 * the 88 dp minimum cell, so a card's column is its absolute index mod the column count (VOD-FR-50),
 * never the grid's momentary list of visible items. Slots outside the window are plain tiles.
 */
@Composable
internal fun LibraryWall(model: LibraryModel, grid: LazyGridState, rail: RailFocus, back: FocusRequester, modifier: Modifier) = trace("Library:Wall") {
    val wall by model.wall.collectAsStateWithLifecycle()
    val search by model.search.collectAsStateWithLifecycle()
    val ticks by model.ticks.collectAsStateWithLifecycle()
    BoxWithConstraints(modifier.testTag("library-wall")) {
        val columns = maxOf(1, ((maxWidth + GAP_H) / (MIN_CELL + GAP_H)).toInt())
        val cellWidth = (maxWidth - GAP_H * (columns - 1)) / columns
        val posterPx = with(LocalDensity.current) {
            IntSize(cellWidth.roundToPx().coerceAtMost(POSTER_MAX_W), (cellWidth * 1.5f).roundToPx().coerceAtMost(POSTER_MAX_H))
        }
        val window = wall.window
        val message = wallMessage(wall, search)
        if (message != null && window.items.isEmpty()) {
            Text(message, Modifier.align(Alignment.Center).testTag("library-message"), style = Sohva.typography.bodyLarge, color = Sohva.palette.textMuted)
            return@BoxWithConstraints
        }
        ReturnFocus(model, grid, back)
        val scope = rememberCoroutineScope()
        val focus = remember(grid) { WallFocus(grid, scope) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(GAP_H),
            verticalArrangement = Arrangement.spacedBy(GAP_V),
        ) {
            items(
                count = window.slots(columns),
                key = { i -> window.itemAt(i)?.row?.key ?: "slot:$i" },
                contentType = { i -> if (window.itemAt(i) == null) 0 else 1 },
            ) { i ->
                val item = window.itemAt(i)
                if (item == null) {
                    EmptySlot()
                } else {
                    PosterCard(
                        item = item,
                        enabled = wall.current,
                        watched = item.row.key in ticks,
                        posterPx = posterPx,
                        onFocus = {
                            focus.landed(i)
                            model.focused(i, item.row.key, columns)
                        },
                        onOpen = { model.open(item, i) },
                        onMove = { direction -> focus.move(model.wall.value.window, i, columns, direction, rail) },
                        register = focus,
                        index = i,
                        modifier = if (item.row.key == model.focusedKey) Modifier.focusRequester(back) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * Moves focus between cards by absolute index (VOD-FR-50/51): ±1 along a row, ±columns between
 * rows, so a held key never drifts a column or skips a card while the grid scrolls. A move into a
 * slot whose page is not loaded yet stays put until the page lands (§9.3). Up from the top row is
 * left to the platform, as in beta 23. Only composed cards are registered, so the map is bounded
 * by what the grid composes.
 */
internal class WallFocus(private val grid: LazyGridState, private val scope: CoroutineScope) {
    private val cards = HashMap<Int, FocusRequester>()

    /** Where focus is going; presses that arrive before it lands build on it, so none is lost. */
    private var pending = NONE
    private var driver: Job? = null

    fun register(index: Int, requester: FocusRequester) {
        cards[index] = requester
    }

    fun unregister(index: Int, requester: FocusRequester) {
        if (cards[index] === requester) cards.remove(index)
    }

    /** A card took focus: a pending move to it is done. */
    fun landed(index: Int) {
        if (index == pending) pending = NONE
    }

    /** True when the key was handled here. */
    fun move(window: WallWindow, focused: Int, columns: Int, direction: Key, rail: RailFocus): Boolean {
        val from = if (pending != NONE) pending else focused
        val column = from % columns
        val target = when (direction) {
            Key.DirectionLeft -> if (column == 0) return true.also { pending = NONE; rail.focusRail() } else from - 1
            Key.DirectionRight -> if (column == columns - 1) return true else from + 1
            Key.DirectionUp -> if (from < columns) return pending != NONE else from - columns
            Key.DirectionDown -> {
                val below = from + columns
                // A short last row: Down lands on its last card.
                if (window.atEnd && below >= window.end && from / columns < (window.end - 1) / columns) window.end - 1 else below
            }
            else -> return false
        }
        // Beyond the loaded pages: stay until the page lands (never skip).
        if (window.itemAt(target) == null) return true
        pending = target
        if (driver?.isActive != true) driver = scope.launch { drive(columns) }
        return true
    }

    /** Scrolls the pending card's row into view when it is not composed, then focuses it. */
    private suspend fun drive(columns: Int) {
        var frames = 0
        while (pending != NONE && frames < MAX_FRAMES) {
            val target = pending
            val card = cards[target]
            if (card != null && runCatching { card.requestFocus() }.getOrDefault(false)) {
                if (pending == target) pending = NONE
                continue
            }
            val info = grid.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isNotEmpty()) {
                val pitch = visible.first().size.height + info.mainAxisItemSpacing
                val rows = maxOf(1, (info.viewportSize.height + info.mainAxisItemSpacing) / maxOf(1, pitch))
                val row = target / columns
                val firstRow = visible.first().index / columns
                // The target's row becomes the last whole row when below, the first when above.
                val top = if (row < firstRow) row else maxOf(0, row - rows + 1)
                grid.scrollToItem(top * columns)
            }
            withFrameNanos { }
            frames++
        }
        pending = NONE
    }

    private companion object {
        const val NONE = -1
        const val MAX_FRAMES = 30
    }
}

/** Back from a details page focuses the same card, scrolled into view (VOD-FR-57). */
@Composable
private fun ReturnFocus(model: LibraryModel, grid: LazyGridState, back: FocusRequester) {
    val wall by model.wall.collectAsStateWithLifecycle()
    LaunchedEffect(wall.current) {
        if (!wall.current || !model.focusOnWall || model.focusedKey == null) return@LaunchedEffect
        if (wall.window.itemAt(model.focusedIndex)?.row?.key != model.focusedKey) return@LaunchedEffect
        val visible = grid.layoutInfo.visibleItemsInfo.any { it.index == model.focusedIndex }
        if (!visible) grid.scrollToItem(model.focusedIndex)
        back.requestFocusWhenAttached()
    }
}

/** A slot whose page is not in memory: one solid rounded rectangle, never focusable (§9.6). */
@Composable
private fun EmptySlot() {
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).roundFill(Sohva.palette.surfaceSubtle, Sohva.shapes.medium))
        Spacer(Modifier.height(SLOT_TEXT))
    }
}

@Composable
private fun wallMessage(wall: WallState, search: String): String? = when {
    wall.destination == null -> null
    wall.failed -> stringResource(R.string.catalogue_v2_failed)
    !wall.current -> stringResource(R.string.catalogue_v2_loading)
    wall.window.items.isEmpty() && search.isNotBlank() -> stringResource(R.string.catalogue_no_results)
    wall.window.items.isEmpty() -> stringResource(R.string.catalogue_v2_empty_group)
    else -> null
}

/** Header label of a destination (VOD-FR-05). */
@Composable
internal fun destinationLabel(destination: WallDestination?): String? = when (destination) {
    null -> null
    WallDestination.History -> stringResource(R.string.catalogue_history)
    is WallDestination.Group -> destination.name
    WallDestination.AllGroups -> stringResource(R.string.catalogue_all)
    is WallDestination.OfGenre -> stringResource(genreLabel(destination.genre))
    WallDestination.Unsorted -> stringResource(R.string.catalogue_genre_unsorted)
    is WallDestination.Custom -> destination.group.name
}

internal val MIN_CELL = 88.dp
internal val GAP_H = 22.dp
internal val GAP_V = 30.dp

/** Poster title (9 + 19) and facts (16) under the poster. */
private val SLOT_TEXT = 44.dp
private const val POSTER_MAX_W = 192
private const val POSTER_MAX_H = 288
