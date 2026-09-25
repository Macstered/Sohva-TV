package com.sohva.tv.core.sync.metadata

import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.MATCHED
import com.sohva.tv.core.data.database.MetadataMatchEntity
import com.sohva.tv.core.data.database.MetadataQueueEntity
import com.sohva.tv.core.data.database.NO_MATCH
import com.sohva.tv.core.data.database.QueueCandidate
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.model.metadata.TmdbGenres
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.core.net.metadata.MetadataRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A title on screen for the foreground budget: what its lookup needs. */
data class VisibleTitle(val key: String, val type: MediaType, val name: String, val year: Int?)

/** How a run ended, and so when the worker runs next (spec 41 META-FR-63). */
enum class RunEnd {
    /** Nothing left to do, or metadata is off. */
    DONE,

    /** The app came to the front: the worker waits for the next time it leaves. */
    STOPPED,

    /** Three failures in a row: providers are in trouble, continue in 15 minutes. */
    BACK_OFF,

    /** The 4-minute budget is used up: continue in a second. */
    CONTINUE,

    /** The TMDB key was refused three times in a row: nothing runs until the key is saved again (spec 41 Q8). */
    KEY_REFUSED,
}

/**
 * The background enrichment of the library (spec 41 §4.11): a durable queue synchronised with the
 * catalogue in key pages of 2,000 (stamp and sweep), then batches of up to 60 titles looked up
 * 225 ms apart and applied in one transaction each. Everything waits on [paused] (video playing
 * or the app in front) and runs on [bulk], a background-priority thread.
 */
class Enrichment(
    private val db: SohvaDatabase,
    private val service: MetadataService,
    private val passes: LibraryPasses,
    private val clock: Clock,
    private val bulk: CoroutineDispatcher,
    private val log: DiagnosticsLog,
    private val preferredCopy: suspend () -> PreferredCopy,
    private val enabledSources: suspend () -> List<String>,
    /** True while video plays or the app is in front: the run stops (META-FR-63). */
    private val paused: () -> Boolean,
    /** True while video plays: even the foreground budget waits (spec 41 Q10). */
    private val playing: () -> Boolean = { false },
) {
    private val dao get() = db.metadata()
    private var lastGenreCount = 0L

    /** Whether the queue must be synchronised before a run (META-FR-56). */
    suspend fun needsSync(): Boolean = withContext(bulk) { dao.queueSize() == 0 || dao.outdatedQueueRows(Genre.VERSION) > 0 }

    /** One paged synchronisation pass (META-FR-55); returns the pending count. */
    suspend fun synchronise(): Int = withContext(bulk) {
        val started = clock.wallMillis()
        val stamp = maxOf(started, (dao.newestStamp() ?: 0) + 1)
        var titles = 0
        var pages = 0
        var pending = 0
        for (source in enabledSources()) {
            for ((range, type) in listOf(KeyRange.movies(source) to MediaType.MOVIE, KeyRange.series(source) to MediaType.SERIES)) {
                var after = range.from
                while (true) {
                    val page = if (type == MediaType.MOVIE) dao.filmCandidates(after, range.until, PAGE) else dao.seriesCandidates(after, range.until, PAGE)
                    if (page.isEmpty()) break
                    pending += reconcile(page, type, stamp)
                    titles += page.size
                    pages++
                    after = page.last().key
                    if (page.size < PAGE) break
                }
            }
        }
        dao.sweepQueue(stamp)
        log.info(EVENT, "queue synchronised: $titles titles, $pages pages, $pending pending, ${clock.wallMillis() - started} ms")
        pending
    }

    private fun reconcile(page: List<QueueCandidate>, type: MediaType, stamp: Long): Int {
        val keys = page.map { it.key }
        val old = keys.chunked(IN_LIMIT).flatMap(dao::queueOf).associateBy { it.contentKey }
        val matches = keys.chunked(IN_LIMIT).flatMap(dao::matchesOf).associateBy { it.contentKey }
        val now = clock.wallMillis()
        var pending = 0
        val rows = page.map { c ->
            val previous = old[c.key]
            val match = matches[c.key]
            val sameLookup = previous != null && previous.title == c.name && previous.year == c.year && previous.targetVersion == Genre.VERSION
            val settled = match?.takeIf { it.genresVersion == Genre.VERSION }
            // A miss is looked up again after 30 days: providers add records daily (spec 41 Q2).
            val staleMiss = settled?.status == NO_MATCH && now - settled.updatedAt > NO_MATCH_RETRY_MS
            val state = when {
                settled?.status == NO_MATCH && !staleMiss && (previous == null || (previous.state == NO_MATCH && sameLookup)) -> NO_MATCH
                settled?.status == MATCHED -> COMPLETE
                previous?.state == RETRY && sameLookup -> RETRY
                else -> PENDING
            }
            if (state == PENDING) pending++
            val priority = when {
                c.watched -> 0
                c.visible -> 1
                else -> 2
            }
            MetadataQueueEntity(
                c.key, type.wire, c.name, c.year, Genre.VERSION, state,
                if (state == RETRY) previous!!.attempts else 0,
                if (state == RETRY) previous!!.nextAttemptAt else 0,
                priority, stamp,
            )
        }
        db.runInTransaction { dao.putQueue(rows) }
        return pending
    }

    /**
     * The foreground budget (spec 41 Q10, decided): the titles on screen that metadata has not
     * settled yet, looked up one a second and applied one by one, so a fresh library does not show
     * provider titles for its whole first session. Never during playback; the caller cancels it the
     * moment a key moves focus, so a held key pauses it. A provider failure leaves the title to
     * the background worker.
     */
    suspend fun lookUpVisible(titles: List<VisibleTitle>): Unit = withContext(bulk) {
        if (titles.isEmpty()) return@withContext
        val settled = titles.map { it.key }.chunked(IN_LIMIT).flatMap(dao::matchesOf).mapTo(HashSet()) { it.contentKey }
        for (title in titles.take(VISIBLE_MAX)) {
            if (title.key in settled) continue
            if (playing()) return@withContext
            val row = MetadataQueueEntity(title.key, title.type.wire, title.name, title.year, Genre.VERSION, PENDING, 0, 0, 0, 0)
            val outcome = service.catalogue(MetadataRequest(title.type, title.name, title.year, contentKey = title.key))
            if (outcome != CatalogueOutcome.Retry) apply(listOf(row to outcome))
            delay(VISIBLE_SPACING_MS)
        }
    }

    /**
     * One run (META-FR-63): batches until the queue is empty, [budgetMs] has passed, three
     * lookups in a row failed, or the app came to the front.
     */
    suspend fun run(budgetMs: Long = BUDGET_MS): RunEnd = withContext(bulk) { loop(clock.wallMillis() + budgetMs) }

    private suspend fun loop(deadline: Long): RunEnd {
        var refusedInRow = 0
        while (true) {
            if (paused()) return RunEnd.STOPPED
            val batch = nextBatch()
            if (batch.isEmpty()) return RunEnd.DONE
            val results = ArrayList<Pair<MetadataQueueEntity, CatalogueOutcome>>(batch.size)
            var retries = 0
            for (row in batch) {
                if (paused()) break
                delay(SPACING_MS)
                val type = MediaType.entries.firstOrNull { it.wire == row.mediaType } ?: MediaType.MOVIE
                val outcome = service.catalogue(MetadataRequest(type, row.title, row.year, contentKey = row.contentKey))
                results += row to outcome
                if (outcome == CatalogueOutcome.Retry) {
                    retries++
                    val failure = service.lastFailure?.error
                    refusedInRow = if (failure is AppError.MetadataHttp && (failure.status == 401 || failure.status == 403)) refusedInRow + 1 else 0
                    if (retries >= MAX_RETRIES_IN_ROW) break
                } else {
                    retries = 0
                    refusedInRow = 0
                }
            }
            apply(results)
            if (refusedInRow >= MAX_RETRIES_IN_ROW) return RunEnd.KEY_REFUSED
            if (paused()) return RunEnd.STOPPED
            if (retries >= MAX_RETRIES_IN_ROW) return RunEnd.BACK_OFF
            if (clock.wallMillis() >= deadline) return RunEnd.CONTINUE
        }
    }

    /** Up to 60 due titles, pending first, each group by priority then content key (META-FR-57, -65). */
    private fun nextBatch(): List<MetadataQueueEntity> {
        val pending = dao.pending(BATCH)
        if (pending.size == BATCH) return pending
        return pending + dao.retryDue(clock.wallMillis(), BATCH - pending.size)
    }

    /**
     * A batch in one transaction (META-FR-60): matches and misses into `metadata_match` and the
     * rows, failures back off (15 min doubling to 24 h). Then the standing copies of films whose
     * identity changed, and the genre counts at most every 30 s.
     */
    private suspend fun apply(results: List<Pair<MetadataQueueEntity, CatalogueOutcome>>) {
        if (results.isEmpty()) return
        val now = clock.wallMillis()
        val rekeyed = HashSet<String>()
        db.runInTransaction {
            for ((row, outcome) in results) {
                val type = MediaType.entries.firstOrNull { it.wire == row.mediaType } ?: MediaType.MOVIE
                when (outcome) {
                    is CatalogueOutcome.Matched -> {
                        val match = matchOf(row, type, outcome.record, now)
                        if (type == MediaType.MOVIE) dao.workKeyOf(row.contentKey)?.let(rekeyed::add)
                        dao.putMatch(match)
                        passes.apply(match)
                        if (type == MediaType.MOVIE) dao.workKeyOf(row.contentKey)?.let(rekeyed::add)
                        dao.putQueue(listOf(row.copy(state = COMPLETE, attempts = 0, nextAttemptAt = 0)))
                    }
                    CatalogueOutcome.NoMatch -> {
                        val match = MetadataMatchEntity(row.contentKey, row.mediaType, NO_MATCH, null, null, null, Genre.VERSION, row.title, null, false, now)
                        dao.putMatch(match)
                        passes.apply(match)
                        dao.putQueue(listOf(row.copy(state = NO_MATCH, attempts = 0, nextAttemptAt = 0)))
                    }
                    CatalogueOutcome.Retry -> {
                        val attempts = minOf(row.attempts + 1, MAX_ATTEMPTS)
                        dao.putQueue(listOf(row.copy(state = RETRY, attempts = attempts, nextAttemptAt = now + retryDelay(attempts))))
                    }
                }
            }
        }
        if (rekeyed.isNotEmpty()) passes.refreshCopies(rekeyed.toList(), preferredCopy())
        if (now - lastGenreCount >= GENRE_COUNT_MS) {
            passes.recountGenres()
            lastGenreCount = now
        }
    }

    private fun matchOf(row: MetadataQueueEntity, type: MediaType, record: MetadataRecord, now: Long): MetadataMatchEntity {
        // The metadata poster replaces the provider's only where the provider has none (META-FR-60).
        val providerPoster = dao.providerPoster(row.contentKey)
        val title = record.title.trim().take(160).ifEmpty { null }
        val genre = TmdbGenres.primary(type, record.genreIds)?.wire
        return MetadataMatchEntity(
            contentKey = row.contentKey, mediaType = row.mediaType, status = MATCHED, provider = record.provider.id,
            externalId = record.externalId, genre = genre, genresVersion = Genre.VERSION, replacementTitle = title,
            replacementPoster = record.poster, replaceProviderPoster = providerPoster.isNullOrBlank() && record.poster != null, updatedAt = now,
        )
    }

    companion object {
        const val PAGE: Int = 2_000
        const val VISIBLE_MAX: Int = 48
        const val VISIBLE_SPACING_MS: Long = 1_000
        const val IN_LIMIT: Int = 500
        const val BATCH: Int = 60
        const val SPACING_MS: Long = 225
        const val BUDGET_MS: Long = 4 * 60_000
        const val MAX_RETRIES_IN_ROW: Int = 3
        const val MAX_ATTEMPTS: Int = 16
        const val GENRE_COUNT_MS: Long = 30_000
        const val NO_MATCH_RETRY_MS: Long = 30L * 24 * 60 * 60 * 1000
        const val PENDING: String = "pending"
        const val RETRY: String = "retry"
        const val COMPLETE: String = "complete"
        private const val EVENT = "metadata"

        /** 15 min × 2^(attempts − 1), the exponent 0…7, at most 24 h (META-FR-60). */
        fun retryDelay(attempts: Int): Long =
            minOf(15L * 60_000 * (1L shl (attempts - 1).coerceIn(0, 7)), 24L * 60 * 60_000)
    }
}
