package com.sohva.tv.app

import android.content.Context
import android.os.Build
import androidx.work.WorkManager
import com.sohva.tv.core.net.http.ProviderHttp
import com.sohva.tv.core.sync.EpisodeFetch
import com.sohva.tv.core.sync.FallbackNames
import com.sohva.tv.core.sync.ImportEnvironment
import com.sohva.tv.core.sync.ImportRunner
import com.sohva.tv.core.sync.RefreshScheduler
import com.sohva.tv.core.sync.SourceChecks
import com.sohva.tv.core.sync.StreamSealer
import com.sohva.tv.ui.design.R

/**
 * Imports and their scheduling (spec 10 §4.18–4.19). Lazy like the rest of the graph: nothing here
 * is built before the first frame, and the provider client is created on the first import.
 */
class SyncGraph(private val graph: AppGraph) {
    val http: ProviderHttp by lazy {
        val agent = ProviderHttp.userAgent(BuildConfig.VERSION_NAME, Build.VERSION.RELEASE)
        ProviderHttp(ProviderHttp.client(agent), graph.diagnostics)
    }

    private val environment: ImportEnvironment by lazy {
        val data = graph.data
        ImportEnvironment(
            db = data.database,
            http = http,
            sealer = StreamSealer { data.cipher.encrypt(it) },
            clock = graph.clock,
            dispatchers = graph.dispatchers,
            pauseGate = graph.pauseGate,
            log = graph.diagnostics,
            names = ResourceFallbackNames(graph.app),
            preferredCopy = { data.preferences.preferredCopy() },
        )
    }

    val runner: ImportRunner by lazy { ImportRunner(environment, graph.data.sources, graph.appScope) }

    /** A series' episodes from the provider (spec 40 VOD-FR-75). */
    val episodes: EpisodeFetch by lazy { EpisodeFetch(environment, graph.data.sources) }

    val checks: SourceChecks by lazy { SourceChecks(http, graph.dispatchers.io) }

    val scheduler: RefreshScheduler by lazy { RefreshScheduler(WorkManager.getInstance(graph.app)) }
}

/**
 * The fallback names in the interface language. Below Android 13 the application context does not
 * carry the chosen language, so it is applied here (the texts are beta 23's `guide_channel_number`
 * and `addon_ui_episode`).
 */
private class ResourceFallbackNames(app: Context) : FallbackNames {
    private val context: Context by lazy { AppLocales.wrap(app) }

    override fun channel(number: Int): String = context.getString(R.string.guide_channel_number, number)

    override fun episode(number: Int): String = context.getString(R.string.addon_ui_episode, number)
}
