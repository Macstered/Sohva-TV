package com.sohva.tv.app

import android.app.Application
import android.util.Log
import com.sohva.tv.app.settings.PhoneSetup
import com.sohva.tv.core.data.DataGraph
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.data.diagnostics.RingDiagnosticsLog
import com.sohva.tv.core.model.FeatureFlags
import com.sohva.tv.core.model.concurrent.AppDispatchers
import com.sohva.tv.core.model.concurrent.PauseGate
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.time.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The app's object graph: lazy holders only (plan/03 §4.4). Creating it opens nothing; the first
 * touch of [data] happens off the main thread in the start-up sequence (plan/03 §4.9).
 */
class AppGraph(val app: Application, val flags: FeatureFlags) {
    val clock: Clock = SystemClock
    val dispatchers: AppDispatchers by lazy { AndroidDispatchers() }

    val diagnostics: DiagnosticsLog by lazy {
        RingDiagnosticsLog(clock) { level, line ->
            if (level == 'E') Log.e(LOG_TAG, line) else Log.i(LOG_TAG, line)
        }
    }

    val data: DataGraph by lazy { DataGraph(app, dispatchers) }

    /** Work that outlives a screen: imports started from Settings keep going (spec 10 SRC-FR-94). */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    /** True while an activity is started; set by [MainActivity]. */
    val inForeground: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** True while video plays; the player (M2) sets it. */
    val playbackActive: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val pauseGate: PauseGate by lazy { PauseGate(playbackActive, inForeground) }

    val sync: SyncGraph by lazy { SyncGraph(this) }

    /** The playback engine's and player screen's view of the graph; built on first playback. */
    val player: com.sohva.tv.app.player.PlayerGraph by lazy { com.sohva.tv.app.player.PlayerGraph(this) }

    /** The phone setup page (spec 11); built on first use. */
    val phone: PhoneSetup by lazy { PhoneSetup(this) }

    /**
     * The channel last played from the guide in this process: the guide opens on it (spec 20 §3.1,
     * session memory, never stored).
     */
    @Volatile
    var guideFocusChannel: String? = null

    /** Options that open screens of later milestones say so briefly (the shell's placeholder toast). */
    fun notYetAvailable() {
        android.widget.Toast.makeText(app, app.getString(com.sohva.tv.ui.design.R.string.home_coming_soon), android.widget.Toast.LENGTH_SHORT).show()
    }

    private val started = AtomicBoolean(false)

    /**
     * What waits for the first frame (plan/03 §4.9): start-up repair of interrupted imports and the
     * refresh schedule, which follows the interval setting from then on. Runs once per process.
     */
    fun afterFirstFrame() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            // Beta 23's sources first, so an upgraded install syncs them at once (decision A1).
            val imported = data.beta23Import.run()
            if (imported is Beta23SourceImport.Result.Imported) imported.sourceIds.forEach(sync.scheduler::syncNow)
            sync.runner.recoverAfterRestart()
        }
        appScope.launch { data.preferences.refreshInterval.collect { sync.scheduler.schedule(it) } }
    }

    private companion object {
        const val LOG_TAG = "SohvaTV"
    }
}
