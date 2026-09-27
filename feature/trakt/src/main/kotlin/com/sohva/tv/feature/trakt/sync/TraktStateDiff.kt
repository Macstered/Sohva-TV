package com.sohva.tv.feature.trakt.sync

import com.sohva.tv.core.data.database.TraktStateEntity
import com.sohva.tv.core.data.trakt.TraktStateTable
import com.sohva.tv.feature.trakt.protocol.PausedItem
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktItem
import com.sohva.tv.feature.trakt.protocol.WatchedMovie
import com.sohva.tv.feature.trakt.protocol.WatchedShow

/** The rows Trakt's lists describe (spec 51 FR-22 step 4). */
object TraktStateRows {
    const val MOVIE: String = "movie"
    const val EPISODE: String = "episode"

    /** `tmdb:<n>` when Trakt gave a TMDB id, else `imdb:<tt…>`; neither → the item is skipped. */
    fun id(ids: TraktIds): String? = ids.tmdb?.let { "tmdb:$it" } ?: ids.imdb?.let { "imdb:$it" }

    fun watchedMovies(profile: String, list: List<WatchedMovie>): List<TraktStateEntity> = list.mapNotNull { m ->
        val id = id(m.ids) ?: return@mapNotNull null
        TraktStateEntity(profile, "$MOVIE:$id", MOVIE, m.ids.tmdb, m.ids.imdb, null, null, 0.0, true, m.plays, m.lastWatchedAt)
    }

    fun watchedEpisodes(profile: String, shows: List<WatchedShow>): List<TraktStateEntity> = shows.flatMap { show ->
        val id = id(show.ids) ?: return@flatMap emptyList()
        show.episodes.map { e ->
            TraktStateEntity(profile, "$EPISODE:$id:${e.season}:${e.number}", EPISODE, show.ids.tmdb, show.ids.imdb, e.season, e.number, 0.0, true, e.plays, e.lastWatchedAt)
        }
    }

    fun paused(profile: String, items: List<PausedItem>): List<TraktStateEntity> = items.mapNotNull { p ->
        when (val item = p.item) {
            is TraktItem.Movie -> id(item.ids)?.let { id ->
                TraktStateEntity(profile, "$MOVIE:$id", MOVIE, item.ids.tmdb, item.ids.imdb, null, null, p.progress, false, 0, p.pausedAt)
            }
            is TraktItem.Episode -> id(item.show)?.let { id ->
                TraktStateEntity(profile, "$EPISODE:$id:${item.season}:${item.number}", EPISODE, item.show.tmdb, item.show.imdb, item.season, item.number, p.progress, false, 0, p.pausedAt)
            }
        }
    }
}

/**
 * Brings one kind of a profile's cache to what Trakt says (FR-22 steps 3–5, §9 rule "write a
 * diff"): walks the stored rows in key order, pages of [PAGE], and writes only rows that change.
 * [watched] or [paused] null means that part did not change on Trakt and the stored one is kept,
 * so a paused-only activity never needs the watched lists.
 */
class TraktStateDiff(private val table: TraktStateTable) {
    class Result(val deletes: List<String>, val upserts: List<TraktStateEntity>)

    suspend fun compute(profile: String, kind: String, watched: List<TraktStateEntity>?, paused: List<TraktStateEntity>?): Result {
        val w = watched?.associateByTo(HashMap()) { it.key }
        val p = paused?.associateByTo(HashMap()) { it.key }
        val deletes = ArrayList<String>()
        val upserts = ArrayList<TraktStateEntity>()
        fun visit(old: TraktStateEntity) {
            val next = merge(old, w?.remove(old.key), p?.remove(old.key), w != null, p != null)
            when {
                next == null -> deletes += old.key
                next != old -> upserts += next
            }
        }
        if (w == null && p != null) {
            // Only the pauses changed: only rows paused now or named by the new list can change, so
            // the history is not walked (a new pause on a large account is a few indexed reads).
            val old = table.paused(profile).filter { it.kind == kind }.associateByTo(LinkedHashMap()) { it.key }
            p.keys.filterNot(old::containsKey).chunked(LOOKUP).forEach { chunk -> table.byKeys(profile, chunk).forEach { old[it.key] = it } }
            old.values.forEach(::visit)
        } else {
            var after = ""
            while (true) {
                val page = table.page(profile, kind, after, PAGE)
                page.forEach(::visit)
                if (page.size < PAGE) break
                after = page.last().key
            }
        }
        // What is left is new to the cache.
        val fresh = LinkedHashSet<String>().apply { w?.keys?.let(::addAll); p?.keys?.let(::addAll) }
        for (key in fresh) merge(null, w?.get(key), p?.get(key), w != null, p != null)?.let(upserts::add)
        return Result(deletes, upserts)
    }

    companion object {
        const val PAGE: Int = 2_000
        private const val LOOKUP = 200

        /**
         * One key's row: watched flag, plays and watch time from [w] (or the stored row when the
         * watched list was not fetched), progress and pause time from [p] (likewise); the time is
         * the later of the two. Neither watched nor paused → no row.
         */
        fun merge(old: TraktStateEntity?, w: TraktStateEntity?, p: TraktStateEntity?, watchedGiven: Boolean, pausedGiven: Boolean): TraktStateEntity? {
            val watchedSide = if (watchedGiven) w else old?.takeIf { it.watched }
            val pausedSide = if (pausedGiven) p else old?.takeIf { it.progress > 0.0 }
            val base = pausedSide ?: watchedSide ?: return null
            return base.copy(
                tmdb = pausedSide?.tmdb ?: watchedSide?.tmdb,
                imdb = pausedSide?.imdb ?: watchedSide?.imdb,
                progress = pausedSide?.progress ?: 0.0,
                watched = watchedSide != null,
                plays = watchedSide?.plays ?: 0,
                updatedAt = maxOf(watchedSide?.updatedAt ?: 0L, pausedSide?.updatedAt ?: 0L),
            )
        }
    }
}
