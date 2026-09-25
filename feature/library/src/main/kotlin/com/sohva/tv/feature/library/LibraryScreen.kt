package com.sohva.tv.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallRoom
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.OptionsSheet
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.components.errorMessage
import com.sohva.tv.ui.design.focus.KeepVisibleBringIntoViewSpec
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.launch

/**
 * The Movies or Series wall (spec 40 §5 "Wall", layout §1): header, then the 216 dp rail and the
 * poster grid, and the Options sheet above both. Back closes the sheet, else the app pops.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(model: LibraryModel) = trace("Library:Screen") {
    val sheetOpen by model.sheetOpen.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val railList = rememberLazyListState()
    val destination = remember { FocusRequester() }
    val options = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val rail = remember(model) {
        RailFocus {
            scope.launch {
                // The destination row, scrolled into view first (VOD-FR-50); Options with an empty rail.
                val index = railTarget(model)
                if (index == null) {
                    options.requestFocusWhenAttached()
                    return@launch
                }
                if (railList.layoutInfo.visibleItemsInfo.none { it.index == index }) railList.scrollToItem(index)
                destination.requestFocusWhenAttached()
            }
        }
    }
    val refocus by model.railRefocus.collectAsStateWithLifecycle()
    LaunchedEffect(refocus) { if (refocus > 0) rail.focusRail() }
    LaunchedEffect(model) {
        if (model.firstEntry) {
            rail.focusRail()
            model.entryPlaced()
        }
    }
    // Focus goes to Options before the sheet hides, so Compose never hands it elsewhere.
    val closeSheet = {
        runCatching { options.requestFocus() }
        model.closeSheet()
    }
    BackHandler(enabled = sheetOpen, onBack = closeSheet)
    ScreenBackground(Modifier.fillMaxSize().testTag(if (model.room == WallRoom.MOVIES) "screen-movies" else "screen-series")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
            LibraryHeader(model)
            // Scroll only as far as the focused card or row needs; the TV default pivots it to 30 %,
            // which cut the wall's top row off on entry (design/02 §20).
            CompositionLocalProvider(LocalBringIntoViewSpec provides KeepVisibleBringIntoViewSpec) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    LibraryRail(model, railList, destination, options, Modifier.width(216.dp).fillMaxHeight())
                    LibraryWall(model, grid, rail, back, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        if (sheetOpen) {
            Box(Modifier.fillMaxSize().zIndex(1f)) { LibrarySheet(model, closeSheet) }
        }
    }
}

/** The rail index of the destination row: the selection, else History, else the first row. */
private fun railTarget(model: LibraryModel): Int? = railTarget(model.rail.value, model.selected.value)

private fun railTarget(rows: List<RailRow>, selectedKey: String?): Int? {
    if (rows.isEmpty()) return null
    val selected = rows.indexOfFirst { it.key == selectedKey }
    if (selected >= 0) return selected
    val history = rows.indexOfFirst { it.key == LibraryModel.HISTORY_KEY }
    return if (history >= 0) history else 0
}

/** Title, "label  ·  count", the loading or failure note and the refresh result (layout §1 "Header"). */
@Composable
private fun LibraryHeader(model: LibraryModel) {
    val wall by model.wall.collectAsStateWithLifecycle()
    val rail by model.rail.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val search by model.search.collectAsStateWithLifecycle()
    val note by model.note.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            stringResource(if (model.room == WallRoom.MOVIES) R.string.catalogue_movies else R.string.catalogue_series),
            style = Sohva.typography.display.copy(fontWeight = FontWeight.Black),
            color = Sohva.palette.textPrimary,
        )
        val label = destinationLabel(wall.destination)
        if (label != null) {
            val count = headerCount(wall, rail.firstOrNull { it.key == selected }, search)
            Text(
                if (count != null) "$label  ·  $count" else label,
                Modifier.padding(start = 16.dp, bottom = 5.dp).testTag("library-label"),
                style = Sohva.typography.bodyLarge,
                color = Sohva.palette.textDim,
            )
        }
        Column(Modifier.padding(start = 16.dp, bottom = 5.dp)) {
            val stale = when {
                wall.failed -> stringResource(R.string.catalogue_v2_failed)
                !wall.current && wall.window.items.isNotEmpty() -> stringResource(R.string.catalogue_v2_loading)
                else -> null
            }
            stale?.let { Text(it, style = Sohva.typography.caption, color = Sohva.palette.textMuted) }
            note?.let { Text(noteText(it), Modifier.testTag("library-note"), style = Sohva.typography.caption, color = Sohva.palette.textMuted) }
            val credit by model.tvmazeCredit.collectAsStateWithLifecycle()
            if (credit) {
                Text(stringResource(R.string.metadata_tvmaze_credit), Modifier.testTag("library-tvmaze-credit"), style = Sohva.typography.caption, color = Sohva.palette.textDim)
            }
        }
    }
}

/**
 * The header count (VOD-FR-43): while searching, the wall's size once its end is known; else the
 * destination's count, and for History the wall's size.
 */
private fun headerCount(wall: WallState, row: RailRow?, search: String): Int? {
    val whole = wall.current && wall.window.atEnd && wall.window.first == 0
    return when {
        search.isNotBlank() || wall.destination == WallDestination.History -> if (whole) wall.window.items.size else null
        else -> row?.count ?: if (whole) wall.window.items.size else null
    }
}

@Composable
private fun noteText(note: RefreshNote): String = when (note) {
    is RefreshNote.Imported -> stringResource(
        R.string.catalogue_imported,
        pluralStringResource(R.plurals.catalogue_imported_movies, note.movies, note.movies),
        pluralStringResource(R.plurals.catalogue_imported_series, note.series, note.series),
    )
    RefreshNote.NoSource -> stringResource(R.string.catalogue_add_xtream_first)
    is RefreshNote.Failed -> errorMessage(note.error)
}

/** Search, the Groups | Genres toggle, Options and the destinations (layout §1 "Rail"). */
@Composable
private fun LibraryRail(model: LibraryModel, list: LazyListState, destination: FocusRequester, options: FocusRequester, modifier: Modifier) {
    val search by model.search.collectAsStateWithLifecycle()
    val view by model.view.collectAsStateWithLifecycle()
    val rows by model.rail.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val onRail = Modifier.onFocusChanged { if (it.hasFocus) model.railFocused() }
    Column(modifier.then(onRail), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvUrlField(
            search,
            model::setSearch,
            stringResource(if (model.room == WallRoom.MOVIES) R.string.catalogue_search_movie else R.string.catalogue_search_series),
            Modifier.fillMaxWidth().testTag("library-search"),
            icon = TvIcons.Search,
            input = FieldInput(keyboard = KeyboardType.Text, compact = true),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TvActionButton(
                stringResource(R.string.catalogue_grouping_groups),
                { model.showView(RailView.GROUPS) },
                Modifier.width(105.dp).testTag("library-view-groups"),
                state = SurfaceState(selected = view == RailView.GROUPS),
                compact = true,
            )
            TvActionButton(
                stringResource(R.string.catalogue_grouping_genres),
                { model.showView(RailView.GENRES) },
                Modifier.width(105.dp).testTag("library-view-genres"),
                state = SurfaceState(selected = view == RailView.GENRES),
                compact = true,
            )
        }
        TvActionButton(
            stringResource(R.string.catalogue_options),
            model::openSheet,
            Modifier.fillMaxWidth().focusRequester(options).testTag("library-options"),
            icon = TvIcons.Info,
            compact = true,
        )
        // Read from the collected states, so a new selection moves the requester to its row.
        val target = remember(rows, selected) { railTarget(rows, selected) }
        LazyColumn(Modifier.weight(1f).testTag("library-rail"), state = list) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                TvListRow(
                    label = row.label ?: destinationLabel(row.destination).orEmpty(),
                    onClick = { model.select(row.key) },
                    modifier = (if (index == target) Modifier.focusRequester(destination) else Modifier).testTag("library-row-${row.key}"),
                    icon = if (row.destination == WallDestination.History) TvIcons.Replay else null,
                    trailing = row.count?.toString(),
                    state = SurfaceState(selected = row.key == selected),
                    layout = ListRowLayout(dense = true, divider = true, labelLines = 2),
                )
            }
        }
    }
}

/**
 * "Library options" (VOD-FR-45…48): Refresh (first focus; "Refreshing…" and disabled while it
 * runs), Edit, Back, Close. The sheet keeps focus inside it until it closes.
 */
@Composable
private fun LibrarySheet(model: LibraryModel, close: () -> Unit) {
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    OptionsSheet(
        stringResource(R.string.catalogue_options_title),
        Modifier.testTag("library-sheet").focusProperties { onExit = { if (requestedFocusDirection in KEY_MOVES) cancelFocusChange() } }.focusGroup(),
        subtitle = stringResource(R.string.catalogue_subtitle),
    ) {
        val full = Modifier.fillMaxWidth()
        TvActionButton(
            stringResource(if (refreshing) R.string.action_refreshing else R.string.action_refresh),
            model::refresh,
            full.focusRequester(first).testTag("library-refresh"),
            icon = TvIcons.Refresh,
            state = SurfaceState(enabled = !refreshing, keepsFocus = true),
        )
        TvActionButton(stringResource(R.string.category_edit), model::openManager, full.testTag("library-edit"), icon = TvIcons.Check)
        TvActionButton(stringResource(R.string.action_back), {
            close()
            model.leave()
        }, full, icon = TvIcons.Back)
        TvActionButton(stringResource(R.string.catalogue_close_options), close, full.testTag("library-close"), icon = TvIcons.Close)
    }
    LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
}

/** D-pad moves stay inside the sheet; a placement asked for in code (on close) may leave it. */
private val KEY_MOVES = setOf(FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down, FocusDirection.Next, FocusDirection.Previous)
