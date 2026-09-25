package com.sohva.tv.feature.channels

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LogoTile
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The channel list (spec 21 §5, CHAN-NFR-01/03): the filtered count, then rows read by index from
 * the held pages; a row whose page is not held yet draws empty and asks for it. Focus on a row
 * selects it; the list body reads only the page version, never the selection.
 */
@Composable
internal fun ChannelListPane(model: ChannelsModel, modifier: Modifier) {
    val pages by model.pages.collectAsStateWithLifecycle()
    val version by model.version.collectAsStateWithLifecycle()
    val focusFirst by model.focusFirstRequest.collectAsStateWithLifecycle()
    // Held as a State and read only inside each row's derived flag: a focus move redraws the two
    // rows whose selection changed, not the list (CHAN-NFR-03).
    val selected = model.selected.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    val first = remember { FocusRequester() }
    Column(
        modifier
            .roundFill(Sohva.palette.surface, Sohva.shapes.medium)
            .border(1.dp, Sohva.palette.outline.copy(alpha = 0.56f), RoundedCornerShape(Sohva.shapes.medium))
            .padding(8.dp),
    ) {
        val size = pages?.size ?: 0
        Text(
            pluralStringResource(R.plurals.channels_count, size, size),
            Modifier.testTag("channels-count"),
            style = Sohva.typography.caption.copy(fontWeight = FontWeight.Normal),
            color = Sohva.palette.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("channels-list")) {
            items(size, key = { index -> pages?.rowAt(index)?.key ?: "#$index" }) { index ->
                // Reading the version here redraws a row when its page arrives.
                val row = if (version >= 0) pages?.rowAt(index) else null
                if (row == null) {
                    LaunchedEffect(index, pages) { model.request(index) }
                    Box(Modifier.fillMaxWidth().height(ROW_HEIGHT))
                } else {
                    ChannelListRow(model, row, selected, if (index == 0) Modifier.focusRequester(first) else Modifier)
                    // Read the neighbouring page before the remote reaches it, so a held key never
                    // meets an empty row it cannot focus.
                    LaunchedEffect(index, pages) {
                        val offset = index % ChannelPages.PAGE
                        if (offset >= ChannelPages.PAGE - PREFETCH) model.request(index + PREFETCH)
                        if (offset < PREFETCH && index >= PREFETCH) model.request(index - PREFETCH)
                    }
                }
            }
        }
    }
    // A filter or sort change puts focus on the first row (CHAN-FR-17); typing in the search field does not.
    LaunchedEffect(focusFirst) {
        // The model's current list, not the composed one: the request can arrive a frame before it.
        val list = model.pages.value ?: return@LaunchedEffect
        if (focusFirst == 0 || list.size == 0) return@LaunchedEffect
        // Wait until the list has laid out these rows: before that, index 0 is still the old first
        // row, and the list would then keep that row at the top by its key.
        var frames = 0
        while (frames++ < FRAMES && !laidOut(state, list)) withFrameNanos { }
        state.requestScrollToItem(0)
        withFrameNanos { }
        first.requestFocusWhenAttached()
    }
}

/** True once the list shows [list]'s rows: its first visible item has the key [list] has at that index. */
private fun laidOut(state: androidx.compose.foundation.lazy.LazyListState, list: ChannelPages): Boolean {
    val info = state.layoutInfo
    if (info.totalItemsCount != list.size) return false
    val firstShown = info.visibleItemsInfo.firstOrNull() ?: return false
    return list.rowAt(firstShown.index)?.key == firstShown.key
}

/** 54 dp; selected fill `surfaceRaised`, else `surfaceSubtle`; focus draws a 3 dp ring, no scale (§5). */
@Composable
private fun ChannelListRow(model: ChannelsModel, row: ChannelRow, selected: State<ChannelRow?>, modifier: Modifier) {
    val isSelected by remember(row.key, selected) { derivedStateOf { selected.value?.key == row.key } }
    val style = SurfaceStyle(
        corner = Sohva.shapes.small,
        resting = if (isSelected) Sohva.palette.surfaceRaised else Sohva.palette.surfaceSubtle,
        restingContent = Sohva.palette.textPrimary,
        focusRing = true,
        focusScale = 1f,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 5.dp),
    )
    TvSurface(
        { model.select(row) },
        modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .onFocusChanged { if (it.isFocused) model.select(row) }
            .testTag("channels-row-${row.key}"),
        SurfaceState(selected = isSelected),
        style,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            row.channel.number?.let {
                Text(
                    it.toString(),
                    Modifier.widthIn(min = 22.dp),
                    style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = Sohva.palette.textMuted,
                )
                Spacer(Modifier.size(6.dp))
            }
            LogoTile(row.channel.name, row.channel.logoUrl, 38.dp, padding = 4.dp, fontSize = 14.sp)
            Spacer(Modifier.size(8.dp))
            Column {
                Text(
                    row.channel.name,
                    style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (row.hidden) Sohva.palette.textMuted else Sohva.palette.textPrimary,
                    maxLines = 1,
                )
                val line = if (row.hidden) row.line + stringResource(R.string.channels_hidden_suffix) else row.line
                Text(
                    line,
                    style = Sohva.typography.caption.copy(fontWeight = FontWeight.Normal),
                    color = if (row.hidden) Sohva.palette.accent else Sohva.palette.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

private val ROW_HEIGHT = 54.dp
private const val FRAMES = 30
private const val PREFETCH = 50
