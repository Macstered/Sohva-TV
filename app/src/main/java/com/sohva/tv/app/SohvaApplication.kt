package com.sohva.tv.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.sohva.tv.core.model.FeatureFlags
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.player.PlayerEnvironment
import com.sohva.tv.core.player.PlayerHost
import com.sohva.tv.core.sync.ImportRunner
import com.sohva.tv.core.sync.RefreshHost
import com.sohva.tv.core.sync.RefreshWorkerFactory
import com.sohva.tv.core.sync.metadata.Enrichment
import com.sohva.tv.core.sync.metadata.EnrichmentHost
import com.sohva.tv.core.sync.metadata.EnrichmentScheduler
import com.sohva.tv.core.sync.metadata.EnrichmentWorkerFactory

/**
 * Process entry. Builds lazy holders only: no disk, database, preferences, WorkManager,
 * Keystore, network or image loader before the first frame (plan/03 §4.9).
 */
class SohvaApplication : Application(), Configuration.Provider, RefreshHost, PlayerHost, EnrichmentHost {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Debug builds log disk and network work on the main thread (plan/03 §4.4). The one
            // allowed read is the locale file in attachBaseContext below Android 13.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        }
        graph = AppGraph(this, FeatureFlags.resolve(BuildInfo.KIND))
    }

    // WorkManager initialises on first use, after the first frame (the manifest removes its start-up initializer).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.ERROR)
            .setWorkerFactory(
                DelegatingWorkerFactory().apply {
                    addFactory(RefreshWorkerFactory(this@SohvaApplication))
                    addFactory(EnrichmentWorkerFactory(this@SohvaApplication))
                },
            )
            .build()

    override val importRunner: ImportRunner get() = graph.sync.runner

    override fun appInForeground(): Boolean = graph.inForeground.value

    override suspend fun everyLiveSourceImportedOnce(): Boolean = graph.data.refreshFacts.liveSourcesNeverImported() == 0

    override suspend fun failuresSince(sinceMillis: Long, kinds: Set<RefreshKind>): Int =
        graph.data.refreshFacts.failuresSince(sinceMillis, kinds.map { it.id })

    override fun nowMillis(): Long = graph.clock.wallMillis()

    override fun playerEnvironment(): PlayerEnvironment = graph.player

    override val enrichment: Enrichment get() = graph.metadata.enrichment

    override val enrichmentScheduler: EnrichmentScheduler get() = graph.metadata.scheduler

    override suspend fun metadataEnabled(): Boolean = graph.flags.metadataWorker && graph.metadata.settings.current().enabled

    override suspend fun keyRefused(): Boolean = graph.metadata.settings.keyRefused()

    override suspend fun setKeyRefused() = graph.metadata.settings.setKeyRefused(true)
}
