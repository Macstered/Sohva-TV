package com.sohva.tv.core.data.trakt

import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.TraktEpisodeRow
import com.sohva.tv.core.data.database.TraktMatchRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * What Trakt reads from the library (spec 51 FR-13): a title's metadata match (provider and id)
 * and an episode's series and numbers. Small keyed reads on the database dispatcher.
 */
class TraktLibraryReads(private val db: SohvaDatabase, private val io: CoroutineDispatcher) {
    private val dao get() = db.trakt()

    suspend fun match(contentKey: String): TraktMatchRow? = withContext(io) { dao.match(contentKey) }

    suspend fun episode(episodeKey: String): TraktEpisodeRow? = withContext(io) { dao.episode(episodeKey) }

    /** FR-30: the library's own page for a Trakt title: a film by its TMDB identity, a series only when TMDB matched it. */
    suspend fun filmFor(tmdb: Long): String? = withContext(io) { dao.filmRoute("tmdb:$tmdb") }

    suspend fun seriesFor(tmdb: Long): String? = withContext(io) { dao.seriesRoute(tmdb.toString()) }

    /**
     * Spec 02 HOME-FR-98: which of Home's Trakt titles the library has, by TMDB id, films and series
     * apart; two indexed reads for all of Home's rows (at most a few hundred ids, under SQLite's
     * 999-parameter limit, chunked anyway).
     */
    suspend fun owned(films: Collection<Long>, series: Collection<Long>): Owned = withContext(io) {
        val f = films.distinct().chunked(CHUNK).flatMap { ids -> dao.filmsOwned(ids.map { "tmdb:$it" }) }
            .mapNotNullTo(HashSet()) { it.removePrefix("tmdb:").toLongOrNull() }
        val s = series.distinct().chunked(CHUNK).flatMap { ids -> dao.seriesOwned(ids.map(Long::toString)) }
            .mapNotNullTo(HashSet()) { it.toLongOrNull() }
        Owned(f, s)
    }

    class Owned(val films: Set<Long>, val series: Set<Long>)

    private companion object {
        const val CHUNK = 400
    }
}
