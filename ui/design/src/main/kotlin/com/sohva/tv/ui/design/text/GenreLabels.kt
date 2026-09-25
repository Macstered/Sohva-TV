package com.sohva.tv.ui.design.text

import androidx.annotation.StringRes
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.ui.design.R

/** The genre's label (`genre_*`, spec 40 VOD-FR-12): the wall rail, the header and Settings' groups of your own. */
@StringRes
fun genreLabel(genre: Genre): Int = when (genre) {
    Genre.ACTION -> R.string.genre_action
    Genre.ADVENTURE -> R.string.genre_adventure
    Genre.ANIMATION -> R.string.genre_animation
    Genre.COMEDY -> R.string.genre_comedy
    Genre.CRIME -> R.string.genre_crime
    Genre.DOCUMENTARY -> R.string.genre_documentary
    Genre.DRAMA -> R.string.genre_drama
    Genre.FAMILY -> R.string.genre_family
    Genre.FANTASY -> R.string.genre_fantasy
    Genre.HISTORY -> R.string.genre_history
    Genre.HORROR -> R.string.genre_horror
    Genre.MUSIC -> R.string.genre_music
    Genre.MYSTERY -> R.string.genre_mystery
    Genre.NEWS -> R.string.genre_news
    Genre.REALITY -> R.string.genre_reality
    Genre.ROMANCE -> R.string.genre_romance
    Genre.SCIENCE_FICTION -> R.string.genre_science_fiction
    Genre.SOAP -> R.string.genre_soap
    Genre.TALK -> R.string.genre_talk
    Genre.THRILLER -> R.string.genre_thriller
    Genre.WAR -> R.string.genre_war
    Genre.WESTERN -> R.string.genre_western
}
