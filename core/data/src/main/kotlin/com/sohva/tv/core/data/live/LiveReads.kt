package com.sohva.tv.core.data.live

import com.sohva.tv.core.data.database.DEFAULT_PROFILE
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.model.guide.GuideProgramme
import kotlinx.coroutines.flow.Flow

/**
 * What the guide and the player read ([LiveStore] in the app). An interface so device tests can
 * gate one read and prove that slow data never moves focus (AGENTS.md §8).
 */
interface LiveReads {
    val sources: Flow<List<LiveSource>>

    fun changes(): Flow<Unit>

    fun guideChanges(): Flow<Unit>

    suspend fun rail(sourceId: String): List<LiveGroup>

    suspend fun open(spec: ListSpec): ChannelList

    suspend fun page(list: ChannelList, page: Int): List<LiveChannel>

    suspend fun indexOf(list: ChannelList, channel: LiveChannel): Int

    suspend fun channel(key: String): LiveChannel?

    suspend fun favourites(sourceId: String, profileId: String = DEFAULT_PROFILE): ListSpec.Named

    suspend fun recents(sourceId: String, profileId: String = DEFAULT_PROFILE): ListSpec.Named

    fun favouriteKeys(profileId: String = DEFAULT_PROFILE): Flow<Set<String>>

    suspend fun toggleFavourite(key: String, profileId: String = DEFAULT_PROFILE): Boolean

    suspend fun recordWatched(key: String, profileId: String = DEFAULT_PROFILE)

    suspend fun schedules(source: LiveSource, epgIds: Collection<String>, windowStart: Long): Map<String, List<GuideProgramme>>

    suspend fun description(programmeId: Long): String?

    suspend fun search(spec: ListSpec, source: LiveSource, query: String, windowStart: Long): ListSpec.Named

    suspend fun dial(list: ChannelList, number: Int): Int
}
