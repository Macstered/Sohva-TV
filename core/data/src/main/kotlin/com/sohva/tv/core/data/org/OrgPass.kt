package com.sohva.tv.core.data.org

import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.OrgChannelRow
import com.sohva.tv.core.data.database.OrgGroupRow
import com.sohva.tv.core.data.database.OrgTitleRow
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.org.OrgItem
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgResolver
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.RuleKey
import com.sohva.tv.core.model.vod.PreferredCopy

/**
 * Resolves the organisation rules into stored columns once, so no screen evaluates rules per row
 * (spec 42 §9.1): each group's `shown`, `position` and `sort_mode`, and each film's, series' and
 * channel's `visible` (films and series also `item_position`). Pages of ≤ 2,000 rows, one
 * transaction each, writing only rows whose result changed. After an import it runs for the
 * source; after a rule change only for the rows the changed keys can reach. Blocking: callers run
 * it on the bulk-write dispatcher.
 */
class OrgPass(private val db: SohvaDatabase, private val rules: OrgRules, private val passes: LibraryPasses) {
    private val dao get() = db.organization()

    /** After an import of [sourceId]: its groups in every room, then its titles and channels. */
    fun resolveSource(sourceId: String, preferred: PreferredCopy) {
        val resolver = OrgResolver(rules.all())
        for (room in OrgRoom.entries) resolveGroups(room, resolver)
        // The standing copies are decided for the whole source right after (refreshSource).
        titles(KeyRange.movies(sourceId), OrgRoom.MOVIES, resolver, null)
        titles(KeyRange.series(sourceId), OrgRoom.SERIES, resolver, null)
        channels(sourceId, resolver)
        passes.refreshSource(sourceId, preferred)
        db.library().recountSeriesGroups(sourceId)
    }

    /** Channels whose edits changed (channel management): the rules decide their visibility again. */
    fun resolveChannels(keys: List<String>) {
        if (keys.isEmpty()) return
        val resolver = OrgResolver(rules.of(OrgRoom.LIVE))
        keys.chunked(IN_LIMIT).forEach { chunk -> db.runInTransaction { dao.channelsOf(chunk).forEach { applyChannel(it, resolver) } } }
    }

    /** After a playlist import of [sourceId]: the Live groups, then the source's channels and their group counts. */
    fun resolveLive(sourceId: String) {
        val resolver = OrgResolver(rules.of(OrgRoom.LIVE))
        resolveGroups(OrgRoom.LIVE, resolver)
        channels(sourceId, resolver, force = true)
    }

    /**
     * After rules changed: every group of the changed rooms, then the items the changed keys can
     * reach: a group key's members, an item key's copies. Shortcut and list rules are read where
     * they are used and resolve nothing here.
     */
    fun afterChange(keys: Collection<RuleKey>, preferred: PreferredCopy) {
        if (keys.isEmpty()) return
        val resolver = OrgResolver(rules.all())
        for (room in keys.map { it.room }.toSet()) {
            val groups = resolveGroups(room, resolver)
            val touched = keys.filter { it.room == room }
            val itemKeys = touched.map { it.itemKey }.filter { it.isNotEmpty() && !it.startsWith("@") }.toSet()
            val groupIds = groups.filter { g -> touched.any { k -> k.itemKey.isEmpty() && reaches(k, g) } }.map { it.id }
            val sources = HashSet<String>()
            when (room) {
                OrgRoom.MOVIES -> {
                    val works = HashSet<String>()
                    ofGroups(groupIds) { id, after -> dao.filmsOfGroup(id, after, PAGE) }.forEach { page -> apply(page, room, resolver, works, sources) }
                    val workKeys = itemKeys.filter { it.startsWith(WORK) }.map { it.removePrefix(WORK) }
                    val contentKeys = itemKeys.filter { !it.startsWith(WORK) }
                    if (workKeys.isNotEmpty() || contentKeys.isNotEmpty()) {
                        workKeys.chunked(IN_LIMIT).forEach { apply(dao.filmsOf(it, emptyList()), room, resolver, works, sources) }
                        contentKeys.chunked(IN_LIMIT).forEach { apply(dao.filmsOf(emptyList(), it), room, resolver, works, sources) }
                    }
                    if (works.isNotEmpty()) works.chunked(IN_LIMIT).forEach { passes.refreshCopies(it, preferred) }
                    sources.forEach { db.library().recountFilmGroups(it) }
                }
                OrgRoom.SERIES -> {
                    ofGroups(groupIds) { id, after -> dao.seriesOfGroup(id, after, PAGE) }.forEach { page -> apply(page, room, resolver, null, sources) }
                    itemKeys.chunked(IN_LIMIT).forEach { apply(dao.seriesOf(it), room, resolver, null, sources) }
                    sources.forEach { db.library().recountSeriesGroups(it) }
                }
                OrgRoom.LIVE -> {
                    // Channel groups are few and small next to a source: re-resolve the sources the keys reach.
                    val reached = groups.filter { it.id in groupIds }.map { it.sourceId }.toSet() +
                        itemKeys.chunked(IN_LIMIT).flatMap { dao.channelsOf(it.toList()) }.map { it.sourceId }
                    reached.forEach { channels(it, resolver) }
                }
            }
        }
        passes.recountGenres()
    }

    /** Whether a group rule's key covers group [g] (ORG-FR-12): its key or its name key, its source or every source. */
    private fun reaches(k: RuleKey, g: OrgGroupRow): Boolean =
        (k.sourceId.isEmpty() || k.sourceId == g.sourceId) && (k.groupKey == g.groupKey || k.groupKey == OrgKeys.nameKey(g.name))

    /** Every group of [room]: shown, manual position (none sorts last) and content order (ORG-FR-19, -20). */
    private fun resolveGroups(room: OrgRoom, resolver: OrgResolver): List<OrgGroupRow> {
        val rows = dao.groups(room.wire)
        db.runInTransaction {
            for (g in rows) {
                val rule = resolver.groupRule(room, g.sourceId, g.groupKey, OrgKeys.nameKey(g.name))
                val shown = rule.enabled != false
                val position = rule.position?.coerceAtMost(Int.MAX_VALUE.toLong() - 1)?.toInt() ?: Int.MAX_VALUE
                val sort = resolver.itemSort(room, g.sourceId, g.groupKey, OrgKeys.nameKey(g.name)).wire
                if (shown != g.shown || position != g.position || sort != g.sortMode) dao.setGroup(g.id, shown, position, sort)
            }
        }
        return rows
    }

    private fun titles(range: KeyRange, room: OrgRoom, resolver: OrgResolver, works: MutableSet<String>?) {
        var after = range.from
        while (true) {
            val page = if (room == OrgRoom.MOVIES) dao.filmsPage(after, range.until, PAGE) else dao.seriesPage(after, range.until, PAGE)
            if (page.isEmpty()) return
            apply(page, room, resolver, works, HashSet())
            after = page.last().key
            if (page.size < PAGE) return
        }
    }

    /** One page of films or series; collects the work keys and sources of the rows that changed. */
    private fun apply(page: List<OrgTitleRow>, room: OrgRoom, resolver: OrgResolver, works: MutableSet<String>?, sources: MutableSet<String>) {
        if (page.isEmpty()) return
        db.runInTransaction {
            for (row in page) {
                val item = OrgItem(
                    room, row.sourceId, row.groupKey ?: OrgKeys.nameKey(row.groupName), OrgKeys.nameKey(row.groupName), row.key,
                    identity = if (room == OrgRoom.MOVIES) row.workKey?.let(OrgKeys::film) else null,
                )
                val visible = resolver.eligible(item)
                val position = resolver.memberRule(item).position?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
                if (visible == row.visible && position == row.itemPosition) continue
                if (room == OrgRoom.MOVIES) dao.setFilm(row.id, visible, position) else dao.setSeries(row.id, visible, position)
                row.workKey?.let { works?.add(it) }
                sources += row.sourceId
            }
        }
    }

    /** A source's channels (ORG-FR-16): rules, and the household's own hidden flag (legacy hidden, ORG-FR-15). */
    private fun channels(sourceId: String, resolver: OrgResolver, force: Boolean = false) {
        // Channel keys start with their source: its channels are one range of the key index.
        val range = KeyRange.channels(sourceId)
        var after = range.from
        var changed = false
        while (true) {
            val page = dao.channelsPage(after, range.until, PAGE)
            if (page.isEmpty()) break
            db.runInTransaction { page.forEach { if (applyChannel(it, resolver)) changed = true } }
            after = page.last().key
            if (page.size < PAGE) break
        }
        if (changed || force) db.library().recountChannelGroups(sourceId)
    }

    private fun applyChannel(row: OrgChannelRow, resolver: OrgResolver): Boolean {
        val item = OrgItem(OrgRoom.LIVE, row.sourceId, row.groupKey ?: OrgKeys.nameKey(row.groupName), OrgKeys.nameKey(row.groupName), row.key, legacyHidden = row.hidden)
        val visible = resolver.eligible(item)
        if (visible == row.visible) return false
        dao.setChannel(row.id, visible)
        return true
    }

    /** Pages of each group's members by row id, one group at a time (an index range each). */
    private fun ofGroups(groupIds: List<Long>, read: (Long, Long) -> List<OrgTitleRow>): Sequence<List<OrgTitleRow>> = sequence {
        for (id in groupIds) {
            var after = 0L
            while (true) {
                val page = read(id, after)
                if (page.isEmpty()) break
                yield(page)
                after = page.last().id
                if (page.size < PAGE) break
            }
        }
    }

    private companion object {
        const val PAGE = 2_000
        const val IN_LIMIT = 500
        const val WORK = "work:"
    }
}
