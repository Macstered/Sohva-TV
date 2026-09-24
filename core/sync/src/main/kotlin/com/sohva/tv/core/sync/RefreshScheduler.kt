package com.sohva.tv.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
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
        // Beta 23's jobs name a worker class this build does not have; left alone they would fail
        // on every run of an upgraded install (same application id).
        LEGACY_WORK.forEach(workManager::cancelUniqueWork)
        workManager.enqueueUniquePeriodicWork(
            LIVE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RefreshWorker>(interval.hours.toLong(), TimeUnit.HOURS)
                .setConstraints(NETWORK)
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .setInputData(RefreshWorker.input(setOf(RefreshKind.PLAYLIST, RefreshKind.EPG), sourceId = null, now = false))
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            CATALOGUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RefreshWorker>(CATALOGUE_HOURS, TimeUnit.HOURS)
                .setConstraints(NETWORK)
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .setInputData(RefreshWorker.input(setOf(RefreshKind.CATALOGUE), sourceId = null, now = false))
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

    companion object {
        const val LIVE_WORK = "sohva-live-refresh"
        const val CATALOGUE_WORK = "sohva-catalogue-refresh"
        const val SYNC_NOW_PREFIX = "sohva-sync-now-"
        private const val CATALOGUE_HOURS = 24L
        private const val BACKOFF_MINUTES = 15L
        private val LEGACY_WORK = listOf("streammate-playlist-refresh", "streammate-epg-refresh", "streammate-catalogue-refresh")
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
}

/** Runs one scheduled or requested refresh through the runner (SRC-FR-98). */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val host = applicationContext as? RefreshHost ?: return Result.failure()
        val kinds = inputData.getString(KEY_KINDS).orEmpty().split(',').mapNotNull(RefreshKind::fromId).toSet()
        val sourceId = inputData.getString(KEY_SOURCE)
        val now = inputData.getBoolean(KEY_NOW, false)
        // An automatic run waits while the viewer is in the app, unless a source still needs its first import.
        if (!now && host.appInForeground() && host.everyLiveSourceImportedOnce()) return Result.retry()
        val origin = if (now) WorkOrigin.VIEWER else WorkOrigin.AUTOMATIC
        val started = host.nowMillis()
        run(host, kinds, sourceId, origin)
        if (now && RefreshKind.CATALOGUE in kinds && host.failuresSince(started, setOf(RefreshKind.CATALOGUE)) > 0) {
            // Providers often refuse the request that follows a playlist and a guide (SRC-FR-98).
            delay(CATALOGUE_RETRY_MS)
            run(host, setOf(RefreshKind.CATALOGUE), sourceId, origin)
        }
        return if (now || host.failuresSince(started, kinds) == 0) Result.success() else Result.retry()
    }

    private suspend fun run(host: RefreshHost, kinds: Set<RefreshKind>, sourceId: String?, origin: WorkOrigin) {
        if (sourceId == null) host.importRunner.syncAll(kinds, origin) else host.importRunner.sync(sourceId, kinds, origin).join()
    }

    companion object {
        private const val KEY_KINDS = "kinds"
        private const val KEY_SOURCE = "source_id"
        private const val KEY_NOW = "now"
        private const val CATALOGUE_RETRY_MS = 20_000L

        fun input(kinds: Set<RefreshKind>, sourceId: String?, now: Boolean): Data = Data.Builder()
            .putString(KEY_KINDS, kinds.joinToString(",") { it.id })
            .putString(KEY_SOURCE, sourceId)
            .putBoolean(KEY_NOW, now)
            .build()
    }
}
