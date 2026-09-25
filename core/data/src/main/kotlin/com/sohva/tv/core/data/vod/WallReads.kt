package com.sohva.tv.core.data.vod

import com.sohva.tv.core.data.database.HistoryRow
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.WallRow
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.vod.Genre
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Movies or Series (spec 40 VOD-FR-01). */
enum class WallRoom(val groupRoom: String) { MOVIES("MOVIES"), SERIES("SERIES") }

/** What a wall shows (spec 40 VOD-FR-02): a slice the database answers directly. */
sealed interface WallDestination {
    data object History : WallDestination

    /** A provider group, merged by name across enabled sources: [groupIds] holds one id per source. */
    data class Group(val name: String, val groupIds: List<Long>) : WallDestination

    /** Every title: the wall when every provider group is hidden (VOD-08). */
    data object AllGroups : WallDestination

    data class OfGenre(val genre: Genre) : WallDestination

    data object Unsorted : WallDestination
}

/** A rail row for a provider group: its label, its sources' group ids and the title count. */
data class RailGroup(val name: String, val groupIds: List<Long>, val count: Int)

/**
 * One wall entry with the position the pager keys on: `(sortName, id)` for A–Z walls,
 * `(watchedAt, progressKey)` for History.
 */
data class WallItem(val row: WallRow, val watchedAt: Long = 0, val progressKey: String = "")

/**
 * The walls' reads (spec 40 §9.3) on the database dispatcher. Pages are keyset ranges; a merged
 * group reads each source's group with the same cursor and merges the pages (decision "Provider
 * groups across sources"), so no read sorts more than one page.
 */
class WallReads(private val db: SohvaDatabase, private val io: CoroutineDispatcher, private val profile: () -> String) {
    private val dao get() = db.walls()

    /** Any write that can change a wall or its rail: imports, source edits, progress. */
    fun changes(): Flow<Unit> =
        db.invalidationTracker.createFlow("movie", "series", "content_group", "source", "watch_progress").map { }

    /**
     * The room's provider groups of the enabled sources, merged by `lowercase(trim(name))` and
     * labelled with the smallest spelling, case-insensitive A–Z (VOD-FR-03's default order). A few
     * hundred rows per source.
     */
    suspend fun groups(room: WallRoom): List<RailGroup> = withContext(io) {
        val rows = dao.groups(room.groupRoom, dao.enabledSources())
        rows.groupBy { it.name.trim().lowercase(Locale.ROOT) }
            .map { (_, same) -> RailGroup(same.minOf { it.name.trim() }, same.map { it.id }, same.sumOf { it.itemCount }) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /**
     * One page of [destination] after (or, with [forward] false, before) [from], at most [limit]
     * entries, in wall order either way. [from] null starts at the beginning (or end).
     */
    suspend fun page(room: WallRoom, destination: WallDestination, search: String, from: WallItem?, forward: Boolean, limit: Int): List<WallItem> =
        withContext(io) {
            val sources = dao.enabledSources()
            if (sources.isEmpty()) return@withContext emptyList()
            val query = search.trim().takeIf { it.isNotEmpty() }?.let(SortNames::of)
            if (destination == WallDestination.History) return@withContext history(room, query, from, forward, limit, sources)
            val name = from?.row?.sortName ?: if (forward) "" else MAX_NAME
            val id = from?.row?.id ?: if (forward) Long.MIN_VALUE else Long.MAX_VALUE
            val rows = when (destination) {
                is WallDestination.Group -> merged(destination.groupIds, forward, limit) { groupId ->
                    when (room) {
                        WallRoom.MOVIES -> if (forward) dao.filmGroupAfter(groupId, name, id, sources, query, limit) else dao.filmGroupBefore(groupId, name, id, sources, query, limit)
                        WallRoom.SERIES -> if (forward) dao.seriesGroupAfter(groupId, name, id, sources, query, limit) else dao.seriesGroupBefore(groupId, name, id, sources, query, limit)
                    }
                }
                WallDestination.AllGroups -> when (room) {
                    WallRoom.MOVIES -> if (forward) dao.filmAllAfter(name, id, sources, query, limit) else dao.filmAllBefore(name, id, sources, query, limit)
                    WallRoom.SERIES -> if (forward) dao.seriesAllAfter(name, id, sources, query, limit) else dao.seriesAllBefore(name, id, sources, query, limit)
                }
                is WallDestination.OfGenre -> {
                    val g = destination.genre.wire
                    when (room) {
                        WallRoom.MOVIES -> if (forward) dao.filmGenreAfter(g, name, id, sources, query, limit) else dao.filmGenreBefore(g, name, id, sources, query, limit)
                        WallRoom.SERIES -> if (forward) dao.seriesGenreAfter(g, name, id, sources, query, limit) else dao.seriesGenreBefore(g, name, id, sources, query, limit)
                    }
                }
                WallDestination.Unsorted -> when (room) {
                    WallRoom.MOVIES -> if (forward) dao.filmUnsortedAfter(name, id, sources, query, limit) else dao.filmUnsortedBefore(name, id, sources, query, limit)
                    WallRoom.SERIES -> if (forward) dao.seriesUnsortedAfter(name, id, sources, query, limit) else dao.seriesUnsortedBefore(name, id, sources, query, limit)
                }
                WallDestination.History -> error("handled above")
            }
            val ordered = if (forward) rows else rows.asReversed()
            ordered.map { WallItem(it) }
        }

    private fun history(room: WallRoom, query: String?, from: WallItem?, forward: Boolean, limit: Int, sources: List<String>): List<WallItem> {
        val who = profile()
        // "Forward" on History is older: the wall runs newest first.
        val at = from?.watchedAt ?: if (forward) Long.MAX_VALUE else Long.MIN_VALUE
        val key = from?.progressKey ?: if (forward) MAX_NAME else ""
        val rows: List<HistoryRow> = when (room) {
            WallRoom.MOVIES -> if (forward) dao.filmHistoryOlder(who, at, key, sources, query, limit) else dao.filmHistoryNewer(who, at, key, sources, query, limit)
            WallRoom.SERIES -> if (forward) dao.seriesHistoryOlder(who, at, key, sources, query, limit) else dao.seriesHistoryNewer(who, at, key, sources, query, limit)
        }
        val ordered = if (forward) rows else rows.asReversed()
        return ordered.map { WallItem(it.title, it.updatedAt, it.contentKey) }
    }

    /** Pages each group with the same cursor and keeps the first [limit] in wall order. */
    private inline fun merged(groupIds: List<Long>, forward: Boolean, limit: Int, read: (Long) -> List<WallRow>): List<WallRow> {
        if (groupIds.size == 1) return read(groupIds[0])
        val all = groupIds.flatMap(read)
        val order = compareBy<WallRow>({ it.sortName }, { it.id })
        return all.sortedWith(if (forward) order else order.reversed()).take(limit)
    }

    private companion object {
        /** Sorts after every `sort_name` and content key: those are lower-case text. */
        const val MAX_NAME = "￿"
    }
}
