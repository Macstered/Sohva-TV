package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.KeyedId
import com.sohva.tv.core.data.source.SourceStore
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.ImportRoute
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.core.net.xtream.XtreamUrls
import com.sohva.tv.core.sync.diff.KeyedDiff
import com.sohva.tv.core.sync.diff.Room
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one entry for imports (spec 10 SRC-FR-94): Settings, the phone page, the guide's empty
 * state and the background worker all call [sync]. It runs in the application's scope, so an import
 * started from Settings continues after Settings closes, and its progress is observable.
 *
 * - One import of a kind per source at a time (SRC-FR-75): a mutex per (source, kind). A second
 *   request for kinds already queued or running for that source joins the running job
 *   (SRC-FR-100: merged, not dropped).
 * - Kinds run playlist → guide → catalogue, filtered by the source's scope; ANALYZE of the touched
 *   tables runs once at the end (SRC-FR-79).
 * - Cancellation is not failure (SRC-FR-78): the refresh state goes back to what it was.
 * - [remove] stops the source's imports before deleting anything, so no import can recreate rows
 *   (SRC-FR-41 rebuild rule).
 */
class ImportRunner(
    private val env: ImportEnvironment,
    private val sources: SourceStore,
    private val scope: CoroutineScope,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val running = HashMap<String, MutableList<Running>>()
    // Not ConcurrentHashMap.newKeySet() (API 24): on Android 6 it would swap in the desugared map.
    private val removing: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val status = StatusBook(env.db.sourceStatus(), env.clock)
    private val live = LiveImport(env)
    private val guide = GuideImport(env)
    private val catalogue = CatalogueImport(env)

    private val progressState = MutableStateFlow<Map<String, ImportProgress>>(emptyMap())

    /** Running imports by source id. */
    val progress: StateFlow<Map<String, ImportProgress>> = progressState.asStateFlow()

    private class Running(val job: Job, val kinds: Set<RefreshKind>)

    /** Imports [kinds] of one source; returns the job doing it (possibly one already running). */
    fun sync(sourceId: String, kinds: Set<RefreshKind> = ALL_KINDS, origin: WorkOrigin = WorkOrigin.VIEWER): Job = synchronized(running) {
        val jobs = running.getOrPut(sourceId) { ArrayList() }
        jobs.firstOrNull { it.job.isActive && it.kinds.containsAll(kinds) }?.let { return it.job }
        val job = scope.launch { runSource(sourceId, kinds, origin) }
        val entry = Running(job, kinds)
        jobs += entry
        job.invokeOnCompletion {
            synchronized(running) {
                running[sourceId]?.let { list ->
                    list.remove(entry)
                    if (list.isEmpty()) running.remove(sourceId)
                }
            }
        }
        job
    }

    /**
     * Every enabled source, kind by kind: all playlists, then all guides, then all catalogues (SRC-FR-99).
     * With [freshMs], a source whose kind succeeded that recently is left out: a scheduled run that
     * waited while the viewer was in the app must not redo the first sync it just missed (decision
     * "No second refresh right after a sync").
     */
    suspend fun syncAll(kinds: Set<RefreshKind> = ALL_KINDS, origin: WorkOrigin, freshMs: Long = 0) {
        val enabled = sources.all().filter { it.enabled }
        for (kind in ORDER.filter { it in kinds }) {
            val due = if (freshMs <= 0) {
                enabled
            } else {
                withContext(env.dispatchers.bulkWrite) {
                    val since = env.clock.wallMillis() - freshMs
                    enabled.filter { (status.get(it.id, kind)?.lastSuccessAt ?: 0L) < since }
                }
            }
            due.map { sync(it.id, setOf(kind), origin) }.forEach { it.join() }
        }
    }

    /** Cancels and awaits the source's imports, deletes its rows, then its configuration. */
    suspend fun remove(sourceId: String): Outcome<Unit> {
        removing += sourceId
        try {
            val jobs = synchronized(running) { running[sourceId]?.map { it.job }.orEmpty() }
            jobs.forEach { it.cancelAndJoin() }
            return withLocks(sourceId) {
                withContext(env.dispatchers.bulkWrite + NonCancellable) { deleteContent(sourceId) }
                sources.removeConfiguration(sourceId)
            }
        } finally {
            removing -= sourceId
        }
    }

    /**
     * Start-up repair after the first frame: refreshes left `running` by a dead process read as
     * interrupted, and guide snapshots it left half-written are swept (spec 10 §8, SRC-L-17).
     */
    suspend fun recoverAfterRestart() {
        val interrupted = withContext(env.dispatchers.bulkWrite) { env.db.sourceStatus().markInterrupted(env.clock.wallMillis()) }
        if (interrupted == 0) return
        for (source in sources.all()) {
            lock(source.id, RefreshKind.EPG).withLock {
                val active = withContext(env.dispatchers.bulkWrite) { status.get(source.id, RefreshKind.EPG)?.epgSnapshot }
                guide.sweep(source.id, active ?: GuideImport.NO_SNAPSHOT)
            }
        }
    }

    /**
     * Settings › Clear all guide data (spec 70 SET-FR-96): every stored programme, source by source
     * under its guide lock so a running guide import finishes first. Channels, films and progress stay
     * (decision "Clear all guide data").
     */
    suspend fun clearGuide() {
        for (source in sources.all()) {
            lock(source.id, RefreshKind.EPG).withLock { guide.sweep(source.id, GuideImport.NO_SNAPSHOT) }
        }
    }

    private suspend fun runSource(sourceId: String, kinds: Set<RefreshKind>, origin: WorkOrigin) {
        var config = when (val loaded = sources.load(sourceId)) {
            is Outcome.Failed -> {
                withContext(env.dispatchers.bulkWrite) { kinds.forEach { status.fail(sourceId, it, loaded.error) } }
                return
            }
            is Outcome.Ok -> loaded.value ?: return
        }
        if (!config.source.enabled && origin == WorkOrigin.AUTOMATIC) return
        val touched = HashSet<String>()
        for (kind in ORDER.filter { it in kinds && applies(config, it) }) {
            env.pauseGate.awaitTurn(origin)
            lock(sourceId, kind).withLock {
                // The source may have been removed while this import waited for the lock.
                if (sourceId in removing || sources.source(sourceId) == null) return
                val guideUrl = runKind(config, kind, origin, touched)
                if (guideUrl != null) config = adoptPlaylistGuide(config, guideUrl)
            }
        }
        if (touched.isNotEmpty()) analyze(touched)
    }

    /** Runs one kind; returns the playlist header's guide address when the import found one. */
    private suspend fun runKind(config: SourceConfig, kind: RefreshKind, origin: WorkOrigin, touched: MutableSet<String>): String? {
        val sourceId = config.source.id
        val route = ImportRoute.of(config) ?: return null
        val guideUrl = if (kind == RefreshKind.EPG) guideUrlOf(route) ?: return null else ""
        val (previous, generation) = withContext(env.dispatchers.bulkWrite) { status.start(sourceId, kind) }
        val job = ImportJob(sourceId, origin, generation) { rows ->
            progressState.update { it + (sourceId to ImportProgress(sourceId, kind.id, rows)) }
        }
        try {
            var headerGuide: String? = null
            when (kind) {
                RefreshKind.PLAYLIST -> {
                    val result = when (route) {
                        is ImportRoute.M3u -> live.m3u(route, config.source.importScope, job)
                        is ImportRoute.Xtream -> live.xtream(route, job)
                    }
                    headerGuide = result.headerGuideUrl
                    touched += listOf("channel", "content_group")
                    withContext(env.dispatchers.bulkWrite) { status.succeed(sourceId, kind, result.channels) }
                }
                RefreshKind.EPG -> {
                    val active = previous?.epgSnapshot
                    val result = guide.run(guideUrl, job, active)
                    withContext(env.dispatchers.bulkWrite) {
                        status.succeed(sourceId, kind, result.programmes, epgSnapshot = generation, epgMaxDurationMs = result.maxDurationMs)
                    }
                    guide.sweep(sourceId, keep = generation)
                    touched += listOf("programme", "epg_channel")
                }
                RefreshKind.CATALOGUE -> {
                    val count = when (route) {
                        is ImportRoute.M3u -> catalogue.m3u(route, config.source.importScope, job)
                        is ImportRoute.Xtream -> catalogue.xtream(route, job)
                    }
                    touched += listOf("movie", "series", "episode", "content_group")
                    withContext(env.dispatchers.bulkWrite) { status.succeed(sourceId, kind, count) }
                }
            }
            env.log.info(EVENT, "${kind.id} ok: ${job.rows} rows")
            return headerGuide
        } catch (e: CancellationException) {
            withContext(env.dispatchers.bulkWrite + NonCancellable) { status.cancelled(sourceId, kind, previous, generation) }
            throw e
        } catch (e: AppException) {
            withContext(env.dispatchers.bulkWrite + NonCancellable) { status.fail(sourceId, kind, e.error) }
            env.log.info(EVENT, "${kind.id} failed: ${e.error.code}")
            return null
        } catch (e: RuntimeException) {
            // A defect, not a provider problem: record it plainly and keep the other kinds going.
            withContext(env.dispatchers.bulkWrite + NonCancellable) { status.fail(sourceId, kind, AppError.Unknown) }
            env.log.error(EVENT, "${kind.id} failed", e)
            return null
        } finally {
            progressState.update { it - sourceId }
        }
    }

    /** The guide to read: the XMLTV field, else for Xtream routes the account's `xmltv.php` (SRC-FR-73, SRC-FR-86). */
    private fun guideUrlOf(route: ImportRoute): String? = when (route) {
        is ImportRoute.M3u -> route.guideUrl
        is ImportRoute.Xtream -> route.explicitGuideUrl ?: XtreamUrls(route.account).guide().toString()
    }

    /** A playlist's own `url-tvg` fills an empty XMLTV field (plan/09 M1), saved so the viewer sees it. */
    private suspend fun adoptPlaylistGuide(config: SourceConfig, url: String): SourceConfig {
        if (config.source.type != SourceType.M3U || config.secrets.xmlTvUrl != null) return config
        if (ImportRoute.of(config) !is ImportRoute.M3u) return config
        if (SourceRules.checkAddress(url, AppError.FieldLabel.XMLTV) != null) return config
        val updated = config.copy(secrets = config.secrets.copy(xmlTvUrl = url))
        return when (val saved = sources.save(updated)) {
            is Outcome.Ok -> saved.value
            is Outcome.Failed -> config
        }
    }

    private fun applies(config: SourceConfig, kind: RefreshKind): Boolean = when (kind) {
        RefreshKind.PLAYLIST, RefreshKind.EPG -> config.source.importScope.includesLive
        RefreshKind.CATALOGUE -> config.source.importScope.includesVod
    }

    private fun lock(sourceId: String, kind: RefreshKind): Mutex = locks.getOrPut("$sourceId/${kind.id}") { Mutex() }

    private suspend fun <T> withLocks(sourceId: String, block: suspend () -> T): T =
        lock(sourceId, RefreshKind.PLAYLIST).withLock {
            lock(sourceId, RefreshKind.EPG).withLock {
                lock(sourceId, RefreshKind.CATALOGUE).withLock { block() }
            }
        }

    /** Every row of the source, in short transactions (plan/04 §15.1 principle 8: chunked, explicit cascades). */
    private suspend fun deleteContent(sourceId: String) {
        val db = env.db
        deleteRange(KeyRange.channels(sourceId), db.channelImport()::keysPage, db.channelImport()::delete)
        deleteRange(KeyRange.episodes(sourceId), db.episodeImport()::keysPage, db.episodeImport()::delete)
        deleteRange(KeyRange.movies(sourceId), db.movieImport()::keysPage, db.movieImport()::delete)
        deleteRange(KeyRange.series(sourceId), db.seriesImport()::keysPage, db.seriesImport()::delete)
        guide.sweep(sourceId, GuideImport.NO_SNAPSHOT)
        // The household's edits and list memberships of its channels go with it (spec 21 CHAN-30).
        val channels = KeyRange.channels(sourceId)
        do {
            val deleted = db.runInTransaction<Int> { db.channelEdits().deleteCustomsOf(sourceId, KeyedDiff.DELETE_CHUNK) }
        } while (deleted > 0)
        do {
            val deleted = db.runInTransaction<Int> { db.channelEdits().deleteMembersIn(channels.from, channels.until, KeyedDiff.DELETE_CHUNK) }
        } while (deleted > 0)
        db.runInTransaction {
            for (room in Room.entries) {
                db.groupImport().groups(sourceId, room.name).map { it.id }.chunked(KeyedDiff.DELETE_CHUNK).forEach(db.groupImport()::delete)
            }
            db.sourceStatus().deleteForSource(sourceId)
        }
    }

    private fun deleteRange(range: KeyRange, page: (String, String, Int) -> List<KeyedId>, delete: (List<Long>) -> Unit) {
        while (true) {
            val ids = page(range.from, range.until, KeyedDiff.DELETE_CHUNK).map { it.id }
            if (ids.isEmpty()) return
            env.db.runInTransaction { delete(ids) }
        }
    }

    private suspend fun analyze(tables: Set<String>) = withContext(env.dispatchers.bulkWrite) {
        val sql = env.db.openHelper.writableDatabase
        for (table in tables) sql.execSQL("ANALYZE $table")
    }

    companion object {
        val ALL_KINDS: Set<RefreshKind> = RefreshKind.entries.toSet()
        private val ORDER = listOf(RefreshKind.PLAYLIST, RefreshKind.EPG, RefreshKind.CATALOGUE)
        private const val EVENT = "import"
    }
}
