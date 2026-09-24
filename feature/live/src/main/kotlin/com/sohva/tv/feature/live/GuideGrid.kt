package com.sohva.tv.feature.live

import android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import android.view.KeyEvent.KEYCODE_MEDIA_REWIND
import android.view.KeyEvent.KEYCODE_MENU
import android.view.KeyEvent.KEYCODE_NUMPAD_0
import android.view.KeyEvent.KEYCODE_NUMPAD_9
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The day label and ruler over the rows, and one now-line across both (guide.md §2). A new list
 * gets a new scroll state; paging time never re-reads or reorders rows (GUIDE-FR-31).
 */
@Composable
internal fun GuideGrid(model: GuideModel, view: ListView, actions: RowActions, modifier: Modifier = Modifier) = trace("Guide:Grid") {
    val palette = Sohva.palette
    val measurer = rememberTextMeasurer(cacheSize = TEXT_CACHE)
    val filler = FillerTexts(stringResource(R.string.guide_no_epg), stringResource(R.string.guide_watch_channel))
    val painter = remember(palette, measurer, filler) { RowPainter(measurer, GridColors.from(palette), filler) }
    val memory = remember { GridMemory() }
    key(view.serial) {
        val handles = remember { HashMap<Int, RowHandle>() }
        val start = model.focus.value?.index?.coerceAtLeast(0) ?: 0
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
        BoxWithConstraints(modifier.onPreviewKeyEvent { gridKey(it, model, memory) }) {
            val timeline = maxWidth - CHANNEL_WIDTH - TIMELINE_GAP
            Column(Modifier.fillMaxSize()) {
                GridHeader(model, timeline)
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).testTag("guide-grid"),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                    contentPadding = PaddingValues(bottom = ROW_GAP),
                ) {
                    items(view.size) { index ->
                        GuideRow(index, model.rowPages.row(index), model, painter, memory, handles, actions)
                    }
                }
            }
            NowLine(model, timeline)
        }
        ViewportReporter(listState, model)
        FocusApplier(listState, handles, model, memory)
    }
}

/** Reports the rows on screen, after layout (no request before real rows exist, GUIDE-FR-50). */
@Composable
private fun ViewportReporter(state: LazyListState, model: GuideModel) {
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.let { items -> (items.firstOrNull()?.index ?: 0) to items.size } }
            .distinctUntilChanged()
            .collect { (first, count) -> if (count > 0) model.onViewport(first, count) }
    }
}

/** Carries out the model's focus hand-offs: scroll the row into view, then focus it. */
@Composable
private fun FocusApplier(state: LazyListState, handles: Map<Int, RowHandle>, model: GuideModel, memory: GridMemory) {
    val focus by model.focus.collectAsStateWithLifecycle()
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        val visible = state.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == target.index && it.offset >= 0 }) state.scrollToItem(target.index)
        repeat(FOCUS_FRAMES) {
            val handle = handles[target.index]
            // A row whose page is still being read is a stand-in and cannot take focus: wait for it.
            if (handle != null && model.rowPages.peek(target.index) != null) {
                memory.focusedState?.takeIf { it !== handle.state }?.column = NONE
                if (target.column != GuideModel.KEEP) {
                    memory.channelMode = target.column == GuideModel.CHANNEL
                    handle.state.column = target.column
                }
                handle.requester.requestFocusWhenAttached()
                return@LaunchedEffect
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }
}

/** Container keys while focus is anywhere in the grid (GUIDE-FR-76). */
private fun gridKey(event: androidx.compose.ui.input.key.KeyEvent, model: GuideModel, memory: GridMemory): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val code = event.key.nativeKeyCode
    val row = memory.focusedIndex
    val repeat = event.nativeKeyEvent.repeatCount
    return when {
        code in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
            if (repeat == 0) model.overlays.dialDigit(code - android.view.KeyEvent.KEYCODE_0)
            true
        }
        code in KEYCODE_NUMPAD_0..KEYCODE_NUMPAD_9 -> {
            if (repeat == 0) model.overlays.dialDigit(code - KEYCODE_NUMPAD_0)
            true
        }
        code == KEYCODE_MEDIA_FAST_FORWARD -> pageFromKey(model, memory, +1, days = false, row)
        code == KEYCODE_MEDIA_REWIND -> pageFromKey(model, memory, -1, days = false, row)
        code == KEYCODE_MEDIA_NEXT -> pageFromKey(model, memory, +1, days = true, row)
        code == KEYCODE_MEDIA_PREVIOUS -> pageFromKey(model, memory, -1, days = true, row)
        code == KEYCODE_MENU -> {
            model.overlays.openOptions()
            true
        }
        else -> false
    }
}

private fun pageFromKey(model: GuideModel, memory: GridMemory, direction: Int, days: Boolean, row: Int): Boolean {
    if (row < 0) return true
    if (model.page(direction, row, days)) {
        memory.focusedState?.column = GuideModel.CHANNEL
        memory.channelMode = true
    }
    return true
}

@Composable
private fun GridHeader(model: GuideModel, timeline: Dp) {
    val window by model.window.collectAsStateWithLifecycle()
    val labels by model.labels.collectAsStateWithLifecycle()
    val start = model.windowStartState.longValue
    val now = model.nowState.longValue
    val palette = Sohva.palette
    val type = Sohva.typography
    Row(Modifier.fillMaxWidth().height(20.dp).clearAndSetSemantics { }) {
        Row(Modifier.width(CHANNEL_WIDTH)) {
            Text(labels.dayLabel(start), style = type.label.copy(fontWeight = FontWeight.Bold), color = palette.textPrimary, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            val atNow = window.isAtNow(now)
            Text(
                relativeLabel(atNow, labels.relativeDay(start, now)),
                style = type.caption,
                color = if (atNow) palette.textDim else palette.accent,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(TIMELINE_GAP))
        Box(Modifier.width(timeline).height(20.dp)) {
            val tick = timeline / (GuideWindow.LENGTH_MINUTES / 30)
            for (i in 0 until GuideWindow.LENGTH_MINUTES / 30) {
                Text(
                    labels.guideTime(start + i * GuideWindow.HALF_HOUR_MS),
                    Modifier.offset(x = tick * i),
                    style = type.label,
                    color = palette.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun relativeLabel(atNow: Boolean, day: TimeLabels.RelativeDay?): String = when {
    atNow -> stringResource(R.string.guide_window_now)
    day == TimeLabels.RelativeDay.TODAY -> stringResource(R.string.guide_window_today)
    day == TimeLabels.RelativeDay.TOMORROW -> stringResource(R.string.guide_window_tomorrow)
    day == TimeLabels.RelativeDay.YESTERDAY -> stringResource(R.string.guide_window_yesterday)
    else -> ""
}

/** One red line over the ruler and every row, moved with the minute (guide.md §2 "Now-line"). */
@Composable
private fun NowLine(model: GuideModel, timeline: Dp) {
    val danger = Sohva.palette.danger
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val start = model.windowStartState.longValue
        val now = model.nowState.longValue
        if (now < start || now >= start + GuideWindow.LENGTH_MS) return@Canvas
        val x = (CHANNEL_WIDTH + TIMELINE_GAP).toPx() + timeline.toPx() * (now - start) / GuideWindow.LENGTH_MS
        drawLine(danger, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        drawCircle(danger, 4.5.dp.toPx(), Offset(x, 4.5.dp.toPx()))
    }
}

private const val TEXT_CACHE = 512
/** About five seconds of frames: a page read at owner scale takes well under one. */
private const val FOCUS_FRAMES = 300
