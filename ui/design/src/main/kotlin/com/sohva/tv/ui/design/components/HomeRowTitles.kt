package com.sohva.tv.ui.design.components

import androidx.annotation.StringRes
import com.sohva.tv.ui.design.R

/**
 * The title of a Home row by its layout id (spec 02 HOME-FR-02, -94), for Home and Settings › Home
 * alike. The ids are HomeLayout's; they are spelled out here because the design system does not
 * depend on the model.
 */
@StringRes
fun homeRowTitle(id: String): Int = when (id) {
    "continue-watching" -> R.string.home_continue_watching
    "watch-next" -> R.string.home_watch_next
    "todays-sport" -> R.string.home_sports_today
    "recommended" -> R.string.home_recommended
    "trakt:watchlist-movies" -> R.string.trakt_row_watchlist_movies
    "trakt:watchlist-shows" -> R.string.trakt_row_watchlist_shows
    "trakt:trending-movies" -> R.string.trakt_row_trending_movies
    "trakt:trending-shows" -> R.string.trakt_row_trending_shows
    "trakt:popular-movies" -> R.string.trakt_row_popular_movies
    "trakt:popular-shows" -> R.string.trakt_row_popular_shows
    "trakt:anticipated-movies" -> R.string.trakt_row_anticipated_movies
    "trakt:anticipated-shows" -> R.string.trakt_row_anticipated_shows
    "trakt:boxoffice" -> R.string.trakt_row_box_office
    "recent-channels" -> R.string.home_recent_channels
    // A public list's row, before its name is known (HOME-FR-99).
    else -> R.string.trakt_row_list
}
