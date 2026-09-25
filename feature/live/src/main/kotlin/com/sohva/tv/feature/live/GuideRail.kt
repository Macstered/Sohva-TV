package com.sohva.tv.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

internal val RAIL_WIDTH = 200.dp

/**
 * The group rail (guide.md §4): composed only while open; opening narrows the timeline. Its scroll
 * state is kept by the screen so reopening shows the same viewport (GUIDE-FR-25). Right anywhere
 * in it returns to the selected row (GUIDE-FR-26).
 */
@Composable
internal fun GuideRail(model: GuideModel, listState: LazyListState, options: FocusRequester, modifier: Modifier = Modifier) {
    val items by model.rail.collectAsStateWithLifecycle()
    val entry by model.entry.collectAsStateWithLifecycle()
    val search by model.overlays.search.collectAsStateWithLifecycle()
    val selected = remember { FocusRequester() }
    val searchField = remember { FocusRequester() }
    Column(
        modifier.width(RAIL_WIDTH).fillMaxHeight().testTag("guide-rail").onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
                model.overlays.closeRail()
                model.selection.value?.row?.index?.let { index -> model.focusRow(index, GuideModel.KEEP) }
                true
            } else {
                false
            }
        },
    ) {
        Text(
            stringResource(R.string.guide_groups).uppercase(),
            Modifier.padding(start = 14.dp, bottom = 6.dp),
            style = Sohva.typography.overline.copy(fontWeight = FontWeight.Bold),
            color = Sohva.palette.textDim,
            maxLines = 1,
        )
        TvActionButton(
            stringResource(R.string.guide_options),
            { model.overlays.openOptions() },
            Modifier.fillMaxWidth().padding(bottom = 8.dp).focusRequester(options).testTag("guide-rail-options"),
            icon = TvIcons.Info,
            compact = true,
        )
        if (search.visible) {
            TvUrlField(
                search.query,
                model.overlays::setQuery,
                stringResource(R.string.guide_search_hint),
                Modifier.fillMaxWidth().padding(bottom = 8.dp).focusRequester(searchField).testTag("guide-search"),
                input = FieldInput(compact = true),
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.entry.id }) { _, item ->
                val isSelected = item.entry == entry || (item.entry.id == entry?.id)
                TvListRow(
                    label = railLabel(item.entry),
                    onClick = { model.chooseEntry(item.entry) },
                    modifier = Modifier.then(if (isSelected) Modifier.focusRequester(selected) else Modifier)
                        .testTag("guide-rail-" + item.entry.id),
                    icon = if (item.entry == RailEntry.Favourites) TvIcons.Star else null,
                    trailing = item.count?.toString(),
                    state = SurfaceState(selected = isSelected),
                    layout = ListRowLayout(dense = true),
                )
            }
        }
    }
    // Opening lands on the selected entry, else Options; with search on, in the field (GUIDE-FR-25, -90).
    LaunchedEffect(Unit) {
        if (search.visible) {
            searchField.requestFocusWhenAttached()
            return@LaunchedEffect
        }
        val index = items.indexOfFirst { it.entry.id == entry?.id }
        if (index >= 0) {
            listState.scrollToItem(index)
            if (selected.requestFocusWhenAttached()) return@LaunchedEffect
        }
        options.requestFocusWhenAttached()
    }
}

@Composable
private fun railLabel(entry: RailEntry): String = when (entry) {
    RailEntry.Favourites -> stringResource(R.string.guide_filter_favourites)
    RailEntry.All -> stringResource(R.string.guide_all_channels)
    RailEntry.Recent -> stringResource(R.string.guide_filter_recent)
    is RailEntry.Group -> entry.group.name
}

