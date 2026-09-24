package com.sohva.tv.app

import android.app.Application
import android.util.Log
import com.sohva.tv.core.data.DataGraph
import com.sohva.tv.core.data.diagnostics.RingDiagnosticsLog
import com.sohva.tv.core.model.FeatureFlags
import com.sohva.tv.core.model.concurrent.AppDispatchers
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.time.SystemClock

/**
 * The app's object graph: lazy holders only (plan/03 §4.4). Creating it opens nothing; the first
 * touch of [data] happens off the main thread in the start-up sequence (plan/03 §4.9).
 */
class AppGraph(private val app: Application, val flags: FeatureFlags) {
    val clock: Clock = SystemClock
    val dispatchers: AppDispatchers by lazy { AndroidDispatchers() }

    val diagnostics: DiagnosticsLog by lazy {
        RingDiagnosticsLog(clock) { level, line ->
            if (level == 'E') Log.e(LOG_TAG, line) else Log.i(LOG_TAG, line)
        }
    }

    val data: DataGraph by lazy { DataGraph(app, dispatchers) }

    private companion object {
        const val LOG_TAG = "SohvaTV"
    }
}
