package com.streammate.tv.feature.guide

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.core.model.ChannelStreamTags
import com.streammate.tv.feature.common.tvSurfaceColors
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.repository.GuideTimelineChannel
import com.streammate.tv.iptv.repository.GuideTimelineProgramme
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

/**
 * The grid: a day label and half-hour ruler over a fixed channel column and a
 * three-hour timeline, with one red now-line drawn across the whole of it.
 *
 * Widths come from the constraints rather than from constants. Three hours
 * always fill whatever is left after the channel column, so a block's width is
 * proportional to its real running time on any panel size.
 */
@Composable
internal fun GuideGrid(
    channels: List<GuideTimelineChannel>,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    timeZoneId: String,
    selection: GuideSelection?,
    listState: LazyListState,
    initialFocusIndex: Int,
    firstFocusRequester: FocusRequester,
    returnFocusRequester: FocusRequester,
    onOpenGroupRail: () -> Unit,
    pagedFocusRequester: FocusRequester,
    pagedFocusAtEnd: Boolean,
    onSelection: (GuideTimelineChannel, GuideTimelineProgramme?) -> Unit,
    onPlay: (GuideTimelineChannel, GuideTimelineProgramme?) -> Unit,
    onPageForward: () -> Unit,
    onPageBack: (() -> Unit)?,
    onTransportKey: (Key) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    BoxWithConstraints(modifier = modifier) {
        // Three hours fill the width that is left; nothing here is a pixel
        // coordinate copied off a 1920-wide mockup.
        val timelineWidth = (maxWidth - CHANNEL_COLUMN_WIDTH - GRID_GAP).coerceAtLeast(0.dp)
        val minuteWidth = timelineWidth / TIMELINE_WINDOW_MINUTES
        Column(modifier = Modifier.fillMaxSize()) {
            GuideTimelineHeader(
                windowStart = windowStart,
                now = now,
                timeZoneId = timeZoneId,
                minuteWidth = minuteWidth,
                timelineWidth = timelineWidth,
            )
            Spacer(Modifier.height(GRID_ROW_GAP))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Previewed on the container so the media keys are caught
                    // wherever focus sits in the grid, without touching the
                    // arrow keys that move between programmes.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            onTransportKey(event.key)
                        }
                    },
                state = listState,
                verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
                contentPadding = PaddingValues(bottom = GRID_ROW_GAP),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    GuideChannelRow(
                        number = index + 1,
                        channel = channel,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        now = now,
                        minuteWidth = minuteWidth,
                        timelineWidth = timelineWidth,
                        selection = selection,
                        onSelection = { programme -> onSelection(channel, programme) },
                        onPlay = { programme -> onPlay(channel, programme) },
                        focusRequester = if (index == initialFocusIndex) firstFocusRequester else null,
                        returnFocusRequester = returnFocusRequester.takeIf {
                            channel.id == selection?.channel?.id
                        },
                        onOpenGroupRail = onOpenGroupRail,
                        pagedFocusRequester = pagedFocusRequester.takeIf {
                            channel.id == selection?.channel?.id
                        },
                        pagedFocusAtEnd = pagedFocusAtEnd,
                        onPageForward = onPageForward,
                        onPageBack = onPageBack,
                    )
                }
            }
        }
        // One line for the whole grid rather than a stub inside every row. It
        // starts in the ruler, so the time it marks can be read straight off
        // the header instead of inferred from the blocks around it.
        if (now in windowStart until windowEnd) {
            val nowMinutes = (now - windowStart).toFloat() / MINUTE_MILLIS
            val nowOffset = CHANNEL_COLUMN_WIDTH + GRID_GAP + minuteWidth * nowMinutes
            Box(
                Modifier
                    .offset(x = nowOffset)
                    .width(NOW_LINE_WIDTH)
                    .fillMaxHeight()
                    .background(palette.danger)
                    .testTag("guide-now-line"),
            )
            Box(
                Modifier
                    .offset(x = nowOffset - (NOW_LINE_HEAD - NOW_LINE_WIDTH) / 2)
                    .size(NOW_LINE_HEAD)
                    .clip(CircleShape)
                    .background(palette.danger),
            )
        }
    }
}

/**
 * The ruler: which day is on screen over the channel column, half hours over
 * the timeline.
 */
@Composable
private fun GuideTimelineHeader(
    windowStart: Long,
    now: Long,
    timeZoneId: String,
    minuteWidth: Dp,
    timelineWidth: Dp,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(
            modifier = Modifier.width(CHANNEL_COLUMN_WIDTH),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Which day is on screen. Without it, paging forward looks like the
            // same evening with different programmes in it.
            Text(
                text = formatWindowDate(windowStart, timeZoneId),
                modifier = Modifier.testTag("guide-window-day"),
                color = palette.textPrimary,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = formatWindowDay(windowStart, now, timeZoneId),
                color = if (GuideTimeWindow.isAtNow(windowStart, now)) palette.textDim else palette.accent,
                fontSize = typography.caption.fontSize,
                lineHeight = typography.caption.lineHeight,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(GRID_GAP))
        Box(modifier = Modifier.width(timelineWidth).height(20.dp)) {
            repeat(TIMELINE_WINDOW_MINUTES / TICK_MINUTES) { index ->
                val minute = index * TICK_MINUTES
                Text(
                    text = formatTime(windowStart + minute * MINUTE_MILLIS, timeZoneId),
                    modifier = Modifier.offset(x = minuteWidth * minute),
                    color = palette.textDim,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    number: Int,
    channel: GuideTimelineChannel,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    minuteWidth: Dp,
    timelineWidth: Dp,
    selection: GuideSelection?,
    onSelection: (GuideTimelineProgramme?) -> Unit,
    onPlay: (GuideTimelineProgramme?) -> Unit,
    focusRequester: FocusRequester?,
    returnFocusRequester: FocusRequester?,
    onOpenGroupRail: () -> Unit,
    pagedFocusRequester: FocusRequester?,
    pagedFocusAtEnd: Boolean,
    onPageForward: () -> Unit,
    onPageBack: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth().height(GUIDE_ROW_HEIGHT)) {
        GuideChannelCell(
            number = number,
            channel = channel,
            selected = selection?.channel?.id == channel.id,
            onFocus = { onSelection(channel.preferredProgramme(now)) },
            onClick = { onPlay(null) },
            focusRequester = focusRequester,
            returnFocusRequester = returnFocusRequester,
            onOpenGroupRail = onOpenGroupRail,
        )
        Spacer(Modifier.width(GRID_GAP))
        Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
            if (channel.programmes.isEmpty()) {
                ProgrammeCell(
                    title = stringResource(R.string.guide_no_epg),
                    subtitle = stringResource(R.string.guide_watch_channel),
                    x = 0.dp,
                    width = timelineWidth,
                    selected = selection?.channel?.id == channel.id && selection.programme == null,
                    airing = false,
                    past = false,
                    progress = null,
                    accent = null,
                    onFocus = { onSelection(null) },
                    onClick = { onPlay(null) },
                    // Paging can land on a window this channel has no listings
                    // for. Without the requester here the cell focus was
                    // restored to does not exist, the request fails, and focus
                    // falls out of the grid onto whatever the screen happens to
                    // put first - which is now the hero.
                    pagedFocusRequester = pagedFocusRequester,
                    onPageForward = onPageForward,
                    onPageBack = onPageBack,
                    testTag = "guide-programme-${channel.id}-none",
                )
            } else {
                val visible = channel.programmes.filter { programme ->
                    programme.stopEpochMillis > windowStart && programme.startEpochMillis < windowEnd
                }
                visible.forEachIndexed { index, programme ->
                    val clippedStart = max(programme.startEpochMillis, windowStart)
                    // A few providers publish a corrected entry before the old
                    // one has ended. Give the later start the boundary in the
                    // layout, while retaining the programme's real stop time
                    // for the hero and catch-up request.
                    val clippedEnd = minOf(
                        programme.stopEpochMillis,
                        windowEnd,
                        visible.getOrNull(index + 1)?.startEpochMillis ?: Long.MAX_VALUE,
                    )
                    if (clippedEnd <= clippedStart) return@forEachIndexed
                    val startMinutes = (clippedStart - windowStart).toFloat() / MINUTE_MILLIS
                    val durationMinutes = (clippedEnd - clippedStart).toFloat() / MINUTE_MILLIS
                    ProgrammeCell(
                        title = programme.title,
                        subtitle = formatRange(
                            programme.startEpochMillis,
                            programme.stopEpochMillis,
                            null,
                        ),
                        x = minuteWidth * startMinutes,
                        // The block owns exactly its real time slot. The old
                        // minimum width deliberately spilled short entries over
                        // the programme beginning at their end timestamp.
                        width = minuteWidth * durationMinutes,
                        selected = selection?.channel?.id == channel.id &&
                            selection.programme?.id == programme.id,
                        airing = programme.isLive(now),
                        past = programme.stopEpochMillis <= now,
                        progress = programme.progressAt(now),
                        accent = genreAccent(programme.categories),
                        onFocus = { onSelection(programme) },
                        onClick = { onPlay(programme) },
                        onPageForward = onPageForward.takeIf { index == visible.lastIndex },
                        onPageBack = onPageBack.takeIf { index == 0 },
                        pagedFocusRequester = pagedFocusRequester?.takeIf {
                            index == if (pagedFocusAtEnd) visible.lastIndex else 0
                        },
                        testTag = "guide-programme-${programme.id}",
                    )
                }
            }
        }
    }
}

/** Number, logo, name and what the provider says the feed is. */
@Composable
private fun GuideChannelCell(
    number: Int,
    channel: GuideTimelineChannel,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    returnFocusRequester: FocusRequester?,
    onOpenGroupRail: () -> Unit,
) {
    val typography = StreamMateThemeTokens.typography
    var focused by remember(channel.id) { mutableStateOf(false) }
    val colors = tvSurfaceColors(
        focused = focused,
        selected = selected,
        resting = Color.Transparent,
    )
    val background by animateColorAsState(colors.background, label = "channel background")
    Row(
        modifier = Modifier
            .width(CHANNEL_COLUMN_WIDTH)
            .fillMaxHeight()
            .clip(StreamMateThemeTokens.shapes.small)
            .background(background)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .then(returnFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onOpenGroupRail()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .focusable()
            .testTag("guide-channel-${channel.id}")
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            modifier = Modifier.width(20.dp),
            color = colors.secondaryContent,
            fontSize = typography.caption.fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        ChannelLogo(channel.logoUrl, channel.name, CHANNEL_LOGO_SIZE, focused = focused)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // A name the column cannot hold on one line, such as a long custom
            // name, wraps onto a second line in place of the feed line instead
            // of being cut short; the row keeps its height.
            var nameWraps by remember(channel.name) { mutableStateOf(false) }
            Text(
                text = channel.name,
                color = colors.content,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (nameWraps) 13.sp else typography.label.fontSize,
                lineHeight = if (nameWraps) 15.sp else 17.sp,
                maxLines = if (nameWraps) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                // An ellipsised single line does not count as overflow; ask for the line itself.
                onTextLayout = { layout -> if (!nameWraps && (layout.isLineEllipsized(0) || layout.hasVisualOverflow)) nameWraps = true },
            )
            // What the provider says about the feed - quality and language -
            // read off the channel name, the same markers the stream switcher
            // shows. Falls back to the group when a name says nothing.
            val streamTags = remember(channel.name) {
                ChannelStreamTags.read(channel.name).joinToString(" · ") { it.label }
            }
            if (!nameWraps) Text(
                text = streamTags.ifBlank { channel.groupTitle ?: channel.sourceName },
                color = colors.secondaryContent,
                fontSize = typography.caption.fontSize,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ChannelLogo(
    url: String?,
    channelName: String,
    size: Dp,
    focused: Boolean = false,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Box(
        modifier = Modifier
            .size(size)
            .clip(StreamMateThemeTokens.shapes.small)
            .background(if (focused) palette.background.copy(alpha = 0.10f) else palette.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = channelName.take(2).uppercase(),
                color = if (focused) palette.background else palette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = typography.caption.fontSize,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(3.dp),
            )
        }
    }
}

/**
 * One programme block.
 *
 * Resting is the quietest step on the ladder, what is on now sits one step up
 * with a cyan line along its foot, and the one being read flips to the
 * off-white fill - the same focus rule as everywhere else in the app. What has
 * already finished is dimmed rather than restyled.
 */
@Composable
private fun ProgrammeCell(
    title: String,
    subtitle: String,
    x: Dp,
    width: Dp,
    selected: Boolean,
    airing: Boolean,
    past: Boolean,
    progress: Float?,
    accent: Color?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    testTag: String,
    onPageForward: (() -> Unit)? = null,
    onPageBack: (() -> Unit)? = null,
    pagedFocusRequester: FocusRequester? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val shape = StreamMateThemeTokens.shapes.small
    Box(
        modifier = Modifier
            .zIndex(if (selected) 1f else 0f)
            .then(pagedFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            // Right at the last programme on screen moves time rather than
            // doing nothing. Pressing right to see what is on later is the
            // first thing anyone tries, and the transport keys this used to
            // need are not on the remote that ships with the box.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> onPageForward?.let { it(); true } ?: false
                    Key.DirectionLeft -> onPageBack?.let { it(); true } ?: false
                    else -> false
                }
            }
            .offset(x = x)
            .width(width)
            .fillMaxHeight()
            .padding(end = GRID_ROW_GAP)
            .alpha(if (past && !selected) PAST_PROGRAMME_ALPHA else 1f)
            .clip(shape)
            .background(
                when {
                    selected -> palette.textPrimary
                    airing -> palette.surface
                    else -> palette.surfaceSubtle
                },
            )
            // Draw the live progress after the focus indication and the card
            // contents. A focused clickable can add its own draw layer; when
            // the strip was a child that layer tinted it nearly black.
            .drawWithContent {
                drawContent()
                progress?.let { fraction ->
                    val stripHeight = PROGRAMME_PROGRESS_HEIGHT.toPx()
                    drawRect(
                        color = palette.focus,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            x = 0f,
                            y = size.height - stripHeight,
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            width = size.width * fraction.coerceIn(0f, 1f),
                            height = stripHeight,
                        ),
                    )
                }
            }
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(onClick = onClick)
            .focusable()
            .testTag(testTag),
    ) {
        accent?.takeIf { !selected }?.let { colour ->
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(colour),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (accent != null && !selected) 10.dp else 8.dp, end = 8.dp, top = 4.dp),
        ) {
            Text(
                text = title,
                color = if (selected) palette.background else palette.textPrimary,
                fontSize = typography.caption.fontSize,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = when {
                    selected -> palette.background.copy(alpha = 0.62f)
                    airing -> palette.focus
                    else -> palette.textDim
                },
                fontSize = typography.caption.fontSize,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PROGRAMME_PROGRESS_HEIGHT = 3.dp

/**
 * The date the window falls on, as a person would say it.
 *
 * Paired with [formatWindowDay], which adds "today" or "now" beside it; a bare
 * time would leave paging forward looking like the same evening with different
 * programmes in it.
 */
@Composable
private fun formatWindowDate(windowStart: Long, timeZoneId: String): String {
    val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
    return WINDOW_DAY_FORMATTER.withZone(zone).format(Instant.ofEpochMilli(windowStart))
}

@Composable
private fun formatWindowDay(windowStart: Long, now: Long, timeZoneId: String): String {
    if (GuideTimeWindow.isAtNow(windowStart, now)) return stringResource(R.string.guide_window_now)
    val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
    val day = Instant.ofEpochMilli(windowStart).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> stringResource(R.string.guide_window_today)
        today.plusDays(1) -> stringResource(R.string.guide_window_tomorrow)
        today.minusDays(1) -> stringResource(R.string.guide_window_yesterday)
        else -> ""
    }
}

private const val PAST_PROGRAMME_ALPHA = 0.55f
private val CHANNEL_LOGO_SIZE = 30.dp
