package com.sohva.tv.core.data.home

import com.sohva.tv.core.data.vod.ContinueItem

/**
 * One Continue watching row from the library and Discover (spec 02 §4.4). Entries sharing any
 * alias form a group; the newest entry stands for it (every entry here was watched on this
 * device); groups sort newest first. Titles alone never merge, and a film never merges with a
 * series: only films carry TMDB/IMDb aliases.
 */
object ContinueMerge {
    private val imdb = Regex("tt\\d{5,10}")
    private val tmdb = Regex("tmdb:(\\d+)")

    fun merge(library: List<ContinueItem>, discover: List<ContinueItem>, limit: Int): List<ContinueItem> {
        val all = library + discover
        val parent = IntArray(all.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            parent[i] = r
            return r
        }
        val owner = HashMap<String, Int>()
        all.forEachIndexed { i, item ->
            for (alias in aliases(item)) {
                val j = owner.putIfAbsent(alias, i) ?: continue
                parent[root(i)] = root(j)
            }
        }
        val best = HashMap<Int, ContinueItem>()
        all.forEachIndexed { i, item ->
            val r = root(i)
            val held = best[r]
            if (held == null || newer(item, held)) best[r] = item
        }
        return best.values.sortedWith(compareByDescending<ContinueItem> { it.updatedAt }.thenBy { it.contentKey }).take(limit)
    }

    private fun newer(a: ContinueItem, b: ContinueItem): Boolean =
        a.updatedAt > b.updatedAt || (a.updatedAt == b.updatedAt && a.contentKey < b.contentKey)

    /** HOME-FR-20: the group key, and for films the TMDB and IMDb ids as Trakt reads them. */
    internal fun aliases(item: ContinueItem): List<String> {
        val d = item.discover
        if (d == null) {
            val id = item.tmdbId?.toLongOrNull()?.takeIf { it > 0 && item.season == null }
            return listOfNotNull("vod:${item.groupKey}", id?.let { "movie:tmdb:$it" })
        }
        val film = when {
            d.mediaType != "movie" -> null
            imdb.matches(d.mediaId) -> "movie:imdb:${d.mediaId}"
            else -> tmdb.matchEntire(d.mediaId)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }?.let { "movie:tmdb:$it" }
        }
        return listOfNotNull(item.groupKey, film)
    }
}
