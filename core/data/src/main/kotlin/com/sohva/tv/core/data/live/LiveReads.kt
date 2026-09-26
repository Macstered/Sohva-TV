package com.sohva.tv.core.data.live

import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.org.OrgSort
import kotlinx.coroutines.flow.Flow

/**
 * What the guide and the player read ([LiveStore] in the app). An interface so device tests can
 * gate one read and prove that slow data never moves focus (AGENTS.md §8).
 */
/** A rail shortcut's rule (spec 42 ORG-FR-09): shown, and its place under manual group order. */
data class ShortcutRule(val shown: Boolean = true, val position: Long? = null)

/**
 * The Live room's rules for the guide's rail (spec 20 GUIDE-FR-21, -23): the group order and the
 * shortcuts. Read whole from the few rules of the room.
 */
data class LiveRailRules(
    val order: OrgSort = OrgSort.PROVIDER,
    val favourites: ShortcutRule = ShortcutRule(),
    val recent: ShortcutRule = ShortcutRule(),
    val lists: Map<String, ShortcutRule> = emptyMap(),
) {
    fun list(id: String): ShortcutRule = lists[id] ?: ShortcutRule()
}

interface LiveReads {
    val sources: Flow<List<LiveSource>>

    /** The rail's organisation rules now and after every change; the defaults when none are set. */
    fun railRuleChanges(): Flow<LiveRailRules> = kotlinx.coroutines.flow.flowOf(LiveRailRules())

    fun changes(): Flow<Unit>

    fun guideChanges(): Flow<Unit>

    suspend fun rail(sourceId: String): List<LiveGroup>

    suspend fun open(spec: ListSpec): ChannelList

    suspend fun page(list: ChannelList, page: Int): List<LiveChannel>

    suspend fun indexOf(list: ChannelList, channel: LiveChannel): Int

    suspend fun channel(key: String): LiveChannel?

    suspend fun favourites(sourceId: String): ListSpec.Named

    suspend fun recents(sourceId: String): ListSpec.Named

    fun favouriteKeys(): Flow<Set<String>>

    /** The household's channel lists for the rail, in their order (spec 21 CHAN-28). */
    fun customLists(): Flow<List<CustomListRef>>

    /** A list's channels of [sourceId], in the list's own order (GUIDE-FR-35). */
    suspend fun customList(listId: String, sourceId: String): ListSpec.Named

    suspend fun toggleFavourite(key: String): Boolean

    suspend fun recordWatched(key: String)

    suspend fun schedules(source: LiveSource, epgIds: Collection<String>, windowStart: Long): Map<String, List<GuideProgramme>>

    suspend fun description(programmeId: Long): String?

    suspend fun search(spec: ListSpec, source: LiveSource, query: String, windowStart: Long): ListSpec.Named

    suspend fun dial(list: ChannelList, number: Int): Int
}

/** A channel list as the guide's rail names it. */
data class CustomListRef(val id: String, val name: String)
