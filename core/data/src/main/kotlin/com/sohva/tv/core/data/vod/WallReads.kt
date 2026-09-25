package com.sohva.tv.core.data.vod

import com.sohva.tv.core.data.database.CopyFacts
import com.sohva.tv.core.data.database.HistoryRow
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.WallRow
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgResolver
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.vod.CustomGroup
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.model.vod.VodText
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Movies or Series (spec 40 VOD-FR-01). */
enum class WallRoom(val groupRoom: String, val org: OrgRoom) { MOVIES("MOVIES", OrgRoom.MOVIES), SERIES("SERIES", OrgRoom.SERIES) }

/** What a wall shows (spec 40 VOD-FR-02): a slice the database answers directly. */
sealed interface WallDestination {
    data object History : WallDestination

    /**
     * A provider group, merged by name across enabled sources: [groupIds] holds one id per source;
     * [sort] is its content order (spec 42 ORG-FR-20).
     */
    data class Group(val name: String, val groupIds: List<Long>, val sort: OrgSort = OrgSort.TITLE_ASC) : WallDestination

    /** Every title: the wall when every provider group is hidden (VOD-08). */
    data object AllGroups : WallDestination

    data class OfGenre(val genre: Genre) : WallDestination

    data object Unsorted : WallDestination

    /** A group of your own (VOD-FR-11): its genres' walls merged, with the year and rating bounds. */
    data class Custom(val group: CustomGroup) : WallDestination
}

/** A rail row for a provider group: its label, its sources' group ids, the title count and its manual place. */
data class RailGroup(val name: String, val groupIds: List<Long>, val count: Int, val position: Int = Int.MAX_VALUE, val sort: OrgSort = OrgSort.TITLE_ASC)

/**
 * The rail of a room (spec 42 ORG-FR-19, ORG-11): the shown groups in the room's group order, and
 * History's shortcut rule. [manual] says whether History takes its own place among the groups.
 */
data class Rail(val groups: List<RailGroup>, val historyShown: Boolean, val historyPosition: Long?, val manual: Boolean)

/**
 * One wall entry with the position the pager keys on: `(sortName, id)` for A–Z walls,
 * `(watchedAt, progressKey)` for History. [copies] is how many copies of the film the card stands
 * for on this wall ("×N" from 2, VOD-FR-36).
 */
data class WallItem(val row: WallRow, val watchedAt: Long = 0, val progressKey: String = "", val copies: Int = 1)

/**
 * The walls' reads (spec 40 §9.3) on the database dispatcher. Pages are keyset ranges; a merged
 * group reads each source's group with the same cursor and merges the pages (decision "Provider
 * groups across sources"), so no read sorts more than one page.
 */
class WallReads(private val db: SohvaDatabase, private val io: CoroutineDispatcher, private val profile: () -> String) {
    private val dao get() = db.walls()
    private val orders = GroupOrderPages { db.walls() }

    /** Any write that can change a wall or its rail: imports, source edits, progress. */
    fun changes(): Flow<Unit> =
        db.invalidationTracker.createFlow("movie", "series", "content_group", "source", "watch_progress", "genre_count").map { }

    /**
     * The room's provider groups of the enabled sources, merged by `lowercase(trim(name))` and
     * labelled with the smallest spelling, case-insensitive A–Z (VOD-FR-03's default order). A few
     * hundred rows per source.
     */
    suspend fun groups(room: WallRoom): List<RailGroup> = rail(room).groups

    /**
     * The rail (ORG-FR-19): provider order is the case-insensitive name order for films and series
     * (VOD-FR-03), A–Z and Z–A compare the sort forms, manual order the smallest place among a
     * merged group's sources (none last, then by name). Hidden groups are not read at all.
     */
    suspend fun rail(room: WallRoom): Rail = withContext(io) {
        val rows = dao.groups(room.groupRoom, dao.enabledSources())
        val merged = rows.groupBy { it.name.trim().lowercase(Locale.ROOT) }
            .map { (_, same) ->
                RailGroup(
                    same.minOf { it.name.trim() }, same.map { it.id }, same.sumOf { it.itemCount }, same.minOf { it.position },
                    // Sources' groups of one name share the combined rule; the first source's order stands.
                    OrgSort.of(same.first().sortMode) ?: room.org.defaultSort,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val resolver = OrgResolver(OrgRules(db).of(room.org))
        val order = resolver.groupOrder(room.org)
        val groups = when (order) {
            OrgSort.TITLE_ASC -> merged.sortedBy { SortNames.of(it.name) }
            OrgSort.TITLE_DESC -> merged.sortedByDescending { SortNames.of(it.name) }
            OrgSort.MANUAL -> merged.sortedBy { it.position }
            else -> merged
        }
        Rail(groups, resolver.shortcutShown(room.org, OrgKeys.HISTORY), resolver.shortcutPosition(room.org, OrgKeys.HISTORY), order == OrgSort.MANUAL)
    }

    /**
     * Titles per genre wire value (`""` for Unsorted) from the counts table the background passes
     * keep (spec 40 VOD-FR-04, §9.3): one small read, never a count over the library.
     */
    suspend fun genreCounts(room: WallRoom): Map<String, Int> = withContext(io) {
        db.library().counts(room.groupRoom).associate { it.genre to it.titles }
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
            if (destination is WallDestination.Group && orders.serves(destination.sort)) {
                // A group's other order: each source's group pages in that order, merged by the same tuple.
                val order = orders.comparator(destination.sort)
                val rows = destination.groupIds.flatMap { orders.page(room, it, destination.sort, sources, query, from?.row, forward, limit) }
                    .sortedWith(order)
                val page = if (forward) rows.take(limit) else rows.takeLast(limit)
                return@withContext if (room == WallRoom.MOVIES) fold(page, destination, sources) else page.map { WallItem(it) }
            }
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
                is WallDestination.Custom -> custom(room, destination.group, name, id, sources, query, forward, limit)
                WallDestination.History -> error("handled above")
            }
            val ordered = if (forward) rows else rows.asReversed()
            if (room == WallRoom.MOVIES) fold(ordered, destination, sources) else ordered.map { WallItem(it) }
        }

    /**
     * Folded cards (VOD-FR-27): one read of the page's copies by work key (at most 120 keys), kept
     * to the copies on this wall; a card with two or more takes their count, the union of their
     * quality chips, and a poster, year, rating and override from the first copy that has one.
     */
    private fun fold(rows: List<WallRow>, destination: WallDestination, sources: List<String>): List<WallItem> {
        val works = rows.mapNotNull { it.workKey }.distinct()
        if (works.isEmpty()) return rows.map { WallItem(it) }
        val copies = dao.filmCopies(works, sources).filter { it.onWall(destination) }.groupBy { it.workKey }
        return rows.map { row ->
            val same = row.workKey?.let(copies::get).orEmpty()
            if (same.size < 2) WallItem(row) else WallItem(filled(row, same.filter { it.key != row.key }), copies = same.size)
        }
    }

    private fun CopyFacts.onWall(destination: WallDestination): Boolean = when (destination) {
        is WallDestination.Group -> groupId in destination.groupIds
        is WallDestination.OfGenre -> genre == destination.genre.wire
        WallDestination.Unsorted -> genre == null
        is WallDestination.Custom -> destination.group.matches(Genre.ofWire(genre), year, VodText.ratingNumber(rating))
        WallDestination.AllGroups, WallDestination.History -> true
    }

    /**
     * A group of your own: each genre index walked with the bounds and merged (a title has one
     * genre, so the walks never overlap); without genres, the all-titles index with the bounds.
     */
    private fun custom(room: WallRoom, g: CustomGroup, name: String, id: Long, sources: List<String>, query: String?, forward: Boolean, limit: Int): List<WallRow> {
        val (from, to, min) = Triple(g.fromYear, g.toYear, g.minRatingTenths)
        if (g.genres.isEmpty()) {
            return when (room) {
                WallRoom.MOVIES -> if (forward) dao.filmAllBoundedAfter(from, to, min, name, id, sources, query, limit) else dao.filmAllBoundedBefore(from, to, min, name, id, sources, query, limit)
                WallRoom.SERIES -> if (forward) dao.seriesAllBoundedAfter(from, to, min, name, id, sources, query, limit) else dao.seriesAllBoundedBefore(from, to, min, name, id, sources, query, limit)
            }
        }
        val walks = g.genres.sortedBy { it.ordinal }.map { genre ->
            val w = genre.wire
            when (room) {
                WallRoom.MOVIES -> if (forward) dao.filmGenreBoundedAfter(w, from, to, min, name, id, sources, query, limit) else dao.filmGenreBoundedBefore(w, from, to, min, name, id, sources, query, limit)
                WallRoom.SERIES -> if (forward) dao.seriesGenreBoundedAfter(w, from, to, min, name, id, sources, query, limit) else dao.seriesGenreBoundedBefore(w, from, to, min, name, id, sources, query, limit)
            }
        }
        val order = compareBy<WallRow>({ it.sortName }, { it.id })
        return walks.flatten().sortedWith(if (forward) order else order.reversed()).take(limit)
    }

    private fun filled(row: WallRow, others: List<CopyFacts>): WallRow {
        val override = if (row.replacementTitle.isNullOrBlank()) others.firstOrNull { !it.replacementTitle.isNullOrBlank() } else null
        return row.copy(
            posterUrl = row.posterUrl?.takeIf { it.isNotBlank() } ?: others.firstNotNullOfOrNull { it.posterUrl?.takeIf(String::isNotBlank) },
            year = row.year ?: others.firstNotNullOfOrNull { it.year },
            rating = row.rating?.takeIf { it.isNotBlank() } ?: others.firstNotNullOfOrNull { it.rating?.takeIf(String::isNotBlank) },
            qualityMask = others.fold(row.qualityMask) { mask, copy -> mask or copy.qualityMask },
            replacementTitle = override?.replacementTitle ?: row.replacementTitle,
            replacementPoster = override?.replacementPoster ?: row.replacementPoster,
            replacePoster = override?.replacePoster ?: row.replacePoster,
        )
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
