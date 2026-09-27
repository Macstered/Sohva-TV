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
}
