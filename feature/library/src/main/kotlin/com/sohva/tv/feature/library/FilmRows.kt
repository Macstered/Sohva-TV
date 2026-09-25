package com.sohva.tv.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.model.vod.CopyLanguage
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.TagTone
import com.sohva.tv.ui.design.components.TvTagChip
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Versions (VOD-FR-68, layout §2 "Version card"): only with two or more copies. */
@Composable
internal fun VersionsRow(versions: List<VersionCard>, onPlay: (String) -> Unit) {
    if (versions.size < 2) return
    SectionHeading(stringResource(R.string.details_versions))
    LazyRow(Modifier.testTag("details-versions"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(versions, key = { it.key }) { VersionCardView(it, onPlay) }
    }
}

@Composable
private fun VersionCardView(card: VersionCard, onPlay: (String) -> Unit) {
    val palette = Sohva.palette
    val languages = card.languages.map { stringResource(languageLabel(it)) }
    val claims = remember(languages, card.picture, card.name) {
        (languages + card.picture).joinToString(" · ").ifEmpty { card.name }
    }
    val play = stringResource(R.string.details_version_play)
    val style = SurfaceStyle(
        corner = Sohva.shapes.medium,
        resting = if (card.current) palette.surfaceRaised else palette.surface,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    )
    TvSurface(
        onClick = { onPlay(card.key) },
        modifier = Modifier.width(208.dp).semantics { contentDescription = play }.testTag("details-version-${card.key}"),
        state = SurfaceState(),
        style = style,
    ) { colors ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    card.sourceName,
                    Modifier.weight(1f, fill = false),
                    style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold),
                    color = colors.content,
                    maxLines = 1,
                )
                if (card.current) TvTagChip(stringResource(R.string.details_version_current), tone = TagTone.ACCENT)
            }
            Text(claims, style = Sohva.typography.caption, color = colors.content.copy(alpha = 0.72f), maxLines = 2)
        }
    }
}

@StringRes
private fun languageLabel(language: CopyLanguage): Int = when (language) {
    CopyLanguage.FINNISH -> R.string.copy_language_finnish
    CopyLanguage.SWEDISH -> R.string.copy_language_swedish
    CopyLanguage.ENGLISH -> R.string.copy_language_english
    CopyLanguage.DANISH -> R.string.copy_language_danish
    CopyLanguage.NORWEGIAN -> R.string.copy_language_norwegian
    CopyLanguage.GERMAN -> R.string.copy_language_german
    CopyLanguage.FRENCH -> R.string.copy_language_french
    CopyLanguage.SPANISH -> R.string.copy_language_spanish
    CopyLanguage.NORDIC -> R.string.copy_language_nordic
    CopyLanguage.SUBTITLED -> R.string.copy_language_subtitled
    CopyLanguage.MULTIPLE_AUDIO -> R.string.copy_language_multiple_audio
}

/** Cast (VOD-FR-69, layout §2 "Cast member"): not focusable, so the row only scrolls with the page. */
@Composable
internal fun CastRow(cast: List<CastCard>) {
    if (cast.isEmpty()) return
    SectionHeading(stringResource(R.string.series_cast))
    Row(Modifier.testTag("details-cast"), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        cast.forEach { CastMember(it) }
    }
}

@Composable
private fun CastMember(member: CastCard) {
    val description = if (member.character != null) stringResource(R.string.details_cast_member, member.name, member.character) else member.name
    Column(Modifier.width(84.dp).semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(member)
        Text(
            member.name,
            Modifier.padding(top = 8.dp),
            style = Sohva.typography.label.copy(fontWeight = FontWeight.SemiBold),
            color = Sohva.palette.textPrimary,
            maxLines = 2,
        )
        if (member.character != null) Text(member.character, style = Sohva.typography.caption, color = Sohva.palette.textDim, maxLines = 1)
    }
}

/** A 52 dp circle: the initials on `surface`, the photo drawn over them once it has loaded. */
@Composable
private fun Avatar(member: CastCard) {
    val loader = LocalArtwork.current
    val px = with(LocalDensity.current) { 52.dp.roundToPx() }
    var image by remember(member.photoUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(member.photoUrl) {
        member.photoUrl?.let { image = loader.load(it, px, px, opaque = true) }
    }
    val fill = Sohva.palette.surface
    Box(
        Modifier.size(52.dp).clip(CircleShape).drawBehind { drawRect(fill) }.drawWithContent {
            drawContent()
            image?.let { drawCropped(it) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(member.initials, style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textMuted)
    }
}

/** Similar (VOD-FR-70): only after the details lookup; a placeholder while checking or when nothing is found. */
@Composable
internal fun SimilarRow(state: SimilarState, onOpen: (String) -> Unit) {
    if (state == SimilarState.Hidden) return
    SectionHeading(stringResource(R.string.details_similar))
    val cards = (state as? SimilarState.Ready)?.cards
    if (cards.isNullOrEmpty()) {
        Box(Modifier.fillMaxWidth().height(80.dp).testTag("details-similar-empty"), contentAlignment = Alignment.CenterStart) {
            Text(
                stringResource(if (cards == null) R.string.movie_similar_loading else R.string.movie_no_similar_available),
                style = Sohva.typography.body,
                color = Sohva.palette.textDim,
            )
        }
        return
    }
    val px = with(LocalDensity.current) { IntSize(104.dp.roundToPx(), 156.dp.roundToPx()) }
    LazyRow(Modifier.testTag("details-similar"), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
        items(cards, key = { it.key }) { SimilarCardView(it, px, onOpen) }
    }
}

/**
 * An artwork card (layout §2): 104 dp, the 2:3 poster with the title and year on it over a bottom
 * scrim, and, when focused, a 3 dp ring and 1.05 scale on a child layer (a transform on the
 * focusable node would change the bounds the row scrolls to).
 */
@Composable
private fun SimilarCardView(card: SimilarCard, px: IntSize, onOpen: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val palette = Sohva.palette
    val scrim = remember(palette) { Brush.verticalGradient(0.48f to palette.background.copy(alpha = 0f), 1f to palette.background.copy(alpha = 0.86f)) }
    Box(
        Modifier
            .width(104.dp)
            .testTag("details-similar-${card.key}")
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(card.key) }
            .focusable()
            .semantics { contentDescription = card.title },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    val scale = if (focused) 1.05f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .drawWithContent {
                    drawContent()
                    drawRect(scrim)
                    if (focused) drawRing(palette.textPrimary)
                },
        ) {
            Poster(card.title, card.posterUrl, px)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(card.title, style = Sohva.typography.caption.copy(fontWeight = FontWeight.Bold), color = palette.textPrimary, maxLines = 2)
            card.year?.let {
                Text(it.toString(), Modifier.padding(top = 2.dp), style = Sohva.typography.caption, color = palette.textMuted, maxLines = 1)
            }
        }
    }
}
