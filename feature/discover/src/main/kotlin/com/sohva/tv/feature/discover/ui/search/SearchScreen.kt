package com.sohva.tv.feature.discover.ui.search

import com.sohva.tv.feature.discover.ui.components.marked
import com.sohva.tv.feature.discover.ui.components.movieMarkKey
import com.sohva.tv.feature.discover.ui.components.rememberMarks
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.ui.components.PosterCard
import com.sohva.tv.feature.discover.ui.components.PosterContent
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Discover Search (spec 50 §4.16, §5.9): the input row (Search, Clear), a status line, then Movies
 * and Series rows of 112 dp posters. Down from the input goes to the first non-empty row, rows
 * move to each other's first card, Up from the top row returns to the input (FR-116).
 */
@Composable
fun SearchScreen(model: SearchModel, back: () -> Unit, open: (owner: String, item: MetaPreview) -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    val input = remember { FocusRequester() }
    val firstMovie = remember { FocusRequester() }
    val firstSeries = remember { FocusRequester() }
    BackHandler { back() }
    val disabled = s.eligible == 0
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp).testTag("discover-search"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_search), Modifier.weight(1f), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
            TvActionButton(stringResource(R.string.addon_ui_back_to_catalogs), back, Modifier.testTag("discover-search-back"), TvIcons.Back, compact = true)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionDown) return@onPreviewKeyEvent false
                when {
                    s.movies.isNotEmpty() -> firstMovie.requestFocus()
                    s.series.isNotEmpty() -> firstSeries.requestFocus()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        ) {
            TvUrlField(
                s.query, model::type, stringResource(R.string.addon_ui_search_movies_and_series), Modifier.width(420.dp).focusRequester(input).testTag("discover-search-field"),
                TvIcons.Search, FieldInput(keyboard = KeyboardType.Text), SurfaceState(enabled = !disabled),
            )
            TvActionButton(stringResource(R.string.home_search), model::search, Modifier.testTag("discover-search-go"), compact = true, state = SurfaceState(enabled = !disabled && s.query.isNotBlank()))
            TvActionButton(stringResource(R.string.manager_clear), model::clear, Modifier.testTag("discover-search-clear"), compact = true)
        }
        Text(status(s), Modifier.testTag("discover-search-status"), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
        if (s.limited) Text(stringResource(R.string.addon_ui_search_limit, MAX_SEARCHED), style = Sohva.typography.caption, color = Sohva.palette.textDim)
        ResultRow(stringResource(R.string.home_movies), s.movies, empty(s, s.movies, "movie"), model.movieRow, firstMovie, "movies", model,
            up = { input.requestFocus() }, down = { if (s.series.isNotEmpty()) firstSeries.requestFocus() }, open = open)
        ResultRow(stringResource(R.string.home_series), s.series, empty(s, s.series, "series"), model.seriesRow, firstSeries, "series", model,
            up = { if (s.movies.isNotEmpty()) firstMovie.requestFocus() else input.requestFocus() }, down = {}, open = open)
    }
    LaunchedEffect(Unit) {
        // Returning from a title lands on the card that opened it, without searching again (FR-116).
        val restored = when (model.lastFocus) {
            "movies" -> firstMovie.requestFocusWhenAttached()
            "series" -> firstSeries.requestFocusWhenAttached()
            else -> false
        }
        if (!restored) input.requestFocusWhenAttached()
    }
}

@Composable
private fun status(s: SearchState): String = when {
    s.eligible == 0 -> stringResource(R.string.addon_ui_no_enabled_visible_catalogs_support_plain_text_search_catalogs_req)
    s.phase == SearchPhase.IDLE -> stringResource(R.string.addon_ui_enter_a_title_then_choose_search)
    s.phase == SearchPhase.SEARCHING -> stringResource(R.string.addon_ui_search_progress, s.answered, s.total)
    s.partial -> stringResource(R.string.addon_ui_some_catalogs_could_not_be_searched_choose_search_to_retry)
    s.stale -> stringResource(R.string.addon_ui_showing_saved_search_results_an_addon_is_unavailable)
    s.moviesCapped || s.seriesCapped -> stringResource(R.string.addon_ui_showing_100_results_refine_your_search_for_more_precise_matches)
    else -> stringResource(R.string.addon_ui_search_for, s.searched)
}

@Composable
private fun empty(s: SearchState, hits: List<SearchHit>, type: String): String? {
    if (hits.isNotEmpty()) return null
    return when (s.phase) {
        SearchPhase.IDLE -> stringResource(R.string.addon_ui_search_results_will_appear_here)
        SearchPhase.SEARCHING -> stringResource(R.string.search_loading)
        SearchPhase.DONE -> if (s.movies.isEmpty() && s.series.isEmpty()) {
            stringResource(R.string.addon_ui_no_results_from_available_catalogs)
        } else if (type == "movie") {
            stringResource(R.string.addon_ui_no_matching_type)
        } else {
            stringResource(R.string.addon_ui_no_matching_series)
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    hits: List<SearchHit>,
    emptyText: String?,
    state: LazyListState,
    first: FocusRequester,
    row: String,
    model: SearchModel,
    up: () -> Unit,
    down: () -> Unit,
    open: (String, MetaPreview) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionUp -> up()
                Key.DirectionDown -> down()
                else -> return@onPreviewKeyEvent false
            }
            true
        },
    ) {
        Text(title, style = Sohva.typography.body, color = Sohva.palette.textPrimary)
        if (emptyText != null) {
            Text(emptyText, Modifier.padding(top = 6.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted)
            return@Column
        }
        val marks = rememberMarks(remember(hits) { hits.mapNotNull { movieMarkKey(it.item.type, it.item.id) } })
        LazyRow(state = state, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("discover-search-$row")) {
            itemsIndexed(hits, key = { _, h -> h.key }) { i, h ->
                PosterCard(
                    PosterContent(h.item.name, h.item.poster).marked(movieMarkKey(h.item.type, h.item.id)?.let(marks::get)), 112.dp, { open(h.owner, h.item) },
                    (if (i == 0) Modifier.focusRequester(first) else Modifier).onFocusChanged { if (it.isFocused) model.lastFocus = row }
                        .testTag("discover-search-card-${h.item.id}"),
                )
            }
        }
    }
}

private const val MAX_SEARCHED = 32
