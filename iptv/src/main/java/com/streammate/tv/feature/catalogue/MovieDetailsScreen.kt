package com.streammate.tv.feature.catalogue

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.KeepFocusedChildVisibleColumn
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.common.scrollsToTopWhenFocused
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.metadata.MetadataCastMember
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataMatcher
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.repository.AvailableSimilarMovie
import com.streammate.tv.iptv.repository.CatalogueFilmCopy
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.WatchingProgress
import com.streammate.tv.iptv.repository.VodMovie
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch

@Composable
fun MovieDetailsScreen(
    movie: VodMovie,
    repository: CatalogueRepository,
    metadataRepository: MetadataRepository,
    onPlay: (String, Long) -> Unit,
    onOpenMovie: (VodMovie) -> Unit,
    onBack: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val progress by repository.observeProgress().collectAsStateWithLifecycle(initialValue = emptyMap())
    // Asked for by film rather than read out of the map by copy. The wall shows
    // one card for a film carried by two playlists, and this page is reached
    // through whichever copy stands for it - so the copy in hand may have no
    // position of its own while the film is forty minutes in. Re-asked whenever
    // anything is written, which is what keeps the bar right on the way back
    // from the player.
    val watchingProgress by produceState<WatchingProgress?>(null, movie.contentKey, progress) {
        value = repository.progress(movie.contentKey)
    }
    val metadataLookup = remember(movie.contentKey, movie.name, movie.year) {
        MetadataLookup(
            mediaType = MetadataMediaType.MOVIE,
            title = movie.name,
            year = movie.year,
        )
    }
    var metadata by remember(movie.contentKey, metadataRepository) {
        mutableStateOf(metadataRepository.cached(metadataLookup))
    }
    // Asked for once per film. The wall shows one card for a film carried by
    // two playlists, and this is where the other one is offered rather than
    // hidden - which copy is better varies by title, so the choice is left
    // where it can be made.
    val copies by produceState(initialValue = emptyList<CatalogueFilmCopy>(), movie.contentKey) {
        value = repository.catalogueCopies(movie)
    }
    var similarMovies by remember(movie.contentKey) {
        mutableStateOf<List<AvailableSimilarMovie>>(emptyList())
    }
    var loadedSimilarSignature by remember(movie.contentKey) {
        mutableStateOf<List<String>?>(null)
    }
    val similarSignature = metadata?.similarMovies.orEmpty().map { reference ->
        "${reference.externalId}:${reference.year ?: ""}"
    }
    val displayTitle = metadata?.title?.takeIf(String::isNotBlank)
        ?: MetadataMatcher.searchTitle(movie.name).ifBlank { movie.name }
    val overview = metadata?.overview?.takeIf(String::isNotBlank)
        ?: movie.plot?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.no_details_available)
    // Only what the record holds. The reference also carries a director, an age
    // rating, an audio format and a subtitle language; none of those exist in
    // this app's metadata, so the row closes up around them rather than
    // printing something that is not known.
    val facts = buildList {
        (metadata?.year ?: movie.year)?.let { add(it.toString()) }
        metadata?.runtimeMinutes?.takeIf { it > 0 }?.let { add(formatRuntime(it)) }
    }
    val score = metadata?.rating?.takeIf(String::isNotBlank)
        ?: movie.rating?.takeIf(String::isNotBlank)
    val qualityTags = remember(movie.name) { catalogueQualityTags(movie.name) }
    val resumePosition = catalogueResumePosition(watchingProgress)
    val watchFocus = remember(movie.contentKey) { FocusRequester() }
    val uriHandler = LocalUriHandler.current
    var pickingMatch by remember(movie.contentKey) { mutableStateOf(false) }
    var matchPinned by remember(movie.contentKey) { mutableStateOf(false) }
    LaunchedEffect(metadataLookup, metadataRepository, pickingMatch) {
        matchPinned = metadataRepository.isMatchPinned(metadataLookup)
    }

    LaunchedEffect(metadataLookup, metadataRepository) {
        if (metadataRepository.isEnabled()) {
            metadataRepository.enrichMovieDetails(metadataLookup)?.let { resolved ->
                metadata = resolved
                metadataRepository.backfillMissingCataloguePoster(
                    contentKey = movie.contentKey,
                    providerPosterUrl = movie.posterUrl,
                    metadata = resolved,
                )
            }
        }
    }
    LaunchedEffect(movie.contentKey, metadata?.similarMovies, repository) {
        similarMovies = repository.availableSimilarMovies(
            currentMovie = movie,
            references = metadata?.similarMovies.orEmpty(),
        )
        loadedSimilarSignature = similarSignature
    }
    LaunchedEffect(movie.contentKey) { watchFocus.requestFocusWhenAttached() }
    if (pickingMatch) {
        CatalogueMatchPicker(
            initialQuery = MetadataMatcher.searchTitle(movie.name).ifBlank { movie.name },
            lookup = metadataLookup,
            pinned = matchPinned,
            onSearch = { query -> metadataRepository.searchCandidates(metadataLookup.copy(title = query)) },
            onChoose = { result ->
                metadataRepository.pinMatch(
                    contentKey = movie.contentKey,
                    lookup = metadataLookup,
                    result = result,
                    providerPosterUrl = movie.posterUrl,
                )?.let { metadata = it }
            },
            onClear = {
                metadataRepository.clearPinnedMatch(movie.contentKey, metadataLookup)
                metadata = null
            },
            onDismiss = { pickingMatch = false },
        )
    }
    // The remote's own key leaves, so there is no Back button taking up a row
    // of artwork. Handled here rather than left to the app's back stack so the
    // contract is visible on the screen that owns it.
    BackHandler(onBack = onBack)

    val scrollState = rememberScrollState()

    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
        CatalogueDetailBackdrop(imageUrl = metadata?.backdropUrl, modifier = modifier) {
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
                        library = stringResource(R.string.catalogue_movies),
                        category = movie.categoryName?.let(::catalogueDisplayCategory),
                        title = displayTitle,
                        modifier = Modifier.testTag("movie-details-breadcrumb"),
                    )
                }
                Spacer(Modifier.height(DETAILS_TITLE_GAP))
                Text(
                    text = displayTitle,
                    modifier = Modifier.fillMaxWidth(DETAILS_TEXT_FRACTION).testTag("movie-details-title"),
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
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                watchingProgress?.takeIf { !it.completed }?.let { watched ->
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
                    CatalogueDetailAction(
                        label = if (resumePosition != null) {
                            stringResource(R.string.details_resume)
                        } else {
                            stringResource(R.string.action_watch)
                        },
                        icon = TvIcons.Play,
                        primary = true,
                        onClick = { onPlay(movie.contentKey, resumePosition ?: 0L) },
                        focusRequester = watchFocus,
                        testTag = "movie-details-watch",
                    )
                    // Only offered when there is a position to go back from;
                    // with nothing watched it would be the button beside it
                    // wearing a different label.
                    if (resumePosition != null) {
                        CatalogueDetailAction(
                            label = stringResource(R.string.details_restart),
                            icon = TvIcons.Replay,
                            onClick = { onPlay(movie.contentKey, 0L) },
                            testTag = "movie-details-restart",
                        )
                    }
                    metadata?.let { enriched ->
                        CatalogueDetailAction(
                            label = stringResource(R.string.metadata_source, enriched.attributionName),
                            icon = TvIcons.Info,
                            onClick = { runCatching { uriHandler.openUri(enriched.attributionUrl) } },
                            testTag = "movie-details-attribution",
                        )
                    }
                    // Offered whether or not anything was found. A wrong match
                    // is about as likely as no match, and it is the same
                    // question either way: which title is this?
                    CatalogueDetailAction(
                        label = stringResource(R.string.match_picker_open),
                        icon = TvIcons.Search,
                        onClick = { pickingMatch = true },
                        testTag = "movie-details-fix-match",
                    )
                }
                // Only where there is a choice to make. One copy is not a
                // version of anything, and a row saying so would read as a
                // fault rather than as information.
                if (copies.size > 1) {
                    CatalogueDetailSectionHeading(
                        text = stringResource(R.string.details_versions),
                        modifier = Modifier.padding(top = 30.dp, bottom = 14.dp),
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 18.dp),
                    ) {
                        items(copies, key = CatalogueFilmCopy::contentKey) { copy ->
                            CatalogueCopyCard(
                                sourceName = copy.sourceName,
                                claims = catalogueCopySummary(copy.title),
                                current = copy.current,
                                // The position belongs to the film, so a copy
                                // opened from here carries on where the film
                                // was left rather than starting again.
                                onClick = { onPlay(copy.contentKey, resumePosition ?: 0L) },
                                testTag = "movie-details-version-${copy.sourceId}",
                            )
                        }
                    }
                }
                metadata?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                    CatalogueDetailSectionHeading(
                        text = stringResource(R.string.series_cast),
                        modifier = Modifier.padding(top = 30.dp, bottom = 14.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(cast, key = MetadataCastMember::name) { member ->
                            CatalogueCastMember(member)
                        }
                    }
                }
                // The heading only appears once the lookup has run, so an
                // absent row never reads as an empty one.
                if (metadata?.detailsLoaded == true) {
                    CatalogueDetailSectionHeading(
                        text = stringResource(R.string.details_similar),
                        modifier = Modifier.padding(top = 30.dp, bottom = 14.dp),
                    )
                    when {
                        loadedSimilarSignature != similarSignature -> SimilarPlaceholder(
                            message = stringResource(R.string.movie_similar_loading),
                        )
                        similarMovies.isEmpty() -> SimilarPlaceholder(
                            message = stringResource(R.string.movie_no_similar_available),
                        )
                        else -> LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(end = 18.dp),
                        ) {
                            items(similarMovies, key = { it.movie.contentKey }) { available ->
                                CatalogueArtworkCard(
                                    title = available.title,
                                    subtitle = available.year?.toString(),
                                    artworkUrl = available.posterUrl,
                                    onClick = { onOpenMovie(available.movie) },
                                    testTag = "movie-similar-${available.movie.contentKey}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarPlaceholder(message: String) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Box(
        modifier = Modifier.fillMaxWidth().height(SIMILAR_PLACEHOLDER_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = message,
            color = palette.textDim,
            fontSize = typography.body.fontSize,
            lineHeight = typography.body.lineHeight,
        )
    }
}

private val DETAILS_TITLE_GAP = 34.dp
private val SIMILAR_PLACEHOLDER_HEIGHT = 80.dp

/**
 * How wide the reading column runs.
 *
 * The right of the frame is left to the artwork, which is the whole point of a
 * full-bleed backdrop; a synopsis run to the far edge would cross the bright
 * part of the picture where no scrim can hold it.
 */
internal const val DETAILS_TEXT_FRACTION = 0.62f
