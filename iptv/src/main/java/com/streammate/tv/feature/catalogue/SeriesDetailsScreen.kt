package com.streammate.tv.feature.catalogue

import androidx.compose.ui.platform.LocalContext
import com.streammate.tv.core.error.userMessage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.KeepFocusedChildVisibleColumn
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.common.scrollsToTopWhenFocused
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.metadata.EnrichedMetadata
import com.streammate.tv.iptv.metadata.MetadataCastMember
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataMatcher
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.VodEpisode
import com.streammate.tv.iptv.repository.VodSeries
import com.streammate.tv.iptv.repository.seriesContentKey
import com.streammate.tv.iptv.xtream.xtreamEpisodeDisplayTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailsScreen(
    series: VodSeries,
    repository: CatalogueRepository,
    metadataRepository: MetadataRepository,
    onRefreshEpisodes: suspend () -> Result<Int>,
    onPlay: (String, Long) -> Unit,
    onBack: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val episodesState: List<VodEpisode>? by remember(series.sourceId, series.seriesId, repository) {
        repository.observeEpisodes(series.sourceId, series.seriesId)
            .map<List<VodEpisode>, List<VodEpisode>?> { it }
    }.collectAsStateWithLifecycle(initialValue = null)
    val episodes = episodesState.orEmpty()
    val progress by repository.observeProgress().collectAsStateWithLifecycle(initialValue = emptyMap())
    var loading by remember(series.sourceId, series.seriesId) { mutableStateOf(false) }
    var refreshAttempted by remember(series.sourceId, series.seriesId) { mutableStateOf(false) }
    var loadError by remember(series.sourceId, series.seriesId) { mutableStateOf<String?>(null) }
    var season by remember(series.sourceId, series.seriesId) { mutableIntStateOf(1) }
    var selected by remember(series.sourceId, series.seriesId) { mutableStateOf<VodEpisode?>(null) }
    val scope = rememberCoroutineScope()
    val watchFocus = remember(series.sourceId, series.seriesId) { FocusRequester() }
    var pickingMatch by remember(series.seriesId) { mutableStateOf(false) }
    var matchPinned by remember(series.seriesId) { mutableStateOf(false) }
    var initialWatchFocusPending by remember(series.sourceId, series.seriesId) { mutableStateOf(true) }
    var pendingEpisodeFocusSeason by remember(series.sourceId, series.seriesId) {
        mutableStateOf<Int?>(null)
    }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val seasons = remember(episodes) { episodes.map(VodEpisode::seasonNumber).distinct().sorted() }
    val visibleEpisodes = remember(episodes, season) { episodes.filter { it.seasonNumber == season } }
    val seasonFocusRequesters = remember(seasons) { seasons.associateWith { FocusRequester() } }
    val firstEpisodeFocusRequesters = remember(seasons) { seasons.associateWith { FocusRequester() } }
    val activeSeasonFocus = seasonFocusRequesters[season]
    val activeFirstEpisodeFocus = firstEpisodeFocusRequesters[season]

    val seriesMetadataLookup = remember(series.sourceId, series.seriesId, series.name, series.year) {
        MetadataLookup(
            mediaType = MetadataMediaType.SERIES,
            title = series.name,
            year = series.year,
        )
    }
    val catalogueContentKey = remember(series.sourceId, series.seriesId) {
        seriesContentKey(series.sourceId, series.seriesId)
    }
    LaunchedEffect(seriesMetadataLookup, metadataRepository, pickingMatch) {
        matchPinned = metadataRepository.isMatchPinned(seriesMetadataLookup)
    }
    var seriesMetadata by remember(series.sourceId, series.seriesId, metadataRepository) {
        mutableStateOf(metadataRepository.cached(seriesMetadataLookup))
    }
    var selectedMetadata by remember(series.sourceId, series.seriesId) {
        mutableStateOf<EnrichedMetadata?>(null)
    }

    suspend fun refreshEpisodes() {
        loading = episodes.isEmpty()
        loadError = null
        onRefreshEpisodes().fold(
            onSuccess = { loading = false },
            onFailure = {
                loadError = it.userMessage(context)
                loading = false
            },
        )
    }

    LaunchedEffect(series.sourceId, series.seriesId, episodesState) {
        if (episodesState == null || refreshAttempted) return@LaunchedEffect
        refreshAttempted = true
        if (episodes.isEmpty()) refreshEpisodes()
    }
    LaunchedEffect(seriesMetadataLookup, metadataRepository) {
        if (!metadataRepository.isEnabled()) return@LaunchedEffect
        metadataRepository.enrich(seriesMetadataLookup)?.let { resolved ->
            seriesMetadata = resolved
            metadataRepository.backfillMissingCataloguePoster(
                contentKey = catalogueContentKey,
                providerPosterUrl = series.posterUrl,
                metadata = resolved,
            )
        }
    }
    LaunchedEffect(seasons) {
        if (season !in seasons) season = seasons.firstOrNull() ?: 1
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(season, visibleEpisodes.firstOrNull()?.contentKey) {
        val firstEpisode = visibleEpisodes.firstOrNull()
        if (selected?.contentKey !in visibleEpisodes.map(VodEpisode::contentKey)) {
            selected = firstEpisode
        }
        // Arriving: the page's own Watch button, not the strip at the bottom
        // of it. Read the episode list directly because `selected` is not
        // visible until the next composition.
        if (
            initialWatchFocusPending &&
            firstEpisode != null &&
            watchFocus.requestFocusWhenAttached()
        ) {
            initialWatchFocusPending = false
        }
    }
    LaunchedEffect(
        pendingEpisodeFocusSeason,
        season,
        visibleEpisodes.firstOrNull()?.contentKey,
    ) {
        val pendingSeason = pendingEpisodeFocusSeason ?: return@LaunchedEffect
        if (pendingSeason != season || visibleEpisodes.isEmpty()) return@LaunchedEffect
        val requester = firstEpisodeFocusRequesters[pendingSeason] ?: return@LaunchedEffect
        // Keep the hand-off pending unless the new lazy-row item really took
        // focus. A failed request must not strand focus after a later season.
        if (requester.requestFocusWhenAttached()) {
            pendingEpisodeFocusSeason = null
        }
    }
    LaunchedEffect(selected?.contentKey) {
        val episode = selected ?: return@LaunchedEffect
        if (!metadataRepository.isEnabled()) return@LaunchedEffect
        val lookup = MetadataLookup(
            mediaType = MetadataMediaType.EPISODE,
            title = series.name,
            year = series.year,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
        )
        selectedMetadata = metadataRepository.cached(lookup)
        if (selectedMetadata != null) return@LaunchedEffect
        delay(METADATA_SELECTION_DELAY_MILLIS)
        selectedMetadata = metadataRepository.enrich(lookup)
    }

    val title = seriesMetadata?.title?.takeIf(String::isNotBlank) ?: series.name
    val posterUrl = seriesMetadata?.posterUrl ?: series.posterUrl
    val backdropUrl = seriesMetadata?.backdropUrl ?: series.backdropUrl
    val overview = seriesMetadata?.overview?.takeIf(String::isNotBlank)
        ?: series.plot?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.no_details_available)
    // Only what the record holds. As on the film page, the reference's
    // director, age rating, audio format and subtitle language have nothing
    // behind them here and are left out rather than invented.
    val facts = buildList {
        (seriesMetadata?.year ?: series.year)?.let { add(it.toString()) }
        if (seasons.isNotEmpty()) {
            add(pluralStringResource(R.plurals.series_season_count, seasons.size, seasons.size))
        }
        (
            selectedMetadata?.runtimeMinutes
                ?: selected?.durationSeconds?.takeIf { it > 0 }?.let { (it + 59) / 60 }
                ?: seriesMetadata?.runtimeMinutes
            )?.takeIf { it > 0 }?.let { add(formatRuntime(it)) }
    }
    val score = seriesMetadata?.rating?.takeIf(String::isNotBlank)
        ?: series.rating?.takeIf(String::isNotBlank)
    val qualityTags = remember(series.name) { catalogueQualityTags(series.name) }
    val selectedProgress = selected?.let { progress[it.contentKey] }
    val episodeResumePosition = catalogueResumePosition(selectedProgress)
    val typography = StreamMateThemeTokens.typography
    val scrollState = rememberScrollState()

    if (pickingMatch) {
        CatalogueMatchPicker(
            initialQuery = MetadataMatcher.searchTitle(series.name).ifBlank { series.name },
            lookup = seriesMetadataLookup,
            pinned = matchPinned,
            onSearch = { query ->
                metadataRepository.searchCandidates(seriesMetadataLookup.copy(title = query))
            },
            onChoose = { result ->
                metadataRepository.pinMatch(
                    contentKey = catalogueContentKey,
                    lookup = seriesMetadataLookup,
                    result = result,
                    providerPosterUrl = series.posterUrl,
                )?.let { seriesMetadata = it }
            },
            onClear = {
                metadataRepository.clearPinnedMatch(catalogueContentKey, seriesMetadataLookup)
                seriesMetadata = null
            },
            onDismiss = { pickingMatch = false },
        )
    }
    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
        CatalogueDetailBackdrop(imageUrl = backdropUrl, modifier = modifier) {
            KeepFocusedChildVisibleColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = StreamMateThemeTokens.spacing.xxl,
                        vertical = StreamMateThemeTokens.spacing.xl,
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SohvaTvBrand(fontSize = typography.headline.fontSize)
                    Spacer(Modifier.width(StreamMateThemeTokens.spacing.xl))
                    CatalogueDetailBreadcrumb(
                        library = stringResource(R.string.catalogue_series),
                        category = series.categoryName?.let(::catalogueDisplayCategory),
                        title = title,
                        modifier = Modifier.weight(1f).testTag("series-details-breadcrumb"),
                    )
                }
                Spacer(Modifier.height(SERIES_TITLE_GAP))
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(DETAILS_TEXT_FRACTION).testTag("series-details-title"),
                    color = palette.textPrimary,
                    fontSize = typography.display.fontSize,
                    lineHeight = typography.display.lineHeight,
                    fontWeight = FontWeight.Black,
                    letterSpacing = typography.display.letterSpacing,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CatalogueDetailFacts(
                    rating = score,
                    facts = facts,
                    qualityTags = qualityTags,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = overview,
                    modifier = Modifier.fillMaxWidth(DETAILS_TEXT_FRACTION).padding(top = 16.dp),
                    color = palette.textMuted,
                    fontSize = typography.body.fontSize,
                    lineHeight = typography.body.lineHeight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                selectedProgress?.takeIf { !it.completed }?.let { watched ->
                    CatalogueDetailProgress(
                        positionMillis = watched.positionMillis,
                        durationMillis = watched.durationMillis,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .scrollsToTopWhenFocused(
                            offset = { scrollState.value },
                            scrollToTop = { scrollState.scrollTo(0) },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    selected?.let { episode ->
                        CatalogueDetailAction(
                            label = if (episodeResumePosition != null) {
                                stringResource(R.string.series_continue_episode)
                            } else {
                                stringResource(R.string.series_watch_episode)
                            },
                            icon = TvIcons.Play,
                            primary = true,
                            onClick = { onPlay(episode.contentKey, episodeResumePosition ?: 0L) },
                            focusRequester = watchFocus,
                            testTag = "series-details-watch",
                        )
                        // Only where there is a position to go back from; with
                        // nothing watched it would be the button beside it
                        // wearing a different label.
                        if (episodeResumePosition != null) {
                            CatalogueDetailAction(
                                label = stringResource(R.string.details_restart),
                                icon = TvIcons.Replay,
                                onClick = { onPlay(episode.contentKey, 0L) },
                                testTag = "series-details-restart",
                            )
                        }
                    }
                    CatalogueDetailAction(
                        label = stringResource(R.string.series_refresh_episodes),
                        icon = TvIcons.Refresh,
                        onClick = { scope.launch { refreshEpisodes() } },
                        testTag = "series-refresh-episodes",
                    )
                    // The same question the film page asks, for the same
                    // reason: a series with a common name defeats the matcher
                    // exactly as often.
                    CatalogueDetailAction(
                        label = stringResource(R.string.match_picker_open),
                        icon = TvIcons.Search,
                        onClick = { pickingMatch = true },
                        testTag = "series-details-fix-match",
                    )
                    seriesMetadata?.let { metadata ->
                        CatalogueDetailAction(
                            label = stringResource(R.string.metadata_source, metadata.attributionName),
                            icon = TvIcons.Info,
                            onClick = { runCatching { uriHandler.openUri(metadata.attributionUrl) } },
                            testTag = "series-metadata-attribution",
                        )
                    }
                }
                seriesMetadata?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                    CatalogueDetailSectionHeading(
                        text = stringResource(R.string.series_cast),
                        modifier = Modifier.padding(top = 26.dp, bottom = 12.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(cast, key = MetadataCastMember::name) { member ->
                            CatalogueCastMember(member)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CatalogueDetailSectionHeading(text = stringResource(R.string.series_seasons))
                    Spacer(Modifier.width(StreamMateThemeTokens.spacing.lg))
                    LazyRow(
                        modifier = Modifier.weight(1f).testTag("series-season-selector"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 10.dp),
                    ) {
                        items(seasons, key = { it }) { option ->
                            TvActionButton(
                                label = stringResource(R.string.series_season, option),
                                onClick = {
                                    initialWatchFocusPending = false
                                    season = option
                                    pendingEpisodeFocusSeason = option
                                },
                                modifier = Modifier.focusProperties {
                                    activeFirstEpisodeFocus?.let { down = it }
                                },
                                focusRequester = seasonFocusRequesters[option],
                                compact = true,
                                selected = option == season,
                                testTag = "series-season-$option",
                            )
                        }
                    }
                }
                CatalogueDetailSectionHeading(
                    text = stringResource(R.string.series_episodes),
                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                )
                if (visibleEpisodes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(EPISODE_PLACEHOLDER_HEIGHT),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = when {
                                loading -> stringResource(R.string.series_loading_episodes)
                                loadError != null -> loadError.orEmpty()
                                else -> stringResource(R.string.series_no_episodes)
                            },
                            color = if (loadError != null) palette.danger else palette.textDim,
                            fontSize = typography.body.fontSize,
                            lineHeight = typography.body.lineHeight,
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 18.dp),
                    ) {
                        items(visibleEpisodes, key = VodEpisode::contentKey) { episode ->
                            val isSelected = selected?.contentKey == episode.contentKey
                            EpisodeCard(
                                episode = episode,
                                thumbnailUrl = episode.thumbnailUrl
                                    ?: (if (isSelected) selectedMetadata?.backdropUrl else null)
                                    ?: backdropUrl
                                    ?: posterUrl,
                                watchedFraction = progress[episode.contentKey]?.fraction,
                                selected = isSelected,
                                onFocus = { selected = episode },
                                onPlay = {
                                    onPlay(
                                        episode.contentKey,
                                        progress[episode.contentKey]?.resumePositionMillis ?: 0L,
                                    )
                                },
                                modifier = Modifier
                                    .then(
                                        if (episode == visibleEpisodes.first()) {
                                            if (activeFirstEpisodeFocus != null) {
                                                Modifier.focusRequester(activeFirstEpisodeFocus)
                                            } else {
                                                Modifier
                                            }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .focusProperties { activeSeasonFocus?.let { up = it } },
                            )
                        }
                    }
                }
                loadError?.takeIf { visibleEpisodes.isNotEmpty() }?.let { error ->
                    Text(
                        text = error,
                        modifier = Modifier.padding(top = 10.dp),
                        color = palette.danger,
                        fontSize = typography.label.fontSize,
                        lineHeight = typography.label.lineHeight,
                    )
                }
            }
        }
    }
}

/**
 * One episode.
 *
 * The still is the card, with the episode number and title over a scrim along
 * its foot; there is no panel behind it and no outline at rest. Focus is the
 * off-white fill the rest of the app uses, and the selected episode - the one
 * the buttons above act on - is marked by a cyan rule instead, so the two are
 * never the same signal.
 */
@Composable
private fun EpisodeCard(
    episode: VodEpisode,
    thumbnailUrl: String?,
    watchedFraction: Float?,
    selected: Boolean,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var focused by remember(episode.contentKey) { mutableStateOf(false) }
    val shape = StreamMateThemeTokens.shapes.medium
    val episodeLabel = stringResource(
        R.string.series_episode_label,
        episode.seasonNumber,
        episode.episodeNumber,
    )
    val displayTitle = xtreamEpisodeDisplayTitle(
        rawTitle = episode.name,
        seasonNumber = episode.seasonNumber,
        episodeNumber = episode.episodeNumber,
    )
    Column(
        modifier = modifier
            .width(EPISODE_CARD_WIDTH)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onPlay)
            .focusable()
            .testTag("series-episode-${episode.seasonNumber}-${episode.episodeNumber}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EPISODE_STILL_HEIGHT)
                .clip(shape)
                .background(palette.surfaceSubtle)
                .then(if (focused) Modifier.border(3.dp, palette.textPrimary, shape) else Modifier),
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.42f to palette.background.copy(alpha = 0f),
                        1f to palette.background.copy(alpha = 0.88f),
                    ),
                ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    text = episodeLabel,
                    color = palette.focus,
                    fontSize = typography.caption.fontSize,
                    lineHeight = typography.caption.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = displayTitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = palette.textPrimary,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            watchedFraction?.takeIf { it > 0f && it < 1f }?.let { fraction ->
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(palette.focus),
                )
            }
        }
        // The selected episode is the one the buttons at the top of the page
        // act on. A rule under the still says so without competing with focus.
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(2.dp)
                .clip(StreamMateThemeTokens.shapes.small)
                .background(if (selected) palette.focus else Color.Transparent),
        )
        episode.durationSeconds?.takeIf { it > 0 }?.let { durationSeconds ->
            Text(
                text = formatRuntime((durationSeconds + 59) / 60),
                modifier = Modifier.padding(top = 5.dp),
                color = palette.textDim,
                fontSize = typography.caption.fontSize,
                lineHeight = typography.caption.lineHeight,
                maxLines = 1,
            )
        }
    }
}

private val SERIES_TITLE_GAP = 30.dp
private val EPISODE_CARD_WIDTH = 208.dp
private val EPISODE_STILL_HEIGHT = 117.dp
private val EPISODE_PLACEHOLDER_HEIGHT = 80.dp

private const val METADATA_SELECTION_DELAY_MILLIS = 350L
