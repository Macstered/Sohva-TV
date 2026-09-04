package com.streammate.tv.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvTagChip
import com.streammate.tv.feature.common.TvTagTone
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.metadata.EnrichedMetadata

/**
 * The hero: what the block under the cursor actually is.
 *
 * Two parts, as in the reference. On the left a 16:9 still with the channel and
 * programme written across its foot and a cyan line showing how far in it is.
 * On the right the title, synopsis, facts and the handful of things that can be
 * done with it. A second poster beside the 16:9 still only crowded the synopsis.
 *
 * Nothing here is invented. A fact with no value behind it - no year, no
 * rating, no description - is left out rather than filled in.
 */
@Composable
internal fun GuideHero(
    selection: GuideSelection,
    channelNumber: Int?,
    now: Long,
    timeZoneId: String,
    favourite: Boolean,
    metadata: EnrichedMetadata?,
    onWatch: () -> Unit,
    onToggleFavourite: () -> Unit,
    onPlayCatchup: (() -> Unit)?,
    onSearch: () -> Unit,
    searchVisible: Boolean,
    onOpenMetadata: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = StreamMateThemeTokens.spacing
    val programme = selection.programme
    val live = programme?.isLive(now) == true
    Row(modifier = modifier.fillMaxWidth().height(GUIDE_HERO_HEIGHT)) {
        GuideHeroStill(
            selection = selection,
            channelNumber = channelNumber,
            live = live,
            progress = programme?.progressAt(now),
            metadata = metadata,
        )
        Spacer(Modifier.width(spacing.lg))
        GuideHeroDetail(
            selection = selection,
            live = live,
            now = now,
            timeZoneId = timeZoneId,
            favourite = favourite,
            metadata = metadata,
            onWatch = onWatch,
            onToggleFavourite = onToggleFavourite,
            onPlayCatchup = onPlayCatchup,
            onSearch = onSearch,
            searchVisible = searchVisible,
            onOpenMetadata = onOpenMetadata,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The still.
 *
 * A backdrop is the artwork shaped for a broadcast, so it is preferred; a
 * poster stretched to 16:9 is a crop of somebody's face. When there is neither,
 * the channel's own logo sits on a quiet surface at logo shape rather than
 * being blown up to fill the frame.
 */
@Composable
private fun GuideHeroStill(
    selection: GuideSelection,
    channelNumber: Int?,
    live: Boolean,
    progress: Float?,
    metadata: EnrichedMetadata?,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val still = metadata?.backdropUrl?.takeIf(String::isNotBlank)
        ?: metadata?.posterUrl?.takeIf(String::isNotBlank)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(HERO_STILL_ASPECT)
            .clip(StreamMateThemeTokens.shapes.large)
            .background(palette.surfaceSubtle)
            .testTag("guide-hero-still"),
    ) {
        if (still != null) {
            AsyncImage(
                model = still,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChannelLogo(
                    url = selection.channel.logoUrl,
                    channelName = selection.channel.name,
                    size = HERO_FALLBACK_LOGO,
                )
            }
        }
        // The scrim only exists so the two lines below it stay readable, so it
        // starts halfway down and leaves the top of the picture alone.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.46f to palette.background.copy(alpha = 0f),
                    1f to palette.background.copy(alpha = 0.92f),
                ),
            ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Box(
                        Modifier
                            .size(HERO_LIVE_DOT)
                            .clip(CircleShape)
                            .background(palette.danger),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = listOfNotNull(
                        selection.channel.name,
                        channelNumber?.let { stringResource(R.string.guide_channel_number, it) },
                    ).joinToString(HERO_SEPARATOR).uppercase(),
                    color = palette.textPrimary,
                    fontSize = typography.overline.fontSize,
                    lineHeight = typography.overline.lineHeight,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = typography.overline.letterSpacing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = selection.programme?.title ?: selection.channel.name,
                color = palette.textPrimary,
                fontSize = typography.bodyLarge.fontSize,
                lineHeight = typography.bodyLarge.lineHeight,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        progress?.let { fraction ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(palette.textPrimary.copy(alpha = 0.18f)),
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(palette.focus))
            }
        }
    }
}

@Composable
private fun GuideHeroDetail(
    selection: GuideSelection,
    live: Boolean,
    now: Long,
    timeZoneId: String,
    favourite: Boolean,
    metadata: EnrichedMetadata?,
    onWatch: () -> Unit,
    onToggleFavourite: () -> Unit,
    onPlayCatchup: (() -> Unit)?,
    onSearch: () -> Unit,
    searchVisible: Boolean,
    onOpenMetadata: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val programme = selection.programme
    Column(modifier = modifier.fillMaxHeight()) {
            Text(
                text = programme?.title ?: selection.channel.name,
                modifier = Modifier.testTag("guide-hero-title"),
                color = palette.textPrimary,
                fontSize = typography.headline.fontSize,
                lineHeight = typography.headline.lineHeight,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (live) {
                    TvTagChip(
                        label = stringResource(R.string.guide_live),
                        tone = TvTagTone.LIVE,
                    )
                }
                // Only what the record carries. A missing year or rating is
                // left out; there is nothing here to fill it in with.
                val facts = listOfNotNull(
                    programme?.let { formatRange(it.startEpochMillis, it.stopEpochMillis, timeZoneId) },
                    programme?.categories?.firstOrNull()?.takeIf(String::isNotBlank),
                    metadata?.year?.toString(),
                ).joinToString(HERO_SEPARATOR)
                if (facts.isNotBlank()) {
                    Text(
                        text = facts,
                        color = palette.textMuted,
                        fontSize = typography.label.fontSize,
                        lineHeight = typography.label.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                metadata?.rating?.takeIf(String::isNotBlank)?.let { rating ->
                    TvTagChip(
                        label = stringResource(R.string.guide_rating, rating),
                        tone = TvTagTone.RATING,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = metadata?.overview?.takeIf(String::isNotBlank)
                    ?: programme?.description?.takeIf(String::isNotBlank)
                    ?: programme?.subtitle?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.guide_programme_no_details),
                color = palette.textMuted,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvActionButton(
                    label = stringResource(R.string.action_watch),
                    icon = TvIcons.Play,
                    onClick = onWatch,
                    compact = true,
                    testTag = "guide-preview-watch",
                )
                TvActionButton(
                    label = if (favourite) {
                        stringResource(R.string.guide_favourite)
                    } else {
                        stringResource(R.string.guide_add_favourite)
                    },
                    icon = if (favourite) TvIcons.Star else TvIcons.StarOutline,
                    onClick = onToggleFavourite,
                    compact = true,
                    selected = favourite,
                    testTag = "guide-toggle-favourite",
                )
                // Catch-up appears only where the source says the channel has
                // it and the programme is inside the window it reaches back to.
                if (onPlayCatchup != null) {
                    TvActionButton(
                        label = if (live) {
                            stringResource(R.string.guide_watch_from_start)
                        } else {
                            stringResource(R.string.guide_watch_recording)
                        },
                        icon = TvIcons.Replay,
                        onClick = onPlayCatchup,
                        compact = true,
                        testTag = "guide-preview-catchup",
                    )
                }
                TvActionButton(
                    label = if (searchVisible) {
                        stringResource(R.string.guide_close_search)
                    } else {
                        stringResource(R.string.guide_find_programme)
                    },
                    icon = TvIcons.Search,
                    onClick = onSearch,
                    compact = true,
                    selected = searchVisible,
                    testTag = "guide-search-toggle",
                )
                if (metadata != null && onOpenMetadata != null) {
                    TvActionButton(
                        label = stringResource(R.string.metadata_source, metadata.attributionName),
                        icon = TvIcons.Info,
                        onClick = onOpenMetadata,
                        compact = true,
                        testTag = "guide-metadata-attribution",
                    )
                }
            }
    }
}

private const val HERO_SEPARATOR = "  ·  "
private const val HERO_STILL_ASPECT = 16f / 9f
private val HERO_LIVE_DOT = 8.dp
private val HERO_FALLBACK_LOGO = 56.dp
