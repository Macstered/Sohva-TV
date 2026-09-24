package com.sohva.tv.app.settings

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.core.model.source.XtreamAccount
import com.sohva.tv.core.net.phone.LocalAddress
import com.sohva.tv.core.net.phone.QrCodes
import com.sohva.tv.core.sync.SourceChecks
import com.sohva.tv.feature.settings.SettingsServices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings' services from the app graph. Every call reaches the graph on the io dispatcher: the
 * lazy stores open files (secret store, database, preferences) on first touch, which must never
 * happen on the main thread (AGENTS.md §4 rule 1).
 */
class AppSettingsServices(private val graph: AppGraph) : SettingsServices {
    private val io get() = graph.dispatchers.io

    override fun sources(): Flow<List<Source>> = flow { emitAll(graph.data.sources.observe()) }.flowOn(io)

    override fun health(): Flow<List<SourceHealth>> = flow { emitAll(graph.data.refreshStatus.observe()) }.flowOn(io)

    override fun refreshInterval(): Flow<RefreshInterval> = flow { emitAll(graph.data.preferences.refreshInterval) }.flowOn(io)

    override suspend fun load(sourceId: String): Outcome<SourceConfig?> = withContext(io) { graph.data.sources.load(sourceId) }

    override suspend fun save(config: SourceConfig): Outcome<SourceConfig> = withContext(io) { graph.data.sources.save(config) }

    override suspend fun remove(sourceId: String): Outcome<Unit> = withContext(io) { graph.sync.runner.remove(sourceId) }

    override fun syncNow(sourceId: String) {
        // WorkManager starts on first use and reads its database then.
        graph.appScope.launch { graph.sync.scheduler.syncNow(sourceId) }
    }

    override suspend fun refresh(sourceId: String, kind: RefreshKind): SourceHealth? = withContext(io) {
        graph.sync.runner.sync(sourceId, setOf(kind), WorkOrigin.VIEWER).join()
        graph.data.refreshStatus.get(sourceId, kind)
    }

    override suspend fun catalogueCounts(sourceId: String): Pair<Int, Int> = withContext(io) { graph.data.refreshStatus.catalogueCounts(sourceId) }

    override suspend fun testPlaylist(address: String): SourceChecks.Result = withContext(io) { graph.sync.checks.testPlaylist(address) }

    override suspend fun testXtream(account: XtreamAccount): SourceChecks.Result = withContext(io) { graph.sync.checks.testXtream(account) }

    override suspend fun setRefreshInterval(interval: RefreshInterval) = withContext(io) { graph.data.preferences.setRefreshInterval(interval) }

    override fun phoneSetup(): Flow<PhoneSetupState> = graph.phone.state()

    // Opening a socket and reading the interfaces are io work; the server thread does the rest.
    override fun openPhoneSetup() {
        graph.appScope.launch { graph.phone.server.start(LocalAddress.current()) }
    }

    override fun closePhoneSetup() {
        graph.appScope.launch { graph.phone.server.stop() }
    }

    override suspend fun qrCode(url: String): QrMatrix? = withContext(io) { QrCodes.of(url) }
}
