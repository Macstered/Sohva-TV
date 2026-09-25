package com.sohva.tv.core.data.vod

import androidx.sqlite.db.SimpleSQLiteQuery
import com.sohva.tv.core.data.database.WallDao
import com.sohva.tv.core.data.database.WallRow
import com.sohva.tv.core.data.database.WallSql
import com.sohva.tv.core.model.org.OrgSort

/**
 * A provider group's wall in one of its other orders (spec 42 ORG-07, ORG-FR-22): Z–A, newest
 * or oldest release, highest rating, or the manual order. Every page is a keyset range over one
 * of the group indexes, read forwards or backwards, so no page sorts (spec 40 §9.3). A missing
 * year, rating or place comes last in both directions: the rows that have one are read first,
 * then those without, in title order (two phases of one index).
 *
 * Oldest first reads the newest-first index backwards, so titles of one year run Z–A (decision
 * "Group orders").
 */
internal class GroupOrderPages(private val dao: () -> WallDao) {
    private class Shape(val key: String?, val keyAsc: Boolean, val titleAsc: Boolean, val index: String)

    private fun shape(room: WallRoom, sort: OrgSort): Shape? {
        val t = if (room == WallRoom.MOVIES) "movie" else "series"
        val p = if (room == WallRoom.MOVIES) "group_primary" else "primary_copy"
        return when (sort) {
            OrgSort.TITLE_DESC -> Shape(null, true, false, "index_${t}_group_id_visible_${p}_sort_name")
            OrgSort.NEWEST -> Shape("year", false, true, "index_${t}_group_id_visible_${p}_year_sort_name")
            OrgSort.OLDEST -> Shape("year", true, false, "index_${t}_group_id_visible_${p}_year_sort_name")
            OrgSort.RATING -> Shape("rating_x10", false, true, "index_${t}_group_id_visible_${p}_rating_x10_sort_name")
            OrgSort.MANUAL -> Shape("item_position", true, true, "index_${t}_group_id_visible_${p}_item_position_sort_name")
            else -> null
        }
    }

    /** Whether [sort] is served here; A–Z and provider order use the plain title pages. */
    fun serves(sort: OrgSort): Boolean = shape(WallRoom.MOVIES, sort) != null

    /** One page after (or before, [forward] false) [from], in wall order. */
    fun page(room: WallRoom, groupId: Long, sort: OrgSort, sources: List<String>, search: String?, from: WallRow?, forward: Boolean, limit: Int): List<WallRow> {
        val s = shape(room, sort) ?: error("not a group order: $sort")
        if (s.key == null) return read(room, s, groupId, sources, search, from, forward, limit, phase = null)
        val fromKey = from?.let { keyOf(s, it) }
        val out = ArrayList<WallRow>(limit)
        if (forward) {
            // Phase 1 (with a key) before phase 2 (without).
            if (from == null || fromKey != null) out += read(room, s, groupId, sources, search, from, true, limit, phase = 1)
            if (out.size < limit) out += read(room, s, groupId, sources, search, if (from != null && fromKey == null) from else null, true, limit - out.size, phase = 2)
        } else {
            if (from == null || fromKey == null) out += read(room, s, groupId, sources, search, from, false, limit, phase = 2)
            if (out.size < limit) out += read(room, s, groupId, sources, search, if (from != null && fromKey != null) from else null, false, limit - out.size, phase = 1)
        }
        return out
    }

    /** Wall order of [rows] from several sources' groups, for merging (same tuple as the pages). */
    fun comparator(sort: OrgSort): Comparator<WallRow> {
        val s = shape(WallRoom.MOVIES, sort) ?: return compareBy({ it.sortName }, { it.id })
        val title = compareBy<WallRow>({ it.sortName }, { it.id }).let { if (s.titleAsc) it else it.reversed() }
        if (s.key == null) return title
        val key = compareBy<WallRow> { keyOf(s, it) == null }.thenComparator { a, b ->
            val ka = keyOf(s, a) ?: 0
            val kb = keyOf(s, b) ?: 0
            if (s.keyAsc) ka.compareTo(kb) else kb.compareTo(ka)
        }
        return key.then(title)
    }

    private fun keyOf(s: Shape, row: WallRow): Int? = when (s.key) {
        "year" -> row.year
        "rating_x10" -> row.ratingX10
        "item_position" -> row.itemPosition
        else -> null
    }

    /**
     * One phase read in the given direction; rows come back in wall order. Phase 1 has a key,
     * phase 2 none; [from] is a cursor inside that phase, or null to start at its edge.
     */
    private fun read(room: WallRoom, s: Shape, groupId: Long, sources: List<String>, search: String?, from: WallRow?, forward: Boolean, limit: Int, phase: Int?): List<WallRow> {
        val (sql, args) = build(room, s, groupId, sources, search, from, forward, limit, phase)
        val rows = dao().rows(SimpleSQLiteQuery(sql, args.toTypedArray()))
        return if (forward) rows else rows.asReversed()
    }

    private fun build(room: WallRoom, s: Shape, groupId: Long, sources: List<String>, search: String?, from: WallRow?, forward: Boolean, limit: Int, phase: Int?): Pair<String, List<Any?>> {
        val table = if (room == WallRoom.MOVIES) "movie" else "series"
        val primary = if (room == WallRoom.MOVIES) "group_primary" else "primary_copy"
        val args = ArrayList<Any?>()
        val where = StringBuilder("group_id = ? AND visible = 1 AND $primary = 1")
        args += groupId
        where.append(" AND source_id IN (${sources.joinToString(",") { "?" }})")
        args.addAll(sources)
        if (search != null) {
            where.append(" AND (instr(sort_name, ?) > 0 OR instr(COALESCE(replacement_sort, ''), ?) > 0)")
            args += search
            args += search
        }
        // The direction each column is read in: the wall's own order, or its reverse for a backward page.
        val titleAsc = s.titleAsc == forward
        val keyAsc = s.keyAsc == forward
        val t = if (titleAsc) ">" else "<"
        when (phase) {
            1 -> where.append(" AND ${s.key} IS NOT NULL")
            2 -> where.append(" AND ${s.key} IS NULL")
        }
        if (from != null) {
            val titleAfter = "(sort_name $t ? OR (sort_name = ? AND id $t ?))"
            if (phase == 1) {
                val k = if (keyAsc) ">" else "<"
                where.append(" AND (${s.key} $k ? OR (${s.key} = ? AND $titleAfter))")
                val key = keyOf(s, from)
                args += key
                args += key
            } else {
                where.append(" AND $titleAfter")
            }
            args += from.sortName
            args += from.sortName
            args += from.id
        }
        val titleDir = if (titleAsc) "ASC" else "DESC"
        val order = if (phase == 1) "${s.key} ${if (keyAsc) "ASC" else "DESC"}, sort_name $titleDir, id $titleDir" else "sort_name $titleDir, id $titleDir"
        val sql = "SELECT ${WallSql.COLUMNS} FROM $table INDEXED BY ${s.index} WHERE $where ORDER BY $order LIMIT ?"
        args += limit
        return sql to args
    }

    internal companion object {
        /** The SQL of one phase, for the plan tests. */
        fun planSql(room: WallRoom, sort: OrgSort, phase: Int?, withCursor: Boolean, forward: Boolean): String {
            val pages = GroupOrderPages { error("plan SQL only") }
            val cursor = if (withCursor) WallRow(1, "k", "s", "n", "n", null, 2000, null, 0, null, ratingX10 = 70, itemPosition = 3) else null
            return pages.build(room, pages.shape(room, sort)!!, 1, listOf("s"), null, cursor, forward, 10, phase).first
        }
    }
}
