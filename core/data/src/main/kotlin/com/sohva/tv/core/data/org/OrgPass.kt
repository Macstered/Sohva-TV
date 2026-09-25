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
    fun resolveSource(sourceId: String, preferred: PreferredCopy, phase: (String) -> Unit = {}) {
        val resolver = OrgResolver(rules.all())
        for (room in OrgRoom.entries) resolveGroups(room, resolver)
        // The standing copies are decided for the whole source right after (refreshSource).
        titles(KeyRange.movies(sourceId), OrgRoom.MOVIES, resolver, null)
        titles(KeyRange.series(sourceId), OrgRoom.SERIES, resolver, null)
        channels(sourceId, resolver)
        phase("organisation")
        passes.refreshSource(sourceId, preferred, phase)
        db.library().recountSeriesGroups(sourceId)
        phase("series counts")
    }

    /** Channels whose edits changed (channel management): the rules decide their visibility again. */
    fun resolveChannels(keys: List<String>) {
        if (keys.isEmpty()) return
        val resolver = OrgResolver(rules.of(OrgRoom.LIVE))
        val rows = keys.chunked(IN_LIMIT).flatMap { dao.channelsOf(it) }
        for ((sourceId, ofSource) in rows.groupBy { it.sourceId }) {
            val ranks = LiveRanks(db, liveOrder(sourceId, resolver))
            db.runInTransaction {
                ofSource.forEach {
                    applyChannel(it, resolver)
                    ranks.place(it)
                }
            }
            ranks.finish()
        }
    }

    /** The Live order of [sourceId]'s groups as they are stored now (after [resolveGroups]). */
    private fun liveOrder(sourceId: String, resolver: OrgResolver): LiveOrder =
        LiveOrder(dao.groups(OrgRoom.LIVE.wire).filter { it.sourceId == sourceId }, resolver)

    /** After a playlist import of [sourceId]: the Live groups, then the source's channels and their group counts. */
    fun resolveLive(sourceId: String) {
        val resolver = OrgResolver(rules.of(OrgRoom.LIVE))
        resolveGroups(OrgRoom.LIVE, resolver)
        channels(sourceId, resolver, force = true)
    }

    /**
     * After rules changed: every group of the changed rooms, then the items the changed keys can
     * reach: a group key's sources (walked by key range: about 1.5 s for 200,000 films on the
     * stand-in, and no extra index on the title tables), an item key's copies. Shortcut and list
     * rules are read where they are used and resolve nothing here.
     */
    fun afterChange(keys: Collection<RuleKey>, preferred: PreferredCopy) {
        if (keys.isEmpty()) return
        val resolver = OrgResolver(rules.all())
        for (room in keys.map { it.room }.toSet()) {
            val groups = resolveGroups(room, resolver)
            val touched = keys.filter { it.room == room }
            val itemKeys = touched.map { it.itemKey }.filter { it.isNotEmpty() && !it.startsWith("@") }.toSet()
            val reachedGroups = groups.filter { g -> touched.any { k -> k.itemKey.isEmpty() && reaches(k, g) } }
            val groupSources = reachedGroups.map { it.sourceId }.toSet()
            val sources = HashSet<String>()
            when (room) {
                OrgRoom.MOVIES -> {
                    val works = HashSet<String>()
                    groupSources.forEach { titles(KeyRange.movies(it), room, resolver, works, sources) }
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
                    groupSources.forEach { titles(KeyRange.series(it), room, resolver, null, sources) }
                    itemKeys.chunked(IN_LIMIT).forEach { apply(dao.seriesOf(it), room, resolver, null, sources) }
                    sources.forEach { db.library().recountSeriesGroups(it) }
                }
                OrgRoom.LIVE -> {
                    // Channel groups are few and small next to a source: re-resolve the sources the keys reach.
                    // The group order and the room's default order place every channel of every source.
                    val everywhere = touched.any { it.itemKey.isEmpty() && it.sourceId.isEmpty() && (it.groupKey == OrgKeys.GROUPS || it.groupKey.isEmpty()) }
                    val reached = if (everywhere) {
                        groups.map { it.sourceId }.toSet()
                    } else {
                        groupSources + itemKeys.chunked(IN_LIMIT).flatMap { dao.channelsOf(it.toList()) }.map { it.sourceId }
                    }
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

    private fun titles(range: KeyRange, room: OrgRoom, resolver: OrgResolver, works: MutableSet<String>?, sources: MutableSet<String> = HashSet()) {
        var after = range.from
        while (true) {
            val page = if (room == OrgRoom.MOVIES) dao.filmsPage(after, range.until, PAGE) else dao.seriesPage(after, range.until, PAGE)
            if (page.isEmpty()) return
            apply(page, room, resolver, works, sources)
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

    /**
     * A source's channels (ORG-FR-16): rules, and the household's own hidden flag (legacy hidden,
     * ORG-FR-15); and their order (GUIDE-13), written only where it changed.
     */
    private fun channels(sourceId: String, resolver: OrgResolver, force: Boolean = false) {
        // Channel keys start with their source: its channels are one range of the key index.
        val range = KeyRange.channels(sourceId)
        val ranks = LiveRanks(db, liveOrder(sourceId, resolver))
        var after = range.from
        var changed = false
        while (true) {
            val page = dao.channelsPage(after, range.until, PAGE)
            if (page.isEmpty()) break
            db.runInTransaction {
                page.forEach {
                    if (applyChannel(it, resolver)) changed = true
                    ranks.place(it)
                }
            }
            after = page.last().key
            if (page.size < PAGE) break
        }
        ranks.finish()
        if (changed || force) db.library().recountChannelGroups(sourceId)
    }

    private fun applyChannel(row: OrgChannelRow, resolver: OrgResolver): Boolean {
        val item = OrgItem(OrgRoom.LIVE, row.sourceId, row.groupKey ?: OrgKeys.nameKey(row.groupName), OrgKeys.nameKey(row.groupName), row.key, legacyHidden = row.hidden)
        val visible = resolver.eligible(item)
        if (visible == row.visible) return false
        dao.setChannel(row.id, visible)
        return true
    }

    private companion object {
        const val PAGE = 2_000
        const val IN_LIMIT = 500
        const val WORK = "work:"
    }
}
