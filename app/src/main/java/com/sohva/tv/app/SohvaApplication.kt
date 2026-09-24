package com.sohva.tv.app

import android.app.Application
import android.os.StrictMode
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
        if (BuildConfig.DEBUG) {
            // Debug builds log disk and network work on the main thread (plan/03 §4.4). The one
            // allowed read is the locale file in attachBaseContext below Android 13.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        }
        graph = AppGraph(this, FeatureFlags.resolve(BuildInfo.KIND, BuildInfo.TRAKT_CONFIGURED))
    }
}
