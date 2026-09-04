package com.streammate.tv.app

import android.content.Context
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.streammate.tv.iptv.metadata.CatalogueEnrichmentResult
import com.streammate.tv.iptv.metadata.CatalogueEnrichmentUpdate
import com.streammate.tv.iptv.metadata.MetadataLookup
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

object CatalogueMetadataScheduler {
    fun initialize(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
    }

    /** Called after the last StreamMate activity leaves the foreground. */
    fun start(context: Context) {
        enqueue(
            context = context,
            synchronizeQueue = true,
            policy = ExistingWorkPolicy.KEEP,
            initialDelayMillis = BACKGROUND_SETTLE_MILLIS,
        )
    }

    /** Called after a catalogue import changed the active snapshots. */
    fun restart(context: Context) {
        enqueue(
            context = context,
            synchronizeQueue = true,
            policy = ExistingWorkPolicy.REPLACE,
            initialDelayMillis = if (StreamMateForegroundState.isForeground) {
                BACKGROUND_SETTLE_MILLIS
            } else {
                0
            },
        )
    }

    fun pause(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(WORK_NAME)
            cancelUniqueWork(LEGACY_WORK_NAME)
        }
    }

    internal fun continueAfter(context: Context, delayMillis: Long) {
        enqueue(
            context = context,
            synchronizeQueue = false,
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE,
            initialDelayMillis = delayMillis,
        )
    }

    private fun enqueue(
        context: Context,
        synchronizeQueue: Boolean,
        policy: ExistingWorkPolicy,
        initialDelayMillis: Long,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<CatalogueMetadataWorker>()
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putBoolean(CatalogueMetadataWorker.KEY_SYNCHRONIZE_QUEUE, synchronizeQueue)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private const val WORK_NAME = "streammate-catalogue-metadata-enrichment-v2"
    private const val LEGACY_WORK_NAME = "streammate-catalogue-metadata-enrichment"
    private const val BACKGROUND_SETTLE_MILLIS = 30_000L
}

class CatalogueMetadataWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        if (StreamMateForegroundState.isForeground) return Result.success()
        val container = (applicationContext as StreamMateApplication).container
        val metadataRepository = container.metadataRepository
        if (!metadataRepository.isEnabled()) return Result.success()

        val synchronizeQueue = inputData.getBoolean(KEY_SYNCHRONIZE_QUEUE, false) ||
            metadataRepository.catalogueMetadataWorkNeedsSync()
        if (synchronizeQueue) {
            metadataRepository.synchronizeCatalogueMetadataWork(
                container.catalogueRepository.catalogueMetadataCandidates(),
            )
        }

        val stopAt = SystemClock.elapsedRealtime() + MAX_RUN_MILLIS
        while (!StreamMateForegroundState.isForeground && SystemClock.elapsedRealtime() < stopAt) {
            currentCoroutineContext().ensureActive()
            val batch = metadataRepository.nextCatalogueMetadataWork(BATCH_SIZE)
            if (batch.isEmpty()) return Result.success()

            val updates = ArrayList<CatalogueEnrichmentUpdate>(batch.size)
            var consecutiveRetries = 0
            for (work in batch) {
                currentCoroutineContext().ensureActive()
                if (StreamMateForegroundState.isForeground) break
                delay(LOOKUP_SPACING_MILLIS)
                val result = metadataRepository.enrichCatalogue(
                    MetadataLookup(
                        mediaType = work.mediaType,
                        title = work.title,
                        year = work.year,
                    ),
                )
                updates += CatalogueEnrichmentUpdate(work, result)
                if (result == CatalogueEnrichmentResult.Retry) {
                    consecutiveRetries += 1
                    if (consecutiveRetries >= MAX_CONSECUTIVE_RETRIES) break
                } else {
                    consecutiveRetries = 0
                }
            }
            metadataRepository.applyCatalogueEnrichmentBatch(updates)

            if (StreamMateForegroundState.isForeground) return Result.success()
            if (consecutiveRetries >= MAX_CONSECUTIVE_RETRIES) {
                CatalogueMetadataScheduler.continueAfter(applicationContext, PROVIDER_RETRY_MILLIS)
                return Result.success()
            }
        }

        if (!StreamMateForegroundState.isForeground) {
            CatalogueMetadataScheduler.continueAfter(applicationContext, CONTINUATION_DELAY_MILLIS)
        }
        return Result.success()
    }

    companion object {
        const val KEY_SYNCHRONIZE_QUEUE = "catalogue_metadata_synchronize_queue"
        internal const val BATCH_SIZE = 60
        internal const val MAX_CONSECUTIVE_RETRIES = 3
        private const val LOOKUP_SPACING_MILLIS = 225L
        private const val MAX_RUN_MILLIS = 4L * 60 * 1_000
        private const val CONTINUATION_DELAY_MILLIS = 1_000L
        private const val PROVIDER_RETRY_MILLIS = 15L * 60 * 1_000
    }
}
