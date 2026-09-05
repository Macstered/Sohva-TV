package com.streammate.tv.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.streammate.tv.iptv.repository.GuideImportException
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.iptv.xtream.derivedXtreamSourceOrNull
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

object GuideRefreshScheduler {
    fun schedule(context: Context, playlistEpgInterval: PlaylistEpgRefreshInterval) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        RefreshKind.entries.forEach { kind ->
            scheduleKind(
                context = context,
                kind = kind,
                repeatHours = repeatHoursFor(kind, playlistEpgInterval),
                constraints = constraints,
            )
        }
    }

    internal fun repeatHoursFor(
        kind: RefreshKind,
        playlistEpgInterval: PlaylistEpgRefreshInterval,
    ): Long = when (kind) {
        RefreshKind.PLAYLIST,
        RefreshKind.EPG -> playlistEpgInterval.hours
        RefreshKind.CATALOGUE -> CATALOGUE_REFRESH_HOURS
    }

    /**
     * Periodic maintenance yields to a viewer who is using the app. A sync the
     * viewer asked for, and the very first import of a source that has never
     * produced channels, run regardless: a fresh install is always in the
     * foreground, and waiting for it to leave meant the guide stayed empty for
     * as long as anyone was looking at it.
     */
    internal fun shouldDeferAutomaticRefresh(
        isAppForeground: Boolean,
        immediate: Boolean = false,
        awaitingFirstImport: Boolean = false,
    ): Boolean = isAppForeground && !immediate && !awaitingFirstImport

    /** Channels, then the guide, then movies and series, for one source or all of them, now. */
    fun syncNow(context: Context, sourceId: String? = null) {
        val request = OneTimeWorkRequestBuilder<GuideRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                Data.Builder()
                    .putString(GuideRefreshWorker.KEY_KIND, GuideRefreshWorker.KIND_ALL)
                    .putString(GuideRefreshWorker.KEY_SOURCE_ID, sourceId)
                    .putBoolean(GuideRefreshWorker.KEY_IMMEDIATE, true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sohva-sync-now-${sourceId ?: "all"}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleKind(
        context: Context,
        kind: RefreshKind,
        repeatHours: Long,
        constraints: Constraints,
    ) {
        val request = PeriodicWorkRequestBuilder<GuideRefreshWorker>(repeatHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString(GuideRefreshWorker.KEY_KIND, kind.name).build())
            // If the scheduled run lands while StreamMate is being browsed,
            // retry after the foreground session instead of competing with
            // Compose, image decoding and focus for CPU and GC time.
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                FOREGROUND_RETRY_MINUTES,
                TimeUnit.MINUTES,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "streammate-${kind.name.lowercase()}-refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private const val CATALOGUE_REFRESH_HOURS = 24L
    private const val FOREGROUND_RETRY_MINUTES = 15L
}

enum class RefreshKind {
    PLAYLIST,
    EPG,
    CATALOGUE,
}

class GuideRefreshWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val kinds = refreshKindsFor(inputData.getString(KEY_KIND)) ?: return Result.failure()
        val immediate = inputData.getBoolean(KEY_IMMEDIATE, false)
        val onlySourceId = inputData.getString(KEY_SOURCE_ID)
        val sources = container().secretSettingsStore.loadSources()
            .asSequence()
            .filter { it.enabled }
            .filter { onlySourceId == null || it.id == onlySourceId }
            .sortedByDescending { it.priority }
            .toList()
        // A Shield trace caught the periodic playlist refresh encrypting
        // thousands of URLs while the viewer switched VOD groups. Automatic
        // maintenance is deferrable; a sync the viewer asked for, or the
        // first import of a source, is not.
        val awaitingFirstImport = sources.any { source ->
            source.importScope.importsLiveTv && !container().guideRepository.hasImportedPlaylist(source.id)
        }
        if (
            GuideRefreshScheduler.shouldDeferAutomaticRefresh(
                isAppForeground = StreamMateForegroundState.isForeground,
                immediate = immediate,
                awaitingFirstImport = awaitingFirstImport,
            )
        ) {
            return Result.retry()
        }
        var failedSources = 0
        kinds.forEach { kind ->
            sources.forEach { source ->
                var failed = runRefresh(kind, source)
                // A provider that has just served the playlist and a week of
                // guide often refuses the catalogue request that follows on
                // its heels. One more try after a breath is cheap.
                if (failed > 0 && kind == RefreshKind.CATALOGUE && immediate) {
                    delay(CATALOGUE_RETRY_DELAY_MILLIS)
                    failed = runRefresh(kind, source)
                }
                failedSources += failed
            }
            if (kind == RefreshKind.CATALOGUE) {
                CatalogueMetadataScheduler.restart(applicationContext)
            }
        }
        // An immediate sync reports through the refresh states the screens
        // show; retrying it would only repeat a failure the viewer can see.
        return when {
            failedSources == 0 || immediate -> Result.success()
            else -> Result.retry()
        }
    }

    /** One kind for one source; 1 when the import failed, 0 otherwise. */
    private suspend fun runRefresh(kind: RefreshKind, source: IptvSourceConfiguration): Int {
        return try {
            container().guideRepository.upsertSourceState(source)
                when (source.type) {
                    IptvSourceType.M3U -> {
                        when (kind) {
                            RefreshKind.PLAYLIST -> if (source.importScope.importsLiveTv) {
                                source.derivedXtreamSourceOrNull()
                                    ?.let { container().xtreamImportService.refreshPlaylist(it) }
                                    ?: container().guideImportService.refreshPlaylist(source)
                            }
                            RefreshKind.EPG -> if (source.importScope.importsLiveTv) {
                                val guideUrl = source.xmlTvUrl?.takeIf(String::isNotBlank)
                                if (guideUrl != null) {
                                    container().guideImportService.refreshEpg(source.id, guideUrl)
                                } else {
                                    source.derivedXtreamSourceOrNull()
                                        ?.let { container().xtreamImportService.refreshEpg(it) }
                                }
                            }
                            RefreshKind.CATALOGUE -> if (source.importScope.importsVod) {
                                source.derivedXtreamSourceOrNull()
                                    ?.let { container().xtreamCatalogueImportService.refresh(it) }
                                    ?: container().m3uCatalogueImportService.refresh(source)
                            }
                        }
                    }
                    IptvSourceType.XTREAM -> when (kind) {
                        RefreshKind.PLAYLIST -> if (source.importScope.importsLiveTv) {
                            container().xtreamImportService.refreshPlaylist(source)
                        }
                        RefreshKind.EPG -> if (source.importScope.importsLiveTv) {
                            container().xtreamImportService.refreshEpg(source)
                        }
                        RefreshKind.CATALOGUE -> if (source.importScope.importsVod) {
                            container().xtreamCatalogueImportService.refresh(source)
                        }
                    }
                }
            0
        } catch (_: GuideImportException) {
            1
        } catch (_: LocalizedException) {
            // The catalogue services raise their own type; either way the
            // refresh state carries the message and the loop moves on.
            1
        }
    }

    private fun container(): StreamMateContainer =
        (applicationContext as StreamMateApplication).container

    companion object {
        const val KEY_KIND = "refresh_kind"
        const val KEY_SOURCE_ID = "refresh_source_id"
        const val KEY_IMMEDIATE = "refresh_immediate"
        const val KIND_ALL = "ALL"
        private const val CATALOGUE_RETRY_DELAY_MILLIS = 20_000L

        /** The kinds a work request names: one, or all three in import order. */
        internal fun refreshKindsFor(value: String?): List<RefreshKind>? = when (value) {
            null -> null
            KIND_ALL -> listOf(RefreshKind.PLAYLIST, RefreshKind.EPG, RefreshKind.CATALOGUE)
            else -> runCatching { listOf(RefreshKind.valueOf(value)) }.getOrNull()
        }
    }
}
