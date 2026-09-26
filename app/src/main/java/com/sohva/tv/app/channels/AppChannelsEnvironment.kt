package com.sohva.tv.app.channels

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.channels.ChannelFields
import com.sohva.tv.core.data.channels.ManagerQuery
import com.sohva.tv.core.data.database.ChannelCustomEntity
import com.sohva.tv.core.data.database.ChannelListEntity
import com.sohva.tv.core.data.database.EpgChannelOption
import com.sohva.tv.core.data.database.ManagedChannel
import com.sohva.tv.core.data.database.ManagerSource
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.net.phone.LocalAddress
import com.sohva.tv.core.net.phone.PhoneMode
import com.sohva.tv.core.net.phone.QrCodes
import com.sohva.tv.feature.channels.ChannelFilter
import com.sohva.tv.feature.channels.ChannelSort
import com.sohva.tv.feature.channels.ChannelsEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Channel management's side of the graph (plan/03 §4.6): the manager reads and the edit and list stores. */
class AppChannelsEnvironment(private val graph: AppGraph) : ChannelsEnvironment {
    private val io get() = graph.dispatchers.io
    private val reads get() = graph.data.channelManager

    override val format: CoroutineDispatcher get() = graph.dispatchers.ui

    override val pinConfigured: kotlinx.coroutines.flow.StateFlow<Boolean> get() = graph.data.profiles.pinConfigured

    override suspend fun isLocked(key: String): Boolean = graph.data.profiles.isLocked(key)

    override suspend fun setLocked(key: String, locked: Boolean) = graph.data.profiles.setLocked(key, locked)

    override suspend fun sources(): List<ManagerSource> = reads.sources()

    override suspend fun groupNames(sourceId: String): List<String> = reads.groupNames(sourceId)

    override suspend fun page(sourceId: String, filter: ChannelFilter, after: ManagedChannel?, limit: Int): List<ManagedChannel> =
        reads.page(sourceId, filter.query(), after, limit)

    override suspend fun count(sourceId: String, filter: ChannelFilter): Int = reads.count(sourceId, filter.query())

    override suspend fun custom(key: String): ChannelCustomEntity? = reads.custom(key)

    override suspend fun epgName(sourceId: String, epgId: String): String? = reads.epgName(sourceId, epgId)

    override suspend fun epgOptions(sourceId: String, search: String, after: EpgChannelOption?, limit: Int): List<EpgChannelOption> =
        reads.epgOptions(sourceId, search, after, limit)

    override suspend fun save(key: String, fields: ChannelFields) = graph.data.channelEdits.save(key, fields)

    override suspend fun setHidden(key: String, hidden: Boolean) = graph.data.channelEdits.setHidden(key, hidden)

    /** Reset also deletes a phone-sent logo file of the channel (spec 21 CHAN-FR-31, rebuild rule). */
    override suspend fun reset(key: String) {
        graph.data.channelEdits.reset(key)
        withContext(io) { graph.phone.logos.delete(key) }
    }

    /** The neighbour on screen is the next row under the screen's own filters (CHAN-FR-29). */
    override suspend fun move(key: String, up: Boolean, filter: ChannelFilter): Boolean {
        val sourceId = graph.data.live.channel(key)?.sourceId ?: return false
        return graph.data.channelEdits.move(key, up) { rank, id ->
            reads.neighbour(sourceId, filter.query(), rank, id, up)?.let { it.id to it.key }
        }
    }

    override val lists: Flow<List<ChannelListEntity>> get() = flow { emitAll(graph.data.channelLists.lists) }.flowOn(io)

    override fun listsOf(key: String): Flow<List<String>> = flow { emitAll(graph.data.channelLists.listsOf(key)) }.flowOn(io)

    override suspend fun createList(name: String): String? = graph.data.channelLists.create(name)

    override suspend fun deleteList(id: String) = graph.data.channelLists.delete(id)

    override suspend fun addToList(listId: String, key: String) = graph.data.channelLists.add(listId, key)

    override suspend fun removeFromList(listId: String, key: String) = graph.data.channelLists.remove(listId, key)

    override val showHidden: Flow<Boolean> get() = flow { emitAll(graph.data.preferences.editorsShowHidden) }.flowOn(io)

    override suspend fun setShowHidden(value: Boolean) = withContext(io) { graph.data.preferences.setEditorsShowHidden(value) }

    override val phone: Flow<PhoneSetupState> get() = graph.phone.state()

    // Opening a socket and reading the interfaces are io work; the server thread does the rest.
    override fun openLogoPhone(key: String, name: String) {
        graph.appScope.launch { graph.phone.server.start(LocalAddress.current(), PhoneMode.Logo(key, name)) }
    }

    override fun closePhone() {
        graph.appScope.launch { graph.phone.server.stop() }
    }

    override suspend fun qrCode(url: String): QrMatrix? = withContext(io) { QrCodes.of(url) }

    private fun ChannelFilter.query() = ManagerQuery(showHidden, groupName, search, byName = sort == ChannelSort.NAME)
}
