package com.streammate.tv.feature.catalogue

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.TvTagChip
import com.streammate.tv.feature.common.TvTagTone
import com.streammate.tv.iptv.R
import com.streammate.tv.iptv.metadata.MetadataCastMember
import com.streammate.tv.iptv.repository.WatchingProgress

/**
 * The breadcrumb across the top: where this title sits, not a page heading.
 *
 * Every part of it is a real navigation value - the library it came from, the
 * provider's own category, the title itself - so it says where Back goes as
 * well as what is on screen. There is no Back button beside it: the remote's
 * own key leaves, and a button that duplicates a hardware key is a button that
 * costs a row of artwork.
 */
@Composable
internal fun CatalogueDetailBreadcrumb(
    library: String,
    category: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val crumbs = remember(library, category, title) {
        listOfNotNull(library, category?.takeIf(String::isNotBlank), title)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Text(
                    text = BREADCRUMB_SEPARATOR,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = palette.textDisabled,
                    fontSize = typography.label.fontSize,
                    lineHeight = typography.label.lineHeight,
                )
            }
            Text(
                text = crumb.uppercase(),
                color = if (index == crumbs.lastIndex) palette.textMuted else palette.textDim,
                fontSize = typography.label.fontSize,
                lineHeight = typography.label.lineHeight,
                fontWeight = FontWeight.Bold,
                letterSpacing = typography.overline.letterSpacing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * The gold score, then the plain facts, then whatever the provider wrote into
 * the file name about the picture.
 *
 * Only what a record actually holds. A film with no year gets no year; the row
 * closes up around what is missing rather than printing a dash.
 */
@Composable
internal fun CatalogueDetailFacts(
    rating: String?,
    facts: List<String>,
    qualityTags: List<String>,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    if (rating == null && facts.isEmpty() && qualityTags.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rating?.takeIf(String::isNotBlank)?.let { score ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(TvIcons.Star),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(palette.rating),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = score,
                    color = palette.rating,
                    fontSize = typography.bodyLarge.fontSize,
                    lineHeight = typography.bodyLarge.lineHeight,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (facts.isNotEmpty()) {
            Text(
                text = facts.joinToString(FACT_SEPARATOR),
                color = palette.textMuted,
                fontSize = typography.bodyLarge.fontSize,
                lineHeight = typography.bodyLarge.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        qualityTags.forEach { tag -> CatalogueMetadataPill(tag) }
    }
}

/**
 * A fact about a film or series: its year, its runtime, how good the picture is.
 *
 * Delegates to the shared chip so a film's pills and a stream's quality chips
 * are the same object rather than two that drift. Picture quality reads louder
 * than the rest, because between two copies of the same film it is the thing
 * being chosen on.
 */
@Composable
internal fun CatalogueMetadataPill(label: String) {
    TvTagChip(
        label = label,
        tone = if (label in QUALITY_LABELS) TvTagTone.PRIMARY else TvTagTone.MUTED,
    )
}

/**
 * How far in, in words and as a line.
 *
 * Drawn only where there is a stored position and a known running time; without
 * a duration there is no fraction to show and nothing honest to say.
 */
@Composable
internal fun CatalogueDetailProgress(
    positionMillis: Long,
    durationMillis: Long,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    if (durationMillis <= 0L || positionMillis <= 0L) return
    val fraction = (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    val remaining = (durationMillis - positionMillis).coerceAtLeast(0L)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                R.string.details_watched_of,
                formatRuntime(millisToMinutes(positionMillis)),
                formatRuntime(millisToMinutes(durationMillis)),
            ),
            color = palette.textDim,
            fontSize = typography.label.fontSize,
            lineHeight = typography.label.lineHeight,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .width(DETAIL_PROGRESS_WIDTH)
                .height(4.dp)
                .clip(StreamMateThemeTokens.shapes.small)
                .background(palette.surfaceRaised)
                .testTag("details-progress"),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(palette.focus))
        }
        Text(
            text = stringResource(R.string.details_time_left, formatRuntime(millisToMinutes(remaining))),
            color = palette.textDim,
            fontSize = typography.label.fontSize,
            lineHeight = typography.label.lineHeight,
            maxLines = 1,
        )
    }
}

/**
 * A detail-page action. Bigger than a row control and otherwise the same
 * surface as everything else: borderless at rest, off-white fill on focus.
 */
@Composable
internal fun CatalogueDetailAction(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    testTag: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    TvSurface(
        onClick = onClick,
        modifier = modifier.height(DETAIL_ACTION_HEIGHT),
        shape = StreamMateThemeTokens.shapes.medium,
        // Primary sits a step higher on the ladder rather than being filled
        // white at rest: white is what focus means here, and a button already
        // white has nowhere left to go when it is pointed at.
        resting = if (primary) palette.surfaceRaised else palette.surface,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        focusRequester = focusRequester,
        testTag = testTag,
        contentPadding = PaddingValues(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.content),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = colors.content,
                fontSize = typography.body.fontSize,
                lineHeight = typography.body.lineHeight,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/**
 * One face from the cast.
 *
 * Circular and borderless, with the actor above the part they played. A missing
 * portrait falls back to initials rather than to an empty circle, which is the
 * common case on a scraped library.
 */
@Composable
internal fun CatalogueCastMember(member: MetadataCastMember) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val description = member.character?.takeIf(String::isNotBlank)?.let { character ->
        stringResource(R.string.details_cast_member, member.name, character)
    } ?: member.name
    Column(
        modifier = Modifier.width(CAST_COLUMN_WIDTH).semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(CAST_AVATAR_SIZE)
                .clip(CircleShape)
                .background(palette.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = catalogueInitials(member.name),
                color = palette.textMuted,
                fontSize = typography.label.fontSize,
                fontWeight = FontWeight.Black,
            )
            if (!member.profileUrl.isNullOrBlank()) {
                AsyncImage(
                    model = member.profileUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = member.name,
            modifier = Modifier.padding(top = 8.dp),
            color = palette.textPrimary,
            fontSize = typography.label.fontSize,
            lineHeight = typography.label.lineHeight,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        member.character?.takeIf(String::isNotBlank)?.let { character ->
            Text(
                text = character,
                modifier = Modifier.padding(top = 2.dp),
                color = palette.textDim,
                fontSize = typography.caption.fontSize,
                lineHeight = typography.caption.lineHeight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A poster in a bottom strip: another film, or another episode's still.
 *
 * The artwork is the card. Its title sits over a scrim along the foot rather
 * than in a panel below, so a row of them reads as pictures, and focus is the
 * off-white ring the rest of the app uses.
 */
@Composable
internal fun CatalogueArtworkCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = SIMILAR_CARD_WIDTH,
    aspectRatio: Float = POSTER_ASPECT,
    testTag: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    var focused by remember(title) { mutableStateOf(false) }
    val shape = StreamMateThemeTokens.shapes.medium
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "artwork card scale")
    val tagModifier = testTag?.let { Modifier.testTag(it) } ?: Modifier
    Box(
        modifier = modifier
            .width(width)
            .then(tagModifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .semantics { contentDescription = title },
    ) {
        // The lift lives on a child layer: a transform on the focusable node
        // itself changes the bounds it reports, and the row then scrolls to fit
        // them, shifting every sibling on each press.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(palette.surfaceSubtle)
                .then(if (focused) Modifier.border(3.dp, palette.textPrimary, shape) else Modifier),
        ) {
            if (artworkUrl.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = catalogueInitials(title),
                        color = palette.textMuted,
                        fontSize = typography.headline.fontSize,
                        fontWeight = FontWeight.Black,
                    )
                }
            } else {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.48f to palette.background.copy(alpha = 0f),
                        1f to palette.background.copy(alpha = 0.86f),
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
                    text = title,
                    color = palette.textPrimary,
                    fontSize = typography.caption.fontSize,
                    lineHeight = typography.caption.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 2.dp),
                        color = palette.textMuted,
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

/** A row heading on a detail page. */
/**
 * One copy of a film, offered so that it can be played instead of the one the
 * wall happened to show.
 *
 * Nothing is hidden and nothing is ranked: which copy is better varies by
 * title - a 4K copy with no Finnish audio is the wrong one in this house and
 * the right one in the next - and the app is in no position to be certain. What
 * it can do is say what each copy claims and let the choice be made.
 */
@Composable
internal fun CatalogueCopyCard(
    sourceName: String,
    claims: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val description = stringResource(R.string.details_version_play)
    TvSurface(
        onClick = onClick,
        modifier = modifier.width(COPY_CARD_WIDTH),
        shape = StreamMateThemeTokens.shapes.medium,
        // The copy on screen sits a step higher at rest, so which one is being
        // shown is readable without moving focus onto it.
        resting = if (current) palette.surfaceRaised else palette.surface,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        testTag = testTag,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopStart,
    ) { colors ->
        Column(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sourceName,
                    color = colors.content,
                    fontSize = typography.body.fontSize,
                    lineHeight = typography.body.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (current) {
                    Spacer(Modifier.width(8.dp))
                    TvTagChip(
                        label = stringResource(R.string.details_version_current),
                        tone = TvTagTone.ACCENT,
                    )
                }
            }
            Text(
                text = claims,
                color = colors.content.copy(alpha = 0.72f),
                fontSize = typography.caption.fontSize,
                lineHeight = typography.caption.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What a copy says it is, in one line: the languages it announces and then the
 * picture it claims. A copy that announces nothing shows the name the provider
 * gave it, which is at least something to tell it apart by.
 */
@Composable
internal fun catalogueCopySummary(title: String): String {
    val claims = remember(title) { catalogueCopyClaims(title) }
    val languages = claims.languages.map { language -> stringResource(catalogueCopyLanguageLabel(language)) }
    val spoken = languages + claims.picture
    return if (spoken.isEmpty()) title else spoken.joinToString(FACT_SEPARATOR_THIN)
}

@StringRes
private fun catalogueCopyLanguageLabel(language: CatalogueCopyLanguage): Int = when (language) {
    CatalogueCopyLanguage.FINNISH -> R.string.copy_language_finnish
    CatalogueCopyLanguage.SWEDISH -> R.string.copy_language_swedish
    CatalogueCopyLanguage.ENGLISH -> R.string.copy_language_english
    CatalogueCopyLanguage.DANISH -> R.string.copy_language_danish
    CatalogueCopyLanguage.NORWEGIAN -> R.string.copy_language_norwegian
    CatalogueCopyLanguage.GERMAN -> R.string.copy_language_german
    CatalogueCopyLanguage.FRENCH -> R.string.copy_language_french
    CatalogueCopyLanguage.SPANISH -> R.string.copy_language_spanish
    CatalogueCopyLanguage.NORDIC -> R.string.copy_language_nordic
    CatalogueCopyLanguage.SUBTITLED -> R.string.copy_language_subtitled
    CatalogueCopyLanguage.MULTIPLE_AUDIO -> R.string.copy_language_multiple_audio
}

@Composable
internal fun CatalogueDetailSectionHeading(text: String, modifier: Modifier = Modifier) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Text(
        text = text,
        modifier = modifier,
        color = palette.textPrimary,
        fontSize = typography.headline.fontSize,
        lineHeight = typography.headline.lineHeight,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * A running time as a person would say it: minutes on their own under an hour,
 * hours and minutes above it.
 */
@Composable
internal fun formatRuntime(minutes: Int): String = if (minutes >= MINUTES_PER_HOUR) {
    stringResource(
        R.string.details_runtime_hours,
        minutes / MINUTES_PER_HOUR,
        minutes % MINUTES_PER_HOUR,
    )
} else {
    stringResource(R.string.series_episode_duration_minutes, minutes)
}

/**
 * Where playback would pick up, or null when there is nowhere to pick up from.
 *
 * The distinction decides the whole action row: with a position there is a
 * Resume and a Start-from-beginning beside it, and without one a single Watch.
 * Something finished counts as nowhere - it starts again rather than resuming
 * onto its own credits.
 */
internal fun catalogueResumePosition(progress: WatchingProgress?): Long? = progress
    ?.takeIf { !it.completed && it.positionMillis > 0L }
    ?.resumePositionMillis
    ?.takeIf { it > 0L }

/** Rounded up, so a title one second in does not read as zero minutes watched. */
internal fun millisToMinutes(millis: Long): Int =
    ((millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0)

internal fun catalogueQualityTags(value: String): List<String> {
    val normalized = value.uppercase()
    return buildList {
        if (QUALITY_4K.containsMatchIn(normalized)) add("4K UHD")
        when {
            "DOLBY VISION" in normalized || "DOVI" in normalized -> add("Dolby Vision")
            "HDR10+" in normalized -> add("HDR10+")
            "HDR10" in normalized || QUALITY_HDR.containsMatchIn(normalized) -> add("HDR10")
        }
    }
}

private val QUALITY_LABELS = setOf("4K UHD", "Dolby Vision", "HDR10+", "HDR10")

private val QUALITY_4K = Regex("(?:^|[^A-Z0-9])(?:4K|UHD)(?:[^A-Z0-9]|$)")
private val QUALITY_HDR = Regex("(?:^|[^A-Z0-9])HDR(?:[^A-Z0-9]|$)")

private const val BREADCRUMB_SEPARATOR = "›"
private const val FACT_SEPARATOR = "  ·  "
private const val FACT_SEPARATOR_THIN = " · "
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
private const val POSTER_ASPECT = 2f / 3f

internal val DETAIL_ACTION_HEIGHT = 48.dp
internal val DETAIL_PROGRESS_WIDTH = 220.dp
internal val SIMILAR_CARD_WIDTH = 104.dp
internal val COPY_CARD_WIDTH = 208.dp
internal val CAST_AVATAR_SIZE = 52.dp
internal val CAST_COLUMN_WIDTH = 84.dp
