package com.streammate.tv.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.streammate.tv.iptv.repository.GuideImportException
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.iptv.xtream.derivedXtreamSourceOrNull
import java.util.concurrent.TimeUnit

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

    internal fun shouldDeferAutomaticRefresh(isAppForeground: Boolean): Boolean = isAppForeground

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
        // A Shield trace caught the periodic playlist refresh encrypting
        // thousands of URLs while the viewer switched VOD groups. Automatic
        // maintenance is deferrable; explicit refresh actions bypass this
        // worker and remain immediate.
        if (GuideRefreshScheduler.shouldDeferAutomaticRefresh(StreamMateForegroundState.isForeground)) {
            return Result.retry()
        }
        val kind = inputData.getString(KEY_KIND)?.let { value ->
            runCatching { RefreshKind.valueOf(value) }.getOrNull()
        } ?: return Result.failure()
        val sources = container().secretSettingsStore.loadSources()
            .asSequence()
            .filter { it.enabled }
            .sortedByDescending { it.priority }
            .toList()
        var failedSources = 0
        sources.forEach { source ->
            try {
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
            } catch (_: GuideImportException) {
                failedSources += 1
            }
        }
        if (kind == RefreshKind.CATALOGUE) {
            CatalogueMetadataScheduler.restart(applicationContext)
        }
        return if (failedSources == 0) Result.success() else Result.retry()
    }

    private fun container(): StreamMateContainer =
        (applicationContext as StreamMateApplication).container

    companion object {
        const val KEY_KIND = "refresh_kind"
    }
}
