package com.sohva.tv.core.sync.metadata

import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.vod.LibraryPasses
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Forgetting what metadata did (spec 41 §4.15). The library overrides are cleared in pages of
 * 2,000 rows by id, so no single write holds the database for the whole catalogue (§9.2); film
 * identities (`tmdb:<id>`) stay, since they do not depend on the language, and the worker writes
 * them again anyway. Pins live in their own table and survive both (META-FR-78).
 */
class MetadataReset(
    private val db: SohvaDatabase,
    private val service: MetadataService,
    private val passes: LibraryPasses,
    private val bulk: CoroutineDispatcher,
) {
    private val dao get() = db.metadata()

    /**
     * A new metadata language (META-FR-79): the memory cache, the overrides, the matches and the
     * queue go; genres stay (they do not depend on the language). The caller restarts the worker.
     */
    suspend fun languageChanged(): Unit = withContext(bulk) {
        service.forgetMemory()
        clearOverrides(genres = false)
        db.runInTransaction {
            dao.deleteAllMatches()
            dao.deleteQueue()
        }
    }

    /**
     * "Clear metadata cache" (META-FR-80): also every cached answer and every genre. The worker is
     * not restarted: the empty queue makes its next run synchronise from the start.
     */
    suspend fun clearAll(): Unit = withContext(bulk) {
        service.forgetMemory()
        clearOverrides(genres = true)
        db.runInTransaction {
            dao.deleteAllCache()
            dao.deleteAllMatches()
            dao.deleteQueue()
        }
        passes.recountGenres()
    }

    private fun clearOverrides(genres: Boolean) {
        var after = 0L
        while (true) {
            val last = dao.lastFilmId(after, PAGE) ?: break
            dao.clearFilmPage(after, PAGE, genres)
            after = last
        }
        after = 0L
        while (true) {
            val last = dao.lastSeriesId(after, PAGE) ?: break
            dao.clearSeriesPage(after, PAGE, genres)
            after = last
        }
    }

    private companion object {
        const val PAGE = 2_000
    }
}
