package com.sohva.tv.feature.trakt.sync

import com.sohva.tv.core.data.database.TraktStateEntity
import com.sohva.tv.core.data.trakt.TraktStateTable
import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.LastActivities
import com.sohva.tv.feature.trakt.protocol.PausedItem
import com.sohva.tv.feature.trakt.protocol.TraktException
import com.sohva.tv.feature.trakt.protocol.TraktFailure
import com.sohva.tv.feature.trakt.protocol.TraktKind
import com.sohva.tv.feature.trakt.protocol.WatchedMovie
import com.sohva.tv.feature.trakt.protocol.WatchedShow
import com.sohva.tv.feature.trakt.shelf.TraktCard
import com.sohva.tv.feature.trakt.shelf.TraktShelf
import com.sohva.tv.feature.trakt.shelf.TraktShelfKind
import com.sohva.tv.feature.trakt.shelf.TraktShelves
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The read side of Trakt (spec 51 FR-22, -23, -25, -26), one sync at a time per app. Each of the
 * four activity times is compared with its own stamp and only what moved is fetched (§9 rule):
 * a new pause fetches the playback list only, a watched film the films list only. Any failed
 * call abandons the cycle; the cache and stamps are written only after complete lists.
 */
class TraktSync(
    private val host: TraktHost,
    private val table: TraktStateTable,
    private val shelves: TraktShelves,
) {
    private val lock = Mutex()
    private val diff = TraktStateDiff(table)

    /** The four activity stamps and the sync format stored with them (§6 `activity:`, FR-23). */
    private data class Stamps(val activities: LastActivities, val format: Long)

    private data class Need(val movies: Boolean, val shows: Boolean, val paused: Boolean) {
        val any: Boolean get() = movies || shows || paused
        val watched: Boolean get() = movies || shows
    }

    private fun need(before: LastActivities, now: LastActivities) = Need(
        movies = now.moviesWatched > before.moviesWatched,
        shows = now.episodesWatched > before.episodesWatched,
        paused = now.moviesPaused > before.moviesPaused || now.episodesPaused > before.episodesPaused,
    )

    /**
     * FR-22. [force] fetches everything (no Watch next stored yet, or an older sync format).
     * Returns false when there is nothing to sync with or a call failed.
     */
    suspend fun sync(profile: String, force: Boolean = false, watchNext: Boolean = true): Boolean = lock.withLock {
        if (!host.configured || !host.access.allowed(profile)) return false
        val account = host.account(profile)?.takeIf { it.tokens != null } ?: return false
        try {
            pull(profile, account.uuid, force, watchNext)
            true
        } catch (e: TraktException) {
            val wait = host.gate.waitSeconds().takeIf { it > 0 }?.let { ", Trakt asked to wait $it s" }.orEmpty()
            host.log.info("trakt", "sync abandoned (${e.failure.name.lowercase()}$wait)")
            false
        }
    }

    private suspend fun pull(profile: String, uuid: String, force: Boolean, watchNext: Boolean) {
        val before = host.gate.requests
        val stamps = withContext(host.dispatchers.io) { readStamps(profile) }
        val full = force || stamps.format < FORMAT_VERSION
        var acts = activities(profile)
        var need = if (full) Need(movies = true, shows = true, paused = true) else need(stamps.activities, acts)
        if (!need.any) return

        var movies: List<WatchedMovie>? = null
        var shows: List<WatchedShow>? = null
        var paused: List<PausedItem>? = null
        var pausedWritten = false
        // FR-23: the lists are walked again once when Trakt's activity moved during the walk.
        for (walk in 1..2) {
            coroutineScope {
                val m = if (need.movies) async { host.withTokens(profile) { host.api.watchedMovies(it.access) } } else null
                val s = if (need.shows) async { host.withTokens(profile) { host.api.watchedShows(it.access) } } else null
                val p = if (need.paused) async {
                    val list = host.withTokens(profile) { host.api.playback(it.access) }
                    // FR-22 step 3: bars appear before the larger watched lists finish.
                    if (need.watched && sameAccount(profile, uuid)) {
                        applyPaused(profile, list)
                        pausedWritten = true
                    }
                    list
                } else null
                m?.let { movies = it.await() }
                s?.let { shows = it.await() }
                p?.let { paused = it.await() }
            }
            val after = activities(profile)
            if (after == acts || walk == 2) break
            need = need(acts, after)
            if (need.paused) pausedWritten = false
            acts = after
        }
        if (!sameAccount(profile, uuid)) return

        val pausedRows = paused?.let { TraktStateRows.paused(profile, it) }
        val kinds = listOf(
            Triple(TraktStateRows.MOVIE, movies?.let { TraktStateRows.watchedMovies(profile, it) }, pausedRows?.filter { it.kind == TraktStateRows.MOVIE }),
            Triple(TraktStateRows.EPISODE, shows?.let { TraktStateRows.watchedEpisodes(profile, it) }, pausedRows?.filter { it.kind == TraktStateRows.EPISODE }),
        )
        for ((kind, watched, pausedKind) in kinds) {
            if (watched == null && (pausedKind == null || pausedWritten)) continue
            write(profile, kind, watched, pausedKind)
        }
        withContext(host.dispatchers.io) {
            host.store.putLong("activity:$profile", acts.newest)
            host.store.putLong("activity-mw:$profile", acts.moviesWatched)
            host.store.putLong("activity-mp:$profile", acts.moviesPaused)
            host.store.putLong("activity-ew:$profile", acts.episodesWatched)
            host.store.putLong("activity-ep:$profile", acts.episodesPaused)
            // Written last: a sync cut short leaves the old format and the next one is full again.
            host.store.putLong("format:$profile", FORMAT_VERSION)
        }
        host.log.info("trakt", "synced ${table.count(profile)} Trakt titles; ${host.gate.requests - before} requests")
        shows?.let { list ->
            if (list.isNotEmpty() && list.all { it.episodes.isEmpty() }) host.log.info("trakt", "watched shows came without seasons")
            // A hidden Watch next makes no progress calls (spec 02 HOME-FR-87).
            if (watchNext) watchNext(profile, uuid, list)
        }
    }

    private suspend fun activities(profile: String): LastActivities = host.withTokens(profile) { host.api.lastActivities(it.access) }

    private suspend fun sameAccount(profile: String, uuid: String): Boolean = host.account(profile)?.uuid == uuid

    private suspend fun applyPaused(profile: String, list: List<PausedItem>) {
        val rows = TraktStateRows.paused(profile, list)
        write(profile, TraktStateRows.MOVIE, null, rows.filter { it.kind == TraktStateRows.MOVIE })
        write(profile, TraktStateRows.EPISODE, null, rows.filter { it.kind == TraktStateRows.EPISODE })
    }

    private suspend fun write(profile: String, kind: String, watched: List<TraktStateEntity>?, paused: List<TraktStateEntity>?) {
        val result = withContext(host.dispatchers.parse) { diff.compute(profile, kind, watched, paused) }
        table.write(profile, result.deletes, result.upserts)
    }

    private fun readStamps(profile: String) = Stamps(
        LastActivities(
            host.store.long("activity-mw:$profile"),
            host.store.long("activity-mp:$profile"),
            host.store.long("activity-ew:$profile"),
            host.store.long("activity-ep:$profile"),
        ),
        host.store.long("format:$profile"),
    )

    /** FR-24: an account without a first complete sync yet. */
    suspend fun firstSyncPending(profile: String): Boolean =
        withContext(host.dispatchers.io) { host.store.long("activity:$profile") == 0L }

    /**
     * FR-25: the eight most recently watched shows with a Trakt id, three at a time; a show whose
     * calls fail keeps its previous card, a finished or missing show leaves. Published and stored
     * once when all are done (§9 rule).
     */
    private suspend fun watchNext(profile: String, uuid: String, shows: List<WatchedShow>) {
        val previous = shelves.read(profile, TraktShelfKind.WATCH_NEXT)?.cards.orEmpty().associateBy { it.ids.trakt }
        val picks = shows.filter { it.ids.trakt != null }.sortedByDescending { it.lastWatchedAt }.take(WATCH_NEXT_SHOWS)
        val gate = Semaphore(WATCH_NEXT_PARALLEL)
        val cards = coroutineScope {
            picks.map { show -> async { gate.withPermit { nextCard(profile, show, previous[show.ids.trakt]) } } }.awaitAll()
        }.filterNotNull().sortedByDescending { it.updatedAt }
        if (!sameAccount(profile, uuid)) return
        shelves.save(profile, TraktShelfKind.WATCH_NEXT, TraktShelf(host.clock.wallMillis(), cards))
        host.log.info("trakt", "next up: ${cards.size} shows")
    }

    private suspend fun nextCard(profile: String, show: WatchedShow, previous: TraktCard?): TraktCard? {
        val trakt = show.ids.trakt ?: return null
        return try {
            val progress = host.withTokens(profile) { host.api.showProgress(it.access, trakt) } ?: return null
            val season = progress.nextSeason ?: return null
            val number = progress.nextNumber ?: return null
            val title = previous?.takeIf { it.poster != null }?.let { p -> Picture(p.title, p.year, p.overview, p.poster, p.fanart) }
                ?: host.withTokens(profile) { host.api.showSummary(it.access, trakt) }?.let { t -> Picture(t.title, t.year, t.overview, t.poster, t.fanart) }
                ?: return null
            TraktCard(
                TraktKind.SHOW, show.ids, title.title, title.year, title.overview, title.poster, title.fanart,
                season, number, progress.nextTitle, maxOf(show.lastWatchedAt, progress.lastWatchedAt),
            )
        } catch (e: TraktException) {
            if (e.failure == TraktFailure.NOT_FOUND) null else previous
        }
    }

    private class Picture(val title: String, val year: Int?, val overview: String?, val poster: String?, val fanart: String?)

    /** FR-26: when the stored list is missing or older than 12 hours; films and shows interleaved. */
    suspend fun recommendations(profile: String) {
        if (!host.configured || !host.access.allowed(profile)) return
        val account = host.account(profile)?.takeIf { it.tokens != null } ?: return
        val stored = shelves.read(profile, TraktShelfKind.RECOMMENDED)
        if (stored != null && host.clock.wallMillis() - stored.at < RECOMMENDED_MAX_AGE_MS) return
        try {
            val (films, series) = coroutineScope {
                val m = async { host.withTokens(profile) { host.api.recommendations(it.access, TraktKind.MOVIE, RECOMMENDED_PER_KIND) } }
                val s = async { host.withTokens(profile) { host.api.recommendations(it.access, TraktKind.SHOW, RECOMMENDED_PER_KIND) } }
                m.await() to s.await()
            }
            val cards = ArrayList<TraktCard>()
            for (i in 0 until maxOf(films.size, series.size)) {
                films.getOrNull(i)?.let { cards += TraktCard(it.kind, it.ids, it.title, it.year, it.overview, it.poster, it.fanart) }
                series.getOrNull(i)?.let { cards += TraktCard(it.kind, it.ids, it.title, it.year, it.overview, it.poster, it.fanart) }
            }
            if (!sameAccount(profile, account.uuid)) return
            shelves.save(profile, TraktShelfKind.RECOMMENDED, TraktShelf(host.clock.wallMillis(), cards))
            host.log.info("trakt", "recommendations: ${cards.size} titles")
        } catch (e: TraktException) {
            host.log.info("trakt", "recommendations kept (${e.failure.name.lowercase()})")
        }
    }

    companion object {
        /** FR-23: raised whenever a sync fix needs every account to walk its lists again. */
        const val FORMAT_VERSION: Long = 1
        const val WATCH_NEXT_SHOWS: Int = 8
        const val WATCH_NEXT_PARALLEL: Int = 3
        const val RECOMMENDED_PER_KIND: Int = 10
        const val RECOMMENDED_MAX_AGE_MS: Long = 12L * 60 * 60 * 1000
    }
}
