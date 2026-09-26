package com.sohva.tv.feature.discover.data

import com.sohva.tv.feature.discover.protocol.AddonVideo

/**
 * What plays after an episode (spec 50 ADDON-FR-94): numbered episodes advance to the smallest
 * (season, episode) greater than the current one, crossing seasons, but specials (season 0) only
 * advance within specials and regular seasons never enter them; an unnumbered episode moves to the
 * next entry in the provider's list with another id. Another copy of the same episode is not replayed.
 */
object EpisodeOrder {
    fun next(videos: List<AddonVideo>, currentId: String): AddonVideo? {
        val index = videos.indexOfFirst { it.id == currentId }
        if (index < 0) return null
        val current = videos[index]
        val season = current.season
        val episode = current.episode
        if (season == null || episode == null) {
            return videos.drop(index + 1).firstOrNull { it.id != currentId }
        }
        val special = season == 0
        return videos.asSequence()
            .filter { it.season != null && it.episode != null && it.id != currentId }
            .filter { (it.season == 0) == special }
            .filter { compare(it.season!!, it.episode!!, season, episode) > 0 }
            .minWithOrNull(compareBy<AddonVideo>({ it.season }, { it.episode }).thenBy { videos.indexOf(it) })
    }

    private fun compare(s1: Int, e1: Int, s2: Int, e2: Int): Int = if (s1 != s2) s1.compareTo(s2) else e1.compareTo(e2)
}
