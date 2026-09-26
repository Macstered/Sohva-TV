package com.sohva.tv.app.settings

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.core.model.source.XtreamAccount
import com.sohva.tv.core.net.phone.LocalAddress
import com.sohva.tv.core.net.phone.QrCodes
import com.sohva.tv.core.sync.SourceChecks
import com.sohva.tv.feature.settings.LibrarySettingsServices
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
class AppSettingsServices(
    private val graph: AppGraph,
    openManager: () -> Unit = {},
    switchProfile: (String) -> Unit = {},
    applyLanguage: (String?) -> Unit = {},
    afterRestore: () -> Unit = {},
    activity: android.content.Context? = null,
    openLegal: () -> Unit = {},
) : SettingsServices {
    private val io get() = graph.dispatchers.io

    override val library: LibrarySettingsServices = AppLibrarySettings(graph, openManager)

    override val profiles: com.sohva.tv.feature.settings.ProfileSettingsServices = AppProfileSettings(graph, switchProfile)

    override val general: com.sohva.tv.feature.settings.GeneralSettingsServices = AppGeneralSettings(graph, applyLanguage)

    override val playback: com.sohva.tv.feature.settings.PlaybackSettingsServices = AppPlaybackSettings(graph)

    override val backup: com.sohva.tv.feature.settings.BackupSettingsServices = AppBackupSettings(graph, afterRestore)

    override val about: com.sohva.tv.feature.settings.AboutSettingsServices = AppAboutSettings(graph, activity, openLegal)

    override val sport: com.sohva.tv.feature.settings.SportSettingsServices = AppSportSettings(graph)

    override fun sources(): Flow<List<Source>> = flow { emitAll(graph.data.sources.observe()) }.flowOn(io)

    override suspend fun importProblem(): AppError? = withContext(io) { graph.data.beta23Import.problem() }

    override fun health(): Flow<List<SourceHealth>> = flow { emitAll(graph.data.refreshStatus.observe()) }.flowOn(io)

    override fun refreshInterval(): Flow<RefreshInterval> = flow { emitAll(graph.data.preferences.refreshInterval) }.flowOn(io)

    override suspend fun load(sourceId: String): Outcome<SourceConfig?> = withContext(io) { graph.data.sources.load(sourceId) }

    override suspend fun save(config: SourceConfig): Outcome<SourceConfig> = withContext(io) { graph.data.sources.save(config) }

    /** The source's phone-sent logo files go with it (spec 21 §6 rebuild rule; beta 23 left them behind). */
    override suspend fun remove(sourceId: String): Outcome<Unit> = withContext(io) {
        val logos = graph.data.channelManager.phoneLogoKeys(sourceId)
        val result = graph.sync.runner.remove(sourceId)
        if (result is Outcome.Ok) logos.forEach(graph.phone.logos::delete)
        result
    }

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

    override fun remindersCanOpen(): Boolean? = if (graph.flags.reminders) graph.reminders.mayOpenOverOtherApps() else null

    override fun openOverlaySettings(): Boolean = graph.reminders.openOverlaySettings()

    override fun remoteMapping(): Flow<RemoteMapping> = flow { emitAll(graph.data.preferences.remoteMapping) }.flowOn(io)

    override suspend fun setRemoteAction(button: RemoteButton, gesture: Gesture, action: RemoteAction) =
        withContext(io) { graph.data.preferences.setRemoteAction(button, gesture, action) }

    override suspend fun resetRemoteMapping() = withContext(io) { graph.data.preferences.resetRemoteMapping() }

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
