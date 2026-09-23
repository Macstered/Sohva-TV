package com.sohva.tv.addons

/** Metadata order can be arbitrary; episode IDs remain opaque provider identifiers. */
fun AddonMedia.nextEpisode(currentVideoId: String): AddonVideo? {
    if (key.type == "movie") return null
    val currentIndex = videos.indexOfFirst { it.id == currentVideoId }
    if (currentIndex < 0) return null
    val current = videos[currentIndex]
    val season = current.season
    val episode = current.episode
    // Unnumbered addon video lists have only the provider's declared order.
    if (season == null || episode == null) return videos.drop(currentIndex + 1).firstOrNull { it.id != current.id }
    return videos.asSequence()
        .filter { it.id != current.id && it.season != null && it.episode != null }
        // Specials advance within specials, never into or out of the regular run.
        .filter { if (season == 0) it.season == 0 else it.season!! > 0 }
        .filter { it.season!! > season || (it.season == season && it.episode!! > episode) }
        .minWithOrNull(compareBy<AddonVideo> { it.season }.thenBy { it.episode })
}
