package com.sohva.tv.core.data.trakt

import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.TraktStateEntity
import com.sohva.tv.core.data.vod.ContinueItem
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.core.model.metadata.TitleCleaner

/**
 * Trakt's history on the library (spec 51 FR-32 to FR-34). Films reach Trakt through their film
 * identity `tmdb:<n>`, series only when TMDB made the match; both lookups are by key, never a join
 * that could start from the catalogue (§9). Callers run it on the database dispatcher.
 */
class TraktOverlay(private val db: SohvaDatabase) {
    private val dao get() = db.trakt()

    /** A film's Trakt row, from its film identity. */
    fun film(profile: String, workKey: String?): TraktStateEntity? {
        val key = filmKey(workKey) ?: return null
        return dao.byKeys(profile, listOf(key)).firstOrNull()
    }

    /** Rows for many films at once (walls, ≤ 200 per call), keyed by Trakt key. */
    fun films(profile: String, workKeys: Collection<String?>): Map<String, TraktStateEntity> {
        val keys = workKeys.mapNotNull(::filmKey).distinct()
        if (keys.isEmpty()) return emptyMap()
        return keys.chunked(LOOKUP).flatMap { dao.byKeys(profile, it) }.associateBy { it.key }
    }

    /** Films gain the IMDb id Trakt holds for their TMDB id, so Home can merge them with Discover copies (TRAKT-23). */
    fun withImdb(profile: String, items: List<ContinueItem>): List<ContinueItem> {
        val rows = films(profile, items.filter { it.season == null && it.discover == null }.map { it.tmdbId?.let { id -> "tmdb:$id" } })
        if (rows.isEmpty()) return items
        return items.map { item ->
            val imdb = item.tmdbId?.let { rows["movie:tmdb:$it"]?.imdb }
            if (imdb == null || item.season != null) item else item.copy(imdbId = imdb)
        }
    }

    /** One episode's Trakt row and its playlist runtime; null when the series has no TMDB match. */
    fun episode(profile: String, seriesKey: String, season: Int, number: Int): TraktStateEntity? {
        val show = dao.seriesTmdb(seriesKey)?.toLongOrNull() ?: return null
        return dao.byKeys(profile, listOf("episode:tmdb:$show:$season:$number")).firstOrNull()
    }

    /** Every episode of one series with its Trakt row (FR-32 series variant): episode key → (row, runtime). */
    fun series(profile: String, seriesKey: String): Map<String, Pair<TraktStateEntity, Long>> {
        val show = dao.seriesTmdb(seriesKey)?.toLongOrNull() ?: return emptyMap()
        val rows = dao.byTmdb(profile, listOf(show)).filter { it.kind == "episode" }
        if (rows.isEmpty()) return emptyMap()
        val bySlot = rows.associateBy { it.season to it.number }
        return dao.seriesEpisodes(seriesKey).mapNotNull { e ->
            bySlot[e.season to e.number]?.let { e.key to (it to (e.durationSeconds ?: 0) * 1_000L) }
        }.toMap()
    }

    /**
     * Library copies paused on Trakt (FR-34): one card per Trakt identity (a film, or a series by
     * its newest paused episode), the lowest content key, newest first, at most [limit].
     */
    fun continueItems(profile: String, limit: Int): List<ContinueItem> {
        val paused = dao.paused(profile, PAUSED_MAX).sortedByDescending { it.updatedAt }
        if (paused.isEmpty()) return emptyList()
        val films = paused.filter { it.kind == "movie" }.mapNotNull { it.tmdb }.distinct().map { "tmdb:$it" }
            .chunked(LOOKUP).flatMap { dao.filmCopies(profile, it) }
            .groupBy { it.workKey }.mapValues { (_, copies) -> copies.minBy { it.key } }
        val shows = paused.filter { it.kind == "episode" }.mapNotNull { it.tmdb?.toString() }.distinct()
            .chunked(LOOKUP).flatMap { dao.seriesCopies(profile, it) }
            .groupBy { it.tmdb }.mapValues { (_, copies) -> copies.minBy { it.key } }
        val out = ArrayList<ContinueItem>()
        val seen = HashSet<String>()
        for (row in paused) {
            if (out.size >= limit) break
            val tmdb = row.tmdb ?: continue
            if (!seen.add("${row.kind}:$tmdb")) continue
            val fraction = (row.progress / 100.0).toFloat()
            if (row.kind == "movie") {
                val copy = films["tmdb:$tmdb"] ?: continue
                out += ContinueItem(
                    contentKey = copy.key, groupKey = copy.key,
                    title = copy.replacementTitle?.takeIf { it.isNotBlank() } ?: TitleCleaner.searchTitle(copy.name).ifBlank { copy.name },
                    year = copy.year, posterUrl = copy.posterUrl, replacementPoster = copy.replacementPoster, replacePoster = copy.replacePoster,
                    season = null, episode = null, episodeTitle = null, tmdbId = tmdb.toString(),
                    positionMs = 0, durationMs = 0, updatedAt = row.updatedAt, fraction = fraction,
                )
            } else {
                val series = shows[tmdb.toString()] ?: continue
                val e = dao.episodeAt(series.id, row.season ?: continue, row.number ?: continue).minByOrNull { it.key } ?: continue
                val runtime = (e.durationSeconds ?: 0) * 1_000L
                out += ContinueItem(
                    contentKey = e.key, groupKey = series.key,
                    title = series.replacementTitle?.takeIf { it.isNotBlank() } ?: TitleCleaner.searchTitle(series.name).ifBlank { series.name },
                    year = null, posterUrl = series.posterUrl, replacementPoster = series.replacementPoster, replacePoster = series.replacePoster,
                    season = e.season, episode = e.number, episodeTitle = e.name, tmdbId = null,
                    positionMs = (runtime * fraction).toLong(), durationMs = runtime, updatedAt = row.updatedAt,
                    fraction = fraction.takeIf { runtime <= 0 },
                )
            }
        }
        return out
    }

    companion object {
        const val LOOKUP: Int = 200

        /** Trakt's paused list holds at most 500 titles (FR-22); a bound in case it ever grows. */
        const val PAUSED_MAX: Int = 1_000

        fun filmKey(workKey: String?): String? = workKey?.takeIf { it.startsWith("tmdb:") }?.let { "movie:$it" }

        /**
         * FR-33, per content key: the local place wins when it is at least as new as Trakt's row;
         * otherwise a Trakt pause becomes a partial place (runtime × %, or only the bar without a
         * known runtime) and a Trakt watched mark "completed". A pause outranks Trakt's own watched
         * mark (a rewatch); a row neither paused nor watched changes nothing.
         */
        fun merge(local: Progress?, row: TraktStateEntity?, runtimeMs: Long = 0): Progress? {
            if (row == null) return local
            val paused = row.progress > 0.0 && row.progress < 100.0
            if (!paused && !row.watched) return local
            if (local != null && local.updatedAt >= row.updatedAt) return local
            val runtime = runtimeMs.takeIf { it > 0 } ?: local?.durationMs?.takeIf { it > 0 } ?: 0L
            return when {
                paused && runtime > 0 -> Progress((runtime * row.progress / 100.0).toLong(), runtime, false, row.updatedAt)
                paused -> Progress(0, 0, false, row.updatedAt, traktFraction = (row.progress / 100.0).toFloat())
                else -> Progress(runtime, runtime, true, row.updatedAt)
            }
        }

        /**
         * FR-34: Trakt's cards joined with the local list. For the same film identity or series the
         * newer card wins, the local one on a tie; then newest first, at most [limit].
         */
        fun join(local: List<ContinueItem>, trakt: List<ContinueItem>, limit: Int): List<ContinueItem> {
            fun identity(i: ContinueItem) = if (i.season == null && i.tmdbId != null) "film:${i.tmdbId}" else i.groupKey
            val best = LinkedHashMap<String, ContinueItem>()
            for (item in local) best.putIfAbsent(identity(item), item)
            for (item in trakt) {
                val id = identity(item)
                val current = best[id]
                if (current == null || item.updatedAt > current.updatedAt) best[id] = item
            }
            return best.values.sortedByDescending { it.updatedAt }.take(limit)
        }
    }
}
