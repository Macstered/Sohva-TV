package com.sohva.tv.core.data.vod

import com.sohva.tv.core.data.database.EpisodeRecord
import com.sohva.tv.core.data.database.FilmRecord
import com.sohva.tv.core.data.database.PlayableTitle
import com.sohva.tv.core.data.database.SeriesRecord
import com.sohva.tv.core.data.database.SohvaDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The details pages' and the player's reads of one title (spec 40 §4.10–4.13), each an indexed
 * lookup by key on the database dispatcher. A series' episodes are one query per series (§9.8).
 */
class TitleReads(private val db: SohvaDatabase, private val io: CoroutineDispatcher) {
    private val dao get() = db.titles()

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
}
