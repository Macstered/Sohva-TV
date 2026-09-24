package com.sohva.tv.spike.guidegrid

import androidx.compose.animation.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.motion.Motion
import com.sohva.tv.ui.design.theme.Sohva

internal const val NONE = -2
internal const val CHANNEL = -1

/**
 * Where the selection is across rows. Plain fields, never snapshot state: nothing in composition
 * may read it (GUIDE-NFR-02). The time anchor keeps the selected time when moving between rows.
 */
@Stable
internal class GridSelection {
    var anchorMinute: Float = SpikeData.NOW_MINUTE.toFloat()
    var channelMode: Boolean = true

    fun enter(row: SpikeRow): Int = if (channelMode) CHANNEL else SpikeData.blockAt(row, anchorMinute)
}

/** One row's selected column: read only in its draw phase and key handler. */
@Stable
internal class RowSelection {
    var column by mutableIntStateOf(NONE)
}

/**
 * Variant A: one focusable and one draw node per row. A press changes two rows' [RowSelection]:
 * those two rows redraw; nothing recomposes, nothing re-measures (labels come from a cached
 * measurer keyed without colour).
 */
@Composable
fun CanvasGrid(firstRow: FocusRequester, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val selection = remember { GridSelection() }
    val colors = GridColors.from(Sohva.palette)
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(SpikeData.CHANNELS, key = { it }) { index ->
            CanvasRow(index, selection, RowPainter(measurer, colors), if (index == 0) firstRow else null)
        }
    }
}

@Composable
private fun CanvasRow(index: Int, selection: GridSelection, painter: RowPainter, requester: FocusRequester?) {
    val row = remember(index) { SpikeData.row(index) }
    val state = remember { RowSelection() }
    // Only the channel cell's fill animates, and only in the draw phase.
    val channelFill = remember { Animatable(Color.Transparent) }
    val spec = Motion.focus<Color>()
    val colors = painter.colors
    LaunchedEffect(state) {
        snapshotFlow { state.column }.collect { column ->
            val target = when (column) {
                CHANNEL -> colors.focusFill
                NONE -> Color.Transparent
                else -> colors.rowSelected
            }
            channelFill.animateTo(target, spec)
        }
    }
    val focus = if (requester != null) Modifier.focusRequester(requester) else Modifier
    Box(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .then(focus)
            .onFocusChanged { state.column = if (it.isFocused) selection.enter(row) else NONE }
            .onPreviewKeyEvent { handleKey(it, row, state, selection) }
            .focusable()
            .clearAndSetSemantics { contentDescription = "${row.number} ${row.name}" },
    ) {
        // Two render nodes: the channel cell's fade redraws only the cell, never the programmes;
        // scrolling moves both instead of re-recording them.
        Spacer(
            Modifier.width(CHANNEL_WIDTH).fillMaxHeight().graphicsLayer()
                .drawBehind { with(painter) { drawChannel(row, state.column, channelFill.value) } },
        )
        Spacer(
            Modifier.fillMaxSize().padding(start = CHANNEL_WIDTH + GAP).graphicsLayer()
                .drawBehind { with(painter) { drawTimeline(row, state.column) } },
        )
    }
}

/** Left and Right move within the row; Up and Down fall through to focus search (the next row). */
private fun handleKey(event: KeyEvent, row: SpikeRow, state: RowSelection, selection: GridSelection): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val visible = SpikeData.visibleBlocks(row)
    val column = state.column
    val next = when (event.key) {
        Key.DirectionRight -> when {
            column == CHANNEL -> visible.firstOrNull { row.programmes[it].endMinute > selection.anchorMinute } ?: visible.first()
            column < visible.last() -> column + 1
            else -> return true // the real guide pages +90 min here
        }
        Key.DirectionLeft -> when {
            column == CHANNEL -> return false // the real guide opens the rail
            column > visible.first() -> column - 1
            else -> CHANNEL
        }
        else -> return false
    }
    state.column = next
    selection.channelMode = next == CHANNEL
    if (next >= 0) selection.anchorMinute = row.programmes[next].startMinute.coerceAtLeast(0).toFloat()
    return true
}
