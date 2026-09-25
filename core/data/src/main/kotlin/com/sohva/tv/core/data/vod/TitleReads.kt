package com.sohva.tv.core.data.vod

import com.sohva.tv.core.data.database.EpisodeRecord
import com.sohva.tv.core.data.database.FilmRecord
import com.sohva.tv.core.data.database.PlayableTitle
import com.sohva.tv.core.data.database.SeriesRecord
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.VersionRow
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.SimilarCandidate
import com.sohva.tv.core.model.vod.SimilarReference
import com.sohva.tv.core.model.vod.SimilarTitles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The details pages' and the player's reads of one title (spec 40 §4.10–4.13), each an indexed
 * lookup by key on the database dispatcher. A series' episodes are one query per series (§9.8).
 */
class TitleReads(private val db: SohvaDatabase, private val io: CoroutineDispatcher, private val clock: Clock) {
    private val dao get() = db.titles()

    /** Similar answers per (film, reference ids): 24 entries, each for 5 minutes (VOD-FR-71). */
    private val similarCache = object : LinkedHashMap<String, SimilarMemo>(SIMILAR_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SimilarMemo>): Boolean = size > SIMILAR_CACHE
    }

    private class SimilarMemo(val films: List<SimilarFilm>, val at: Long)

    suspend fun film(key: String): FilmRecord? = withContext(io) { dao.film(key) }

    suspend fun series(key: String): SeriesRecord? = withContext(io) { dao.series(key) }

    /** The series' episodes now and after every change to the episode table (VOD-FR-73/74). */
    fun episodes(seriesKey: String): Flow<List<EpisodeRecord>> =
        db.invalidationTracker.createFlow("episode").map { withContext(io) { dao.episodes(seriesKey) } }

    /** The stream of a film or an episode of an enabled source, still sealed; null when gone. */
    suspend fun playable(key: String): PlayableTitle? = withContext(io) {
        when {
            ContentKeys.isFilm(key) -> dao.playableFilm(key)
            ContentKeys.isEpisode(key) -> dao.playableEpisode(key)
            else -> null
        }
    }

    suspend fun nextEpisode(episodeKey: String): String? = withContext(io) { dao.nextEpisode(episodeKey) }

    suspend fun seriesOfEpisode(episodeKey: String): String? = withContext(io) { dao.seriesOfEpisode(episodeKey) }

    /** The film's copies for the Versions row (VOD-FR-68); empty without a work key. */
    suspend fun versions(workKey: String?): List<VersionRow> = if (workKey == null) emptyList() else withContext(io) { dao.versions(workKey) }

    /**
     * The library films that stand for TMDB's similar titles (VOD-FR-71): one indexed read of the
     * references' comparison keys, then [SimilarTitles.pick]. A cached answer is used only while
     * all its films are still visible.
     */
    suspend fun similar(film: FilmRecord, references: List<SimilarReference>): List<SimilarFilm> = withContext(io) {
        val refs = references.take(SimilarTitles.REFERENCES)
        val cacheKey = film.key + "|" + refs.joinToString(",") { it.externalId } + "|" + SimilarTitles.LIMIT
        val now = clock.wallMillis()
        val memo = synchronized(similarCache) { similarCache[cacheKey]?.takeIf { now - it.at < SIMILAR_TTL_MS } }
        if (memo != null) {
            val keys = memo.films.map { it.key }
            if (keys.isEmpty() || dao.visible(keys).size == keys.size) return@withContext memo.films
        }
        val keys = refs.flatMap(SimilarTitles::keys).distinct()
        val rows = if (keys.isEmpty()) emptyList() else keys.chunked(IN_LIMIT).flatMap(dao::similar).distinctBy { it.key }
        val byKey = rows.associateBy { it.key }
        val candidates = rows.map { SimilarCandidate(it.key, it.sourceId, it.year, it.similarKey, it.replacementKey) }
        val films = SimilarTitles.pick(film.key, film.sourceId, refs, candidates).map { (reference, candidate) ->
            SimilarFilm(candidate.key, reference, byKey[candidate.key]?.posterUrl)
        }
        synchronized(similarCache) { similarCache[cacheKey] = SimilarMemo(films, now) }
        films
    }

    /** Repairs a missing library poster from a details record (spec 41 META-FR-71). */
    suspend fun repairPoster(key: String, poster: String): Unit = withContext(io) {
        when {
            ContentKeys.isFilm(key) -> dao.repairFilmPoster(key, poster)
            else -> dao.repairSeriesPoster(key, poster)
        }
    }

    private companion object {
        const val SIMILAR_CACHE = 24
        const val SIMILAR_TTL_MS = 5 * 60_000L
        const val IN_LIMIT = 400
    }
}

/** A Similar card (VOD-FR-70): the library film, TMDB's reference for it, and the library poster as a fallback. */
data class SimilarFilm(val key: String, val reference: SimilarReference, val libraryPoster: String?)
