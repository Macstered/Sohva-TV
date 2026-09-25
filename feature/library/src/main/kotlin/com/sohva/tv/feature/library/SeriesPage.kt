package com.sohva.tv.feature.library

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.errorMessage
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The series page (spec 40 §4.11, layout §4): as the film page, then the seasons and the
 * selected season's episodes, and the "Loading episodes…" pill pinned top-right while loading.
 */
@Composable
fun SeriesPage(model: SeriesModel) = trace("Library:Series") {
    val page by model.page.collectAsStateWithLifecycle()
    val gone by model.gone.collectAsStateWithLifecycle()
    val episodes by model.episodesState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().testTag("screen-series-page")) {
        DetailsBackdrop(page?.record?.backdropUrl)
        val state = page
        when {
            gone -> Text(
                stringResource(R.string.catalogue_source_disabled),
                Modifier.align(Alignment.Center),
                style = Sohva.typography.bodyLarge,
                color = Sohva.palette.textMuted,
            )
            state != null -> SeriesColumn(model, state)
        }
        if (episodes.loading) LoadingPill(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 32.dp))
    }
}

@Composable
private fun SeriesColumn(model: SeriesModel, page: SeriesPageState) {
    val scroll = rememberScrollState()
    val focus = remember { SeriesFocus() }
    val record = page.record
    Column(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                // Any key the viewer presses means focus is theirs from now on (decision on spec 40 Q8).
                if (it.type == KeyEventType.KeyDown) focus.touched = true
                false
            }
            .verticalScroll(scroll)
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SohvaTvBrand(fontSize = 22.sp)
            Spacer(Modifier.width(24.dp))
            Breadcrumb(listOfNotNull(stringResource(R.string.catalogue_series), page.breadcrumbGroup, record.name), Modifier.weight(1f))
        }
        Spacer(Modifier.height(30.dp))
        Text(
            record.name,
            Modifier.fillMaxWidth(0.62f).testTag("details-title"),
            style = Sohva.typography.display.copy(fontWeight = FontWeight.Black),
            color = Sohva.palette.textPrimary,
            maxLines = 2,
        )
        SeriesFacts(model, page)
        Text(
            record.plot?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_details_available),
            Modifier.fillMaxWidth(0.62f).padding(top = 16.dp),
            style = Sohva.typography.body,
            color = Sohva.palette.textMuted,
            maxLines = 3,
        )
        val selected by model.selected.collectAsStateWithLifecycle()
        val cards by model.cards.collectAsStateWithLifecycle()
        ProgressLine(cards.firstOrNull { it.record.key == selected }?.progress, Modifier.padding(top = 18.dp))
        SeriesActions(model, focus, scroll)
        Seasons(model, focus)
        Episodes(model, focus)
    }
}

/** Year, "N seasons", the selected episode's runtime, the score and the chips (VOD-FR-78). */
@Composable
private fun SeriesFacts(model: SeriesModel, page: SeriesPageState) {
    val seasons by model.seasons.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val cards by model.cards.collectAsStateWithLifecycle()
    val seconds = cards.firstOrNull { it.record.key == selected }?.record?.durationSeconds
    val facts = listOfNotNull(
        page.record.year?.toString(),
        seasons.size.takeIf { it > 0 }?.let { seasonCount(it) },
        seconds?.takeIf { it > 0 }?.let { runtime(it * 1_000L) },
    )
    FactsRow(page.record.rating, facts, page.quality, Modifier.padding(top = 12.dp))
}

/** Focus targets shared by the page's rows (VOD-FR-82, -88). */
internal class SeriesFocus {
    val watch = FocusRequester()
    val refresh = FocusRequester()
    val season = FocusRequester()
    val firstEpisode = FocusRequester()

    /** True once the viewer pressed a key: arriving episodes then leave focus alone. */
    var touched = false

    /** A season was chosen with OK: its first episode takes focus as soon as it exists. */
    var pendingEpisodes by mutableStateOf(false)
}

/**
 * Continue or Watch episode, Start from beginning, Mark as watched (the selected episode), Mark
 * season as watched, Refresh episodes, Wrong details? (VOD-FR-81). Before any episode exists focus
 * waits on Refresh episodes, then moves to Watch episode when they arrive, unless the viewer has
 * pressed a key or picked a season meanwhile.
 */
@Composable
private fun SeriesActions(model: SeriesModel, focus: SeriesFocus, scroll: ScrollState) {
    val cards by model.cards.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val season by model.season.collectAsStateWithLifecycle()
    val card = cards.firstOrNull { it.record.key == selected }
    val scope = rememberCoroutineScope()
    val toTop = Modifier.onFocusChanged { if (it.isFocused) scrollToTop(scope, scroll) }
    val resume = card?.progress?.resumeMs?.takeIf { it > 0 }
    ActionRow {
        if (card != null) {
            DetailsButton(
                stringResource(if (resume != null) R.string.series_continue_episode else R.string.series_watch_episode),
                { model.watch() },
                toTop.focusRequester(focus.watch).testTag("details-watch"),
                icon = TvIcons.Play,
                primary = true,
            )
            if (resume != null) {
                DetailsButton(stringResource(R.string.details_restart), model::restart, toTop.testTag("details-restart"), icon = TvIcons.Replay)
            }
            DetailsButton(
                stringResource(if (card.progress?.completed == true) R.string.details_mark_unwatched else R.string.details_mark_watched),
                model::toggleWatched,
                toTop.testTag("details-mark"),
                icon = TvIcons.Check,
            )
        }
        if (season != null && cards.any { it.record.season == season }) {
            DetailsButton(stringResource(R.string.series_mark_season_watched), model::markSeasonWatched, toTop.testTag("details-mark-season"), icon = TvIcons.Check)
        }
        DetailsButton(stringResource(R.string.series_refresh_episodes), model::refresh, toTop.focusRequester(focus.refresh).testTag("details-refresh"), icon = TvIcons.Refresh)
        DetailsButton(stringResource(R.string.match_picker_open), model::wrongDetails, toTop.testTag("details-wrong"), icon = TvIcons.Search)
    }
    val ready = card != null
    LaunchedEffect(ready) {
        when {
            !ready && !focus.touched -> focus.refresh.requestFocusWhenAttached()
            ready && !focus.touched && !model.seasonChosen -> focus.watch.requestFocusWhenAttached()
        }
    }
}

/** "Seasons": a compact button per season, a tick on seasons watched to the end (VOD-FR-82). */
@Composable
private fun Seasons(model: SeriesModel, focus: SeriesFocus) {
    val seasons by model.seasons.collectAsStateWithLifecycle()
    val current by model.season.collectAsStateWithLifecycle()
    val watched by model.watchedSeasons.collectAsStateWithLifecycle()
    if (seasons.isEmpty()) return
    SectionHeading(stringResource(R.string.series_seasons), top = 26, bottom = 16)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(seasons, key = { it }) { season ->
            val isCurrent = season == current
            TvActionButton(
                stringResource(R.string.series_season, season),
                {
                    model.chooseSeason(season)
                    focus.pendingEpisodes = true
                },
                Modifier
                    .then(if (isCurrent) Modifier.focusRequester(focus.season) else Modifier)
                    // Down from any season goes to the current season's first episode.
                    .focusProperties { down = focus.firstEpisode }
                    .testTag("series-season-$season"),
                icon = if (season in watched) TvIcons.Check else null,
                state = SurfaceState(selected = isCurrent),
                compact = true,
            )
        }
    }
}

/** "Episodes": the selected season's cards, or the loading, error or empty placeholder (VOD-FR-83). */
@Composable
private fun Episodes(model: SeriesModel, focus: SeriesFocus) {
    val cards by model.cards.collectAsStateWithLifecycle()
    val season by model.season.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val state by model.episodesState.collectAsStateWithLifecycle()
    val page by model.page.collectAsStateWithLifecycle()
    val shown = remember(cards, season) { cards.filter { it.record.season == season } }
    SectionHeading(stringResource(R.string.series_episodes), top = 20, bottom = 12)
    if (shown.isEmpty()) {
        val (text, color) = when {
            state.loading -> stringResource(R.string.series_loading_episodes) to Sohva.palette.textPrimary
            state.error != null -> errorMessage(state.error!!) to Sohva.palette.danger
            else -> stringResource(R.string.series_no_episodes) to Sohva.palette.textDim
        }
        Box(Modifier.fillMaxWidth().height(80.dp).testTag("series-episodes-message"), contentAlignment = Alignment.CenterStart) {
            Text(text, style = Sohva.typography.body, color = color)
        }
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("series-episodes")) {
        items(shown, key = { it.record.key }) { card ->
            EpisodeCardView(
                card = card,
                selected = card.record.key == selected,
                fallbackImage = page?.record?.backdropUrl ?: page?.record?.posterUrl,
                onFocus = { model.select(card.record.key) },
                onOpen = { model.watch(card) },
                modifier = (if (card === shown.first()) Modifier.focusRequester(focus.firstEpisode) else Modifier)
                    // Up from any episode returns to the current season's button.
                    .focusProperties { up = focus.season },
            )
        }
    }
    state.error?.let { Text(errorMessage(it), Modifier.padding(top = 8.dp), style = Sohva.typography.label, color = Sohva.palette.danger) }
    LaunchedEffect(focus.pendingEpisodes, shown.firstOrNull()?.record?.key) {
        if (focus.pendingEpisodes && shown.isNotEmpty()) {
            focus.pendingEpisodes = false
            focus.firstEpisode.requestFocusWhenAttached()
        }
    }
}

/** "Loading episodes…" pinned top-right, so no scroll can hide it (VOD-FR-86). */
@Composable
private fun LoadingPill(modifier: Modifier) {
    val shape = RoundedCornerShape(Sohva.shapes.medium)
    Box(
        modifier
            .roundFill(Sohva.palette.panel.copy(alpha = 0.94f), Sohva.shapes.medium)
            .border(1.dp, Sohva.palette.outline, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("series-loading-pill"),
    ) {
        Text(stringResource(R.string.series_loading_episodes), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
    }
}
