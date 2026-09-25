package com.sohva.tv.app.live

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.live.GuideEnvironment
import com.sohva.tv.feature.live.SavedSource
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The guide's view of the app (spec 20 §6): every store is touched off the main thread. */
class AppGuideEnvironment(private val graph: AppGraph, override val locale: Locale) : GuideEnvironment {
    private val io get() = graph.dispatchers.io
    private val prefs get() = graph.data.preferences

    override val reads: LiveReads = graph.liveReads
    override val clock: Clock get() = graph.clock
    override val format: CoroutineDispatcher get() = graph.dispatchers.ui

    override val lastGuideSource: Flow<String?> = flow { emitAll(prefs.lastGuideSource) }.flowOn(io)
    override val lastChannel: Flow<String?> = flow { emitAll(prefs.lastChannel) }.flowOn(io)
    override val showChannelNumbers: Flow<Boolean> = flow { emitAll(prefs.showChannelNumbers) }.flowOn(io)
    override val timeZone: Flow<String?> = flow { emitAll(prefs.timeZone) }.flowOn(io)

    override val savedSources: Flow<List<SavedSource>> =
        flow { emitAll(graph.data.sources.observe()) }.map { list -> list.map { SavedSource(it.id, it.name, it.enabled) } }.flowOn(io)

    override val health: Flow<List<SourceHealth>> = flow { emitAll(graph.data.refreshStatus.observe()) }.flowOn(io)

    override suspend fun saveGuideSource(id: String) = withContext(io) { prefs.setLastGuideSource(id) }

    override fun syncAll() {
        graph.appScope.launch { graph.sync.scheduler.syncNow(null) }
    }

    override fun keptList(spec: ListSpec): ChannelList? = graph.keptRows.get(spec)

    override fun keepList(list: ChannelList) = graph.keptRows.keep(list)
}
