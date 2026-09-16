package com.streammate.tv.feature.home

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.tween
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import com.streammate.tv.iptv.R as IptvR
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import com.sohva.tv.addons.AddonWatchProgress
import com.streammate.tv.trakt.TraktHomeTitle
import kotlinx.coroutines.delay
import com.streammate.tv.R
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.SohvaNavigationIcons
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.InheritedFocusScrollBehavior
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.common.tickerFlow
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.ContinueWatchingItem
import com.streammate.tv.iptv.repository.GuideChannel
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.metadata.MetadataLookup
import com.streammate.tv.iptv.metadata.MetadataMediaType
import com.streammate.tv.iptv.metadata.MetadataRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    guideRepository: GuideRepository,
    catalogueRepository: CatalogueRepository,
    preferencesRepository: AppPreferencesRepository,
    sportsEvents: List<TodayEvent>,
    onLiveTv: () -> Unit,
    onSportMate: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    /** Opens the who-is-watching page; null while the household has one profile. */
    onProfiles: (() -> Unit)? = null,
    /** Optional addon destination; unavailable in demo and restricted profiles. */
    onDiscover: (() -> Unit)? = null,
    onPlayChannel: (String) -> Unit,
    onPlayVod: (String, Long) -> Unit,
    /** Opens Sohva Sport on this match's card rather than on the day's list. */
    onOpenSportEvent: (TodayEvent) -> Unit = { onSportMate() },
    /** Discover's part-watched titles, read by the host; empty when Discover is unavailable. */
    discoverHistory: List<AddonWatchProgress> = emptyList(),
    /** Supplied by the application store; standalone previews can use repository reads. */
    resumeSnapshot: HomeResumeSnapshot? = null,
    onRetryResume: () -> Unit = {},
    initialTraktHistoryPending: Boolean = false,
    onOpenDiscoverTitle: (AddonWatchProgress) -> Unit = {},
    /** The same lookups the details pages use for a synopsis and a backdrop; null leaves the hero to stored text. */
    metadataRepository: MetadataRepository? = null,
    /** A Discover title's synopsis from the addon's cached details, when the host has them. */
    discoverSynopsis: suspend (AddonWatchProgress) -> String? = { null },
    /** Trakt's next episodes and picks for the profile; empty when Trakt is not connected. */
    nextUp: List<TraktHomeTitle> = emptyList(),
    recommendations: List<TraktHomeTitle> = emptyList(),
    onOpenTraktTitle: (TraktHomeTitle) -> Unit = {},
) {
    val spacing = StreamMateThemeTokens.spacing
    val welcomeFocus = remember { FocusRequester() }
    // The rail sits over the rows rather than beside them, so the way back out
    // of it is stated rather than searched for.
    val contentFocus = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val preferences by preferencesRepository.preferences
        .collectAsStateWithLifecycle(initialValue = AppPreferences())
    // Null until the first read: focus must not be placed while the first row is still on its way.
    val standaloneResume = if (resumeSnapshot == null) {
        val local by remember(catalogueRepository) {
            catalogueRepository.observeContinueWatching()
        }.collectAsStateWithLifecycle(initialValue = null)
        HomeResumeSnapshot(preferences.activeProfileId, mergeHomeResume(local.orEmpty(), discoverHistory),
            if (local == null) HomeResumeStatus.LOADING else if (local!!.isEmpty() && discoverHistory.isEmpty()) HomeResumeStatus.EMPTY else HomeResumeStatus.READY)
    } else resumeSnapshot
    val resume = standaloneResume
    var focusedRow by remember(resume.profileId, resume.discoverAllowed) { mutableStateOf<String?>(null) }
    val listState = key(resume.profileId, resume.discoverAllowed) { rememberLazyListState() }
    // Subscribed once on entry rather than against a ticking clock: Home is a
    // launch screen, and re-creating this query every minute is exactly what
    // makes the player and the guide churn.
    val guideEntryMillis = remember { System.currentTimeMillis() }
    // Only the channels the row can show are read. Reading the whole guide
    // for a few recent tiles cost seconds and tens of megabytes on a library
    // of fifty thousand channels.
    val recentChannelIds = preferences.recentChannelIds
    val channels by remember(guideRepository, guideEntryMillis, recentChannelIds) {
        guideRepository.observeGuideChannels(recentChannelIds, guideEntryMillis)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val latestRows = HomeRows(
        resume = resume.entries,
        nextUp = nextUp,
        sports = sportsEvents.take(HOME_ROW_LIMIT),
        recommendations = recommendations,
        channels = remember(channels, preferences.recentChannelIds) {
            val positions = preferences.recentChannelIds.withIndex().associate { it.value to it.index }
            channels.filter { it.id in positions }.sortedBy { positions[it.id] }.take(HOME_ROW_LIMIT)
        },
    )
    var previousRows by remember(resume.profileId, resume.discoverAllowed) { mutableStateOf(latestRows) }
    val structureLocked = listState.firstVisibleItemIndex > 0 ||
        (focusedRow != null && focusedRow != previousRows.firstKey)
    val displayedRows = previousRows.updated(latestRows, structureLocked)
    SideEffect { previousRows = displayedRows }
    val resumeEntries = displayedRows.resume
    val recentChannels = displayedRows.channels
    val todaysSport = displayedRows.sports
    val visibleNextUp = displayedRows.nextUp
    val visibleRecommendations = displayedRows.recommendations
    // The Continue watching card whose actions are open, if any.
    var resumeActions by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    resumeActions?.let { item ->
        HomeResumeActionsDialog(
            item = item,
            onResume = { resumeActions = null; onPlayVod(item.contentKey, item.progress.resumePositionMillis) },
            onStartOver = { resumeActions = null; onPlayVod(item.contentKey, 0L) },
            onMarkWatched = { resumeActions = null; scope.launch { catalogueRepository.markWatched(item.contentKey, true) } },
            onRemove = { resumeActions = null; scope.launch { catalogueRepository.forgetProgress(item.contentKey) } },
            onDismiss = { resumeActions = null },
        )
    }

    // The clock and the live progress bars are the only things here that have
    // to keep moving. One minute-long ticker drives all of them, and nothing
    // re-queries the database on it.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        tickerFlow(periodMillis = MINUTE_MILLIS, emitImmediately = false).collect {
            now = System.currentTimeMillis()
        }
    }

    // The hero describes whatever card is focused. Rapid D-pad travel across a
    // row must not fetch a backdrop per tick, so a focus only becomes the hero
    // once it has rested for a moment. With nothing focused, the newest
    // part-watched title stands, then the last channel, then a welcome.
    val idleHero = rememberIdleHero(resumeEntries, recentChannels, now)
    var focusedHero by remember(resume.profileId, resume.discoverAllowed) { mutableStateOf<HomeHero?>(null) }
    val hero by key(resume.profileId, resume.discoverAllowed) {
        produceState(initialValue = idleHero, focusedHero, idleHero) {
            val focused = focusedHero
            if (focused == null) value = idleHero
            else { delay(HERO_FOCUS_SETTLE_MILLIS); value = focused }
        }
    }
    val heroDetails = rememberHeroDetails(hero, catalogueRepository, guideRepository, metadataRepository, discoverSynopsis, enabled = resume.settled)
    val showResumeStatus = !structureLocked && resumeEntries.isEmpty() && resume.status in setOf(HomeResumeStatus.LOADING, HomeResumeStatus.FAILED)
    val rowsEmpty = !showResumeStatus && resumeEntries.isEmpty() && recentChannels.isEmpty() && todaysSport.isEmpty() && visibleNextUp.isEmpty() && visibleRecommendations.isEmpty()

    // Whatever bring-into-view policy the platform installed for rails is kept
    // for them; the row container itself pulls the focused row up to its top.
    val railScrollBehavior = LocalBringIntoViewSpec.current
    var loadingFocused by remember { mutableStateOf(false) }
    // Initial focus only. Later refreshes never pull the viewer back from another row or the rail.
    LaunchedEffect(resume.profileId, resume.discoverAllowed) {
        if (rowsEmpty) welcomeFocus.requestFocusWhenAttached() else contentFocus.requestFocusWhenAttached()
    }
    // Capture before removing the focused placeholder: Compose can move focus
    // to the rail during disposal, which must not erase this pending handoff.
    val handoffFromLoading = loadingFocused
    LaunchedEffect(showResumeStatus) {
        if (!showResumeStatus && handoffFromLoading) {
            if (rowsEmpty) welcomeFocus.requestFocusWhenAttached() else contentFocus.requestFocusWhenAttached()
            loadingFocused = false
        }
    }

    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { contentModifier ->
        Box(modifier = contentModifier) {
            HomeHeroBackdrop(hero = hero, artwork = heroDetails.backdropUrl)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = HOME_RAIL_WIDTH + spacing.lg, end = spacing.xl),
            ) {
                // A band of fixed height, whatever the hero says: the rows below
                // must not shift as focus moves between cards. The text sits on
                // the band's floor, just above the first row.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(HOME_HERO_FRACTION)
                        .padding(top = spacing.lg, bottom = spacing.md),
                ) {
                    HomeHeader(timeZoneId = preferences.timeZoneId, now = now)
                    if (initialTraktHistoryPending && resume.settled) {
                        Text(
                            text = stringResource(R.string.home_trakt_first_sync),
                            style = StreamMateThemeTokens.typography.body,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        )
                    }
                    HomeHeroPanel(
                        hero = hero,
                        plot = heroDetails.description,
                        now = now,
                        welcomeFocus = welcomeFocus,
                        showWelcomeAction = rowsEmpty,
                        onLiveTv = onLiveTv,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides HomeRowsPivotSpec) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(contentFocus)
                            // Coming back from the rail returns to the card you left,
                            // not to the top of the screen.
                            .focusRestorer()
                            .focusGroup(),
                        contentPadding = PaddingValues(top = spacing.md, bottom = HOME_ROWS_BOTTOM_SLACK),
                        verticalArrangement = Arrangement.spacedBy(HOME_ROW_GAP),
                    ) {
                        if (showResumeStatus) {
                            item(key = "continue-watching") {
                                HomeRow(title = stringResource(R.string.home_continue_watching), focusScrollBehavior = railScrollBehavior) {
                                    item(key = "resume-status") {
                                        TvSurface(
                                            onClick = { if (resume.status == HomeResumeStatus.FAILED) onRetryResume() },
                                            modifier = Modifier.testTag("home-resume-status")
                                                .onFocusChanged { if (it.hasFocus) loadingFocused = true }
                                                .onPreviewKeyEvent { resume.status == HomeResumeStatus.LOADING && it.key == Key.DirectionDown },
                                        ) {
                                            Text(stringResource(if (resume.status == HomeResumeStatus.LOADING) R.string.home_resume_loading else R.string.home_resume_unavailable), modifier = Modifier.padding(24.dp))
                                        }
                                    }
                                }
                            }
                        } else if (resumeEntries.isNotEmpty()) {
                            item(key = "continue-watching") {
                                HomeRow(
                                    title = stringResource(R.string.home_continue_watching),
                                    hint = stringResource(R.string.home_rows_hint),
                                    focusScrollBehavior = railScrollBehavior,
                                ) {
                                    items(resumeEntries, key = HomeResumeEntry::key) { entry ->
                                        HomeResumeCard(
                                            entry = entry,
                                            onClick = {
                                                when (entry) {
                                                    is HomeResumeEntry.Vod -> onPlayVod(entry.item.contentKey, entry.item.progress.resumePositionMillis)
                                                    is HomeResumeEntry.Discover -> onOpenDiscoverTitle(entry.progress)
                                                }
                                            },
                                            onLongClick = { (entry as? HomeResumeEntry.Vod)?.let { resumeActions = it.item } },
                                            onFocused = { loadingFocused = false; focusedRow = "continue-watching"; focusedHero = HomeHero.Resume(entry) },
                                        )
                                    }
                                }
                            }
                        }
                        if (visibleNextUp.isNotEmpty()) {
                            item(key = "watch-next") {
                                HomeRow(title = stringResource(R.string.home_watch_next), focusScrollBehavior = railScrollBehavior) {
                                    items(visibleNextUp, key = TraktHomeTitle::key) { title ->
                                        HomeTraktCard(title = title, landscape = true, onClick = { onOpenTraktTitle(title) },
                                            onFocused = { loadingFocused = false; focusedRow = "watch-next"; focusedHero = HomeHero.Trakt(title, next = true) })
                                    }
                                }
                            }
                        }
                        if (todaysSport.isNotEmpty()) {
                            item(key = "todays-sport") {
                                HomeRow(
                                    title = stringResource(R.string.home_sports_today),
                                    hint = pluralStringResource(
                                        R.plurals.home_sports_count,
                                        sportsEvents.size,
                                        sportsEvents.size,
                                    ),
                                    focusScrollBehavior = railScrollBehavior,
                                ) {
                                    items(todaysSport, key = TodayEvent::id) { event ->
                                        HomeSportCard(event = event, onClick = { onOpenSportEvent(event) }, onFocused = { loadingFocused = false; focusedRow = "todays-sport"; focusedHero = HomeHero.Sport(event) })
                                    }
                                }
                            }
                        }
                        if (visibleRecommendations.isNotEmpty()) {
                            item(key = "recommended") {
                                HomeRow(title = stringResource(R.string.home_recommended), focusScrollBehavior = railScrollBehavior) {
                                    items(visibleRecommendations, key = TraktHomeTitle::key) { title ->
                                        HomeTraktCard(title = title, landscape = false, onClick = { onOpenTraktTitle(title) },
                                            onFocused = { loadingFocused = false; focusedRow = "recommended"; focusedHero = HomeHero.Trakt(title, next = false) })
                                    }
                                }
                            }
                        }
                        if (recentChannels.isNotEmpty()) {
                            item(key = "recent-channels") {
                                HomeRow(
                                    title = stringResource(R.string.home_recent_channels),
                                    focusScrollBehavior = railScrollBehavior,
                                ) {
                                    items(recentChannels, key = GuideChannel::id) { channel ->
                                        HomeChannelCard(
                                            channel = channel,
                                            now = now,
                                            onClick = { onPlayChannel(channel.id) },
                                            onFocused = { loadingFocused = false; focusedRow = "recent-channels"; focusedHero = HomeHero.Channel(channel, live = channel.isLiveAt(now)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // With no card anywhere, the way out of the rail is the welcome button.
            val rowsFocus = if (rowsEmpty) welcomeFocus else contentFocus
            BackHandler(enabled = railFocused) { rowsFocus.requestFocus() }
            HomeRail(
                contentFocus = rowsFocus,
                onFocusedChange = { railFocused = it; if (it) { loadingFocused = false; focusedHero = null } },
                onLiveTv = onLiveTv,
                onSportMate = onSportMate,
                onMovies = onMovies,
                onSeries = onSeries,
                onSearch = onSearch,
                onSettings = onSettings,
                onProfiles = onProfiles,
                onDiscover = onDiscover,
                modifier = Modifier.zIndex(1f),
            )
        }
    }
}

/**
 * The row container's bring-into-view policy: a focused row is pulled up to
 * the top of the container, so the rows travel under the hero and the focus
 * stays on one line instead of walking off the bottom of the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
private object HomeRowsPivotSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset
}

// ---------------------------------------------------------------- the hero --

/**
 * What the top of the screen is about: the card under focus, or with nothing
 * focused, the newest part-watched title, then the last channel, and with an
 * empty library a plain invitation into the guide. Every field of every
 * variant is read off a stored record; nothing here is composed for the look
 * of it.
 */
private sealed interface HomeHero {
    data class Resume(val entry: HomeResumeEntry) : HomeHero

    data class Channel(val channel: GuideChannel, val live: Boolean) : HomeHero

    data class Sport(val event: TodayEvent) : HomeHero

    /** A Trakt title: the next episode of a show ([next]) or a recommendation. */
    data class Trakt(val title: TraktHomeTitle, val next: Boolean) : HomeHero

    data object Welcome : HomeHero
}

@Composable
private fun rememberIdleHero(
    resumeEntries: List<HomeResumeEntry>,
    recentChannels: List<GuideChannel>,
    now: Long,
): HomeHero = remember(resumeEntries, recentChannels, now) {
    val resumable = resumeEntries.firstOrNull { it.fraction > 0f }
    val channel = recentChannels.firstOrNull { it.currentProgrammeTitle != null }
        ?: recentChannels.firstOrNull()
    when {
        resumable != null -> HomeHero.Resume(resumable)
        channel != null -> HomeHero.Channel(channel, live = channel.isLiveAt(now))
        else -> HomeHero.Welcome
    }
}

private fun GuideChannel.isLiveAt(now: Long): Boolean {
    val start = programmeStartEpochMillis ?: return false
    val stop = programmeStopEpochMillis ?: return false
    return now in start until stop
}

private fun GuideChannel.progressAt(now: Long): Float? {
    val start = programmeStartEpochMillis ?: return null
    val stop = programmeStopEpochMillis ?: return null
    if (stop <= start) return null
    return ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/** What the hero says and shows about its subject beyond the card's own fields. */
private data class HeroDetails(val description: String? = null, val backdropUrl: String? = null)

/**
 * The synopsis and backdrop for the hero, read the way the details pages read
 * them: the metadata match first, the provider's own text second. Library
 * titles that were enriched answer from the cache; a channel's programme is
 * matched by title the way the guide does it; Discover answers from the
 * addon's cached details. Nothing is fetched for a hero that has moved on.
 */
@Composable
private fun rememberHeroDetails(
    hero: HomeHero,
    catalogueRepository: CatalogueRepository,
    guideRepository: GuideRepository,
    metadataRepository: MetadataRepository?,
    discoverSynopsis: suspend (AddonWatchProgress) -> String?,
    enabled: Boolean = true,
): HeroDetails {
    val subject: Any? = when (hero) {
        is HomeHero.Resume -> hero.entry.key
        is HomeHero.Channel -> hero.channel.id + ":" + hero.channel.currentProgrammeTitle
        is HomeHero.Trakt -> hero.title.key
        is HomeHero.Sport, HomeHero.Welcome -> null
    }
    val details by produceState(initialValue = HeroDetails(), subject, enabled) {
        value = HeroDetails()
        if (!enabled || subject == null) return@produceState
        value = runCatching {
            when (hero) {
                is HomeHero.Resume -> when (val entry = hero.entry) {
                    is HomeResumeEntry.Vod -> vodHeroDetails(entry.item.contentKey, catalogueRepository, metadataRepository)
                    is HomeResumeEntry.Discover -> HeroDetails(description = discoverSynopsis(entry.progress))
                }
                is HomeHero.Channel -> channelHeroDetails(hero.channel, guideRepository, metadataRepository)
                is HomeHero.Trakt -> traktHeroDetails(hero.title, metadataRepository)
                else -> HeroDetails()
            }
        }.getOrDefault(HeroDetails())
    }
    return details
}

private suspend fun vodHeroDetails(contentKey: String, catalogue: CatalogueRepository, metadata: MetadataRepository?): HeroDetails {
    val enabled = metadata?.isEnabled() == true
    if (contentKey.startsWith(MOVIE_CONTENT_KEY_PREFIX)) {
        val movie = catalogue.movie(contentKey) ?: return HeroDetails()
        val match = if (enabled) metadata?.enrich(MetadataLookup(MetadataMediaType.MOVIE, movie.name, movie.year)) else null
        return HeroDetails(
            description = match?.overview?.takeIf(String::isNotBlank) ?: movie.plot?.takeIf(String::isNotBlank),
            backdropUrl = match?.backdropUrl?.takeIf(String::isNotBlank),
        )
    }
    val episode = catalogue.episode(contentKey) ?: return HeroDetails()
    val series = catalogue.seriesForEpisode(contentKey)
    val seriesMatch = if (enabled && series != null) metadata?.enrich(MetadataLookup(MetadataMediaType.SERIES, series.name, series.year)) else null
    val episodeMatch = if (enabled && series != null) {
        metadata?.enrich(MetadataLookup(MetadataMediaType.EPISODE, series.name, series.year, episode.seasonNumber, episode.episodeNumber))
    } else null
    return HeroDetails(
        description = episode.plot?.takeIf(String::isNotBlank)
            ?: episodeMatch?.overview?.takeIf(String::isNotBlank)
            ?: seriesMatch?.overview?.takeIf(String::isNotBlank)
            ?: series?.plot?.takeIf(String::isNotBlank),
        backdropUrl = seriesMatch?.backdropUrl?.takeIf(String::isNotBlank) ?: series?.backdropUrl?.takeIf(String::isNotBlank),
    )
}

/**
 * Trakt's text is English. The record is asked of TMDB by id in the interface
 * language first, and Trakt's own synopsis stands in only when TMDB has none,
 * so the hero never shows English for a moment before the Finnish arrives.
 */
private suspend fun traktHeroDetails(title: TraktHomeTitle, metadata: MetadataRepository?): HeroDetails {
    val fallback = HeroDetails(description = title.overview, backdropUrl = title.fanart ?: title.poster)
    val tmdb = title.ids.tmdb?.toString() ?: return fallback
    val mediaType = if (title.kind == "movie") MetadataMediaType.MOVIE else MetadataMediaType.SERIES
    val details = metadata?.detailsByExternalId(tmdb, mediaType) ?: return fallback
    return HeroDetails(
        description = details.overview ?: title.overview,
        backdropUrl = details.backdropUrl ?: title.fanart ?: title.poster,
    )
}

private suspend fun channelHeroDetails(channel: GuideChannel, guide: GuideRepository, metadata: MetadataRepository?): HeroDetails {
    val title = channel.currentProgrammeTitle ?: return HeroDetails()
    val now = System.currentTimeMillis()
    val programme = guide.observeTimelineForChannels(listOf(channel.id), now, now + 1).first()
        .firstOrNull()?.programmes?.firstOrNull { now in it.startEpochMillis until it.stopEpochMillis }
    val match = if (metadata?.isEnabled() == true) metadata.enrich(MetadataLookup(MetadataMediaType.PROGRAMME, title)) else null
    return HeroDetails(
        description = match?.overview?.takeIf(String::isNotBlank) ?: programme?.description?.takeIf(String::isNotBlank),
        backdropUrl = match?.backdropUrl?.takeIf(String::isNotBlank) ?: match?.posterUrl?.takeIf(String::isNotBlank),
    )
}

/**
 * The artwork behind the hero, buried under scrims on the side the text sits.
 *
 * A resume item brings its own artwork. A channel or a match has only logos,
 * which are not backdrops and would look wrong stretched across a screen, so
 * they borrow the bundled Live TV artwork the same way the empty state does.
 * The previous picture stays until the next has arrived: the two are
 * crossfaded rather than one cleared before the other is loaded.
 */
@Composable
private fun HomeHeroBackdrop(hero: HomeHero, artwork: String?, modifier: Modifier = Modifier) {
    val palette = StreamMateThemeTokens.palette
    val context = LocalContext.current
    // The washes cover the whole screen so they end nowhere visible; only the
    // picture is confined to the hero's part of it.
    Box(modifier = modifier.fillMaxSize()) {
        val artwork = artwork?.takeIf(String::isNotBlank) ?: (hero as? HomeHero.Resume)?.entry?.backdropUrl?.takeIf(String::isNotBlank)
        // The picture fades out at its bottom and its left edge instead of being
        // painted over: what shows through is the screen's own ground, so there
        // is no seam where a flat scrim would meet the background gradient.
        Box(
            modifier = Modifier
                .fillMaxWidth(HOME_HERO_ART_FRACTION)
                .fillMaxHeight(HOME_HERO_ART_FRACTION_HEIGHT)
                .align(Alignment.TopEnd)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black), blendMode = BlendMode.DstOut)
                    drawRect(Brush.horizontalGradient(0f to Color.Black, 0.4f to Color.Transparent), blendMode = BlendMode.DstOut)
                },
        ) {
            // The bundled artwork is the floor rather than the alternative, so
            // there is never a bare rectangle here while a picture is still on
            // its way down, or if it never arrives.
            Image(
                painter = painterResource(R.drawable.home_backdrop_live_tv),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // A match has no picture of its own; its two crests, large and quiet
            // over the bundled art, say what it is without pretending to be a still.
            (hero as? HomeHero.Sport)?.event?.let { event -> HomeSportBackdrop(event) }
            Crossfade(targetState = artwork, animationSpec = tween(HERO_CROSSFADE_MILLIS), label = "hero artwork") { url ->
                if (url != null) {
                    // Decoded no larger than the screen and in a 16-bit config:
                    // a full-size poster in ARGB is texture memory this heap
                    // cannot spare, and the difference is invisible on a TV.
                    val request = remember(context, url) {
                        ImageRequest.Builder(context).data(url).size(HERO_ART_MAX_WIDTH_PX, HERO_ART_MAX_HEIGHT_PX)
                            .bitmapConfig(Bitmap.Config.RGB_565).build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        // Two scrims, as in the reference: one across, so the text side is
        // near-black whatever the artwork does, and one down, so the rows
        // below start on clean ground rather than on a cut-off picture.
        // A light wash over the text side and the top edge; legibility only,
        // since the picture's own fade already clears the ground below.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to palette.background.copy(alpha = 0.85f),
                    0.4f to palette.background.copy(alpha = 0.55f),
                    0.7f to palette.background.copy(alpha = 0.05f),
                    1f to palette.background.copy(alpha = 0.25f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to palette.background.copy(alpha = 0.5f),
                    0.3f to palette.background.copy(alpha = 0f),
                ),
            ),
        )
    }
}

@Composable
private fun HomeSportBackdrop(event: TodayEvent) {
    val palette = StreamMateThemeTokens.palette
    Box(Modifier.fillMaxSize().background(palette.background.copy(alpha = 0.6f)))
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((index, logo) in listOf(event.homeLogoUrl, event.awayLogoUrl).withIndex()) {
            if (index == 1) Spacer(Modifier.width(HOME_SPORT_BACKDROP_GAP))
            Box(Modifier.size(HOME_SPORT_BACKDROP_CREST).alpha(0.55f), contentAlignment = Alignment.Center) {
                if (!logo.isNullOrBlank()) {
                    AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun HomeHeroPanel(
    hero: HomeHero,
    plot: String?,
    now: Long,
    welcomeFocus: FocusRequester,
    showWelcomeAction: Boolean,
    onLiveTv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val spacing = StreamMateThemeTokens.spacing
    val locale = rememberInterfaceLocale()

    val live = when (hero) {
        is HomeHero.Channel -> hero.live
        is HomeHero.Sport -> hero.event.status == TodayEventStatus.LIVE
        else -> false
    }
    val kicker = when (hero) {
        is HomeHero.Trakt -> stringResource(if (hero.next) R.string.home_watch_next else R.string.home_recommended)
        is HomeHero.Resume -> stringResource(R.string.home_hero_resume)
        is HomeHero.Channel -> if (hero.live) stringResource(R.string.home_hero_live) else stringResource(R.string.home_live_tv)
        is HomeHero.Sport -> if (live) stringResource(R.string.home_hero_live) else stringResource(R.string.home_sports_today)
        HomeHero.Welcome -> stringResource(R.string.home_hero_welcome)
    }
    val title = when (hero) {
        is HomeHero.Resume -> hero.entry.title
        is HomeHero.Channel -> hero.channel.currentProgrammeTitle ?: hero.channel.name
        is HomeHero.Sport -> hero.event.home + TEAM_SEPARATOR + hero.event.away
        is HomeHero.Trakt -> hero.title.title
        HomeHero.Welcome -> stringResource(R.string.home_live_tv)
    }
    val metadata = heroMetadata(hero)
    val description = when (hero) {
        is HomeHero.Resume, is HomeHero.Trakt -> plot
        is HomeHero.Channel, is HomeHero.Sport -> null
        HomeHero.Welcome -> stringResource(R.string.home_live_tv_description)
    }
    val progress = when (hero) {
        is HomeHero.Resume -> hero.entry.fraction.takeIf { it > 0f }
        is HomeHero.Channel -> hero.channel.progressAt(now)
        is HomeHero.Sport, is HomeHero.Trakt, HomeHero.Welcome -> null
    }

    Column(modifier = modifier.fillMaxWidth(HOME_HERO_TEXT_FRACTION)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Box(
                    Modifier
                        .size(HOME_LIVE_DOT)
                        .clip(StreamMateThemeTokens.shapes.small)
                        .background(palette.danger),
                )
                Spacer(Modifier.width(spacing.sm))
            }
            Text(
                text = kicker.uppercase(locale),
                color = palette.focus,
                fontSize = typography.overline.fontSize,
                lineHeight = typography.overline.lineHeight,
                fontWeight = FontWeight.Bold,
                letterSpacing = typography.overline.letterSpacing,
            )
        }
        Spacer(Modifier.height(spacing.md))
        Text(
            text = title,
            modifier = Modifier.testTag("home-hero-title"),
            color = palette.textPrimary,
            fontSize = typography.display.fontSize,
            lineHeight = typography.display.lineHeight,
            fontWeight = FontWeight.Black,
            letterSpacing = typography.display.letterSpacing,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (metadata.isNotEmpty()) {
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = metadata.joinToString(METADATA_SEPARATOR),
                color = palette.textMuted,
                fontSize = typography.bodyLarge.fontSize,
                lineHeight = typography.bodyLarge.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        progress?.let { fraction ->
            Spacer(Modifier.height(spacing.md))
            Box(
                Modifier
                    .width(HOME_HERO_PROGRESS_WIDTH)
                    .height(4.dp)
                    .clip(StreamMateThemeTokens.shapes.small)
                    .background(palette.surfaceRaised)
                    .testTag("home-hero-progress"),
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(palette.focus))
            }
        }
        description?.let {
            Spacer(Modifier.height(spacing.md))
            Text(
                text = it,
                color = palette.textMuted,
                fontSize = typography.bodyLarge.fontSize,
                lineHeight = typography.bodyLarge.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The hero describes; the cards act. Only an empty Home, with no card
        // anywhere to hold focus, gets a button of its own.
        if (showWelcomeAction) {
            Spacer(Modifier.height(spacing.xl))
            HomeHeroAction(
                label = stringResource(R.string.home_action_guide),
                icon = TvIcons.Guide,
                primary = true,
                testTag = "home-hero-primary",
                focusRequester = welcomeFocus,
                onClick = onLiveTv,
            )
        }
    }
}

/** The facts under the title, each one read off the record behind the hero. */
@Composable
private fun heroMetadata(hero: HomeHero): List<String> = when (hero) {
    is HomeHero.Resume -> buildList {
        hero.entry.subtitleLabel()?.takeIf(String::isNotBlank)?.let(::add)
        hero.entry.remainingLabel()?.let(::add)
    }
    is HomeHero.Channel -> buildList {
        add(hero.channel.name)
        hero.channel.currentProgrammeSubtitle?.takeIf(String::isNotBlank)?.let(::add)
        hero.channel.programmeWindowLabel()?.let(::add)
    }
    is HomeHero.Trakt -> buildList {
        hero.title.episodeLabel()?.let(::add)
        hero.title.year?.let { add(it.toString()) }
    }
    is HomeHero.Sport -> buildList {
        add(hero.event.competition)
        hero.event.statusLabel.ifBlank { hero.event.startLabel }.takeIf(String::isNotBlank)?.let(::add)
        hero.event.score?.takeIf(String::isNotBlank)?.let(::add)
    }
    HomeHero.Welcome -> emptyList()
}

@Composable
private fun HomeResumeEntry.remainingLabel(): String? {
    val remaining = remainingMillis ?: return null
    val minutes = (remaining / MINUTE_MILLIS).toInt().coerceAtLeast(1)
    return pluralStringResource(R.plurals.home_minutes_left, minutes, minutes)
}

private fun GuideChannel.programmeWindowLabel(): String? {
    val start = programmeStartEpochMillis ?: return null
    val stop = programmeStopEpochMillis ?: return null
    // The guide stores instants; the window is shown in whatever zone the
    // device is set to, which is the same zone the guide itself lays out in.
    val zone = ZoneId.systemDefault()
    return formatClock(start, zone) + PROGRAMME_WINDOW_DASH + formatClock(stop, zone)
}

private fun formatClock(epochMillis: Long, zone: ZoneId): String =
    HERO_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/**
 * A hero button. Taller and wider than a row control, and otherwise the same
 * surface as everything else: no resting outline, off-white fill on focus.
 */
@Composable
private fun HomeHeroAction(
    label: String?,
    @DrawableRes icon: Int,
    primary: Boolean,
    testTag: String,
    onClick: () -> Unit,
    contentDescription: String? = null,
    focusRequester: FocusRequester? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val semanticsModifier = contentDescription?.let { description ->
        Modifier.semantics { this.contentDescription = description }
    } ?: Modifier
    TvSurface(
        onClick = onClick,
        modifier = Modifier.height(HOME_HERO_BUTTON_HEIGHT).then(semanticsModifier),
        shape = StreamMateThemeTokens.shapes.medium,
        // Primary sits a step higher on the ladder rather than being filled
        // white at rest: white is what focus means here, and a button already
        // white has nowhere left to go when it is pointed at.
        resting = if (primary) palette.surfaceRaised else palette.surface,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        focusRequester = focusRequester,
        testTag = testTag,
        contentPadding = PaddingValues(horizontal = if (label == null) 14.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.content),
                modifier = Modifier.size(20.dp).clearAndSetSemantics { },
            )
            label?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = it,
                    color = colors.content,
                    fontSize = typography.body.fontSize,
                    lineHeight = typography.body.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

// -------------------------------------------------------------- the header --

@Composable
private fun HomeHeader(timeZoneId: String, now: Long) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SohvaTvBrand(
            modifier = Modifier.testTag("home-brand"),
            fontSize = typography.headline.fontSize,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = rememberClockLabel(timeZoneId, now),
            modifier = Modifier.testTag("home-clock"),
            color = palette.textMuted,
            fontSize = typography.body.fontSize,
            lineHeight = typography.body.lineHeight,
        )
    }
}

/**
 * Weekday, date and time in the interface language and the configured zone.
 *
 * The patterns come from the platform rather than being written out here: a
 * hard-coded `EEE d.M.` is Finnish word order spelled in Latin letters, and
 * whether the clock runs to twelve or twenty-four is the viewer's setting
 * rather than this screen's.
 */
@Composable
private fun rememberClockLabel(timeZoneId: String, now: Long): String {
    val context = LocalContext.current
    val locale = rememberInterfaceLocale()
    val twentyFourHour = DateFormat.is24HourFormat(context)
    val formatter = remember(locale, twentyFourHour) {
        val date = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        val time = DateFormat.getBestDateTimePattern(locale, if (twentyFourHour) "Hm" else "hmma")
        DateTimeFormatter.ofPattern(date + CLOCK_SEPARATOR_PATTERN + time, locale)
    }
    val zone = remember(timeZoneId) {
        runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
    }
    return remember(formatter, zone, now) {
        formatter.format(Instant.ofEpochMilli(now).atZone(zone))
    }
}

/**
 * The language the interface is drawn in.
 *
 * Compose's own locale rather than the Configuration's or the JVM default: it
 * is readable without a version guard, and reading it recomposes when the
 * interface language changes, which the other two do not.
 */
@Composable
private fun rememberInterfaceLocale(): Locale {
    val tag = ComposeLocale.current.toLanguageTag()
    return remember(tag) { Locale.forLanguageTag(tag) }
}

// ---------------------------------------------------------------- the rail --

private data class HomeDestination(
    val id: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

/**
 * The destination rail down the left edge.
 *
 * Collapsed to icons while nothing in it has focus, and widened over the
 * content - never beside it - once something does, so opening it never
 * re-lays-out the rows it stands in front of. Home itself is a marker rather
 * than a control: it is the page already being looked at, and a button that
 * navigates nowhere is worse than no button.
 */
@Composable
private fun HomeRail(
    contentFocus: FocusRequester,
    onFocusedChange: (Boolean) -> Unit,
    onLiveTv: () -> Unit,
    onSportMate: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProfiles: (() -> Unit)?,
    onDiscover: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val spacing = StreamMateThemeTokens.spacing
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        if (expanded) HOME_RAIL_EXPANDED_WIDTH else HOME_RAIL_WIDTH,
        label = "rail width",
    )
    val scrim by animateFloatAsState(if (expanded) 0.97f else 0.70f, label = "rail scrim")
    val navigation = stringResource(R.string.home_navigation)
    val destinations = listOfNotNull(
        HomeDestination("live", R.string.home_live_tv, SohvaNavigationIcons.LiveTv, onLiveTv),
        HomeDestination("sportmate", R.string.home_sportmate, SohvaNavigationIcons.Sport, onSportMate),
        HomeDestination("movies", R.string.home_movies, SohvaNavigationIcons.Movies, onMovies),
        HomeDestination("series", R.string.home_series, SohvaNavigationIcons.Series, onSeries),
        HomeDestination("search", R.string.home_search, SohvaNavigationIcons.Search, onSearch),
        onDiscover?.let { HomeDestination("discover", R.string.home_discover, SohvaNavigationIcons.Discover, it) },
        // Who is watching sits beside Settings once there is a choice to make.
        onProfiles?.let { HomeDestination("profiles", IptvR.string.profile_active_title, TvIcons.Star, it) },
        HomeDestination("settings", R.string.home_settings, SohvaNavigationIcons.Settings, onSettings),
    )
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    0f to palette.background.copy(alpha = scrim),
                    0.72f to palette.background.copy(alpha = scrim * 0.9f),
                    1f to palette.background.copy(alpha = 0f),
                ),
            )
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusedChange(it.hasFocus)
            }
            .focusGroup()
            .semantics { contentDescription = navigation }
            // The rail runs to the edge of the panel, so the icons are held
            // clear of overscan by this padding rather than by the screen's
            // safe area, which the content beside it uses.
            .padding(
                top = spacing.xl,
                bottom = spacing.xl,
                start = spacing.xl,
                end = spacing.sm,
            )
            // Discover adds a destination; keep Settings reachable at smaller TV scales.
            .then(if (onDiscover != null) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        HomeRailCurrentPage(expanded = expanded)
        Spacer(Modifier.height(spacing.sm))
        destinations.forEach { destination ->
            HomeRailItem(
                destination = destination,
                expanded = expanded,
                contentFocus = contentFocus,
            )
        }
    }
}

@Composable
private fun HomeRailCurrentPage(expanded: Boolean) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val label = stringResource(R.string.home_nav_home)
    Row(
        modifier = Modifier
            .height(HOME_RAIL_ITEM_SIZE)
            .fillMaxWidth()
            .clip(StreamMateThemeTokens.shapes.medium)
            .background(palette.textPrimary)
            .padding(horizontal = HOME_RAIL_ITEM_PADDING)
            .testTag("home-nav-home")
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(SohvaNavigationIcons.FrontPage),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.background),
            modifier = Modifier.size(HOME_RAIL_ICON_SIZE).clearAndSetSemantics { },
        )
        if (expanded) {
            Spacer(Modifier.width(HOME_RAIL_LABEL_GAP))
            Text(
                text = label,
                color = palette.background,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeRailItem(
    destination: HomeDestination,
    expanded: Boolean,
    contentFocus: FocusRequester,
) {
    val typography = StreamMateThemeTokens.typography
    val label = stringResource(destination.label)
    TvSurface(
        onClick = destination.onClick,
        modifier = Modifier
            .height(HOME_RAIL_ITEM_SIZE)
            .fillMaxWidth()
            // Right is the way back to the rows. The rail is drawn over them
            // and widens when it takes focus, so the card you came from ends
            // up underneath it and a search to the right finds only the next
            // item down the menu.
            .focusProperties { right = contentFocus },
        shape = StreamMateThemeTokens.shapes.medium,
        resting = Color.Transparent,
        focusScale = 1f,
        testTag = "home-" + destination.id,
        contentPadding = PaddingValues(horizontal = HOME_RAIL_ITEM_PADDING),
    ) { colors ->
        Row(
            modifier = Modifier.fillMaxHeight().semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(destination.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.content),
                modifier = Modifier.size(HOME_RAIL_ICON_SIZE).clearAndSetSemantics { },
            )
            if (expanded) {
                Spacer(Modifier.width(HOME_RAIL_LABEL_GAP))
                Text(
                    text = label,
                    color = colors.content,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- the rows --

@Composable
private fun HomeRow(
    title: String,
    focusScrollBehavior: BringIntoViewSpec,
    hint: String? = null,
    content: LazyListScope.() -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val spacing = StreamMateThemeTokens.spacing
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = typography.headline.fontSize,
                lineHeight = typography.headline.lineHeight,
                fontWeight = FontWeight.Bold,
            )
            hint?.let {
                Spacer(Modifier.width(spacing.md))
                Text(
                    text = it,
                    color = palette.textDim,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                )
            }
        }
        Spacer(Modifier.height(spacing.md))
        InheritedFocusScrollBehavior(focusScrollBehavior) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                contentPadding = HOME_ROW_EDGE_SLACK,
                content = content,
            )
        }
    }
}

/** A recently watched channel: what it is, what is on it, and how far in. */
@Composable
private fun HomeChannelCard(channel: GuideChannel, now: Long, onClick: () -> Unit, onFocused: () -> Unit = {}) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(HOME_CHANNEL_CARD_WIDTH).height(HOME_CHANNEL_CARD_HEIGHT).onFocusChanged { if (it.isFocused) onFocused() },
        shape = StreamMateThemeTokens.shapes.medium,
        resting = palette.surfaceSubtle,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        testTag = "home-channel-" + channel.id,
        contentPadding = PaddingValues(12.dp),
        contentAlignment = Alignment.TopStart,
    ) { colors ->
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier
                    .size(HOME_CHANNEL_LOGO_SIZE)
                    .clip(StreamMateThemeTokens.shapes.small)
                    .background(colors.content.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logoUrl.isNullOrBlank()) {
                    Text(
                        text = channel.name.artworkInitials(),
                        color = colors.secondaryContent,
                        fontSize = typography.caption.fontSize,
                        fontWeight = FontWeight.Black,
                    )
                } else {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                    )
                }
            }
            Column {
                Text(
                    text = channel.name,
                    color = colors.content,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                channel.currentProgrammeTitle?.let {
                    Text(
                        text = it,
                        color = colors.secondaryContent,
                        fontSize = typography.caption.fontSize,
                        lineHeight = typography.caption.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                channel.progressAt(now)?.let { fraction ->
                    Spacer(Modifier.height(6.dp))
                    HomeProgressBar(fraction = fraction, track = colors.content.copy(alpha = 0.20f))
                }
            }
        }
    }
}

/**
 * A part-watched title. The artwork is the card; the title and how much is left
 * of it sit underneath rather than inside a second box drawn over the picture.
 */
@Composable
private fun HomeResumeCard(entry: HomeResumeEntry, onClick: () -> Unit, onLongClick: () -> Unit, onFocused: () -> Unit = {}) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    TvSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.width(HOME_CARD_WIDTH).onFocusChanged { if (it.isFocused) onFocused() },
        shape = StreamMateThemeTokens.shapes.medium,
        resting = Color.Transparent,
        restingContent = palette.textPrimary,
        focusRing = true,
        focusScale = 1f,
        testTag = "home-resume-" + entry.key,
        contentPadding = PaddingValues(4.dp),
        contentAlignment = Alignment.TopStart,
    ) { colors ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HOME_CARD_ART_HEIGHT)
                    .clip(StreamMateThemeTokens.shapes.medium)
                    .background(palette.surfaceSubtle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.title.artworkInitials(),
                    color = palette.textMuted,
                    fontSize = typography.headline.fontSize,
                    fontWeight = FontWeight.Black,
                )
                if (!entry.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (entry.fraction > 0f) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        HomeProgressBar(
                            fraction = entry.fraction,
                            track = palette.background.copy(alpha = 0.62f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.title,
                color = colors.content,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HomeCardSubtitle(text = entry.subtitleWithRemaining())
        }
    }
}

@Composable
private fun TraktHomeTitle.episodeLabel(): String? = episodeLabel(season, number, episodeTitle)

/** `S1 E2 · Title` in the interface language; null when this is not an episode. */
@Composable
private fun episodeLabel(season: Int?, number: Int?, episodeTitle: String?): String? {
    if (season == null || number == null) return null
    val label = stringResource(IptvR.string.series_episode_label, season, number)
    return episodeTitle?.takeIf(String::isNotBlank)?.let { "$label · $it" } ?: label
}

/** What the card says under the title: the episode for a series, the year for a film, Discover's own line otherwise. */
@Composable
private fun HomeResumeEntry.subtitleLabel(): String? = when (this) {
    is HomeResumeEntry.Vod -> episodeLabel(item.seasonNumber, item.episodeNumber, item.episodeTitle) ?: item.subtitle
    is HomeResumeEntry.Discover -> subtitle
}

/**
 * A Trakt title. Next-up cards are landscape, with the show's fanart and the
 * episode under it; recommendations are posters, as a shelf of new things
 * usually is. Both carry Trakt's own artwork, so they need no local copy.
 */
@Composable
private fun HomeTraktCard(title: TraktHomeTitle, landscape: Boolean, onClick: () -> Unit, onFocused: () -> Unit = {}) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val width = if (landscape) HOME_CARD_WIDTH else HOME_POSTER_CARD_WIDTH
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(width).onFocusChanged { if (it.isFocused) onFocused() },
        shape = StreamMateThemeTokens.shapes.medium,
        resting = Color.Transparent,
        restingContent = palette.textPrimary,
        focusRing = true,
        focusScale = 1f,
        testTag = "home-trakt-" + title.key,
        contentPadding = PaddingValues(4.dp),
        contentAlignment = Alignment.TopStart,
    ) { colors ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (landscape) HOME_CARD_ART_HEIGHT else HOME_POSTER_CARD_HEIGHT)
                    .clip(StreamMateThemeTokens.shapes.medium)
                    .background(palette.surfaceSubtle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.title.artworkInitials(),
                    color = palette.textMuted,
                    fontSize = typography.headline.fontSize,
                    fontWeight = FontWeight.Black,
                )
                val art = if (landscape) title.fanart ?: title.poster else title.poster ?: title.fanart
                if (!art.isNullOrBlank()) {
                    AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title.title,
                color = colors.content,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HomeCardSubtitle(text = title.episodeLabel() ?: title.year?.toString())
        }
    }
}

@Composable
private fun HomeResumeEntry.subtitleWithRemaining(): String? {
    val parts = buildList {
        subtitleLabel()?.takeIf(String::isNotBlank)?.let(::add)
        remainingLabel()?.let(::add)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(METADATA_SEPARATOR)
}

/**
 * One of today's matches. It opens SportMate rather than pretending to be a
 * stream: whether a match has a channel behind it is SportMate's question, and
 * answering it here would mean matching streams on the home screen.
 */
@Composable
private fun HomeSportCard(event: TodayEvent, onClick: () -> Unit, onFocused: () -> Unit = {}) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val live = event.status == TodayEventStatus.LIVE
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(HOME_SPORT_CARD_WIDTH).height(HOME_SPORT_CARD_HEIGHT).onFocusChanged { if (it.isFocused) onFocused() },
        shape = StreamMateThemeTokens.shapes.medium,
        resting = palette.surfaceSubtle,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        testTag = "home-sport-" + event.id,
        contentPadding = PaddingValues(12.dp),
        contentAlignment = Alignment.TopStart,
    ) { colors ->
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Box(
                        Modifier
                            .size(HOME_LIVE_DOT)
                            .clip(StreamMateThemeTokens.shapes.small)
                            .background(palette.danger),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = event.statusLabel.ifBlank { event.startLabel },
                    color = if (live) palette.danger else colors.secondaryContent,
                    fontSize = typography.caption.fontSize,
                    lineHeight = typography.caption.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeSportTeam(
                    name = event.home,
                    logoUrl = event.homeLogoUrl,
                    contentColor = colors.content,
                    secondaryColor = colors.secondaryContent,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = event.score?.takeIf(String::isNotBlank) ?: TEAM_SEPARATOR.trim(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = if (live) palette.focus else colors.content,
                    fontSize = typography.bodyLarge.fontSize,
                    lineHeight = typography.bodyLarge.lineHeight,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                HomeSportTeam(
                    name = event.away,
                    logoUrl = event.awayLogoUrl,
                    contentColor = colors.content,
                    secondaryColor = colors.secondaryContent,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = event.competition,
                color = colors.secondaryContent,
                fontSize = typography.caption.fontSize,
                lineHeight = typography.caption.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeSportTeam(
    name: String,
    logoUrl: String?,
    contentColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val typography = StreamMateThemeTokens.typography
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(HOME_SPORT_LOGO_SIZE)
                .clip(StreamMateThemeTokens.shapes.small)
                .background(contentColor.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (logoUrl.isNullOrBlank()) {
                Text(
                    text = name.artworkInitials(),
                    color = secondaryColor,
                    fontSize = typography.caption.fontSize,
                    fontWeight = FontWeight.Black,
                )
            } else {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            color = contentColor,
            fontSize = typography.caption.fontSize,
            lineHeight = typography.caption.lineHeight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The second line of a card.
 *
 * Drawn even when there is nothing to say, so a row of cards keeps one baseline
 * whether or not every title happens to carry a subtitle.
 */
@Composable
private fun HomeCardSubtitle(text: String?) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Text(
        text = text.orEmpty(),
        modifier = Modifier.alpha(if (text == null) 0f else 1f),
        color = palette.textDim,
        fontSize = typography.caption.fontSize,
        lineHeight = typography.caption.lineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HomeProgressBar(fraction: Float, track: Color) {
    val palette = StreamMateThemeTokens.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(StreamMateThemeTokens.shapes.small)
            .background(track),
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(palette.focus))
    }
}

/**
 * Up to two letters standing in for missing artwork.
 *
 * Only words that begin with a letter count. Provider titles are full of years,
 * season markers and separators, and "Ben 10" reduced to "B1" reads as a fault
 * rather than as a placeholder. A single-word title gives up its first two
 * letters instead, because one letter alone looks lost on the tile.
 */
internal fun String.artworkInitials(): String {
    val words = trim()
        .split(' ', '.', '-', ':', '_', '·', '/')
        .filter { word -> word.firstOrNull()?.isLetter() == true }
    return when (words.size) {
        0 -> ""
        1 -> words.first().take(2).uppercase()
        else -> words.take(2).map { word -> word.first().uppercaseChar() }.joinToString("")
    }
}

private const val HOME_ROW_LIMIT = 6
private const val HERO_FOCUS_SETTLE_MILLIS = 180L
private const val HERO_CROSSFADE_MILLIS = 250
private const val HERO_ART_MAX_WIDTH_PX = 1920
private const val HERO_ART_MAX_HEIGHT_PX = 1080
private val HOME_SPORT_BACKDROP_CREST = 150.dp
private val HOME_SPORT_BACKDROP_GAP = 56.dp
private const val MOVIE_CONTENT_KEY_PREFIX = "vod:movie:"
private const val METADATA_SEPARATOR = "  ·  "
private const val TEAM_SEPARATOR = " – "
private const val PROGRAMME_WINDOW_DASH = "–"

/** A literal separator between the date and the time, quoted for the formatter. */
private const val CLOCK_SEPARATOR_PATTERN = "' · '"

private const val MINUTE_MILLIS = 60_000L

/** How far the list has to move before the hero counts as left behind. */

private val HERO_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm")

private val HOME_RAIL_WIDTH = 80.dp
private val HOME_RAIL_EXPANDED_WIDTH = 244.dp
private val HOME_RAIL_ITEM_SIZE = 48.dp
private val HOME_RAIL_ITEM_PADDING = 12.dp
private val HOME_RAIL_ICON_SIZE = 24.dp
private val HOME_RAIL_LABEL_GAP = 14.dp

private val HOME_ROW_GAP = 26.dp
/** The hero's share of the screen; the rows have the rest and scroll under it. */
private const val HOME_HERO_FRACTION = 0.46f
/** The artwork reaches a little past the hero so its fade lands on the first row's ground. */
private const val HOME_HERO_ART_FRACTION_HEIGHT = 0.56f
private const val HOME_HERO_ART_FRACTION = 0.66f
/** Room under the last row, so it too can be pulled up to the focus line. */
private val HOME_ROWS_BOTTOM_SLACK = 260.dp
private const val HOME_HERO_TEXT_FRACTION = 0.56f
private val HOME_HERO_PROGRESS_WIDTH = 210.dp
private val HOME_HERO_BUTTON_HEIGHT = 46.dp
private val HOME_LIVE_DOT = 8.dp

private val HOME_CHANNEL_CARD_WIDTH = 168.dp
private val HOME_CHANNEL_CARD_HEIGHT = 104.dp
private val HOME_CHANNEL_LOGO_SIZE = 34.dp
private val HOME_CARD_WIDTH = 186.dp
private val HOME_CARD_ART_HEIGHT = 102.dp
private val HOME_POSTER_CARD_WIDTH = 124.dp
private val HOME_POSTER_CARD_HEIGHT = 178.dp
private val HOME_SPORT_CARD_WIDTH = 244.dp
private val HOME_SPORT_CARD_HEIGHT = 160.dp
private val HOME_SPORT_LOGO_SIZE = 38.dp

/**
 * Slack at the ends of the home rows.
 *
 * A focused card lifts beyond its slot, so without this the first and last
 * cards have their lift shaved off by the screen edge, and the last one cannot
 * scroll clear of it.
 */
private val HOME_ROW_EDGE_SLACK = PaddingValues(horizontal = 4.dp)
