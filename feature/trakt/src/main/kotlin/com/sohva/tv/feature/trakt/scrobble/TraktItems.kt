package com.sohva.tv.feature.trakt.scrobble

import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktItem

/**
 * Titles as Trakt items, by id only (spec 51 FR-12..14). Titles are never matched by name; ids a
 * rule does not accept mean nothing is sent.
 */
object TraktItems {
    private val imdb = Regex("tt\\d{5,10}")
    private val tmdb = Regex("tmdb:(\\d+)")
    private val tail = Regex(".*:(\\d+):(\\d+)")

    /** A Discover media id (FR-14): `tt…` is IMDb, `tmdb:<n>` is TMDB; anything else (e.g. `kitsu:…`) is not sent. */
    fun addonIds(mediaId: String): TraktIds? = when {
        imdb.matches(mediaId) -> TraktIds(imdb = mediaId)
        else -> tmdb.matchEntire(mediaId)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }?.let { TraktIds(tmdb = it) }
    }

    /**
     * FR-14: `movie` → the movie; `series` → the episode by its own season and number, else the last
     * two integers of `<show>:<season>:<episode>`; other types → nothing.
     */
    fun addon(mediaType: String, mediaId: String, videoId: String, season: Int?, episode: Int?): TraktItem? {
        val ids = addonIds(mediaId) ?: return null
        return when (mediaType) {
            "movie" -> TraktItem.Movie(ids)
            "series" -> {
                val (s, e) = if (season != null && episode != null) {
                    season to episode
                } else {
                    val m = tail.matchEntire(videoId) ?: return null
                    (m.groupValues[1].toIntOrNull() ?: return null) to (m.groupValues[2].toIntOrNull() ?: return null)
                }
                if (s < 0 || e < 0) null else TraktItem.Episode(ids, s, e)
            }
            else -> null
        }
    }

    /**
     * FR-13: a library TMDB id only when TMDB produced the match (TVmaze ids look alike and are
     * never accepted): [provider] must be `tmdb`; the id may carry a `tmdb:` prefix.
     */
    fun tmdbId(provider: String?, externalId: String?): Long? {
        if (provider != "tmdb") return null
        return externalId?.removePrefix("tmdb:")?.toLongOrNull()?.takeIf { it > 0 }
    }

    /** One stable key per title, for the queue's "latest report per title" (FR-19). */
    fun key(item: TraktItem): String = when (item) {
        is TraktItem.Movie -> "movie:" + ids(item.ids)
        is TraktItem.Episode -> "episode:" + ids(item.show) + ":${item.season}:${item.number}"
    }

    private fun ids(ids: TraktIds): String = listOf(ids.trakt, ids.tmdb, ids.imdb, ids.tvdb).joinToString("|") { it?.toString().orEmpty() }
}
