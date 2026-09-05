package com.streammate.tv.feature.player

import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.common.TvListRow
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import com.streammate.tv.feature.common.TvTagTone
import com.streammate.tv.feature.common.TvTagChip
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.core.model.ChannelStreamTags
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.metadata.EnrichedMetadata
import com.streammate.tv.iptv.repository.GuideChannel
import com.streammate.tv.iptv.repository.GuideTimelineChannel
import com.streammate.tv.iptv.repository.GuideTimelineProgramme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * What is on, along the foot of the picture.
 *
 * Unboxed: the channel, the programme, how far through it is and the handful of
 * things that can be done, laid over the scrim rather than inside a panel drawn
 * on top of the film. Every time and title comes off the guide; with no EPG the
 * channel identity stands on its own and the programme line says so rather than
 * inventing a schedule.
 */
@Composable
internal fun LiveProgrammeInfoOverlay(
    channel: GuideTimelineChannel?,
    streamName: String,
    nowEpochMillis: Long,
    timeZoneId: String,
    metadata: EnrichedMetadata?,
    aspectModeLabel: String,
    audioTrackLabel: String,
    subtitleTrackLabel: String,
    statsVisible: Boolean,
    onBack: () -> Unit,
    onCycleAspectMode: () -> Unit,
    onCycleAudioTrack: () -> Unit,
    onCycleSubtitleTrack: () -> Unit,
    onOpenChannelBrowser: () -> Unit,
    onToggleStats: () -> Unit,
    onOpenQuickActions: () -> Unit,
    externalPlayerBusy: Boolean,
    onOpenExternal: (() -> Unit)?,
    visibilityKey: Any?,
    enabled: Boolean,
    dismissRequest: Int = 0,
    /** Bumped to hand focus to the row of buttons along the bottom. */
    focusRequestKey: Int = 0,
    onVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var visible by remember(visibilityKey, enabled) { mutableStateOf(enabled) }
    var actionsFocused by remember(visibilityKey) { mutableStateOf(false) }
    val actionsFocus = remember { FocusRequester() }
    LaunchedEffect(visibilityKey, enabled, actionsFocused) {
        if (!enabled) return@LaunchedEffect
        visible = true
        // Idle chrome times out. A box somebody is working their way along is
        // not idle, so while the buttons hold focus it stays until dismissed.
        if (actionsFocused) return@LaunchedEffect
        delay(PLAYER_CONTROLS_TIMEOUT_MILLIS.toLong())
        visible = false
    }
    LaunchedEffect(focusRequestKey) {
        if (focusRequestKey > 0) {
            visible = true
            delay(PLAYER_CONTROL_FOCUS_DELAY_MILLIS)
            actionsFocus.requestFocus()
        }
    }
    LaunchedEffect(dismissRequest) {
        if (dismissRequest > 0) visible = false
    }
    val programme = channel?.currentProgrammeAt(nowEpochMillis)
    val nextProgramme = channel?.nextProgrammeAfter(programme, nowEpochMillis)
    val displayed = enabled && visible && channel != null
    LaunchedEffect(displayed) { onVisibilityChanged(displayed) }
    AnimatedVisibility(
        visible = displayed,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color(0xFF252A31).copy(alpha = 0.16f),
                        1f to palette.backgroundBottom.copy(alpha = 0.52f),
                    ),
                )
                .testTag("player-live-info"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerArtwork(
                    imageUrl = channel?.logoUrl,
                    fallback = channel?.name.orEmpty(),
                    size = CHANNEL_TILE_SIZE,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = channel?.name.orEmpty(),
                        color = palette.textPrimary,
                        fontSize = typography.headline.fontSize,
                        lineHeight = typography.headline.lineHeight,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TvTagChip(
                            label = stringResource(R.string.guide_live),
                            tone = TvTagTone.LIVE,
                        )
                        // What the provider says about the feed, read off the
                        // channel name the same way the guide reads it.
                        val streamTags = remember(channel?.name) {
                            ChannelStreamTags.read(channel?.name.orEmpty())
                        }
                        streamTags.forEach { tag ->
                            TvTagChip(label = tag.label, tone = TvTagTone.MUTED)
                        }
                        channel?.groupTitle?.takeIf(String::isNotBlank)?.let { group ->
                            Text(
                                text = group,
                                color = palette.textMuted,
                                fontSize = typography.label.fontSize,
                                lineHeight = typography.label.lineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Text(
                text = programme?.title ?: stringResource(R.string.player_no_current_programme),
                modifier = Modifier.fillMaxWidth(PLAYER_TEXT_FRACTION).padding(top = 12.dp),
                color = palette.textPrimary,
                // A step down from display: this sits over a moving picture,
                // and at forty it was covering more of it than it earned.
                fontSize = typography.title.fontSize,
                lineHeight = typography.title.lineHeight,
                fontWeight = FontWeight.Black,
                letterSpacing = typography.title.letterSpacing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (programme != null) {
                val fraction = playerProgrammeFraction(
                    startEpochMillis = programme.startEpochMillis,
                    stopEpochMillis = programme.stopEpochMillis,
                    nowEpochMillis = nowEpochMillis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = listOfNotNull(
                            formatPlayerTime(programme.startEpochMillis, timeZoneId),
                            fraction?.let {
                                stringResource(
                                    R.string.player_watched_percent,
                                    playerPercentWatched(it),
                                )
                            },
                            playerRemainingLabel(programme.stopEpochMillis, nowEpochMillis),
                        ).joinToString(PLAYER_FACT_SEPARATOR),
                        modifier = Modifier.weight(1f),
                        color = palette.textMuted,
                        fontSize = typography.body.fontSize,
                        lineHeight = typography.body.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The stop time sits over the right end of the bar, which
                    // is the point on it that the time refers to.
                    Text(
                        text = formatPlayerTime(programme.stopEpochMillis, timeZoneId),
                        color = palette.textMuted,
                        fontSize = typography.body.fontSize,
                        lineHeight = typography.body.lineHeight,
                        maxLines = 1,
                    )
                }
                PlayerProgressTrack(
                    fraction = fraction ?: 0f,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            nextProgramme?.let { next ->
                Text(
                    text = stringResource(
                        R.string.player_next_programme,
                        formatPlayerTime(next.startEpochMillis, timeZoneId),
                        next.title,
                    ),
                    modifier = Modifier.fillMaxWidth(PLAYER_TEXT_FRACTION).padding(top = 8.dp),
                    color = palette.textDim,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val displayedStreamName = streamName.takeIf {
                it.isNotBlank() && !it.equals(channel?.name, ignoreCase = true)
            }
            displayedStreamName?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(PLAYER_TEXT_FRACTION).padding(top = 4.dp),
                    color = palette.textDim,
                    fontSize = typography.caption.fontSize,
                    lineHeight = typography.caption.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .focusRequester(actionsFocus)
                    .focusGroup()
                    .onFocusChanged { actionsFocused = it.hasFocus },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlayerIconAction(
                    icon = TvIcons.Back,
                    description = stringResource(R.string.player_action_back),
                    onClick = onBack,
                    testTag = "player-back",
                )
                PlayerIconAction(
                    icon = TvIcons.Aspect,
                    description = stringResource(R.string.player_picture_mode, aspectModeLabel),
                    onClick = onCycleAspectMode,
                    testTag = "player-aspect",
                )
                PlayerIconAction(
                    icon = TvIcons.Audio,
                    description = stringResource(R.string.player_audio_track, audioTrackLabel),
                    onClick = onCycleAudioTrack,
                    testTag = "player-audio",
                )
                PlayerIconAction(
                    icon = TvIcons.Subtitles,
                    description = stringResource(R.string.player_subtitle_track, subtitleTrackLabel),
                    onClick = onCycleSubtitleTrack,
                    testTag = "player-subtitles",
                )
                PlayerIconAction(
                    icon = TvIcons.Channels,
                    description = stringResource(R.string.player_action_channels),
                    onClick = onOpenChannelBrowser,
                    testTag = "player-channels",
                )
                PlayerIconAction(
                    icon = TvIcons.Stats,
                    description = stringResource(R.string.player_action_stats),
                    onClick = onToggleStats,
                    selected = statsVisible,
                    testTag = "player-stats-toggle",
                )
                PlayerIconAction(
                    icon = TvIcons.Settings,
                    description = stringResource(R.string.player_action_quick),
                    onClick = onOpenQuickActions,
                    testTag = "player-quick-actions",
                )
                onOpenExternal?.let {
                    PlayerIconAction(
                        icon = TvIcons.Forward,
                        description = stringResource(
                            if (externalPlayerBusy) {
                                R.string.player_opening_external
                            } else {
                                R.string.player_external
                            },
                        ),
                        onClick = it,
                        enabled = !externalPlayerBusy,
                        testTag = "player-external",
                    )
                }
            }
        }
    }
}

/**
 * The bar under a programme or a recording: a cyan fill to where playback has
 * reached and a white thumb on it, so the position is findable at a glance from
 * across a room.
 */
@Composable
internal fun PlayerProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val palette = StreamMateThemeTokens.palette
    val safeFraction = fraction.coerceIn(0f, 1f)
    Box(modifier = modifier.height(PLAYER_TRACK_THUMB), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PLAYER_TRACK_HEIGHT)
                .clip(StreamMateThemeTokens.shapes.small)
                .background(palette.textPrimary.copy(alpha = 0.20f)),
        )
        Box(
            Modifier
                .fillMaxWidth(safeFraction)
                .height(PLAYER_TRACK_HEIGHT)
                .clip(StreamMateThemeTokens.shapes.small)
                .background(palette.focus),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .offset(x = (maxWidth - PLAYER_TRACK_THUMB) * safeFraction)
                    .size(PLAYER_TRACK_THUMB)
                    .clip(CircleShape)
                    .background(palette.textPrimary),
            )
        }
    }
}

/**
 * A square icon control. Borderless at rest, off-white fill on focus, and the
 * description is what a screen reader and a test read in place of a label.
 */
@Composable
internal fun PlayerIconAction(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val palette = StreamMateThemeTokens.palette
    TvSurface(
        onClick = onClick,
        modifier = Modifier
            .size(PLAYER_ICON_ACTION_SIZE)
            .semantics { contentDescription = description },
        shape = StreamMateThemeTokens.shapes.small,
        selected = selected,
        enabled = enabled,
        resting = palette.surface,
        restingContent = palette.textPrimary,
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

/** The wall clock, in the interface language and the configured zone. */
@Composable
internal fun playerClockLabel(timeZoneId: String, nowEpochMillis: Long): String {
    val context = LocalContext.current
    val tag = ComposeLocale.current.toLanguageTag()
    val locale = remember(tag) { Locale.forLanguageTag(tag) }
    val twentyFourHour = DateFormat.is24HourFormat(context)
    val formatter = remember(locale, twentyFourHour) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, if (twentyFourHour) "Hm" else "hmma"),
            locale,
        )
    }
    val zone = remember(timeZoneId) {
        runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
    }
    return remember(formatter, zone, nowEpochMillis) {
        formatter.format(Instant.ofEpochMilli(nowEpochMillis).atZone(zone))
    }
}

/** How much is left of a programme, or nothing once it has ended. */
@Composable
private fun playerRemainingLabel(stopEpochMillis: Long, nowEpochMillis: Long): String? {
    val minutes = playerRemainingMinutes(stopEpochMillis, nowEpochMillis) ?: return null
    val (hours, remainder) = playerDurationParts(minutes)
    return if (hours > 0) {
        stringResource(R.string.player_remaining_hours, hours, remainder)
    } else {
        stringResource(R.string.player_remaining_minutes, remainder)
    }
}

data class PlayerTrackChoice(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean = true,
)

@Composable
fun TrackSelectionOverlay(
    title: String,
    choices: List<PlayerTrackChoice>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val initialIndex = choices.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0
    val focusRequesters = remember(choices.size) { List(choices.size) { FocusRequester() } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    LaunchedEffect(title, choices.size, initialIndex) {
        if (choices.isNotEmpty()) {
            listState.scrollToItem(initialIndex)
            // Not a fixed sleep: a requester wired to a lazy row is not attached
            // until that row lays out, and 80ms was a bet on winning that race.
            focusRequesters[initialIndex].requestFocusWhenAttached()
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xA6000000))
            .testTag("player-track-picker"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 390.dp, max = 520.dp)
                .heightIn(max = 520.dp)
                .clip(StreamMateThemeTokens.shapes.large)
                .background(palette.panel.copy(alpha = 0.97f))
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                TvActionButton(
                    label = stringResource(R.string.action_back),
                    icon = TvIcons.Back,
                    compact = true,
                    onClick = onDismiss,
                )
            }
            Text(
                text = stringResource(R.string.player_track_picker_hint),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                color = palette.textMuted,
                fontSize = 12.sp,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(choices.size) { index ->
                    val choice = choices[index]
                    var focused by remember(title, index) { mutableStateOf(false) }
                    val shape = StreamMateThemeTokens.shapes.small
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusRequester(focusRequesters[index])
                            .clip(shape)
                            .background(
                                when {
                                    choice.selected -> palette.surfaceRaised
                                    else -> palette.surfaceSubtle
                                },
                            )
                            .then(if (focused) Modifier.border(3.dp, palette.textPrimary, shape) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .clickable(enabled = choice.enabled) { onSelect(index) }
                            .testTag("player-track-option-$index")
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (choice.selected) "●" else "○",
                            color = if (choice.selected || focused) palette.focus else palette.textMuted,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = choice.label,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            color = if (choice.enabled) palette.textPrimary else palette.textDisabled,
                            fontSize = 14.sp,
                            fontWeight = if (choice.selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PlayerInlineAction(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
    val palette = StreamMateThemeTokens.palette
    var focused by remember { mutableStateOf(false) }
    Text(
        text = label,
        color = when {
            !enabled -> palette.textDisabled
            focused -> palette.focus
            else -> palette.textPrimary
        },
        fontSize = 12.sp,
        fontWeight = if (focused) FontWeight.Black else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .weight(1f)
            .testTag(testTag)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    )
}

/**
 * The channel list, down the right edge. While groups are open they take the
 * right edge and push this list left, keeping the direction of travel visible.
 *
 * A gradient rather than a drawer: the picture keeps running behind it and
 * fades out under the rows instead of being cut off by a rectangle. Rows are
 * borderless; the one being pointed at flips to the off-white fill like every
 * other selected surface in the app.
 *
 * Only the name, the number and what is on carry through here - never the
 * stream address or the credentials behind it.
 */
@Composable
internal fun ChannelBrowserOverlay(
    channels: List<GuideChannel>,
    selectedIndex: Int,
    groups: List<String>,
    groupCounts: Map<String, Int>,
    selectedGroupIndex: Int,
    groupsVisible: Boolean,
    visible: Boolean,
    /**
     * Held by the caller, which moves it in the same keypress that moves the
     * selection. Reacting to the selection here instead let the highlight paint
     * on its new row a frame or two before the list caught up, which reads as
     * the list lurching after the fact.
     */
    listState: LazyListState,
    groupListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    LaunchedEffect(visible) {
        if (!visible || channels.isEmpty()) return@LaunchedEffect
        val index = selectedIndex.coerceIn(channels.indices)
        // The selection walks the list before the list walks under it. Going
        // down it travels from the first row to the middle of the screen and
        // only then does the list start carrying it; coming back up it travels
        // to the top again before the list moves the other way. Scrolling to
        // the selected index instead pins it to the top row, which reads as
        // the list jumping a place every time you press a key.
        val onScreen = listState.layoutInfo.visibleItemsInfo.size
            .takeIf { it > 0 }
            ?: CHANNEL_BROWSER_ROWS_ON_SCREEN
        listState.scrollToItem(playerBrowserScrollTarget(index, onScreen))
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            ChannelBrowserPane(
                channels = channels,
                selectedIndex = selectedIndex,
                listState = listState,
                active = !groupsVisible,
            )
            AnimatedVisibility(
                visible = groupsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ChannelGroupBrowserPane(
                    groups = groups,
                    groupCounts = groupCounts,
                    selectedIndex = selectedGroupIndex,
                    listState = groupListState,
                )
            }
        }
    }
}

@Composable
private fun ChannelGroupBrowserPane(
    groups: List<String>,
    groupCounts: Map<String, Int>,
    selectedIndex: Int,
    listState: LazyListState,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Column(
        modifier = Modifier
            .width(CHANNEL_GROUP_BROWSER_WIDTH)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    0f to palette.background.copy(alpha = 0.76f),
                    1f to palette.background.copy(alpha = 0.97f),
                ),
            )
            .padding(start = 18.dp, end = 12.dp, top = 24.dp, bottom = 20.dp)
            .testTag("player-channel-groups"),
    ) {
        Text(
            text = stringResource(R.string.guide_groups).uppercase(),
            modifier = Modifier.padding(start = 10.dp, bottom = 12.dp),
            color = palette.textDim,
            fontSize = typography.overline.fontSize,
            lineHeight = typography.overline.lineHeight,
            fontWeight = FontWeight.Bold,
            letterSpacing = typography.overline.letterSpacing,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            itemsIndexed(groups, key = { _, group -> group }) { index, group ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHANNEL_GROUP_ROW_HEIGHT)
                        .clip(StreamMateThemeTokens.shapes.medium)
                        .background(if (selected) palette.textPrimary else Color.Transparent)
                        .padding(horizontal = 10.dp)
                        .testTag("player-channel-group-${group.hashCode()}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = group,
                        modifier = Modifier.weight(1f),
                        color = if (selected) palette.background else palette.textPrimary,
                        fontSize = typography.label.fontSize,
                        lineHeight = typography.label.lineHeight,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    groupCounts[group]?.let { count ->
                        Text(
                            text = count.toString(),
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (selected) {
                                palette.background.copy(alpha = 0.62f)
                            } else {
                                palette.textDim
                            },
                            fontSize = typography.caption.fontSize,
                            lineHeight = typography.caption.lineHeight,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelBrowserPane(
    channels: List<GuideChannel>,
    selectedIndex: Int,
    listState: LazyListState,
    active: Boolean,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Column(
        modifier = Modifier
            .width(CHANNEL_BROWSER_WIDTH)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    0f to palette.background.copy(alpha = 0f),
                    0.22f to palette.background.copy(alpha = 0.82f),
                    1f to palette.background.copy(alpha = 0.97f),
                ),
            )
            .padding(start = 28.dp, end = 20.dp, top = 24.dp, bottom = 20.dp)
            .testTag("player-channel-browser"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.player_channels).uppercase(),
                modifier = Modifier.weight(1f),
                color = palette.textDim,
                fontSize = typography.overline.fontSize,
                lineHeight = typography.overline.lineHeight,
                fontWeight = FontWeight.Bold,
                letterSpacing = typography.overline.letterSpacing,
            )
            Text(
                text = "${stringResource(R.string.guide_groups)} →",
                color = palette.textDim,
                fontSize = typography.caption.fontSize,
                lineHeight = typography.caption.lineHeight,
                maxLines = 1,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                val selected = index == selectedIndex
                val activeSelection = selected && active
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHANNEL_ROW_HEIGHT)
                        .clip(StreamMateThemeTokens.shapes.medium)
                        .background(
                            when {
                                activeSelection -> palette.textPrimary
                                selected -> palette.surfaceFocused
                                else -> Color.Transparent
                            },
                        )
                        .padding(horizontal = 10.dp)
                        .testTag("player-channel-${channel.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerArtwork(channel.logoUrl, channel.name, size = CHANNEL_ROW_LOGO)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = channel.name,
                            color = if (activeSelection) palette.background else palette.textPrimary,
                            fontSize = typography.label.fontSize,
                            lineHeight = typography.label.lineHeight,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = channel.currentProgrammeTitle
                                ?: stringResource(R.string.player_no_current_programme),
                            color = if (activeSelection) {
                                palette.background.copy(alpha = 0.62f)
                            } else {
                                palette.textDim
                            },
                            fontSize = typography.caption.fontSize,
                            lineHeight = typography.caption.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomTransportControls(
    title: String,
    isPlaying: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    visibilityKey: Any?,
    focusRequestKey: Int,
    holdVisible: Boolean = false,
    aspectModeLabel: String,
    audioTrackLabel: String,
    subtitleTrackLabel: String,
    onBack: () -> Unit,
    onCycleAspectMode: () -> Unit,
    onCycleAudioTrack: () -> Unit,
    onCycleSubtitleTrack: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onControlsFocusChanged: (Boolean) -> Unit,
    onDismissed: () -> Unit,
    dismissRequest: Int = 0,
    onVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    var visible by remember { mutableStateOf(true) }
    var controlsFocused by remember { mutableStateOf(false) }
    val playPauseFocusRequester = remember { FocusRequester() }
    LaunchedEffect(visibilityKey) {
        visible = true
    }
    LaunchedEffect(dismissRequest) {
        if (dismissRequest > 0) visible = false
    }
    LaunchedEffect(visible) { onVisibilityChanged(visible) }
    LaunchedEffect(visible, controlsFocused, visibilityKey, holdVisible) {
        if (holdVisible) return@LaunchedEffect
        if (visible && !controlsFocused) {
            delay(PLAYER_CONTROLS_TIMEOUT_MILLIS.toLong())
            if (!controlsFocused) {
                visible = false
                onDismissed()
            }
        }
    }
    LaunchedEffect(focusRequestKey) {
        if (focusRequestKey > 0) {
            visible = true
            delay(PLAYER_CONTROL_FOCUS_DELAY_MILLIS)
            playPauseFocusRequester.requestFocus()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Unboxed, like the live overlay: the scrim under it is what makes the
        // controls readable, not a panel drawn around them.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    controlsFocused = focusState.hasFocus
                    onControlsFocusChanged(focusState.hasFocus)
                }
                .focusGroup()
                .testTag("player-bottom-controls"),
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(PLAYER_TEXT_FRACTION),
                color = palette.textPrimary,
                fontSize = StreamMateThemeTokens.typography.headline.fontSize,
                lineHeight = StreamMateThemeTokens.typography.headline.lineHeight,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvActionButton(
                    label = stringResource(R.string.action_back),
                    icon = TvIcons.Back,
                    onClick = onBack,
                    compact = true,
                    testTag = "player-back",
                )
                TvActionButton(
                    label = stringResource(R.string.player_picture_mode, aspectModeLabel),
                    onClick = onCycleAspectMode,
                    compact = true,
                    testTag = "player-aspect",
                )
                TvActionButton(
                    label = stringResource(R.string.player_audio_track, audioTrackLabel),
                    onClick = onCycleAudioTrack,
                    compact = true,
                    testTag = "player-audio",
                )
                TvActionButton(
                    label = stringResource(R.string.player_subtitle_track, subtitleTrackLabel),
                    onClick = onCycleSubtitleTrack,
                    compact = true,
                    testTag = "player-subtitles",
                )
            }
            val duration = durationMillis.takeIf { it > 0L } ?: 0L
            val fraction = if (duration > 0L) {
                (positionMillis.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            PlayerProgressTrack(
                fraction = fraction,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${formatPlayerDuration(positionMillis)} / ${formatPlayerDuration(duration)}",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 18.dp),
                )
                TvActionButton(
                    label = stringResource(R.string.player_rewind),
                    icon = TvIcons.Rewind,
                    onClick = onRewind,
                    compact = true,
                    testTag = "player-rewind",
                )
                TvActionButton(
                    label = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                    icon = if (isPlaying) TvIcons.Pause else TvIcons.Play,
                    onClick = onPlayPause,
                    modifier = Modifier.padding(horizontal = 10.dp),
                    focusRequester = playPauseFocusRequester,
                    testTag = "player-play-pause",
                )
                TvActionButton(
                    label = stringResource(R.string.player_forward),
                    icon = TvIcons.Forward,
                    onClick = onForward,
                    compact = true,
                    testTag = "player-forward",
                )
            }
        }
    }
}

private const val PLAYER_CONTROL_FOCUS_DELAY_MILLIS = 80L

/**
 * The channel list down the right edge.
 *
 * Proportional to the reference's 460px of 1920, which at the type scale this
 * app uses leaves room for a number, a logo, a name and what is on, and shows
 * eight rows on a 1080p panel.
 */
// Wide enough for a long custom name such as "FI: Viaplay 1 Urheilu" next to a logo.
private val CHANNEL_BROWSER_WIDTH = 330.dp
private val CHANNEL_ROW_HEIGHT = 66.dp
private val CHANNEL_GROUP_BROWSER_WIDTH = 220.dp
private val CHANNEL_GROUP_ROW_HEIGHT = 52.dp

/**
 * Rows that fit on a 1080p panel, used only until the list has been laid out
 * once and can say for itself.
 */
internal const val CHANNEL_BROWSER_ROWS_ON_SCREEN = 7
internal const val CHANNEL_GROUP_BROWSER_ROWS_ON_SCREEN = 10

/**
 * Which row the channel browser puts at the top so that [selectedIndex] sits in
 * the upper half of the screen.
 *
 * Zero until the selection reaches the middle, so the highlight travels down
 * the rows while the list stands still; after that the list carries it and the
 * highlight stays put.
 */
internal fun playerBrowserScrollTarget(selectedIndex: Int, rowsOnScreen: Int): Int =
    (selectedIndex - rowsOnScreen / 2).coerceAtLeast(0)
private val CHANNEL_ROW_LOGO = 44.dp

private val CHANNEL_TILE_SIZE = 64.dp
private val PLAYER_ICON_ACTION_SIZE = 44.dp
private val PLAYER_TRACK_HEIGHT = 5.dp
private val PLAYER_TRACK_THUMB = 13.dp
private const val PLAYER_TEXT_FRACTION = 0.62f
private const val PLAYER_FACT_SEPARATOR = "  ·  "

@Composable
private fun PlayerArtwork(imageUrl: String?, fallback: String, size: androidx.compose.ui.unit.Dp = 92.dp) {
    val palette = StreamMateThemeTokens.palette
    Box(
        modifier = Modifier
            .size(size)
            .clip(StreamMateThemeTokens.shapes.small)
            .background(palette.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = fallback.take(2).uppercase(),
                color = palette.focus,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

internal fun GuideTimelineChannel.currentProgrammeAt(nowEpochMillis: Long): GuideTimelineProgramme? =
    programmes.firstOrNull { nowEpochMillis in it.startEpochMillis until it.stopEpochMillis }

internal fun GuideTimelineChannel.nextProgrammeAfter(
    current: GuideTimelineProgramme?,
    nowEpochMillis: Long,
): GuideTimelineProgramme? {
    val threshold = current?.stopEpochMillis ?: nowEpochMillis
    return programmes.firstOrNull { it.startEpochMillis >= threshold }
}

internal fun GuideTimelineProgramme.progressAt(nowEpochMillis: Long): Float? {
    val duration = stopEpochMillis - startEpochMillis
    if (duration <= 0L || nowEpochMillis !in startEpochMillis..stopEpochMillis) return null
    return ((nowEpochMillis - startEpochMillis).toFloat() / duration).coerceIn(0f, 1f)
}

private fun formatPlayerRange(start: Long, stop: Long, timeZoneId: String): String =
    "${formatPlayerTime(start, timeZoneId)}–${formatPlayerTime(stop, timeZoneId)}"

private fun formatPlayerTime(epochMillis: Long, timeZoneId: String): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault()))
        .format(Instant.ofEpochMilli(epochMillis))

private fun formatPlayerDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

/** One row of the long-press menu: what it does, and where it currently stands. */
data class PlayerQuickAction(
    val label: String,
    val value: String?,
    val testTag: String,
    val onSelect: () -> Unit,
)

/**
 * The menu behind a long press on OK.
 *
 * Audio, subtitles and picture shape are all reachable already - on remote keys
 * that many remotes do not have, and that nobody discovers by accident. This is
 * the one route to them that can be found by holding the button you are already
 * pressing.
 */
@Composable
internal fun PlayerQuickActionsOverlay(
    actions: List<PlayerQuickAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(actions.size) { firstFocus.requestFocusWhenAttached() }
    BackHandler(enabled = true, onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xA6000000))
            .testTag("player-quick-actions"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 480.dp)
                .clip(StreamMateThemeTokens.shapes.large)
                .background(palette.panel.copy(alpha = 0.97f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.player_quick_actions_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            actions.forEachIndexed { index, action ->
                TvListRow(
                    label = action.label,
                    trailing = action.value,
                    onClick = action.onSelect,
                    focusRequester = if (index == 0) firstFocus else null,
                    divider = index > 0,
                    testTag = action.testTag,
                )
            }
        }
    }
}
