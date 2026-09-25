package com.sohva.tv.core.data.vod

import com.sohva.tv.core.data.database.CONTENT_EPISODE
import com.sohva.tv.core.data.database.CONTENT_MOVIE
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.WatchProgressEntity
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.WatchedRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A title's saved place for the active profile. */
data class Progress(val positionMs: Long, val durationMs: Long, val completed: Boolean, val updatedAt: Long) {
    /** Where Resume starts: nothing when finished (spec 40 VOD-FR-92). */
    val resumeMs: Long get() = if (completed) 0 else positionMs

    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Positions and watched marks per profile (spec 40 §4.12). The players write through [save]; the
 * pages read through [of]. A film's copies share one place: reading takes the newer of the copy's
 * own row and the newest row of its film identity, so the copy played last wins (VOD-FR-91).
 * Everything runs on the database dispatcher.
 */
class ProgressStore(
    private val db: SohvaDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val profile: () -> String,
) {
    private val dao get() = db.progress()

    /** Any progress write, and once at the start. */
    fun changes(): Flow<Unit> = db.invalidationTracker.createFlow("watch_progress").map { }

    /**
     * The one write call (VOD-FR-89): ignored under 5 s or without a duration, clamped to the
     * duration, finished per the watched rule (a finished row stores position = duration).
     */
    suspend fun save(contentKey: String, positionMs: Long, durationMs: Long): Unit = withContext(io) {
        if (positionMs < WatchedRule.MIN_SAVED_MS || durationMs <= 0) return@withContext
        val clamped = positionMs.coerceAtMost(durationMs)
        val done = WatchedRule.isWatched(clamped, durationMs)
        write(contentKey, if (done) durationMs else clamped, durationMs, done)
    }

    /** The title's place, shared between copies of a film (VOD-FR-91); null when never played. */
    suspend fun of(contentKey: String): Progress? = withContext(io) {
        val who = profile()
        val own = dao.get(who, contentKey)
        val work = if (ContentKeys.isFilm(contentKey)) dao.film(contentKey)?.workKey else null
        val shared = work?.let { dao.newestOfWork(who, it) }
        listOfNotNull(own, shared).maxByOrNull { it.updatedAt }?.toProgress()
    }

    /** Every episode row of one series (VOD-FR-95): one query, keyed by episode key. */
    suspend fun ofSeries(seriesKey: String): Map<String, Progress> = withContext(io) {
        dao.ofSeries(profile(), seriesKey).associate { it.contentKey to it.toProgress() }
    }

    /** "Mark as watched" (VOD-FR-96): finished at whatever duration is known (0 when never played). */
    suspend fun markWatched(contentKey: String, knownDurationMs: Long): Unit = withContext(io) {
        val duration = maxOf(knownDurationMs, dao.get(profile(), contentKey)?.durationMs ?: 0)
        write(contentKey, duration, duration, true)
    }

    /**
     * "Mark as unwatched" and "Remove from Continue watching": the title reads as never seen, so a
     * film loses the rows of all its copies.
     */
    suspend fun forget(contentKey: String): Unit = withContext(io) {
        val who = profile()
        db.runInTransaction {
            dao.delete(who, contentKey)
            if (ContentKeys.isFilm(contentKey)) dao.film(contentKey)?.workKey?.let { dao.deleteWork(who, it) }
        }
    }

    /** "Mark season as watched" (VOD-FR-97): every stored episode of the season, one transaction. */
    suspend fun markSeasonWatched(seriesKey: String, season: Int): Unit = withContext(io) {
        val who = profile()
        val now = clock.wallMillis()
        db.runInTransaction {
            val rows = dao.season(seriesKey, season).map { e ->
                val known = maxOf((e.durationSeconds ?: 0) * 1_000L, dao.get(who, e.key)?.durationMs ?: 0)
                WatchProgressEntity(who, e.key, e.sourceId, CONTENT_EPISODE, null, seriesKey, known, known, true, now)
            }
            dao.putAll(rows)
        }
    }

    private fun write(contentKey: String, positionMs: Long, durationMs: Long, completed: Boolean) {
        val now = clock.wallMillis()
        val row = when {
            ContentKeys.isFilm(contentKey) -> dao.film(contentKey)?.let {
                WatchProgressEntity(profile(), contentKey, it.sourceId, CONTENT_MOVIE, it.workKey, null, positionMs, durationMs, completed, now)
            }
            ContentKeys.isEpisode(contentKey) -> dao.episode(contentKey)?.let {
                WatchProgressEntity(profile(), contentKey, it.sourceId, CONTENT_EPISODE, null, it.seriesKey, positionMs, durationMs, completed, now)
            }
            else -> null
        } ?: return
        dao.put(row)
    }

    private fun WatchProgressEntity.toProgress() = Progress(positionMs, durationMs, completed, updatedAt)
}

/** The content keys progress accepts (spec 40 VOD-FR-23); anything else is ignored by every write. */
object ContentKeys {
    private val film = Regex("""vod:movie:[^:]+:[A-Za-z0-9._-]{1,128}""")
    private val episode = Regex("""vod:episode:[^:]+:[A-Za-z0-9._-]{1,128}""")

    fun isFilm(key: String): Boolean = film.matches(key)

    fun isEpisode(key: String): Boolean = episode.matches(key)
}
