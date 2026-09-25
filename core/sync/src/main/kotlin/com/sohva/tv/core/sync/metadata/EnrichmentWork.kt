package com.sohva.tv.core.sync.metadata

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * When the enrichment runs (spec 41 META-FR-62, -64): 30 s after the app leaves the front, again
 * after a catalogue import, and in continuations; cancelled when the viewer comes back. One unique
 * work, network required. WorkManager is reached only from these calls, never at start-up.
 */
class EnrichmentScheduler(private val workManager: () -> WorkManager) {
    @Volatile
    private var used = false

    /** The app left the front: run after 30 s, unless a run is already queued. */
    fun onLeave() = enqueue(ExistingWorkPolicy.KEEP, synchronise = true, delayMs = LEAVE_DELAY_MS)

    /**
     * The app came back: a queued or running enrichment stops. Before this process scheduled
     * anything WorkManager is left alone (start-up); a run left from an earlier process stops by
     * itself when it sees the app in front.
     */
    fun onReturn() {
        if (used) workManager().cancelUniqueWork(NAME)
    }

    /** After a catalogue import: synchronise and run, 30 s later when the app is in front. */
    fun afterImport(inForeground: Boolean) = enqueue(ExistingWorkPolicy.REPLACE, synchronise = true, delayMs = if (inForeground) LEAVE_DELAY_MS else 0)

    /** A continuation from a run: 1 s after its budget, 15 min after provider trouble. */
    fun continueAfter(delayMs: Long) = enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE, synchronise = false, delayMs = delayMs)

    /** A language change or a new key restarts from a fresh synchronisation (META-FR-79). */
    fun restart() = enqueue(ExistingWorkPolicy.REPLACE, synchronise = true, delayMs = LEAVE_DELAY_MS)

    private fun enqueue(policy: ExistingWorkPolicy, synchronise: Boolean, delayMs: Long) {
        used = true
        val request = OneTimeWorkRequestBuilder<EnrichmentWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putBoolean(SYNCHRONISE, synchronise).build())
            .build()
        workManager().enqueueUniqueWork(NAME, policy, request)
    }

    companion object {
        const val NAME: String = "metadata-enrichment"
        const val SYNCHRONISE: String = "synchronise"
        const val LEAVE_DELAY_MS: Long = 30_000
        const val BACK_OFF_MS: Long = 15 * 60_000
        const val CONTINUE_MS: Long = 1_000
    }
}

/** What the worker needs from the app. */
interface EnrichmentHost {
    val enrichment: Enrichment
    val enrichmentScheduler: EnrichmentScheduler

    fun appInForeground(): Boolean

    suspend fun metadataEnabled(): Boolean

    /** TMDB refused the key three times in a row (spec 41 Q8): remembered until a key is saved. */
    suspend fun keyRefused(): Boolean

    suspend fun setKeyRefused()
}

/** Builds [EnrichmentWorker] for the app's delegating worker factory. */
class EnrichmentWorkerFactory(private val host: EnrichmentHost) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == EnrichmentWorker::class.java.name) EnrichmentWorker(appContext, workerParameters, host) else null
}

/** One enrichment run (META-FR-63): synchronise when asked or needed, then batches for up to 4 minutes. */
class EnrichmentWorker(context: Context, params: WorkerParameters, private val host: EnrichmentHost) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (host.appInForeground() || !host.metadataEnabled() || host.keyRefused()) return Result.success()
        val enrichment = host.enrichment
        if (inputData.getBoolean(EnrichmentScheduler.SYNCHRONISE, false) || enrichment.needsSync()) enrichment.synchronise()
        when (enrichment.run()) {
            RunEnd.CONTINUE -> host.enrichmentScheduler.continueAfter(EnrichmentScheduler.CONTINUE_MS)
            RunEnd.BACK_OFF -> host.enrichmentScheduler.continueAfter(EnrichmentScheduler.BACK_OFF_MS)
            RunEnd.KEY_REFUSED -> host.setKeyRefused()
            RunEnd.DONE, RunEnd.STOPPED -> Unit
        }
        return Result.success()
    }
}
