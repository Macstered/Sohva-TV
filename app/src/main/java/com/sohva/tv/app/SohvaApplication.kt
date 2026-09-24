package com.sohva.tv.app

import android.app.Application
import com.sohva.tv.core.model.FeatureFlags

/**
 * Process entry. Builds lazy holders only: no disk, database, preferences, WorkManager,
 * Keystore, network or image loader before the first frame (plan/03 §4.9).
 */
class SohvaApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this, FeatureFlags.resolve(BuildInfo.KIND, BuildInfo.TRAKT_CONFIGURED))
    }
}
