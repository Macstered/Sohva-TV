package com.sohva.tv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LibraryBadge
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * A Watch next or Recommended card (spec 02 HOME-FR-30, -31; spec 51 FR-28). [key] is
 * `<kind>:<trakt|tmdb|imdb id>:<season>:<number>`; ids open the library copy or the Trakt title page.
 */
@Immutable
data class TraktCard(
    val key: String,
    val next: Boolean,
    val show: Boolean,
    val title: String,
    val year: Int?,
    val season: Int?,
    val number: Int?,
    val episodeTitle: String?,
    val poster: String?,
    val fanart: String?,
    val overview: String?,
    val tmdb: Long?,
    val imdb: String?,
    /** The added row's layout id (HOME-FR-94); null on Watch next and Recommended. */
    val source: String? = null,
    /** The viewer's sources have this title (HOME-FR-98): the card shows "In library". */
    val owned: Boolean = false,
    /** A public list's own name for the hero's kicker (HOME-FR-99). */
    val sourceName: String? = null,
) {
    /** Watch next is landscape (fanart, else poster); Recommended a poster (poster, else fanart). */
    val image: String? get() = if (next) fanart ?: poster else poster ?: fanart
}

/**
 * Home's Trakt lists for the active profile (HOME-FR-30…32, -48), and the added rows' lists by
 * layout id (HOME-FR-94); empty for a restricted profile.
 */
@Immutable
data class TraktLists(
    val next: List<TraktCard>,
    val recommended: List<TraktCard>,
    val firstSync: Boolean,
    val rows: Map<String, List<TraktCard>> = emptyMap(),
    /** Public lists' own names by row id (HOME-FR-99). */
    val names: Map<String, String> = emptyMap(),
) {
    companion object {
        val EMPTY: TraktLists = TraktLists(emptyList(), emptyList(), false)
    }
}

private val POSTER_WIDTH = 124.dp
private val POSTER_W = 116.dp
private val POSTER_H = 178.dp
private val ART_W = 178.dp
private val ART_H = 102.dp

/** A Trakt card (spec 02 §5): Continue watching's landscape anatomy for Watch next, poster art for Recommended. */
@Composable
internal fun TraktCardView(card: TraktCard, modifier: Modifier, onClick: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, focusRing = true, focusScale = 1f, padding = PaddingValues(4.dp))
    TvSurface(onClick = onClick, modifier = modifier.width(if (card.next) LANDSCAPE_WIDTH else POSTER_WIDTH), style = style) {
        Column {
            Box(Modifier.size(if (card.next) ART_W else POSTER_W, if (card.next) ART_H else POSTER_H)) {
                CroppedArt(card.title, card.image, if (card.next) ART_W else POSTER_W, if (card.next) ART_H else POSTER_H)
                if (card.owned) LibraryBadge(Modifier.align(Alignment.TopStart).padding(6.dp).testTag("home-trakt-owned-${card.key}"))
            }
            Spacer(Modifier.height(8.dp))
            Text(card.title, style = Sohva.typography.label.copy(fontWeight = FontWeight.SemiBold), color = Sohva.palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(traktSubtitle(card), style = Sohva.typography.caption, color = Sohva.palette.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "S1 E2 · title" for Watch next, the year for Recommended (HOME-FR-30, -31). */
@Composable
internal fun traktSubtitle(card: TraktCard): String {
    if (!card.next) return card.year?.toString().orEmpty()
    return episodeLabel(card).orEmpty()
}

@Composable
private fun episodeLabel(card: TraktCard): String? {
    val season = card.season ?: return null
    val number = card.number ?: return null
    val code = stringResource(R.string.series_episode_label, season, number)
    return card.episodeTitle?.takeIf { it.isNotBlank() }?.let { "$code · $it" } ?: code
}

/** The hero's facts for a Trakt card (HOME-FR-63): episode label; year. */
@Composable
internal fun traktFacts(card: TraktCard): String = listOfNotNull(episodeLabel(card), card.year?.toString()).joinToString("  ·  ")
