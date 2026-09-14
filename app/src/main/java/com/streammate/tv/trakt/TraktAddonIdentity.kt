package com.streammate.tv.trakt

import com.sohva.tv.addons.AddonVideo
import com.sohva.tv.addons.AddonWatchIdentity
import com.sohva.tv.trakt.TraktIds
import com.sohva.tv.trakt.TraktItem

/**
 * Discover titles carry their identity in the addon media key: an IMDb id
 * (`tt…`) or `tmdb:…`. Episodes are numbered by the video entry, or by the
 * Stremio `<show>:<season>:<episode>` video id when no entry is at hand.
 */
object TraktAddonIdentity {
    fun resolve(identity: AddonWatchIdentity, episode: AddonVideo? = null): TraktItem? {
        val ids = ids(identity.media.id) ?: return null
        return when (identity.media.type) {
            "movie" -> TraktItem.Movie(ids)
            "series" -> {
                val numbers = episode?.let { video -> video.season?.let { s -> video.episode?.let { e -> s to e } } }
                    ?: numbers(identity.video.id) ?: return null
                TraktItem.Episode(ids, numbers.first, numbers.second)
            }
            else -> null
        }
    }

    private fun ids(id: String): TraktIds? = when {
        IMDB.matches(id) -> TraktIds(imdb = id)
        id.startsWith("tmdb:") -> id.removePrefix("tmdb:").toLongOrNull()?.takeIf { it > 0 }?.let { TraktIds(tmdb = it) }
        else -> null
    }

    private fun numbers(videoId: String): Pair<Int, Int>? {
        val parts = videoId.split(':')
        if (parts.size < 3) return null
        val season = parts[parts.size - 2].toIntOrNull() ?: return null
        val episode = parts[parts.size - 1].toIntOrNull() ?: return null
        return if (season >= 0 && episode >= 0) season to episode else null
    }

    private val IMDB = Regex("tt\\d{5,10}")
}
