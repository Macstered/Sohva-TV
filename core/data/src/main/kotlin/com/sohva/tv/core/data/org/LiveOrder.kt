package com.sohva.tv.core.data.org

import com.sohva.tv.core.data.database.OrgChannelRow
import com.sohva.tv.core.data.database.OrgGroupRow
import com.sohva.tv.core.data.database.RankedGroupChannel
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.live.ChannelEffects
import com.sohva.tv.core.model.org.OrgItem
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgResolver
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.text.SortNames

/**
 * One source's channel order under the Live rules (spec 20 GUIDE-FR-32, spec 42 ORG-FR-19…22),
 * stored in `display_rank` so the guide, the player's list and channel up/down page it from an
 * index (GUIDE-13). Groups become blocks in the Live group order; channels without a group come
 * last. Inside a group: with no sort rule, the viewer's position from Channel management, else
 * the playlist order (beta 23's legacy MANUAL); an explicit Provider rule, the playlist order;
 * A–Z, Z–A and Manual, places 1,024 apart from the sorted group.
 */
internal class LiveOrder(groups: List<OrgGroupRow>, private val resolver: OrgResolver) {
    private val blocks: Map<Long, Long>
    private val ungrouped: Long = ChannelEffects.UNGROUPED_BLOCK
    private val sorts: Map<Long, OrgSort?>
    private val byId: Map<Long, OrgGroupRow> = groups.associateBy { it.id }

    init {
        val ordered = when (resolver.groupOrder(OrgRoom.LIVE)) {
            OrgSort.TITLE_ASC -> groups.sortedWith(compareBy<OrgGroupRow>({ SortNames.of(it.name) }, { it.providerOrder }, { it.id }))
            OrgSort.TITLE_DESC -> groups.sortedWith(compareByDescending<OrgGroupRow> { SortNames.of(it.name) }.thenBy { it.providerOrder }.thenBy { it.id })
            OrgSort.MANUAL -> groups.sortedWith(compareBy<OrgGroupRow>({ it.position }, { it.providerOrder }, { it.id }))
            else -> null
        }
        // Provider order: the block the import already wrote (ChannelEffects.providerBlock).
        blocks = ordered?.withIndex()?.associate { (i, g) -> g.id to i + 1L }
            ?: groups.associate { g -> g.id to ChannelEffects.providerBlock(g.providerOrder) }
        sorts = groups.associate { g -> g.id to resolver.ruleSort(OrgRoom.LIVE, g.sourceId, g.groupKey, OrgKeys.nameKey(g.name)) }
    }

    /** Groups whose channels take places from a sort of the whole group rather than one row at a time. */
    fun isSorted(groupId: Long?): Boolean = groupId != null && sorts[groupId].let { it == OrgSort.TITLE_ASC || it == OrgSort.TITLE_DESC || it == OrgSort.MANUAL }

    /** The rank of a channel of an unsorted group or without a group. */
    fun rank(row: OrgChannelRow): Long {
        val block = row.groupId?.let(blocks::get) ?: ungrouped
        val place = if (row.groupId != null && sorts[row.groupId] == OrgSort.PROVIDER) {
            ChannelEffects.playlistRank(row.playlistOrder)
        } else {
            row.position ?: ChannelEffects.playlistRank(row.playlistOrder)
        }
        return ChannelEffects.rank(block, place)
    }

    /** New ranks of a sorted group's channels, in its order (ORG-FR-22; ties by playlist order, title, key). */
    fun ranks(groupId: Long, rows: List<RankedGroupChannel>): List<Pair<RankedGroupChannel, Long>> {
        val group = byId[groupId] ?: return emptyList()
        val block = blocks[groupId] ?: ungrouped
        val sorted = when (sorts[groupId]) {
            OrgSort.TITLE_ASC -> rows.sortedWith(compareBy<RankedGroupChannel>({ it.sortName }, { it.key }))
            OrgSort.TITLE_DESC -> rows.sortedWith(compareByDescending<RankedGroupChannel> { it.sortName }.thenBy { it.key })
            else -> {
                val nameKey = OrgKeys.nameKey(group.name)
                // Manual: the manager's place, else the viewer's position from Channel management, else last.
                val place = rows.associate { r ->
                    val item = OrgItem(OrgRoom.LIVE, group.sourceId, group.groupKey, nameKey, r.key)
                    r.id to (resolver.memberRule(item).position ?: r.position ?: Long.MAX_VALUE)
                }
                rows.sortedWith(compareBy<RankedGroupChannel>({ place.getValue(it.id) }, { it.playlistOrder }, { it.sortName }, { it.key }))
            }
        }
        return sorted.mapIndexed { i, r -> r to ChannelEffects.rank(block, (i + 1L) * ChannelEffects.RANK_STEP) }
    }
}

/** Writes a source's channel ranks: rows as they are walked, sorted groups afterwards, one group at a time. */
internal class LiveRanks(private val db: SohvaDatabase, private val order: LiveOrder) {
    private val dao get() = db.organization()
    private val sortedGroups = LinkedHashSet<Long>()

    /** Inside the caller's transaction: ranks [row] now, or notes its sorted group for [finish]. */
    fun place(row: OrgChannelRow): Boolean {
        if (order.isSorted(row.groupId)) {
            sortedGroups += row.groupId!!
            return false
        }
        val rank = order.rank(row)
        if (rank == row.rank) return false
        dao.setRank(row.id, rank)
        return true
    }

    /**
     * Each noted sorted group, read whole through its index: a sort needs every member, and a
     * group is a small part of a source (spec 42 §9.1 keeps the catalogue out of memory, not one group).
     */
    fun finish(): Boolean {
        var changed = false
        for (groupId in sortedGroups) {
            val ranks = order.ranks(groupId, dao.groupChannels(groupId))
            ranks.chunked(PAGE).forEach { page ->
                db.runInTransaction {
                    for ((row, rank) in page) {
                        if (rank != row.rank) {
                            dao.setRank(row.id, rank)
                            changed = true
                        }
                    }
                }
            }
        }
        sortedGroups.clear()
        return changed
    }

    private companion object {
        const val PAGE = 2_000
    }
}
