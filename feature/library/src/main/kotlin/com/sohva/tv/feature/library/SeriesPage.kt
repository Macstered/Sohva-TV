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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
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
import com.sohva.tv.ui.design.components.NavIcons
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
    val metadata by model.metadata.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().testTag("screen-series-page")) {
        DetailsBackdrop(metadata?.backdropUrl ?: page?.record?.backdropUrl)
        val state = page
        when {
            gone -> Text(
                stringResource(R.string.catalogue_source_disabled),
                Modifier.align(Alignment.Center),
                style = Sohva.typography.bodyLarge,
                color = Sohva.palette.textMuted,
            )
            state != null -> SeriesColumn(model, state, metadata)
        }
        if (episodes.loading) LoadingPill(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 32.dp))
    }
}

@Composable
private fun SeriesColumn(model: SeriesModel, page: SeriesPageState, metadata: TitleMetadata?) {
    val scroll = rememberScrollState()
    val focus = remember { SeriesFocus() }
    val record = page.record
    // VOD-FR-78: the metadata title, else the background match's, else the provider name.
    val title = metadata?.title ?: record.replacementTitle ?: record.name
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
            Breadcrumb(listOfNotNull(stringResource(R.string.catalogue_series), page.breadcrumbGroup, title), Modifier.weight(1f))
        }
        Spacer(Modifier.height(30.dp))
        Text(
            title,
            Modifier.fillMaxWidth(0.62f).testTag("details-title"),
            style = Sohva.typography.display.copy(fontWeight = FontWeight.Black),
            color = Sohva.palette.textPrimary,
            maxLines = 2,
        )
        SeriesFacts(model, page, metadata)
        SeasonsHint(model, metadata)
        Text(
            metadata?.overview ?: record.plot?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_details_available),
            Modifier.fillMaxWidth(0.62f).padding(top = 16.dp),
            style = Sohva.typography.body,
            color = Sohva.palette.textMuted,
            maxLines = 3,
        )
        CastLine(metadata?.cast.orEmpty())
        val selected by model.selected.collectAsStateWithLifecycle()
        val cards by model.cards.collectAsStateWithLifecycle()
        ProgressLine(cards.firstOrNull { it.record.key == selected }?.progress, Modifier.padding(top = 18.dp))
        SeriesActions(model, focus, scroll, metadata?.sourceName)
        Seasons(model, focus)
        Episodes(model, focus)
    }
}

/**
 * Year, "N seasons", the runtime (the selected episode's metadata, else its duration, else the
 * series metadata), the score and the chips (VOD-FR-78).
 */
@Composable
private fun SeriesFacts(model: SeriesModel, page: SeriesPageState, metadata: TitleMetadata?) {
    val seasons by model.seasons.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val cards by model.cards.collectAsStateWithLifecycle()
    val episode by model.episodeMetadata.collectAsStateWithLifecycle()
    val seconds = cards.firstOrNull { it.record.key == selected }?.record?.durationSeconds?.takeIf { it > 0 }
    val minutes = episode?.takeIf { it.key == selected }?.metadata?.runtimeMinutes
    val runtimeMs = when {
        minutes != null -> minutes * 60_000L
        seconds != null -> seconds * 1_000L
        else -> metadata?.runtimeMinutes?.let { it * 60_000L }
    }
    val facts = listOfNotNull(
        (metadata?.year ?: page.record.year)?.toString(),
        seasons.size.takeIf { it > 0 }?.let { seasonCount(it) },
        runtimeMs?.let { runtime(it) },
    )
    FactsRow(metadata?.rating ?: page.record.rating, facts, page.quality, Modifier.padding(top = 12.dp))
}

/**
 * "Your sources have 3 of 5 seasons." when the library has fewer seasons than TMDB says have aired,
 * pointing at "Find in Discover" when it is offered (VOD-FR-113). Specials do not count.
 */
@Composable
private fun SeasonsHint(model: SeriesModel, metadata: TitleMetadata?) {
    val seasons by model.seasons.collectAsStateWithLifecycle()
    val discover by model.discover.collectAsStateWithLifecycle()
    val aired = metadata?.airedSeasons ?: return
    val have = seasons.count { it > 0 }
    if (have == 0 || have >= aired) return
    Text(
        stringResource(if (discover) R.string.series_seasons_missing_discover else R.string.series_seasons_missing, have, aired),
        Modifier.fillMaxWidth(0.62f).padding(top = 10.dp).testTag("details-seasons-missing"),
        style = Sohva.typography.label,
        color = Sohva.palette.textMuted,
        maxLines = 2,
    )
}

/** "Cast: a, b, c" on at most two lines, 0.62 of the width: tiles pushed the episodes off screen (VOD-FR-79). */
@Composable
private fun CastLine(cast: List<CastCard>) {
    if (cast.isEmpty()) return
    val names = remember(cast) { cast.joinToString(", ") { it.name } }
    Text(
        stringResource(R.string.series_cast_line, names),
        Modifier.fillMaxWidth(0.62f).padding(top = 10.dp).testTag("details-cast-line"),
        style = Sohva.typography.label,
        color = Sohva.palette.textMuted,
        maxLines = 2,
    )
}

/** Focus targets shared by the page's rows (VOD-FR-82, -88). */
internal class SeriesFocus {
    val watch = FocusRequester()
    val refresh = FocusRequester()
    val season = FocusRequester()
    val firstEpisode = FocusRequester()
    val discover = FocusRequester()
    val wrong = FocusRequester()

    /** True once the viewer pressed a key: arriving episodes then leave focus alone. */
    var touched = false

    /** OK or Down on a season: scroll its first episode into view, then focus it. */
    var pendingEpisodes by mutableStateOf(false)
}

/**
 * Continue or Watch episode, Start from beginning, Mark as watched (the selected episode), Mark
 * season as watched, Refresh episodes, Wrong details?, and "Source: …" with metadata (VOD-FR-81). Before any episode exists focus
 * waits on Refresh episodes, then moves to Watch episode when they arrive, unless the viewer has
 * pressed a key or picked a season meanwhile.
 */
@Composable
private fun SeriesActions(model: SeriesModel, focus: SeriesFocus, scroll: ScrollState, source: String?) {
    val cards by model.cards.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val season by model.season.collectAsStateWithLifecycle()
    val card = cards.firstOrNull { it.record.key == selected }
    val scope = rememberCoroutineScope()
    val toTop = Modifier.onFocusChanged { if (it.isFocused) scrollToTop(scope, scroll) }
    val resume = card?.progress?.resumeMs?.takeIf { it > 0 }
    val discover by model.discover.collectAsStateWithLifecycle()
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
        if (discover) {
            DetailsButton(stringResource(R.string.details_find_in_discover), model::findInDiscover, toTop.focusRequester(focus.discover).testTag("details-discover"), icon = NavIcons.Discover)
        }
        DetailsButton(stringResource(R.string.match_picker_open), model::wrongDetails, toTop.focusRequester(focus.wrong).testTag("details-wrong"), icon = TvIcons.Search)
        if (source != null) {
            DetailsButton(stringResource(R.string.metadata_source, source), model::openSource, toTop.testTag("details-source"), icon = TvIcons.Info)
        }
    }
    val picker by model.picker.collectAsStateWithLifecycle()
    picker?.let { open ->
        // Focus goes back to "Wrong details?" before the dialog hides (lessons 4.1).
        MatchPickerDialog(open) {
            focus.wrong.requestFocus()
            model.closePicker()
        }
    }
    val ready = card != null
    LaunchedEffect(ready) {
        when {
            model.backFromDiscover -> Unit
            !ready && !focus.touched -> focus.refresh.requestFocusWhenAttached()
            ready && !focus.touched && !model.seasonChosen -> focus.watch.requestFocusWhenAttached()
        }
    }
    // After the effect above, which leaves focus alone while this is pending.
    LaunchedEffect(discover) {
        if (discover && model.backFromDiscover) {
            model.backFromDiscover = false
            focus.discover.requestFocusWhenAttached()
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
                    // A later episode may have scrolled the first card out of the lazy row.
                    .onPreviewKeyEvent {
                        if (it.key != Key.DirectionDown) return@onPreviewKeyEvent false
                        if (it.type == KeyEventType.KeyDown) focus.pendingEpisodes = true
                        true
                    }
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
    val metadata by model.metadata.collectAsStateWithLifecycle()
    val episode by model.episodeMetadata.collectAsStateWithLifecycle()
    val shown = remember(cards, season) { cards.filter { it.record.season == season } }
    val row = rememberLazyListState()
    // Without a thumbnail: the selected card takes its episode's still, the rest the series backdrop, else the poster (VOD-FR-84).
    val seriesImage = metadata?.backdropUrl ?: page?.record?.backdropUrl ?: metadata?.posterUrl ?: page?.record?.posterUrl
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
    LazyRow(state = row, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("series-episodes")) {
        items(shown, key = { it.record.key }) { card ->
            EpisodeCardView(
                card = card,
                selected = card.record.key == selected,
                fallbackImage = episode?.takeIf { card.record.key == selected && it.key == selected }?.metadata?.backdropUrl ?: seriesImage,
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
            row.scrollToItem(0)
            // Clearing the effect's key before this suspending request cancels the request itself.
            if (focus.firstEpisode.requestFocusWhenAttached()) focus.pendingEpisodes = false
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
