package com.streammate.tv.feature.home

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.streammate.tv.R
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.InheritedFocusScrollBehavior
import com.streammate.tv.feature.common.KeepFocusedChildVisibleLazyColumn
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.common.scrollsToTopWhenFocused
import com.streammate.tv.feature.common.tickerFlow
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.ContinueWatchingItem
import com.streammate.tv.iptv.repository.GuideChannel
import com.streammate.tv.iptv.repository.GuideRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

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
    onPlayChannel: (String) -> Unit,
    onPlayVod: (String, Long) -> Unit,
) {
    val spacing = StreamMateThemeTokens.spacing
    val heroFocus = remember { FocusRequester() }
    // The rail sits over the rows rather than beside them, so the way back out
    // of it is stated rather than searched for.
    val contentFocus = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val preferences by preferencesRepository.preferences
        .collectAsStateWithLifecycle(initialValue = AppPreferences())
    val continueWatching by remember(catalogueRepository) {
        catalogueRepository.observeContinueWatching()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Subscribed once on entry rather than against a ticking clock: Home is a
    // launch screen, and re-creating this query every minute is exactly what
    // makes the player and the guide churn.
    val guideEntryMillis = remember { System.currentTimeMillis() }
    val channels by remember(guideRepository, guideEntryMillis) {
        guideRepository.observeGuide(guideEntryMillis)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentChannels = remember(channels, preferences.recentChannelIds) {
        val positions = preferences.recentChannelIds.withIndex().associate { it.value to it.index }
        channels.filter { it.id in positions }
            .sortedBy { positions[it.id] }
            .take(HOME_ROW_LIMIT)
    }
    val resumeItems = remember(continueWatching) { continueWatching.take(HOME_ROW_LIMIT) }
    val todaysSport = remember(sportsEvents) { sportsEvents.take(HOME_ROW_LIMIT) }

    // The clock and the live progress bars are the only things here that have
    // to keep moving. One minute-long ticker drives all of them, and nothing
    // re-queries the database on it.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        tickerFlow(periodMillis = MINUTE_MILLIS, emitImmediately = false).collect {
            now = System.currentTimeMillis()
        }
    }

    val hero = rememberHomeHero(resumeItems, recentChannels, now)
    val heroPlot = rememberHeroPlot(hero, catalogueRepository)

    // The artwork belongs to the hero, so it goes when the hero does. Left
    // where it is, the rows would ride up over the bright middle of a picture
    // that is no longer about anything on screen.
    val listState = rememberLazyListState()
    val scrolledPastHero by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > HERO_ART_FADE_THRESHOLD_PX
        }
    }
    val backdropAlpha by animateFloatAsState(
        if (scrolledPastHero) 0f else 1f,
        label = "hero backdrop",
    )

    LaunchedEffect(Unit) { heroFocus.requestFocusWhenAttached() }

    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { contentModifier ->
        Box(modifier = contentModifier) {
            HomeHeroBackdrop(hero = hero, modifier = Modifier.alpha(backdropAlpha))
            KeepFocusedChildVisibleLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(contentFocus)
                    // Coming back from the rail returns to the card you left,
                    // not to the top of the screen. The hero is the first
                    // focusable here, so a restore that finds nothing to go
                    // back to lands there anyway.
                    .focusRestorer()
                    .focusGroup(),
                contentPadding = PaddingValues(
                    start = HOME_RAIL_WIDTH + spacing.lg,
                    end = spacing.xl,
                    top = spacing.xl,
                    bottom = spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(HOME_ROW_GAP),
            ) { inheritedFocusScrollBehavior ->
                item(key = "hero") {
                    Column(
                        modifier = Modifier.scrollsToTopWhenFocused(
                            offset = { if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) 0 else 1 },
                            scrollToTop = { listState.scrollToItem(0) },
                        ),
                    ) {
                        HomeHeader(timeZoneId = preferences.timeZoneId, now = now)
                        Spacer(Modifier.height(HOME_HEADER_GAP))
                        HomeHeroPanel(
                            hero = hero,
                            plot = heroPlot,
                            now = now,
                            favourite = hero.channelId()?.let { it in preferences.favouriteChannelIds },
                            focusRequester = heroFocus,
                            onPlayChannel = onPlayChannel,
                            onPlayVod = onPlayVod,
                            onLiveTv = onLiveTv,
                            onToggleFavourite = { channelId, favourite ->
                                scope.launch {
                                    preferencesRepository.setFavouriteChannel(channelId, favourite)
                                }
                            },
                        )
                    }
                }
                if (recentChannels.isNotEmpty()) {
                    item(key = "recent-channels") {
                        HomeRow(
                            title = stringResource(R.string.home_recent_channels),
                            hint = stringResource(R.string.home_rows_hint),
                            focusScrollBehavior = inheritedFocusScrollBehavior,
                        ) {
                            items(recentChannels, key = GuideChannel::id) { channel ->
                                HomeChannelCard(
                                    channel = channel,
                                    now = now,
                                    onClick = { onPlayChannel(channel.id) },
                                )
                            }
                        }
                    }
                }
                if (resumeItems.isNotEmpty()) {
                    item(key = "continue-watching") {
                        HomeRow(
                            title = stringResource(R.string.home_continue_watching),
                            focusScrollBehavior = inheritedFocusScrollBehavior,
                        ) {
                            items(resumeItems, key = ContinueWatchingItem::contentKey) { item ->
                                HomeResumeCard(
                                    item = item,
                                    onClick = {
                                        onPlayVod(item.contentKey, item.progress.resumePositionMillis)
                                    },
                                )
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
                            focusScrollBehavior = inheritedFocusScrollBehavior,
                        ) {
                            items(todaysSport, key = TodayEvent::id) { event ->
                                HomeSportCard(event = event, onClick = onSportMate)
                            }
                        }
                    }
                }
            }
            BackHandler(enabled = railFocused) { contentFocus.requestFocus() }
            HomeRail(
                contentFocus = contentFocus,
                onFocusedChange = { railFocused = it },
                onLiveTv = onLiveTv,
                onSportMate = onSportMate,
                onMovies = onMovies,
                onSeries = onSeries,
                onSearch = onSearch,
                onSettings = onSettings,
                modifier = Modifier.zIndex(1f),
            )
        }
    }
}

// ---------------------------------------------------------------- the hero --

/**
 * What the top of the screen is about.
 *
 * In priority order: something part-watched that can be resumed, then whatever
 * is on the channel last watched, and when the library is empty a plain
 * invitation into the guide. Every field of every variant is read off a stored
 * record; nothing here is composed for the look of it.
 */
private sealed interface HomeHero {
    data class Resume(val item: ContinueWatchingItem) : HomeHero

    data class Channel(val channel: GuideChannel, val live: Boolean) : HomeHero

    data object Welcome : HomeHero
}

private fun HomeHero.channelId(): String? = (this as? HomeHero.Channel)?.channel?.id

@Composable
private fun rememberHomeHero(
    resumeItems: List<ContinueWatchingItem>,
    recentChannels: List<GuideChannel>,
    now: Long,
): HomeHero = remember(resumeItems, recentChannels, now) {
    val resumable = resumeItems.firstOrNull {
        !it.progress.completed && it.progress.resumePositionMillis > 0L
    }
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

/**
 * The plot of the film in the hero, when the library already holds one.
 *
 * One suspend read of a row Home's own repository can already reach - not a
 * subscription, and not a new source. Series episodes and channels have no
 * equivalent stored text, so they go without a description rather than being
 * handed one.
 */
@Composable
private fun rememberHeroPlot(hero: HomeHero, catalogueRepository: CatalogueRepository): String? {
    val contentKey = (hero as? HomeHero.Resume)
        ?.item
        ?.contentKey
        ?.takeIf { it.startsWith(MOVIE_CONTENT_KEY_PREFIX) }
        ?: return null
    val plot by produceState<String?>(initialValue = null, contentKey, catalogueRepository) {
        value = catalogueRepository.movie(contentKey)?.plot?.takeIf(String::isNotBlank)
    }
    return plot
}

/**
 * The artwork behind the hero, buried under scrims on the side the text sits.
 *
 * A resume item brings its own poster. A channel has only a logo, which is not
 * a backdrop and would look wrong stretched across a screen, so it borrows the
 * bundled Live TV artwork the same way the empty state does.
 */
@Composable
private fun HomeHeroBackdrop(hero: HomeHero, modifier: Modifier = Modifier) {
    val palette = StreamMateThemeTokens.palette
    Box(modifier = modifier.fillMaxWidth().height(HOME_HERO_ART_HEIGHT)) {
        val poster = (hero as? HomeHero.Resume)?.item?.posterUrl?.takeIf(String::isNotBlank)
        Box(
            modifier = Modifier
                .fillMaxWidth(HOME_HERO_ART_FRACTION)
                .fillMaxHeight()
                .align(Alignment.CenterEnd),
        ) {
            // The bundled artwork is the floor rather than the alternative, so
            // there is never a bare rectangle here while a poster is still on
            // its way down, or if it never arrives.
            Image(
                painter = painterResource(R.drawable.home_backdrop_live_tv),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Two scrims, as in the reference: one across, so the text side is
        // near-black whatever the artwork does, and one down, so the rows
        // below start on clean ground rather than on a cut-off picture.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to palette.background.copy(alpha = 0.97f),
                    0.34f to palette.background.copy(alpha = 0.80f),
                    0.68f to palette.background.copy(alpha = 0.10f),
                    1f to palette.background.copy(alpha = 0.34f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to palette.background.copy(alpha = 0.55f),
                    0.24f to palette.background.copy(alpha = 0f),
                    0.84f to palette.background.copy(alpha = 0.94f),
                    1f to palette.background,
                ),
            ),
        )
    }
}

@Composable
private fun HomeHeroPanel(
    hero: HomeHero,
    plot: String?,
    now: Long,
    favourite: Boolean?,
    focusRequester: FocusRequester,
    onPlayChannel: (String) -> Unit,
    onPlayVod: (String, Long) -> Unit,
    onLiveTv: () -> Unit,
    onToggleFavourite: (String, Boolean) -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val spacing = StreamMateThemeTokens.spacing
    val locale = rememberInterfaceLocale()

    val kicker = when (hero) {
        is HomeHero.Resume -> stringResource(R.string.home_hero_resume)
        is HomeHero.Channel -> if (hero.live) {
            stringResource(R.string.home_hero_live)
        } else {
            stringResource(R.string.home_live_tv)
        }
        HomeHero.Welcome -> stringResource(R.string.home_hero_welcome)
    }
    val title = when (hero) {
        is HomeHero.Resume -> hero.item.title
        is HomeHero.Channel -> hero.channel.currentProgrammeTitle ?: hero.channel.name
        HomeHero.Welcome -> stringResource(R.string.home_live_tv)
    }
    val metadata = heroMetadata(hero)
    val description = when (hero) {
        is HomeHero.Resume -> plot
        is HomeHero.Channel -> null
        HomeHero.Welcome -> stringResource(R.string.home_live_tv_description)
    }
    val progress = when (hero) {
        is HomeHero.Resume -> hero.item.progress.fraction.takeIf { it > 0f }
        is HomeHero.Channel -> hero.channel.progressAt(now)
        HomeHero.Welcome -> null
    }

    Column(modifier = Modifier.fillMaxWidth(HOME_HERO_TEXT_FRACTION)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hero is HomeHero.Channel && hero.live) {
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
        Spacer(Modifier.height(spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            HomeHeroAction(
                label = heroPrimaryLabel(hero),
                icon = TvIcons.Play,
                primary = true,
                testTag = "home-hero-primary",
                focusRequester = focusRequester,
                onClick = {
                    when (hero) {
                        is HomeHero.Resume -> onPlayVod(
                            hero.item.contentKey,
                            hero.item.progress.resumePositionMillis,
                        )
                        is HomeHero.Channel -> onPlayChannel(hero.channel.id)
                        HomeHero.Welcome -> onLiveTv()
                    }
                },
            )
            if (hero !is HomeHero.Welcome) {
                HomeHeroAction(
                    label = stringResource(R.string.home_action_guide),
                    icon = TvIcons.Guide,
                    primary = false,
                    testTag = "home-hero-guide",
                    onClick = onLiveTv,
                )
            }
            // Only a channel has anywhere for a favourite to be kept, so only a
            // channel gets the star. Elsewhere it would be a control that looks
            // like it does something and does not.
            if (hero is HomeHero.Channel && favourite != null) {
                HomeHeroAction(
                    label = null,
                    icon = if (favourite) TvIcons.Star else TvIcons.StarOutline,
                    contentDescription = stringResource(
                        if (favourite) R.string.home_favourite_remove else R.string.home_favourite_add,
                    ),
                    primary = false,
                    testTag = "home-hero-favourite",
                    onClick = { onToggleFavourite(hero.channel.id, !favourite) },
                )
            }
        }
    }
}

/** The facts under the title, each one read off the record behind the hero. */
@Composable
private fun heroMetadata(hero: HomeHero): List<String> = when (hero) {
    is HomeHero.Resume -> buildList {
        hero.item.subtitle?.takeIf(String::isNotBlank)?.let(::add)
        hero.item.remainingLabel()?.let(::add)
    }
    is HomeHero.Channel -> buildList {
        add(hero.channel.name)
        hero.channel.currentProgrammeSubtitle?.takeIf(String::isNotBlank)?.let(::add)
        hero.channel.programmeWindowLabel()?.let(::add)
    }
    HomeHero.Welcome -> emptyList()
}

@Composable
private fun ContinueWatchingItem.remainingLabel(): String? {
    val remaining = progress.durationMillis - progress.positionMillis
    if (progress.durationMillis <= 0L || remaining <= 0L) return null
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

@Composable
private fun heroPrimaryLabel(hero: HomeHero): String = when (hero) {
    is HomeHero.Resume -> hero.item.remainingLabel()
        ?.let { stringResource(R.string.home_action_resume_remaining, it) }
        ?: stringResource(R.string.home_action_resume)
    is HomeHero.Channel -> stringResource(R.string.home_action_watch)
    HomeHero.Welcome -> stringResource(R.string.home_action_guide)
}

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
    val destinations = listOf(
        HomeDestination("live", R.string.home_live_tv, TvIcons.Guide, onLiveTv),
        HomeDestination("sportmate", R.string.home_sportmate, TvIcons.Target, onSportMate),
        HomeDestination("movies", R.string.home_movies, TvIcons.Play, onMovies),
        HomeDestination("series", R.string.home_series, TvIcons.Epg, onSeries),
        HomeDestination("search", R.string.home_search, TvIcons.Search, onSearch),
        HomeDestination("settings", R.string.home_settings, TvIcons.Settings, onSettings),
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
            ),
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
            painter = painterResource(TvIcons.Home),
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
private fun HomeChannelCard(channel: GuideChannel, now: Long, onClick: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(HOME_CHANNEL_CARD_WIDTH).height(HOME_CHANNEL_CARD_HEIGHT),
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
private fun HomeResumeCard(item: ContinueWatchingItem, onClick: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(HOME_CARD_WIDTH),
        shape = StreamMateThemeTokens.shapes.medium,
        resting = Color.Transparent,
        restingContent = palette.textPrimary,
        focusRing = true,
        focusScale = 1f,
        testTag = "home-resume-" + item.contentKey,
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
                    text = item.title.artworkInitials(),
                    color = palette.textMuted,
                    fontSize = typography.headline.fontSize,
                    fontWeight = FontWeight.Black,
                )
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (item.progress.fraction > 0f) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        HomeProgressBar(
                            fraction = item.progress.fraction,
                            track = palette.background.copy(alpha = 0.62f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.title,
                color = colors.content,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HomeCardSubtitle(text = item.subtitleWithRemaining())
        }
    }
}

@Composable
private fun ContinueWatchingItem.subtitleWithRemaining(): String? {
    val parts = buildList {
        subtitle?.takeIf(String::isNotBlank)?.let(::add)
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
private fun HomeSportCard(event: TodayEvent, onClick: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val live = event.status == TodayEventStatus.LIVE
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(HOME_SPORT_CARD_WIDTH).height(HOME_SPORT_CARD_HEIGHT),
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
private const val MOVIE_CONTENT_KEY_PREFIX = "vod:movie:"
private const val METADATA_SEPARATOR = "  ·  "
private const val TEAM_SEPARATOR = " – "
private const val PROGRAMME_WINDOW_DASH = "–"

/** A literal separator between the date and the time, quoted for the formatter. */
private const val CLOCK_SEPARATOR_PATTERN = "' · '"

private const val MINUTE_MILLIS = 60_000L

/** How far the list has to move before the hero counts as left behind. */
private const val HERO_ART_FADE_THRESHOLD_PX = 24

private val HERO_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm")

private val HOME_RAIL_WIDTH = 80.dp
private val HOME_RAIL_EXPANDED_WIDTH = 244.dp
private val HOME_RAIL_ITEM_SIZE = 48.dp
private val HOME_RAIL_ITEM_PADDING = 12.dp
private val HOME_RAIL_ICON_SIZE = 24.dp
private val HOME_RAIL_LABEL_GAP = 14.dp

private val HOME_HEADER_GAP = 26.dp
private val HOME_ROW_GAP = 26.dp
private val HOME_HERO_ART_HEIGHT = 330.dp
private const val HOME_HERO_ART_FRACTION = 0.66f
private const val HOME_HERO_TEXT_FRACTION = 0.56f
private val HOME_HERO_PROGRESS_WIDTH = 210.dp
private val HOME_HERO_BUTTON_HEIGHT = 46.dp
private val HOME_LIVE_DOT = 8.dp

private val HOME_CHANNEL_CARD_WIDTH = 168.dp
private val HOME_CHANNEL_CARD_HEIGHT = 104.dp
private val HOME_CHANNEL_LOGO_SIZE = 34.dp
private val HOME_CARD_WIDTH = 186.dp
private val HOME_CARD_ART_HEIGHT = 102.dp
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
