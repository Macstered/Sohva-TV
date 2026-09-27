package com.sohva.tv.core.data.vod

import com.sohva.tv.core.data.database.CONTENT_EPISODE
import com.sohva.tv.core.data.database.CONTENT_MOVIE
import com.sohva.tv.core.data.database.ContinueRow
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.WatchProgressEntity
import com.sohva.tv.core.data.trakt.TraktOverlay
import com.sohva.tv.core.model.metadata.TitleCleaner
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.WatchedRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A title's saved place for the active profile. */
data class Progress(
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
    /** A Trakt pause without a known runtime: only the bar, no resume point (spec 51 FR-33, Q1). */
    val traktFraction: Float? = null,
) {
    /** Where Resume starts: nothing when finished (spec 40 VOD-FR-92). */
    val resumeMs: Long get() = if (completed) 0 else positionMs

    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else traktFraction ?: 0f
}


/**
 * A Continue watching card (VOD-FR-99): [title] is the series name for an episode, else the film
 * name, as metadata calls it or cleaned by the matcher's rule; [groupKey] the series key for an
 * episode, else the content key; [tmdbId] when the film is matched to TMDB.
 */
data class ContinueItem(
    val contentKey: String,
    val groupKey: String,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val replacementPoster: String?,
    val replacePoster: Boolean,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val tmdbId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Set for a title paused in Discover (spec 02 HOME-FR-18): what its card opens. */
    val discover: DiscoverResume? = null,
    /** A Trakt pause without a known runtime: the card's bar (spec 51 FR-34). */
    val fraction: Float? = null,
) {
    companion object {
        fun of(row: ContinueRow): ContinueItem = ContinueItem(
            contentKey = row.contentKey,
            groupKey = row.seriesKey ?: row.contentKey,
            title = row.replacementTitle?.takeIf { it.isNotBlank() } ?: TitleCleaner.searchTitle(row.name).ifBlank { row.name },
            year = row.year,
            posterUrl = row.posterUrl,
            replacementPoster = row.replacementPoster,
            replacePoster = row.replacePoster,
            season = row.season,
            episode = row.number,
            episodeTitle = row.episodeName,
            tmdbId = row.externalId?.takeIf { row.workKey?.startsWith("tmdb:") == true },
            positionMs = row.positionMs,
            durationMs = row.durationMs,
            updatedAt = row.updatedAt,
        )
    }
}

/**
 * A Discover title in Continue watching (spec 02 HOME-FR-18, -23): the metadata installation
 * ([owner]), the catalog's type and id, the video that was playing, the episode's own title as
 * the subtitle, and the stored backdrop. Nothing here reaches a log.
 */
data class DiscoverResume(
    val owner: String,
    val mediaType: String,
    val mediaId: String,
    val videoId: String,
    val subtitle: String?,
    val backdrop: String?,
) {
    override fun toString(): String = "DiscoverResume($mediaType)"
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
    private val trakt = TraktOverlay(db)

    /**
     * Whether the profile sees Trakt's history (spec 51 FR-36: never a restricted profile). The app
     * sets it when Trakt exists; rows exist only for connected accounts.
     */
    @Volatile var traktAllowed: suspend (String) -> Boolean = { false }

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
        val local = listOfNotNull(own, shared).maxByOrNull { it.updatedAt }?.toProgress()
        if (!traktAllowed(who)) return@withContext local
        // Spec 51 FR-33: the newer of the local place and Trakt's; films by identity, episodes by series match.
        when {
            work != null -> TraktOverlay.merge(local, trakt.film(who, work))
            ContentKeys.isEpisode(contentKey) -> {
                val slot = db.trakt().episode(contentKey)
                val row = slot?.let { trakt.episode(who, it.seriesKey, it.season, it.number) }
                TraktOverlay.merge(local, row, row?.let { db.progress().episodeRuntime(contentKey) } ?: 0)
            }
            else -> local
        }
    }

    /**
     * The films among [films] to tick as watched (spec 40 VOD-FR-37): the newest row among a film's
     * own key and its film identity is finished, so a film finished on one copy is ticked on the
     * other. At most [MAX_TICKS] films per call.
     */
    suspend fun watched(films: List<Pair<String, String?>>): Set<String> = withContext(io) {
        if (films.isEmpty()) return@withContext emptySet()
        val page = films.take(MAX_TICKS)
        val who = profile()
        val own = dao.ticksOwn(who, page.map { it.first }).associateBy { it.key }
        val works = page.mapNotNull { it.second }.distinct()
        val work = if (works.isEmpty()) emptyMap() else dao.ticksWork(who, works).groupBy { it.key }.mapValues { (_, rows) -> rows.maxBy { it.updatedAt } }
        val traktRows = if (traktAllowed(who)) trakt.films(who, page.map { it.second }) else emptyMap()
        page.filter { (key, workKey) ->
            val newest = listOfNotNull(own[key], workKey?.let(work::get)).maxByOrNull { it.updatedAt }
            val local = newest?.let { Progress(0, 0, it.completed, it.updatedAt) }
            // Spec 51 FR-33: a newer Trakt watched mark ticks the film; a newer Trakt pause unticks it.
            val merged = TraktOverlay.merge(local, TraktOverlay.filmKey(workKey)?.let(traktRows::get))
            merged?.completed == true
        }.mapTo(HashSet()) { it.first }
    }

    /** Every episode row of one series (VOD-FR-95): one query, keyed by episode key. */
    suspend fun ofSeries(seriesKey: String): Map<String, Progress> = withContext(io) {
        val who = profile()
        val local = dao.ofSeries(who, seriesKey).associate { it.contentKey to it.toProgress() }
        if (!traktAllowed(who)) return@withContext local
        val rows = trakt.series(who, seriesKey)
        if (rows.isEmpty()) return@withContext local
        (local.keys + rows.keys).mapNotNull { key ->
            val row = rows[key]
            TraktOverlay.merge(local[key], row?.first, row?.second ?: 0)?.let { key to it }
        }.toMap()
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

    /**
     * Continue watching for Home (spec 40 VOD-FR-99): the newest started, unfinished titles, one
     * card per film identity and per series, at most 20. At most 60 rows of each kind are read.
     */
    suspend fun continueWatching(limit: Int = CONTINUE_MAX): List<ContinueItem> = withContext(io) {
        val who = profile()
        val rows = (dao.continueFilms(who, READ_MAX) + dao.continueEpisodes(who, READ_MAX)).sortedByDescending { it.updatedAt }
        val seen = HashSet<String>()
        val local = rows.asSequence()
            .filter { seen.add(it.seriesKey ?: it.workKey ?: it.contentKey) }
            .take(limit)
            .map(ContinueItem::of)
            .toList()
        if (!traktAllowed(who)) return@withContext local
        // Spec 51 FR-34: library copies paused on Trakt join the local list.
        TraktOverlay.join(local, trakt.continueItems(who, limit), limit)
    }

    private fun WatchProgressEntity.toProgress() = Progress(positionMs, durationMs, completed, updatedAt)

    companion object {
        /** Watched ticks are read for at most this many cards at a time (VOD-FR-37, Trakt lesson 8). */
        const val MAX_TICKS: Int = 200

        /** Continue watching holds at most 20 cards (VOD-FR-99). */
        const val CONTINUE_MAX: Int = 20
        private const val READ_MAX = 60
    }
}

/** The content keys progress accepts (spec 40 VOD-FR-23); anything else is ignored by every write. */
object ContentKeys {
    private val film = Regex("""vod:movie:[^:]+:[A-Za-z0-9._-]{1,128}""")
    private val episode = Regex("""vod:episode:[^:]+:[A-Za-z0-9._-]{1,128}""")

    fun isFilm(key: String): Boolean = film.matches(key)

    fun isEpisode(key: String): Boolean = episode.matches(key)
}
