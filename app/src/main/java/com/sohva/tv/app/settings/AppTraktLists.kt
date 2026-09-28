package com.sohva.tv.app.settings

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.feature.settings.ListChoice
import com.sohva.tv.feature.settings.ListFinding
import com.sohva.tv.feature.trakt.protocol.TraktException
import com.sohva.tv.feature.trakt.protocol.TraktFailure
import com.sohva.tv.feature.trakt.protocol.TraktListInfo
import com.sohva.tv.feature.trakt.protocol.TraktListRef
import com.sohva.tv.feature.trakt.shelf.TraktRowSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Public Trakt lists for Home (spec 02 HOME-FR-99, -100): found by address, number or name, and
 * added to the active profile's layout with their name kept for the row. Off the main thread; every
 * request is public (no sign-in) and goes through Trakt's pacing gate.
 */
internal object AppTraktLists {
    /** Whether this profile can add lists: a build with Trakt, configured, and a profile allowed it. */
    suspend fun available(graph: AppGraph, profile: String): Boolean {
        val host = graph.trakt ?: return false
        return graph.flags.trakt && host.configured && host.access.allowed(profile)
    }

    suspend fun find(graph: AppGraph, text: String): ListFinding = withContext(graph.dispatchers.io) {
        val host = graph.trakt ?: return@withContext ListFinding.Failed
        val ref = TraktListRef.parse(text) ?: return@withContext ListFinding.Nothing
        try {
            when (ref) {
                is TraktListRef.Search -> host.api.searchLists(ref.query).takeIf { it.isNotEmpty() }?.let { ListFinding.Found(it.map(::choice)) } ?: ListFinding.Nothing
                else -> host.api.listSummary(ref)?.let { ListFinding.Found(listOf(choice(it))) } ?: ListFinding.Nothing
            }
        } catch (e: TraktException) {
            // A list only its owner may read answers 401 without a sign-in.
            if (e.failure == TraktFailure.REAUTHORIZE) ListFinding.Private else ListFinding.Failed
        }
    }

    /** Adds [list] at the end of the active profile's layout, shown; false when it cannot (8 added rows, no Trakt). */
    suspend fun add(graph: AppGraph, list: ListChoice): Boolean = withContext(graph.dispatchers.io) {
        val host = graph.trakt ?: return@withContext false
        val profile = graph.data.profiles.activeId
        if (!available(graph, profile)) return@withContext false
        graph.traktListGate?.invoke()
        val layout = graph.data.preferences.homeLayout(profile).first()
        val source = if (list.smart) TraktRowSource.smart(list.id) else TraktRowSource.list(list.id)
        val next = layout.withAdded(source.id)
        if (next == layout) return@withContext layout.added.contains(source.id)
        host.rowLists.saveTitle(profile, source, list.name)
        graph.data.preferences.setHomeLayout(profile, next)
        // Fetched in a few seconds rather than at the next 15-minute cycle.
        host.requestSync(profile)
        true
    }

    /** The phone page's address or number (HOME-FR-100): the list's name when added, else null. */
    suspend fun addFromPhone(graph: AppGraph, text: String): String? {
        val ref = TraktListRef.parseAddress(text) ?: return null
        val host = graph.trakt ?: return null
        val info = withContext(graph.dispatchers.io) { runCatching { host.api.listSummary(ref) }.getOrNull() } ?: return null
        return if (add(graph, choice(info))) info.name else null
    }

    private fun choice(info: TraktListInfo) = ListChoice(info.id, info.name, info.owner, info.items, info.likes, info.smart)
}
