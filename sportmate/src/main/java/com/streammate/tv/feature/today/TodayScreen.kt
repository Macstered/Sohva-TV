package com.streammate.tv.feature.today

import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.activity.compose.BackHandler
import com.streammate.tv.feature.common.requestFocusWhenAttached
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.focusGroup
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import com.streammate.tv.feature.common.TvTagTone
import com.streammate.tv.feature.common.TvTagChip
import com.streammate.tv.core.model.StreamTagKind
import com.streammate.tv.core.model.StreamTag
import com.streammate.tv.core.model.ChannelStreamTags
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant
import com.streammate.tv.feature.common.tickerFlow
import com.streammate.tv.feature.common.TvSurface
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.FootballIncident
import com.streammate.tv.core.model.FootballIncidentKind
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.SohvaSportBrand
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.matching.ChannelMatchConfidence
import com.streammate.tv.matching.EventChannelMatch
import com.streammate.tv.matching.MatchCandidateSource
import com.streammate.tv.matching.ManualMatchDecision
import com.streammate.tv.sportmate.R

/**
 * A tab across the top.
 *
 * [icon] is a drawable or nothing. The sports had a font glyph each - a
 * pentagon for rugby, a lozenge for baseball - drawn from whatever typeface the
 * television happened to have, at whatever size it happened to give them. A
 * name reads better than a shape nobody recognises, so the sports go without
 * and only the two tabs with a real icon behind them keep one.
 */
private enum class TodayFilter(
    val labelRes: Int,
    val tag: String,
    val icon: Int? = null,
    val sport: SportType? = null,
) {
    ALL(R.string.today_filter_all, "all"),
    FOOTBALL(R.string.today_filter_football, "football", sport = SportType.FOOTBALL),
    HOCKEY(R.string.today_filter_hockey, "hockey", sport = SportType.ICE_HOCKEY),
    AFL(R.string.today_filter_afl, "afl", sport = SportType.AUSTRALIAN_FOOTBALL),
    BASKETBALL(R.string.today_filter_basketball, "basketball", sport = SportType.BASKETBALL),
    BASEBALL(R.string.today_filter_baseball, "baseball", sport = SportType.BASEBALL),
    HANDBALL(R.string.today_filter_handball, "handball", sport = SportType.HANDBALL),
    RUGBY(R.string.today_filter_rugby, "rugby", sport = SportType.RUGBY),
    VOLLEYBALL(R.string.today_filter_volleyball, "volleyball", sport = SportType.VOLLEYBALL),
    WATCHABLE(R.string.today_filter_watchable, "watchable", icon = TvIcons.Play),
    FAVOURITES(R.string.today_filter_favourites, "favourites", icon = TvIcons.StarOutline),
}

@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onRefresh: () -> Unit,
    onLoadDetails: (String) -> Unit,
    onRefreshDetails: (String) -> Unit,
    onMatchDecision: (String, String, ManualMatchDecision?) -> Unit,
    onGuide: () -> Unit,
    onSettings: () -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(TodayFilter.ALL) }
    var selectedEventId by rememberSaveable { mutableStateOf<String?>(null) }
    val fallbackFocusRequester = remember { FocusRequester() }
    val eventFocusRequester = remember { FocusRequester() }
    val availableFilters = remember(uiState.followedSports) {
        TodayFilter.entries.filter { filter -> filter.sport == null || filter.sport in uiState.followedSports }
    }
    val visibleEvents = remember(selectedFilter, uiState.events) {
        TodayEventOrdering.sort(uiState.events.filterFor(selectedFilter))
    }
    // Grouped away from the composable, so which heading a match sits under can
    // be checked without a television.
    val sections = remember(visibleEvents) { TodaySections.of(visibleEvents) }
    val liveEvents = sections.live
    val upcomingEvents = sections.upcoming
    val finishedEvents = sections.finished
    val firstFocusEventId = sections.firstFocusEventId
    // What every tab would show if it were selected. Counted once per change of
    // the event list rather than on every D-pad press through the strip.
    val filterCounts = remember(uiState.events, availableFilters) {
        availableFilters.associateWith { filter -> uiState.events.filterFor(filter).size }
    }
    val sportsChannels = remember(visibleEvents, uiState.matches) {
        todaySportsChannels(visibleEvents, uiState.matches)
    }

    LaunchedEffect(availableFilters, selectedFilter) {
        if (selectedFilter !in availableFilters) selectedFilter = TodayFilter.ALL
    }
    LaunchedEffect(firstFocusEventId) {
        if (firstFocusEventId != null) {
            eventFocusRequester.requestFocus()
        } else {
            fallbackFocusRequester.requestFocus()
        }
    }

    val selectedEvent = uiState.events.firstOrNull { it.id == selectedEventId }
    val hubOpen = selectedEvent != null
    // The panel needs something to draw while it slides back out, so the last
    // opened match outlives the selection by one animation.
    var lastHubEvent by remember { mutableStateOf<TodayEvent?>(null) }
    LaunchedEffect(selectedEvent?.id) {
        val event = selectedEvent ?: return@LaunchedEffect
        lastHubEvent = event
        if (event.detailsAvailable) onLoadDetails(event.id)
    }
    val hubFocus = remember { FocusRequester() }
    val hubFallbackFocus = remember { FocusRequester() }
    LaunchedEffect(hubOpen) {
        if (!hubOpen) return@LaunchedEffect
        // A dialog window used to hand focus to whatever it found. In the screen
        // nothing does, so if the preferred target is not there - no matches, or
        // none of them watchable yet - focus has to be placed somewhere it
        // certainly is, or the hub opens with nothing highlighted and the remote
        // does nothing at all.
        // Generous, because the hub arrives with an animation and the streams
        // list has to lay out its first row before anything in it can be
        // focused.
        if (!hubFocus.requestFocusWhenAttached(attempts = HUB_FOCUS_ATTEMPTS)) {
            hubFallbackFocus.requestFocusWhenAttached(attempts = HUB_FOCUS_ATTEMPTS)
        }
    }
    BackHandler(enabled = hubOpen) { selectedEventId = null }

    StreamMateScreenBackground(modifier = modifier) { contentModifier ->
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = contentModifier
                // A remote has no "click outside". While the hub is open the
                // list behind it must be unreachable, or one press in the wrong
                // direction strands the viewer on a screen they cannot see.
                //
                // Applied only while it is open. Blocking a focus group means
                // canFocus = false on the group itself; leaving canFocus = true
                // there the rest of the time does the opposite of nothing - it
                // turns the group into a focus target, so focus lands on the
                // Column and the whole screen stops responding to the D-pad.
                .then(
                    if (hubOpen) {
                        Modifier.focusProperties { canFocus = false }.focusGroup()
                    } else {
                        Modifier
                    },
                ),
        ) {
            // The date sits beside the wordmark and the headline is gone: it
            // said "today's sports" on a screen that only ever shows today's
            // sports, and it cost a row of matches to say it.
            Header(
                timeZoneId = uiState.timeZoneId,
                onRefresh = onRefresh,
                onGuide = onGuide,
                onSettings = onSettings,
            )
            Spacer(Modifier.height(20.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(availableFilters, key = { it.tag }) { filter ->
                    FilterTab(
                        filter = filter,
                        count = filterCounts[filter] ?: 0,
                        selected = filter == selectedFilter,
                        focusRequester = if (filter == TodayFilter.ALL) fallbackFocusRequester else null,
                        onClick = { selectedFilter = filter },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp),
            ) {
                when {
                    uiState.isLoading && uiState.events.isEmpty() -> {
                        TodayMessageState(
                            title = stringResource(R.string.today_loading),
                            detail = stringResource(R.string.today_connecting),
                        )
                    }
                    uiState.error != null && uiState.events.isEmpty() -> {
                        TodayMessageState(
                            title = uiState.error.localizedMessage(),
                            detail = stringResource(R.string.today_error_help),
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = onRefresh,
                        )
                    }
                    visibleEvents.isEmpty() -> {
                        EmptyFilterState(selectedFilter)
                    }
                    else -> {
                        // Cards for what is on now and what has just ended -
                        // those carry a score worth the room. What has yet to
                        // start is a time and a matchup, which fits a row.
                        if (liveEvents.isNotEmpty()) {
                            EventCardSection(
                                title = stringResource(R.string.today_section_live),
                                events = liveEvents,
                                matches = uiState.matches,
                                focusEventId = firstFocusEventId,
                                eventFocusRequester = eventFocusRequester,
                                onEventClick = { selectedEventId = it },
                            )
                            Spacer(Modifier.height(SECTION_GAP))
                        }
                        if (upcomingEvents.isNotEmpty()) {
                            CompactEventSection(
                                title = stringResource(R.string.today_section_later),
                                hint = stringResource(R.string.today_section_later_help),
                                events = upcomingEvents,
                                matches = uiState.matches,
                                focusEventId = firstFocusEventId,
                                eventFocusRequester = eventFocusRequester,
                                onEventClick = { selectedEventId = it },
                            )
                            Spacer(Modifier.height(SECTION_GAP))
                        }
                        if (sportsChannels.isNotEmpty()) {
                            SportsChannelSection(
                                channels = sportsChannels,
                                timeZoneId = uiState.timeZoneId,
                                onPlay = onPlay,
                            )
                            Spacer(Modifier.height(SECTION_GAP))
                        }
                        if (finishedEvents.isNotEmpty()) {
                            EventCardSection(
                                title = stringResource(R.string.today_section_finished),
                                events = finishedEvents,
                                matches = uiState.matches,
                                focusEventId = firstFocusEventId,
                                eventFocusRequester = eventFocusRequester,
                                onEventClick = { selectedEventId = it },
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = hubOpen,
            enter = fadeIn() + slideInHorizontally { width -> width / 5 },
            exit = fadeOut() + slideOutHorizontally { width -> width / 5 },
        ) {
            lastHubEvent?.let { event ->
                MatchHub(
                    event = event,
                    detailsState = uiState.eventDetails[event.id] ?: EventDetailsUiState(),
                    matches = uiState.matches[event.id].orEmpty(),
                    focusRequester = hubFocus,
                    fallbackFocusRequester = hubFallbackFocus,
                    onDismiss = { selectedEventId = null },
                    onRefreshDetails = { onRefreshDetails(event.id) },
                    onPlay = onPlay,
                    onDecision = { channelId, decision ->
                        onMatchDecision(event.id, channelId, decision)
                    },
                )
            }
        }
      }
    }
}

/**
 * The wordmark, the day, and the three things that are not a match.
 *
 * The actions are icons: their labels were three words of uppercase across the
 * top of a screen whose subject is elsewhere. Each carries the description a
 * screen reader and a test read instead.
 */
@Composable
private fun Header(
    timeZoneId: String,
    onRefresh: () -> Unit,
    onGuide: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        tickerFlow(periodMillis = CLOCK_TICK_MILLIS, emitImmediately = false).collect {
            now = System.currentTimeMillis()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SohvaSportBrand(modifier = Modifier.testTag("today-brand"))
        Spacer(Modifier.width(18.dp))
        Text(
            text = rememberTodayClockLabel(timeZoneId, now),
            modifier = Modifier.weight(1f).testTag("today-clock"),
            color = palette.textDim,
            fontSize = typography.body.fontSize,
            lineHeight = typography.body.lineHeight,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TodayIconAction(
                icon = TvIcons.Refresh,
                description = stringResource(R.string.today_refresh_description),
                onClick = onRefresh,
                testTag = "nav-refresh",
            )
            TodayIconAction(
                icon = TvIcons.Guide,
                description = stringResource(R.string.today_guide_description),
                onClick = onGuide,
                testTag = "nav-guide",
            )
            TodayIconAction(
                icon = TvIcons.Settings,
                description = stringResource(R.string.today_settings_description),
                onClick = onSettings,
                testTag = "nav-settings",
            )
        }
    }
}

/** A square icon button: borderless at rest, off-white fill on focus. */
@Composable
private fun TodayIconAction(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val palette = StreamMateThemeTokens.palette
    TvSurface(
        onClick = onClick,
        modifier = Modifier.size(ICON_ACTION_SIZE).semantics { contentDescription = description },
        shape = StreamMateThemeTokens.shapes.small,
        resting = palette.surfaceSubtle,
        restingContent = palette.textMuted,
        focusScale = 1f,
        testTag = testTag,
        contentAlignment = Alignment.Center,
    ) { colors ->
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.content),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Weekday, date and time in the interface language and the configured zone.
 *
 * The pattern comes from the platform rather than being written out here: a
 * hard-coded `EEEE d.M.` is Finnish word order spelled in Latin letters.
 */
@Composable
private fun rememberTodayClockLabel(timeZoneId: String, now: Long): String {
    val context = LocalContext.current
    val tag = ComposeLocale.current.toLanguageTag()
    val locale = remember(tag) { Locale.forLanguageTag(tag) }
    val twentyFourHour = DateFormat.is24HourFormat(context)
    val formatter = remember(locale, twentyFourHour) {
        val date = DateFormat.getBestDateTimePattern(locale, "EEEEdMMM")
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
 * A tab, with how many matches sit behind it.
 *
 * Selection is the orange rule under the label; focus is the fill flip every
 * other control in the app uses. The two are never the same signal, so a tab
 * someone is merely passing over never looks chosen.
 */
@Composable
private fun FilterTab(
    filter: TodayFilter,
    count: Int,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var focused by remember { mutableStateOf(false) }
    val focusModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    val background by animateColorAsState(
        targetValue = if (focused) palette.textPrimary else Color.Transparent,
        label = "filter background",
    )
    val labelColor = when {
        focused -> palette.background
        selected -> palette.textPrimary
        else -> palette.textMuted
    }
    Column(
        modifier = Modifier
            .then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(StreamMateThemeTokens.shapes.small)
            .background(background)
            // No press or focus wash: the fill is what says "focused", and the
            // default indication would draw outside it.
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .focusable()
            .testTag("filter-${filter.tag}")
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            filter.icon?.let { icon ->
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(labelColor),
                    modifier = Modifier.size(16.dp).clearAndSetSemantics { },
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(filter.labelRes),
                color = labelColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = typography.bodyLarge.fontSize,
                lineHeight = typography.bodyLarge.lineHeight,
                maxLines = 1,
            )
            // The real number behind the tab, not a decoration: an empty tab
            // says so before anyone selects it.
            Spacer(Modifier.width(7.dp))
            Text(
                text = count.toString(),
                color = if (focused) palette.background.copy(alpha = 0.62f) else palette.textDim,
                fontWeight = FontWeight.Bold,
                fontSize = typography.caption.fontSize,
                lineHeight = typography.caption.lineHeight,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(StreamMateThemeTokens.shapes.small)
                .background(
                    when {
                        !selected -> Color.Transparent
                        focused -> palette.background
                        else -> palette.accent
                    },
                ),
        )
    }
}

/** A section heading: what it is, and how much of it there is. */
@Composable
private fun SectionTitle(title: String, hint: String) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = typography.headline.fontSize,
            lineHeight = typography.headline.lineHeight,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = hint,
            color = palette.textDim,
            fontSize = typography.label.fontSize,
            lineHeight = typography.label.lineHeight,
        )
    }
}

@Composable
private fun EventCardSection(
    title: String,
    events: List<TodayEvent>,
    matches: Map<String, List<EventChannelMatch>>,
    focusEventId: String?,
    eventFocusRequester: FocusRequester,
    onEventClick: (String) -> Unit,
) {
    SectionTitle(
        title = title,
        hint = pluralStringResource(R.plurals.today_match_count, events.size, events.size),
    )
    Spacer(Modifier.height(12.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(events, key = TodayEvent::id) { event ->
            EventCard(
                event = event,
                matches = matches[event.id].orEmpty(),
                focusRequester = if (event.id == focusEventId) eventFocusRequester else null,
                onClick = { onEventClick(event.id) },
            )
        }
    }
}

@Composable
private fun CompactEventSection(
    title: String,
    hint: String,
    events: List<TodayEvent>,
    matches: Map<String, List<EventChannelMatch>>,
    focusEventId: String?,
    eventFocusRequester: FocusRequester,
    onEventClick: (String) -> Unit,
) {
    SectionTitle(title = title, hint = hint)
    Spacer(Modifier.height(12.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(events, key = TodayEvent::id) { event ->
            EventCard(
                event = event,
                matches = matches[event.id].orEmpty(),
                focusRequester = if (event.id == focusEventId) eventFocusRequester else null,
                onClick = { onEventClick(event.id) },
            )
        }
    }
}

/**
 * The channels carrying today's sport, read off the matches the cards above
 * already count. There is no separate list of sports channels in this app's
 * state, so this is a view of the same data rather than a second source.
 */
@Composable
private fun SportsChannelSection(
    channels: List<TodaySportsChannel>,
    timeZoneId: String,
    onPlay: (String) -> Unit,
) {
    SectionTitle(
        title = stringResource(R.string.today_sports_channels),
        hint = stringResource(R.string.today_sports_channels_help),
    )
    Spacer(Modifier.height(12.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(channels, key = TodaySportsChannel::channelId) { channel ->
            SportsChannelRow(
                channel = channel,
                timeZoneId = timeZoneId,
                onClick = { onPlay(channel.channelId) },
            )
        }
    }
}

@Composable
private fun SportsChannelRow(
    channel: TodaySportsChannel,
    timeZoneId: String,
    onClick: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    // Was a painted row with nothing focusable in it, so the D-pad could not
    // reach this section at all: readable from across the room and impossible
    // to open. It names a channel, so pressing it puts that channel on.
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(COMPACT_ROW_WIDTH).height(COMPACT_ROW_HEIGHT),
        shape = StreamMateThemeTokens.shapes.medium,
        resting = palette.surfaceSubtle,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        testTag = "sports-channel-" + channel.channelId,
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) { colors ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.programmeTitle,
                    color = colors.content,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatChannelStart(channel.programmeStartEpochMillis, timeZoneId) +
                        CHANNEL_SEPARATOR + channel.channelName,
                    modifier = Modifier.padding(top = 2.dp),
                    color = colors.secondaryContent,
                    fontSize = typography.caption.fontSize,
                    lineHeight = typography.caption.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (channel.live) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.today_channel_live).uppercase(),
                    color = if (colors.content == palette.background) {
                        palette.background
                    } else {
                        palette.danger
                    },
                    fontSize = typography.caption.fontSize,
                    lineHeight = typography.caption.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatChannelStart(epochMillis: Long, timeZoneId: String): String {
    val zone = runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
    return CHANNEL_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

/**
 * A match, as a card.
 *
 * League and status across the top, the two sides with the score or the kick-off
 * between them, and along the foot the one thing that can be done about it.
 * Borderless at rest; focus flips the whole card to the off-white fill and its
 * call to action to near-black, which is the same rule every other surface in
 * the app follows.
 *
 * Every value on it comes off the event or off a match the matcher made: the
 * league, the names, the score, the minute, the number of channels. Nothing
 * here is composed to fill the shape.
 */
@Composable
private fun EventCard(
    event: TodayEvent,
    matches: List<EventChannelMatch>,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var focused by remember { mutableStateOf(false) }
    val watchable = event.matchingChannels > 0
    val possible = matches.count { it.confidence == ChannelMatchConfidence.POSSIBLE }
    val live = event.status == TodayEventStatus.LIVE
    val accent = event.sport.cardAccent()
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "event scale")
    val focusModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    val background by animateColorAsState(
        if (focused) palette.textPrimary else palette.surfaceSubtle,
        label = "event background",
    )
    val content = if (focused) palette.background else palette.textPrimary
    val secondary = if (focused) palette.background.copy(alpha = 0.62f) else palette.textDim

    Column(
        modifier = Modifier
            .then(focusModifier)
            .width(EVENT_CARD_WIDTH)
            .height(EVENT_CARD_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .shadow(
                elevation = if (focused) 16.dp else 0.dp,
                shape = StreamMateThemeTokens.shapes.large,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(StreamMateThemeTokens.shapes.large)
            // One meaning per property: the fill says "focused", the status
            // pill says "live", and the call to action says "watchable".
            .background(background)
            // No press or focus wash: the fill is what says "focused", and the
            // default indication would draw at the unlifted bounds.
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable()
            // The lift sits inside the focus target on purpose. A layer applied
            // around it enlarges the bounds the component reports, so the row
            // scrolls to fit the newly focused card and every sibling shifts.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("event-${event.id}")
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            event.competitionLogoUrl?.let { logoUrl ->
                AsyncImage(
                    model = logoUrl,
                    contentDescription = stringResource(
                        R.string.competition_logo_description,
                        event.competition,
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(StreamMateThemeTokens.shapes.small),
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = event.competition.uppercase(),
                modifier = Modifier.weight(1f),
                color = secondary,
                fontSize = typography.caption.fontSize,
                lineHeight = typography.caption.lineHeight,
                fontWeight = FontWeight.Bold,
                letterSpacing = typography.overline.letterSpacing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            StatusBadge(event = event, onFocusedCard = focused)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamMark(
                name = event.home,
                logoUrl = event.homeLogoUrl,
                accent = accent,
                onFocusedCard = focused,
                modifier = Modifier.weight(1f),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(
                    text = event.score ?: event.startLabel,
                    color = when {
                        focused -> palette.background
                        live -> palette.focus
                        else -> palette.textPrimary
                    },
                    fontSize = typography.title.fontSize,
                    lineHeight = typography.title.lineHeight,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                // Goals and behinds for AFL; nothing for the sports whose
                // score says it all.
                event.scoreDetail?.let { detail ->
                    Text(
                        text = detail,
                        color = if (focused) palette.background else secondary,
                        fontSize = typography.caption.fontSize,
                        lineHeight = typography.caption.lineHeight,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                // The minute, the period, or whatever the provider calls the
                // state of play. Left out when it says nothing.
                event.statusLabel.takeIf(String::isNotBlank)?.let { label ->
                    Text(
                        text = label,
                        modifier = Modifier.padding(top = 3.dp),
                        color = secondary,
                        fontSize = typography.caption.fontSize,
                        lineHeight = typography.caption.lineHeight,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            TeamMark(
                name = event.away,
                logoUrl = event.awayLogoUrl,
                accent = accent,
                onFocusedCard = focused,
                modifier = Modifier.weight(1f),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EVENT_CTA_HEIGHT)
                    .clip(StreamMateThemeTokens.shapes.small)
                    .background(
                        when {
                            focused -> palette.background
                            watchable || possible > 0 -> palette.surface
                            else -> Color.Transparent
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        watchable -> pluralStringResource(
                            R.plurals.today_watch_channels,
                            event.matchingChannels,
                            event.matchingChannels,
                        )
                        possible > 0 -> pluralStringResource(
                            R.plurals.today_possible_channels,
                            possible,
                            possible,
                        )
                        else -> stringResource(R.string.today_no_broadcast)
                    },
                    color = when {
                        focused -> palette.textPrimary
                        watchable -> palette.focus
                        possible > 0 -> palette.textPrimary
                        else -> palette.textDim
                    },
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
            if (event.isFavourite) {
                Spacer(Modifier.width(10.dp))
                Image(
                    painter = painterResource(TvIcons.Star),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(if (focused) palette.background else palette.accent),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * A sport's own colour, kept local to this screen: it separates one card from
 * the next in a mixed list and means nothing anywhere else in the app.
 */
@Composable
private fun SportType.cardAccent(): Color {
    val palette = StreamMateThemeTokens.palette
    return when (this) {
        SportType.ICE_HOCKEY -> palette.focus
        SportType.FOOTBALL -> palette.focus
        SportType.AUSTRALIAN_FOOTBALL -> Color(0xFFE959FF)
        SportType.BASKETBALL -> Color(0xFFFF9A3C)
        SportType.BASEBALL -> Color(0xFFFF647C)
        SportType.HANDBALL -> Color(0xFFFFC857)
        SportType.RUGBY -> Color(0xFF56D68B)
        SportType.VOLLEYBALL -> Color(0xFF6C9DFF)
    }
}

@Composable
private fun TeamMark(
    name: String,
    logoUrl: String?,
    accent: Color,
    onFocusedCard: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(TEAM_CREST_SIZE)
                .clip(CircleShape)
                .background(
                    if (onFocusedCard) palette.background.copy(alpha = 0.12f) else palette.surface,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.teamInitials,
                color = if (onFocusedCard) palette.background else accent,
                fontSize = typography.caption.fontSize,
                fontWeight = FontWeight.Black,
            )
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = stringResource(R.string.team_logo_description, name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            color = if (onFocusedCard) palette.background else palette.textPrimary,
            fontSize = typography.label.fontSize,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val String.teamInitials: String
    get() = trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "?" }

@Composable
private fun MatchHub(
    event: TodayEvent,
    detailsState: EventDetailsUiState,
    matches: List<EventChannelMatch>,
    focusRequester: FocusRequester,
    fallbackFocusRequester: FocusRequester,
    onDismiss: () -> Unit,
    onRefreshDetails: () -> Unit,
    onPlay: (String) -> Unit,
    onDecision: (String, ManualMatchDecision?) -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val availableStreamCount = matches.count { it.confidence == ChannelMatchConfidence.AVAILABLE }
    val availableStreamLabel = pluralStringResource(
        R.plurals.today_available_streams,
        availableStreamCount,
        availableStreamCount,
    )
    // Part of the screen rather than a Dialog window. A dialog owns its own
    // window and its own focus root, which on a remote means the hub and the
    // list behind it are two places focus can live; keeping the hub in the same
    // composition leaves one.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.94f))
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(palette.surfaceSubtle, palette.surfaceRaised, palette.background),
                            radius = 1050f,
                        ),
                    )
                    .padding(22.dp),
            ) {
                MatchHeroHeader(
                    event = event,
                    availableStreamLabel = availableStreamLabel,
                    focusRequester = fallbackFocusRequester,
                    onDismiss = onDismiss,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IncidentPanel(
                        event = event,
                        state = detailsState,
                        onRefresh = onRefreshDetails,
                        modifier = Modifier.weight(1.12f).fillMaxHeight(),
                    )
                    GuideMatchesPanel(
                        matches = matches,
                        onPlay = onPlay,
                        onDecision = onDecision,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(0.88f).fillMaxHeight(),
                    )
                }
            }
    }
}

@Composable
private fun MatchHeroHeader(
    event: TodayEvent,
    availableStreamLabel: String,
    focusRequester: FocusRequester,
    onDismiss: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(205.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                event.competitionLogoUrl?.let { logoUrl ->
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = stringResource(
                            R.string.competition_logo_description,
                            event.competition,
                        ),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(58.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = event.competition.uppercase(),
                        color = palette.focus,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusBadge(event)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = stringResource(R.string.today_start_and_streams, event.startLabel, availableStreamLabel),
                color = palette.textMuted,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroTeam(event.home, event.homeLogoUrl)
            Column(
                modifier = Modifier.padding(horizontal = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = event.score ?: "–",
                    color = if (event.status == TodayEventStatus.LIVE) palette.danger else palette.textPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                event.scoreDetail?.let { detail ->
                    Text(
                        text = detail,
                        color = palette.textMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            HeroTeam(event.away, event.awayLogoUrl)
        }
        Column(
            modifier = Modifier.width(120.dp),
            horizontalAlignment = Alignment.End,
        ) {
            TvActionButton(
                label = stringResource(R.string.action_close),
                icon = TvIcons.Close,
                onClick = onDismiss,
                testTag = "match-close",
                // The one control the hub always has, and so the one place
                // focus can always be put.
                focusRequester = focusRequester,
                compact = true,
            )
        }
    }
}

@Composable
private fun HeroTeam(name: String, logoUrl: String?) {
    Column(
        modifier = Modifier.width(145.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompactTeamLogo(name, logoUrl, large = true)
        Spacer(Modifier.height(5.dp))
        Text(
            text = name,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactTeamLogo(name: String, logoUrl: String?, large: Boolean = false) {
    val palette = StreamMateThemeTokens.palette
    Box(
        modifier = Modifier
            .size(if (large) 56.dp else 38.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .border(1.dp, palette.outline.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.teamInitials,
            color = palette.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
        logoUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = stringResource(R.string.team_logo_description, name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (large) 48.dp else 30.dp),
            )
        }
    }
}

@Composable
private fun IncidentPanel(
    event: TodayEvent,
    state: EventDetailsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    Column(
        modifier = modifier
            .testTag("incident-panel")
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(TvIcons.Target),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(palette.focus),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.incidents_title),
                        color = palette.focus,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (event.sport == SportType.FOOTBALL) {
                            stringResource(R.string.incidents_football_subtitle)
                        } else {
                            stringResource(R.string.incidents_other_subtitle)
                        },
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            if (event.detailsAvailable) {
                TvActionButton(
                    label = stringResource(R.string.action_refresh),
                    icon = TvIcons.Refresh,
                    onClick = onRefresh,
                    testTag = "details-refresh",
                    compact = true,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                !event.detailsAvailable -> PanelMessage(stringResource(R.string.incidents_unavailable))
                state.isLoading && state.incidents.isEmpty() -> PanelMessage(
                    stringResource(R.string.incidents_loading),
                )
                state.error != null && state.incidents.isEmpty() -> Column {
                    Text(text = state.error.localizedMessage(), color = palette.textMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    TvActionButton(
                        label = stringResource(R.string.action_retry),
                        onClick = onRefresh,
                        testTag = "details-retry",
                        compact = true,
                    )
                }
                state.isLoaded && state.incidents.isEmpty() -> PanelMessage(
                    stringResource(R.string.incidents_empty),
                )
                else -> Column {
                    IncidentTimelineBand(event = event, incidents = state.incidents)
                    Spacer(Modifier.height(10.dp))
                    state.error?.let {
                        Text(
                            text = stringResource(R.string.showing_cached_data, it.localizedMessage()),
                            color = palette.accent,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    IncidentList(state.incidents)
                }
            }
        }
    }
}

/**
 * The shape of the match at a glance: every incident placed along one axis,
 * home above the line and away below it.
 *
 * The list underneath still carries who and what. This band answers the
 * question a list is bad at - when did it turn, and who has been on top - in
 * one look, which is the only kind of reading that works from a sofa.
 */
@Composable
private fun IncidentTimelineBand(
    event: TodayEvent,
    incidents: List<FootballIncident>,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val layout = remember(incidents, event.home, event.away) {
        IncidentTimeline.layout(incidents, event.home, event.away)
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(TIMELINE_BAND_HEIGHT)) {
        val bandWidth = maxWidth
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(palette.divider),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = bandWidth * layout.halfTimePosition)
                .width(1.dp)
                .height(26.dp)
                .background(palette.divider),
        )
        layout.markers.forEach { marker ->
            val accent = marker.incident.kind.accent()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(
                        when (marker.side) {
                            TimelineSide.HOME -> Alignment.TopStart
                            TimelineSide.AWAY -> Alignment.BottomStart
                            TimelineSide.NEUTRAL -> Alignment.CenterStart
                        },
                    )
                    // Half the marker width, so it is centred on its minute
                    // rather than starting at it.
                    .offset(x = bandWidth * marker.position - (TIMELINE_MARKER_WIDTH / 2)),
            ) {
                if (marker.side == TimelineSide.AWAY) {
                    Box(Modifier.size(9.dp).background(accent, CircleShape))
                }
                Text(
                    text = marker.incident.timeLabel,
                    color = accent,
                    fontSize = StreamMateThemeTokens.typography.caption.fontSize,
                    lineHeight = StreamMateThemeTokens.typography.caption.lineHeight,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.width(TIMELINE_MARKER_WIDTH),
                    textAlign = TextAlign.Center,
                )
                if (marker.side != TimelineSide.AWAY) {
                    Box(Modifier.size(9.dp).background(accent, CircleShape))
                }
            }
        }
    }
}

/** One colour per kind of thing that happens, shared by the band and the list. */
@Composable
private fun FootballIncidentKind.accent(): Color {
    val palette = StreamMateThemeTokens.palette
    return when (this) {
        FootballIncidentKind.GOAL -> palette.focus
        FootballIncidentKind.CARD -> palette.accent
        FootballIncidentKind.SUBSTITUTION -> Color(0xFF8EA7FF)
        FootballIncidentKind.VAR -> Color(0xFFE959FF)
        FootballIncidentKind.OTHER -> palette.textMuted
    }
}

/**
 * The incident list, scrollable from a remote.
 *
 * Nothing in this list is actionable, so nothing in it was focusable, so the
 * D-pad had nothing to move to and the list could not be scrolled - a match
 * with more incidents than fit simply hid the rest. The list itself takes focus
 * instead, and up and down scroll it.
 *
 * A press is only swallowed while there is somewhere left to go. At either end
 * it passes through, so focus leaves the panel rather than trapping the viewer
 * in a list they have finished reading.
 */
@Composable
private fun IncidentList(incidents: List<FootballIncident>) {
    val palette = StreamMateThemeTokens.palette
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    val step = remember(density) { with(density) { INCIDENT_SCROLL_STEP.toPx() } }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .clip(StreamMateThemeTokens.shapes.medium)
            .then(
                if (focused) {
                    Modifier.border(3.dp, palette.textPrimary, StreamMateThemeTokens.shapes.medium)
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                val direction = when (keyEvent.key) {
                    Key.DirectionDown -> 1f
                    Key.DirectionUp -> -1f
                    else -> return@onKeyEvent false
                }
                val roomToMove =
                    if (direction > 0) listState.canScrollForward else listState.canScrollBackward
                if (!roomToMove) return@onKeyEvent false
                scope.launch { listState.animateScrollBy(direction * step) }
                true
            }
            .focusable()
            .testTag("incident-list"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(incidents, key = FootballIncident::id) { incident ->
            IncidentRow(incident)
        }
    }
}

@Composable
private fun IncidentRow(incident: FootballIncident) {
    val palette = StreamMateThemeTokens.palette
    val accent = incident.kind.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceSubtle)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = incident.timeLabel,
            color = accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(54.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = incident.localizedPrimaryLabel(),
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = listOfNotNull(incident.kind.localizedLabel(), incident.detail, incident.teamName)
                    .joinToString(" · "),
                color = palette.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun GuideMatchesPanel(
    matches: List<EventChannelMatch>,
    onPlay: (String) -> Unit,
    onDecision: (String, ManualMatchDecision?) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val availableCount = matches.count { it.confidence == ChannelMatchConfidence.AVAILABLE }
    Column(
        modifier = modifier
            .testTag("streams-panel")
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(TvIcons.Play),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(palette.accent),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.streams_title),
                        color = palette.accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.streams_subtitle),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            Text(
                text = stringResource(R.string.streams_available_count, availableCount),
                color = if (availableCount > 0) palette.focus else palette.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (matches.isEmpty()) {
                PanelMessage(
                    stringResource(R.string.streams_empty),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("streams-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        matches,
                        key = { _, match -> "${match.channelId}:${match.programmeId}" },
                    ) { index, match ->
                        MatchOptionRow(
                            match = match,
                            onPlay = onPlay,
                            onDecision = onDecision,
                            // Opening the hub should land on the first stream,
                            // because watching one is why it was opened.
                            focusRequester = focusRequester.takeIf { index == 0 },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchOptionRow(
    match: EventChannelMatch,
    onPlay: (String) -> Unit,
    onDecision: (String, ManualMatchDecision?) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val statusColor = when (match.confidence) {
        ChannelMatchConfidence.AVAILABLE -> palette.focus
        ChannelMatchConfidence.POSSIBLE -> palette.accent
        ChannelMatchConfidence.REJECTED -> palette.textMuted
    }
    // Which control this row leads with. Watch is only drawn for a match that
    // is ready to play, so pinning the requester to it left an unconfirmed or
    // rejected row with nothing focusable to hand focus to.
    val watchable = match.confidence == ChannelMatchConfidence.AVAILABLE
    val undecided = match.manualDecision == null
    var pendingDecision by remember(match.channelId) { mutableStateOf<PendingMatchDecision?>(null) }
    val leadControl = when {
        watchable -> MatchRowControl.WATCH
        undecided && match.confidence == ChannelMatchConfidence.POSSIBLE -> MatchRowControl.CONFIRM
        undecided -> MatchRowControl.REJECT
        else -> MatchRowControl.RESTORE
    }
    val localFocusRequester = remember(match.channelId) { FocusRequester() }
    val leadFocusRequester = focusRequester ?: localFocusRequester
    fun requesterFor(control: MatchRowControl) = leadFocusRequester.takeIf { leadControl == control }
    fun decide(decision: ManualMatchDecision?) {
        pendingDecision = PendingMatchDecision(decision)
        onDecision(match.channelId, decision)
    }

    // Confirm/reject/restore replaces the button that currently owns focus.
    // Wait for that decision to arrive back through state, then put focus on
    // the new lead control in the same row instead of letting Compose fall
    // through to the SportMate screen behind the hub.
    LaunchedEffect(match.manualDecision, match.confidence, pendingDecision) {
        val pending = pendingDecision ?: return@LaunchedEffect
        if (match.manualDecision == pending.value) {
            leadFocusRequester.requestFocusWhenAttached(attempts = HUB_FOCUS_ATTEMPTS)
            pendingDecision = null
        }
    }

    // The controls sit at the foot of the row, so a scrollable asked to reveal
    // the focused one will happily scroll the row's name and status off the
    // top - the part worth reading. Ask for the whole row instead.
    val bringRowIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringRowIntoView)
            .onFocusChanged { state ->
                if (state.hasFocus) scope.launch { bringRowIntoView.bringIntoView() }
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when (match.confidence) {
                    ChannelMatchConfidence.REJECTED -> Brush.horizontalGradient(
                        listOf(palette.surface, palette.background),
                    )
                    ChannelMatchConfidence.AVAILABLE -> Brush.horizontalGradient(
                        listOf(palette.surfaceSubtle, palette.focus.copy(alpha = 0.14f)),
                    )
                    ChannelMatchConfidence.POSSIBLE -> Brush.horizontalGradient(
                        listOf(palette.surfaceSubtle, palette.accent.copy(alpha = 0.14f)),
                    )
                },
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                text = match.confidence.localizedLabel().uppercase(),
                color = statusColor,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(text = match.source.localizedShortLabel(), color = palette.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = match.channelName,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = match.localizedDetailLabel(),
            color = palette.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Two channels often carry the same match. What separates them is the
        // quality and the commentary language, and providers only ever say that
        // in the channel name - so lift it out where it can be compared.
        val streamTags = remember(match.channelName) { ChannelStreamTags.read(match.channelName) }
        if (streamTags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                streamTags.forEach { tag ->
                    TvTagChip(label = tag.label, tone = tag.tone)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = match.localizedOffsetLabel(),
                color = palette.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (match.confidence == ChannelMatchConfidence.AVAILABLE) {
                TvActionButton(
                    label = stringResource(R.string.action_watch),
                    icon = TvIcons.Play,
                    onClick = { onPlay(match.channelId) },
                    testTag = "match-watch-${match.channelId}",
                    focusRequester = requesterFor(MatchRowControl.WATCH),
                    compact = true,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (match.manualDecision == null) {
                if (match.confidence == ChannelMatchConfidence.POSSIBLE) {
                    TvActionButton(
                        label = stringResource(R.string.action_confirm),
                        icon = TvIcons.Check,
                        onClick = { decide(ManualMatchDecision.CONFIRMED) },
                        testTag = "match-confirm-${match.channelId}",
                        focusRequester = requesterFor(MatchRowControl.CONFIRM),
                        compact = true,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                TvActionButton(
                    label = stringResource(R.string.action_reject),
                    icon = TvIcons.Close,
                    onClick = { decide(ManualMatchDecision.REJECTED) },
                    testTag = "match-reject-${match.channelId}",
                    focusRequester = requesterFor(MatchRowControl.REJECT),
                    compact = true,
                )
            } else {
                TvActionButton(
                    label = stringResource(R.string.action_restore),
                    onClick = { decide(null) },
                    testTag = "match-restore-${match.channelId}",
                    focusRequester = requesterFor(MatchRowControl.RESTORE),
                    compact = true,
                )
            }
        }
    }
}

/** Resolution is the fact people choose on; the rest is supporting detail. */
private val StreamTag.tone: TvTagTone
    get() = when (kind) {
        StreamTagKind.RESOLUTION -> TvTagTone.PRIMARY
        StreamTagKind.DYNAMIC_RANGE -> TvTagTone.ACCENT
        StreamTagKind.FRAME_RATE -> TvTagTone.MUTED
        StreamTagKind.LANGUAGE -> TvTagTone.MUTED
    }

@Composable
private fun PanelMessage(message: String) {
    val palette = StreamMateThemeTokens.palette
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = palette.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChannelMatchConfidence.localizedLabel(): String = when (this) {
    ChannelMatchConfidence.AVAILABLE -> stringResource(R.string.match_available)
    ChannelMatchConfidence.POSSIBLE -> stringResource(R.string.match_possible)
    ChannelMatchConfidence.REJECTED -> stringResource(R.string.match_rejected)
}

@Composable
private fun EventChannelMatch.localizedOffsetLabel(): String = when (source) {
        MatchCandidateSource.XMLTV_PROGRAMME -> when {
            startOffsetMinutes == 0L -> stringResource(R.string.offset_epg_exact)
            startOffsetMinutes < 0 -> stringResource(R.string.offset_epg_before, -startOffsetMinutes)
            else -> stringResource(R.string.offset_epg_after, startOffsetMinutes)
        }
        MatchCandidateSource.M3U_CHANNEL_NAME -> when {
            !hasExplicitStartTime -> stringResource(R.string.offset_m3u_teams)
            startOffsetMinutes == 0L -> stringResource(R.string.offset_m3u_exact)
            startOffsetMinutes < 0 -> stringResource(R.string.offset_m3u_before, -startOffsetMinutes)
            else -> stringResource(R.string.offset_m3u_after, startOffsetMinutes)
        }
    }

@Composable
private fun EventChannelMatch.localizedDetailLabel(): String = when (source) {
    MatchCandidateSource.XMLTV_PROGRAMME -> programmeTitle
    MatchCandidateSource.M3U_CHANNEL_NAME -> stringResource(R.string.match_m3u_detail)
}

@Composable
private fun MatchCandidateSource.localizedShortLabel(): String = when (this) {
    MatchCandidateSource.XMLTV_PROGRAMME -> stringResource(R.string.match_source_epg)
    MatchCandidateSource.M3U_CHANNEL_NAME -> stringResource(R.string.match_source_m3u)
}

@Composable
private fun FootballIncident.localizedPrimaryLabel(): String = when (kind) {
        FootballIncidentKind.SUBSTITUTION -> listOfNotNull(actorName, relatedName)
            .joinToString(" → ")
            .ifBlank { stringResource(R.string.incident_substitution) }
        FootballIncidentKind.GOAL -> {
            val scorer = actorName ?: stringResource(R.string.incident_goal)
            relatedName?.let { stringResource(R.string.incident_assist, scorer, it) } ?: scorer
        }
        else -> actorName ?: detail
    }

@Composable
private fun FootballIncidentKind.localizedLabel(): String = when (this) {
    FootballIncidentKind.GOAL -> stringResource(R.string.incident_goal)
    FootballIncidentKind.CARD -> stringResource(R.string.incident_card)
    FootballIncidentKind.SUBSTITUTION -> stringResource(R.string.incident_substitution)
    FootballIncidentKind.VAR -> stringResource(R.string.incident_var)
    FootballIncidentKind.OTHER -> stringResource(R.string.incident_other)
}

@Composable
private fun StatusBadge(event: TodayEvent, onFocusedCard: Boolean = false) {
    val palette = StreamMateThemeTokens.palette
    val color = when (event.status) {
        TodayEventStatus.LIVE -> palette.danger
        TodayEventStatus.SCHEDULED -> palette.textMuted
        TodayEventStatus.FINISHED -> palette.textMuted
        TodayEventStatus.POSTPONED -> palette.accent
        TodayEventStatus.CANCELLED -> palette.danger
        TodayEventStatus.INTERRUPTED -> palette.accent
        TodayEventStatus.UNKNOWN -> palette.textMuted
    }
    if (event.status == TodayEventStatus.LIVE) {
        // The red pill keeps its colour on a focused card: it is the one thing
        // on the screen that means "happening now", and inverting it with the
        // rest would leave nothing saying so.
        LiveBadge(minuteLabel = event.statusLabel.takeIf(String::isNotBlank))
        return
    }
    Text(
        text = event.localizedStatusLabel(),
        color = if (onFocusedCard) palette.background.copy(alpha = 0.62f) else color,
        fontWeight = FontWeight.Bold,
        fontSize = StreamMateThemeTokens.typography.caption.fontSize,
        lineHeight = StreamMateThemeTokens.typography.caption.lineHeight,
        letterSpacing = StreamMateThemeTokens.typography.overline.letterSpacing,
        maxLines = 1,
    )
}

/**
 * The one badge worth noticing across a room: the minute the match is at, and
 * that it is running right now.
 *
 * The pulse is finite and restarts whenever the minute ticks. An endless one
 * would hold the Compose frame clock busy for as long as the screen is up,
 * which is what makes a screen untestable - waitForIdle never returns, so every
 * test on it hangs rather than fails.
 */
@Composable
private fun LiveBadge(minuteLabel: String?) {
    val palette = StreamMateThemeTokens.palette
    val dotAlpha = remember { Animatable(1f) }
    LaunchedEffect(minuteLabel) {
        repeat(2) {
            dotAlpha.animateTo(0.25f, tween(durationMillis = 240))
            dotAlpha.animateTo(1f, tween(durationMillis = 240))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.danger.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { alpha = dotAlpha.value }
                .background(palette.danger, CircleShape),
        )
        minuteLabel?.let {
            Spacer(Modifier.width(7.dp))
            Text(text = it, color = palette.textPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = stringResource(R.string.status_live).uppercase(),
            color = palette.danger,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun EmptyFilterState(filter: TodayFilter) {
    val message = when {
        filter.sport != null -> stringResource(R.string.empty_sport, stringResource(filter.labelRes))
        filter == TodayFilter.WATCHABLE -> stringResource(R.string.empty_watchable)
        filter == TodayFilter.FAVOURITES -> stringResource(R.string.empty_favourites)
        else -> stringResource(R.string.empty_sports)
    }
    TodayMessageState(title = message)
}

@Composable
private fun TodayMessageState(
    title: String,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = StreamMateThemeTokens.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, color = palette.textMuted, fontSize = 18.sp)
            if (detail != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = detail, color = palette.textMuted, fontSize = 14.sp)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                TvActionButton(label = actionLabel, onClick = onAction, testTag = "today-retry")
            }
        }
    }
}

@Composable
fun sportMateStatusSummary(uiState: TodayUiState, timeZoneId: String): String {
    val parts = mutableListOf(stringResource(R.string.today_subtitle_base, timeZoneId))
    when {
        uiState.isLoading && uiState.events.isNotEmpty() -> {
            parts += stringResource(R.string.today_subtitle_refreshing)
        }
        uiState.error != null -> parts += uiState.error.localizedMessage()
        uiState.cacheState != null -> {
            parts += stringResource(
                R.string.today_subtitle_cache,
                uiState.cacheState.localizedCacheState(),
            )
        }
    }
    if (uiState.providerQuotas.isNotEmpty()) {
        val quota =
            uiState.providerQuotas.entries
                .sortedBy { it.key }
                .joinToString(" / ") { (source, remaining) ->
                    "${source.quotaLabel} $remaining"
                }
        parts += stringResource(R.string.today_subtitle_quota, quota)
    }
    parts += stringResource(R.string.today_subtitle_polling, uiState.pollingMinutes)
    return parts.joinToString(" · ")
}

@Composable
private fun String.localizedCacheState(): String = when (lowercase()) {
        "hit" -> stringResource(R.string.cache_hit)
        "miss" -> stringResource(R.string.cache_miss)
        "stale" -> stringResource(R.string.cache_stale)
        "mixed" -> stringResource(R.string.cache_mixed)
        else -> this
}

@Composable
private fun TodayUiError.localizedMessage(): String = when (this) {
    TodayUiError.PARTIAL_DATA -> stringResource(R.string.error_sports_partial)
    TodayUiError.SPORTS_UNAVAILABLE -> stringResource(R.string.error_sports_unavailable)
    TodayUiError.DETAILS_UNAVAILABLE -> stringResource(R.string.error_details_unavailable)
    TodayUiError.MATCH_DECISION_SAVE -> stringResource(R.string.error_match_decision)
}

@Composable
private fun TodayEvent.localizedStatusLabel(): String = when (status) {
    TodayEventStatus.LIVE -> if (statusLabel.any(Char::isDigit)) {
        statusLabel
    } else {
        stringResource(R.string.status_live)
    }
    TodayEventStatus.SCHEDULED -> stringResource(R.string.status_scheduled)
    TodayEventStatus.FINISHED -> stringResource(R.string.status_finished)
    TodayEventStatus.POSTPONED -> stringResource(R.string.status_postponed)
    TodayEventStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    TodayEventStatus.INTERRUPTED -> stringResource(R.string.status_interrupted)
    TodayEventStatus.UNKNOWN -> stringResource(R.string.status_unknown)
}

private val String.quotaLabel: String
    get() = when (this) {
        "api-sports-football" -> "F"
        "api-sports-afl" -> "AFL"
        "api-sports-hockey" -> "H"
        "api-sports-basketball" -> "BK"
        "api-sports-baseball" -> "BB"
        "api-sports-handball" -> "HB"
        "api-sports-rugby" -> "R"
        "api-sports-volleyball" -> "V"
        else -> "API"
    }

private fun List<TodayEvent>.filterFor(filter: TodayFilter): List<TodayEvent> = when {
    filter.sport != null -> filter { it.sport == filter.sport }
    filter == TodayFilter.WATCHABLE -> filter { it.matchingChannels > 0 }
    filter == TodayFilter.FAVOURITES -> filter { it.isFavourite }
    else -> this
}

private val ICON_ACTION_SIZE = 44.dp
private val SECTION_GAP = 26.dp
private val COMPACT_ROW_WIDTH = 272.dp
private val COMPACT_ROW_HEIGHT = 68.dp
private const val CHANNEL_SEPARATOR = "  ·  "
private const val CLOCK_TICK_MILLIS = 60_000L

/** A literal separator between the date and the time, quoted for the formatter. */
private const val CLOCK_SEPARATOR_PATTERN = "'  ·  '"

private val CHANNEL_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm")

private val EVENT_CARD_WIDTH = 238.dp
private val EVENT_CARD_HEIGHT = 224.dp
private val EVENT_CTA_HEIGHT = 38.dp
private val TEAM_CREST_SIZE = 48.dp

private val TIMELINE_BAND_HEIGHT = 62.dp
private val TIMELINE_MARKER_WIDTH = 38.dp
private val INCIDENT_SCROLL_STEP = 120.dp

/** The control a stream row leads with, and so where focus goes on arrival. */
private enum class MatchRowControl { WATCH, CONFIRM, REJECT, RESTORE }

private data class PendingMatchDecision(val value: ManualMatchDecision?)

private const val HUB_FOCUS_ATTEMPTS = 90
