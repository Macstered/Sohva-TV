package com.sohva.tv.feature.trakt.marks

import com.sohva.tv.core.data.database.TraktStateEntity
import com.sohva.tv.core.model.vod.TitleMark

/**
 * Trakt rows as marks under Discover's keys (spec 51 FR-31): for each id form a row has (IMDb and
 * `tmdb:<n>`), `movie:<id>` or `series:<showId>:<season>:<episode>`.
 */
object TraktMarks {
    /** The ids [keys] name, to look up only those rows: IMDb ids and TMDB numbers. */
    fun ids(keys: Collection<String>): Pair<Set<String>, Set<Long>> {
        val imdbs = HashSet<String>()
        val tmdbs = HashSet<Long>()
        for (key in keys) {
            val rest = when {
                key.startsWith("movie:") -> key.removePrefix("movie:")
                key.startsWith("series:") -> key.removePrefix("series:")
                else -> continue
            }
            when {
                rest.startsWith("tt") -> imdbs += rest.substringBefore(':')
                rest.startsWith("tmdb:") -> rest.removePrefix("tmdb:").substringBefore(':').toLongOrNull()?.let(tmdbs::add)
            }
        }
        return imdbs to tmdbs
    }

    fun mark(row: TraktStateEntity): TitleMark? {
        val paused = row.progress > 0.0 && row.progress < 100.0
        val watched = row.watched && !paused
        if (!paused && !watched) return null
        return TitleMark(if (paused) (row.progress / 100.0).toFloat() else null, watched, row.updatedAt)
    }

    /** Every Discover key [row] answers to. */
    fun keys(row: TraktStateEntity): List<String> {
        val ids = listOfNotNull(row.imdb, row.tmdb?.let { "tmdb:$it" })
        return when (row.kind) {
            "movie" -> ids.map { "movie:$it" }
            "episode" -> if (row.season == null || row.number == null) emptyList() else ids.map { "series:$it:${row.season}:${row.number}" }
            else -> emptyList()
        }
    }

    /** The marks of [keys] from the rows looked up for them. */
    fun marks(keys: Collection<String>, rows: List<TraktStateEntity>): Map<String, TitleMark> {
        val wanted = keys.toHashSet()
        val out = HashMap<String, TitleMark>()
        for (row in rows) {
            val mark = mark(row) ?: continue
            for (k in keys(row)) if (k in wanted) out[k] = mark
        }
        return out
    }
}
