package com.sohva.tv.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.source.RefreshKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Background refreshes (spec 10 §4.19 with the SRC-FR-100 rebuild rules): one periodic job runs
 * playlist then guide for every source every *interval* hours, one runs the catalogue every 24
 * hours; both need a network and back off linearly by 15 minutes. "Sync now" is a one-time job so
 * it waits for a network and survives the process. All of them call the one [ImportRunner].
 */
class RefreshScheduler(private val workManager: WorkManager) {
    /** At start-up after the first frame and whenever the interval changes; UPDATE keeps a running job. */
    fun schedule(interval: RefreshInterval) {
        // Beta 23's jobs name worker classes this build does not have; left alone they fail on every
        // run of an upgraded install (same application id). WorkManager tags each job with its class,
        // so this catches every one of them, whatever its unique name.
        LEGACY_WORKERS.forEach(workManager::cancelAllWorkByTag)
        workManager.enqueueUniquePeriodicWork(
            LIVE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RefreshWorker>(interval.hours.toLong(), TimeUnit.HOURS)
                .setConstraints(NETWORK)
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .setInputData(RefreshWorker.input(setOf(RefreshKind.PLAYLIST, RefreshKind.EPG), sourceId = null, now = false, freshMs = TimeUnit.HOURS.toMillis(interval.hours.toLong()) / 2))
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            CATALOGUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RefreshWorker>(CATALOGUE_HOURS, TimeUnit.HOURS)
                .setConstraints(NETWORK)
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .setInputData(RefreshWorker.input(setOf(RefreshKind.CATALOGUE), sourceId = null, now = false, freshMs = TimeUnit.HOURS.toMillis(CATALOGUE_HOURS) / 2))
                .build(),
        )
    }

    /**
     * Sync now (SRC-FR-99): one source, or every source when [sourceId] is null. KEEP: a second
     * request while one waits is the same work; one already running is merged by the runner.
     */
    fun syncNow(sourceId: String?) {
        workManager.enqueueUniqueWork(
            "$SYNC_NOW_PREFIX${sourceId ?: "all"}",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(NETWORK)
                .setInputData(RefreshWorker.input(ImportRunner.ALL_KINDS, sourceId, now = true))
                .build(),
        )
    }

    @Volatile
    private var deferredKinds: Set<RefreshKind> = emptySet()

    @Volatile
    private var deferredFreshMs: Long = 0

    /**
     * An automatic refresh came due while the viewer was in the app. It used to return "retry",
     * and WorkManager's linear backoff then grew with every attempt, up to five hours: after a long
     * evening the refresh ran hours late. It is remembered here instead (in memory: a process that
     * dies in the background runs the periodic work in a new one, which is not in front) and runs
     * when the viewer leaves.
     */
    fun deferred(kinds: Set<RefreshKind>, freshMs: Long) = synchronized(this) {
        deferredKinds = deferredKinds + kinds
        deferredFreshMs = maxOf(deferredFreshMs, freshMs)
    }

    /** The app left the front: what was deferred runs after [LEAVE_DELAY_MS], unless the viewer is back by then. */
    fun onLeave() {
        val (kinds, freshMs) = synchronized(this) {
            val taken = deferredKinds to deferredFreshMs
            deferredKinds = emptySet()
            deferredFreshMs = 0
            taken
        }
        if (kinds.isEmpty()) return
        workManager.enqueueUniqueWork(
            DEFERRED_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(LEAVE_DELAY_MS, TimeUnit.MILLISECONDS)
                .setConstraints(NETWORK)
                .setInputData(RefreshWorker.input(kinds, sourceId = null, now = false, freshMs = freshMs))
                .build(),
        )
    }

    companion object {
        const val LIVE_WORK = "sohva-live-refresh"
        const val DEFERRED_WORK = "sohva-refresh-deferred"
        const val LEAVE_DELAY_MS = 30_000L
        const val CATALOGUE_WORK = "sohva-catalogue-refresh"
        // Not beta 23's "sohva-sync-now-": with KEEP, a job of beta 23's still queued at the upgrade
        // would otherwise stand in for the rebuild's first sync of the imported sources.
        const val SYNC_NOW_PREFIX = "sohva-refresh-now-"
        private const val CATALOGUE_HOURS = 24L
        private const val BACKOFF_MINUTES = 15L
        private val LEGACY_WORKERS = listOf("com.streammate.tv.app.GuideRefreshWorker", "com.streammate.tv.app.CatalogueMetadataWorker")
        private val NETWORK = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}

/** What the application gives the worker: the runner and the facts its deferral rule needs. */
interface RefreshHost {
    val importRunner: ImportRunner

    /** True while at least one activity is started. */
    fun appInForeground(): Boolean

    /** Every live-TV source has completed a playlist import at least once (SRC-FR-98 deferral). */
    suspend fun everyLiveSourceImportedOnce(): Boolean

    /** Failed refreshes recorded at or after [sinceMillis]. */
    suspend fun failuresSince(sinceMillis: Long, kinds: Set<RefreshKind>): Int

    fun nowMillis(): Long

    /** An automatic refresh of [kinds] found the viewer in the app: it runs when they leave ([RefreshScheduler.deferred]). */
    fun refreshDeferred(kinds: Set<RefreshKind>, freshMs: Long)
}

/**
 * Builds the refresh worker with its host (plan/03 §4.4: workers get their dependencies from a
 * factory built from the graph, not from a cast of the application context).
 */
class RefreshWorkerFactory(private val host: RefreshHost) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == RefreshWorker::class.java.name) RefreshWorker(appContext, workerParameters, host) else null
}

/** Runs one scheduled or requested refresh through the runner (SRC-FR-98). */
class RefreshWorker(context: Context, params: WorkerParameters, private val host: RefreshHost) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kinds = inputData.getString(KEY_KINDS).orEmpty().split(',').mapNotNull(RefreshKind::fromId).toSet()
        val sourceId = inputData.getString(KEY_SOURCE)
        val now = inputData.getBoolean(KEY_NOW, false)
        val freshMs = inputData.getLong(KEY_FRESH, 0)
        // An automatic run waits while the viewer is in the app, unless a source still needs its first import.
        if (!now && host.appInForeground() && host.everyLiveSourceImportedOnce()) {
            host.refreshDeferred(kinds, freshMs)
            return Result.success()
        }
        val origin = if (now) WorkOrigin.VIEWER else WorkOrigin.AUTOMATIC
        val started = host.nowMillis()
        run(host, kinds, sourceId, origin, freshMs)
        if (now && RefreshKind.CATALOGUE in kinds && host.failuresSince(started, setOf(RefreshKind.CATALOGUE)) > 0) {
            // Providers often refuse the request that follows a playlist and a guide (SRC-FR-98).
            delay(CATALOGUE_RETRY_MS)
            run(host, setOf(RefreshKind.CATALOGUE), sourceId, origin)
        }
        return if (now || host.failuresSince(started, kinds) == 0) Result.success() else Result.retry()
    }

    private suspend fun run(host: RefreshHost, kinds: Set<RefreshKind>, sourceId: String?, origin: WorkOrigin, freshMs: Long = 0) {
        if (sourceId == null) host.importRunner.syncAll(kinds, origin, freshMs) else host.importRunner.sync(sourceId, kinds, origin).join()
    }

    companion object {
        private const val KEY_KINDS = "kinds"
        private const val KEY_SOURCE = "source_id"
        private const val KEY_NOW = "now"
        private const val KEY_FRESH = "fresh_ms"
        private const val CATALOGUE_RETRY_MS = 20_000L

        /** [freshMs]: a scheduled run skips what succeeded that recently, half its period (see [ImportRunner.syncAll]). */
        fun input(kinds: Set<RefreshKind>, sourceId: String?, now: Boolean, freshMs: Long = 0): Data = Data.Builder()
            .putString(KEY_KINDS, kinds.joinToString(",") { it.id })
            .putString(KEY_SOURCE, sourceId)
            .putBoolean(KEY_NOW, now)
            .putLong(KEY_FRESH, freshMs)
            .build()
    }
}
