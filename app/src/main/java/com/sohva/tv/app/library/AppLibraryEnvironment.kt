package com.sohva.tv.app.library

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.vod.RailGroup
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallItem
import com.sohva.tv.core.data.vod.WallRoom
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.RefreshState
import com.sohva.tv.feature.library.LibraryEnvironment
import com.sohva.tv.feature.library.RefreshNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Where a wall goes: details pages, the library manager, back (spec 40 §3). */
interface LibraryNavigation {
    fun open(room: WallRoom, item: WallItem)

    fun openManager(room: WallRoom)

    fun leave()
}

/** A wall's side of the graph (plan/03 §4.6): the wall reads and the catalogue import. */
class AppLibraryEnvironment(
    private val graph: AppGraph,
    override val room: WallRoom,
    private val navigation: LibraryNavigation,
) : LibraryEnvironment {
    private val reads get() = graph.data.walls

    override fun changes(): Flow<Unit> = reads.changes()

    override suspend fun groups(): List<RailGroup> = reads.groups(room)

    override suspend fun page(destination: WallDestination, search: String, from: WallItem?, forward: Boolean, limit: Int): List<WallItem> =
        reads.page(room, destination, search, from, forward, limit)

    /**
     * Imports every enabled source whose scope includes films and series, one after another
     * (VOD-FR-46), and reports what the library holds afterwards.
     */
    override suspend fun refresh(): RefreshNote = withContext(graph.dispatchers.io) {
        val sources = graph.data.sources.all().filter { it.enabled && it.importScope != ImportScope.LIVE_TV }
        if (sources.isEmpty()) return@withContext RefreshNote.NoSource
        var films = 0
        var series = 0
        for (source in sources) {
            graph.sync.runner.sync(source.id, setOf(RefreshKind.CATALOGUE), WorkOrigin.VIEWER).join()
            val health = graph.data.refreshStatus.get(source.id, RefreshKind.CATALOGUE)
            val failure = health?.error?.takeIf { health.state == RefreshState.FAILED }
            if (failure != null) return@withContext RefreshNote.Failed(failure)
            val (f, s) = graph.data.refreshStatus.catalogueCounts(source.id)
            films += f
            series += s
        }
        RefreshNote.Imported(films, series)
    }

    override fun open(item: WallItem) = navigation.open(room, item)

    override fun openManager() = navigation.openManager(room)

    override fun leave() = navigation.leave()
}
