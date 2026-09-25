package com.sohva.tv.core.data.channels

import com.sohva.tv.core.data.database.ChannelCustomEntity
import com.sohva.tv.core.data.database.EpgChannelOption
import com.sohva.tv.core.data.database.ManagedChannel
import com.sohva.tv.core.data.database.ManagerSource
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.text.SortNames
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The list's filters as the reads take them (spec 21 CHAN-FR-13). */
data class ManagerQuery(val showHidden: Boolean, val groupName: String?, val search: String, val byName: Boolean)

/**
 * Channel management's reads on the IO dispatcher (spec 21 CHAN-NFR-01/02): pages within one
 * source, a count, the chips' group names, and the guide mapping picker's pages. The search is a
 * `LIKE` pattern with escapes on the shown name's sort form and the group title.
 */
class ChannelManagerReads(private val db: SohvaDatabase, private val io: CoroutineDispatcher) {
    private val dao get() = db.manager()

    suspend fun sources(): List<ManagerSource> = withContext(io) { dao.sources() }

    suspend fun groupNames(sourceId: String): List<String> = withContext(io) { dao.groupNames(sourceId) }

    suspend fun page(sourceId: String, query: ManagerQuery, after: ManagedChannel?, limit: Int): List<ManagedChannel> = withContext(io) {
        val pattern = pattern(query.search)
        if (query.byName) {
            dao.namePage(sourceId, query.showHidden, query.groupName, pattern, after?.sortName ?: "", after?.id ?: Long.MIN_VALUE, limit)
        } else {
            dao.playlistPage(sourceId, query.showHidden, query.groupName, pattern, after?.rank ?: Long.MIN_VALUE, after?.id ?: Long.MIN_VALUE, limit)
        }
    }

    suspend fun count(sourceId: String, query: ManagerQuery): Int =
        withContext(io) { dao.count(sourceId, query.showHidden, query.groupName, pattern(query.search)) }

    /** The row next to (rank, id) in Playlist order under the same filters: a move's neighbour on screen. */
    suspend fun neighbour(sourceId: String, query: ManagerQuery, rank: Long, id: Long, up: Boolean): ManagedChannel? = withContext(io) {
        val pattern = pattern(query.search)
        if (up) {
            dao.playlistBefore(sourceId, query.showHidden, query.groupName, pattern, rank, id)
        } else {
            dao.playlistPage(sourceId, query.showHidden, query.groupName, pattern, rank, id, 1).firstOrNull()
        }
    }

    suspend fun custom(key: String): ChannelCustomEntity? = withContext(io) { dao.custom(key) }

    suspend fun epgName(sourceId: String, epgId: String): String? = withContext(io) { dao.epgName(sourceId, epgId) }

    suspend fun epgOptions(sourceId: String, search: String, after: EpgChannelOption?, limit: Int): List<EpgChannelOption> = withContext(io) {
        val like = search.trim().takeIf { it.isNotEmpty() }?.let { "%" + escape(it) + "%" }
        dao.epgOptions(sourceId, like, after?.displayName.orEmpty(), after?.epgId ?: "", limit)
    }

    private fun pattern(search: String): String? = search.trim().takeIf { it.isNotEmpty() }?.let { "%" + escape(SortNames.of(it)) + "%" }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            if (c == '%' || c == '_' || c == '\\') append('\\')
            append(c)
        }
    }
}
