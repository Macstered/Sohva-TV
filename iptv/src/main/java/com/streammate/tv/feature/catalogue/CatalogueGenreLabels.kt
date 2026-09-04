package com.streammate.tv.feature.catalogue

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.iptv.R

/** One localized name shared by the original and rewritten catalogue rails. */
@Composable
internal fun catalogueGenreLabel(genre: CatalogueGenre): String = stringResource(
    when (genre) {
        CatalogueGenre.ACTION -> R.string.genre_action
        CatalogueGenre.ADVENTURE -> R.string.genre_adventure
        CatalogueGenre.ANIMATION -> R.string.genre_animation
        CatalogueGenre.COMEDY -> R.string.genre_comedy
        CatalogueGenre.CRIME -> R.string.genre_crime
        CatalogueGenre.DOCUMENTARY -> R.string.genre_documentary
        CatalogueGenre.DRAMA -> R.string.genre_drama
        CatalogueGenre.FAMILY -> R.string.genre_family
        CatalogueGenre.FANTASY -> R.string.genre_fantasy
        CatalogueGenre.HISTORY -> R.string.genre_history
        CatalogueGenre.HORROR -> R.string.genre_horror
        CatalogueGenre.MUSIC -> R.string.genre_music
        CatalogueGenre.MYSTERY -> R.string.genre_mystery
        CatalogueGenre.NEWS -> R.string.genre_news
        CatalogueGenre.REALITY -> R.string.genre_reality
        CatalogueGenre.ROMANCE -> R.string.genre_romance
        CatalogueGenre.SCIENCE_FICTION -> R.string.genre_science_fiction
        CatalogueGenre.SOAP -> R.string.genre_soap
        CatalogueGenre.TALK -> R.string.genre_talk
        CatalogueGenre.THRILLER -> R.string.genre_thriller
        CatalogueGenre.WAR -> R.string.genre_war
        CatalogueGenre.WESTERN -> R.string.genre_western
    },
)
