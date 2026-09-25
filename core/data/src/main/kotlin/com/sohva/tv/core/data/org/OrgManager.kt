package com.sohva.tv.core.data.org

import com.sohva.tv.core.data.database.ManagerGroupRow
import com.sohva.tv.core.data.database.ManagerItemRow
import com.sohva.tv.core.data.database.ManagerSourceRow
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.org.OrgItem
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgResolver
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.text.SortNames
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A rule key a manager group writes: its source ("" = every source) and group key (ORG-FR-07). */
data class GroupRef(val sourceId: String, val groupKey: String)

/** What a manager group row stands for (ORG-FR-40). */
enum class ManagedKind { SHORTCUT, LIST, GROUP }

/**
 * A group as the library manager shows it (spec 42 ORG-FR-07, -40, -43): a shortcut, a custom
 * list or the provider groups of one name (combined across sources in the "All sources" scope).
 * [backing] are the keys a group action writes, the combined or scoped name key first.
 */
data class ManagedGroup(
    val key: String,
    val kind: ManagedKind,
    val label: String,
    val shown: Boolean,
    val enabled: Int,
    val total: Int,
    val backing: List<GroupRef>,
    val groupIds: List<Long>,
    /** The group's own content order; null follows the room default. */
    val sort: OrgSort?,
    /** The room default or the group's own order, as the items are listed. */
    val effectiveSort: OrgSort,
    val position: Long?,
)

/** One copy of an item in the group: what a member rule names (ORG-FR-32). */
data class ItemCopy(val key: String, val sourceId: String, val groupKey: String)

/** An item of a managed group, one per identity (a film's copies in the group are one row). */
data class ManagedItem(
    val identity: String,
    val title: String,
    val sourceName: String,
    val image: String?,
    val copies: List<ItemCopy>,
    /** On in this group or list (ORG-FR-16). */
    val enabled: Boolean,
    val hiddenEverywhere: Boolean,
    val groupHidden: Boolean,
    val sourceDisabled: Boolean,
    val position: Long?,
    val legacyPosition: Long?,
    val sortName: String,
    val year: Int?,
    val ratingX10: Int?,
    val providerOrder: Int,
) {
    /** Off for a reason OK cannot change here: the menu explains it instead (ORG-FR-48). */
    val blocked: Boolean get() = sourceDisabled || hiddenEverywhere || groupHidden
}

/**
 * The library manager's reads (spec 42 §9.2): the group list from the small group table and the
 * rules, one group's items (at most [ITEMS] rows, a search narrows a larger group) with their
 * states worked out in memory from the rules. No read covers a whole room.
 */
class OrgManager(private val db: SohvaDatabase, private val rules: OrgRules, private val io: CoroutineDispatcher) {
    private val dao get() = db.organization()

    suspend fun sources(): List<ManagerSourceRow> = withContext(io) { dao.managerSources() }

    /** The room's rows in the manager's order (ORG-FR-40): shortcuts, lists, then the groups in group order. */
    suspend fun groups(room: OrgRoom, scope: String?): List<ManagedGroup> = withContext(io) {
        val resolver = OrgResolver(rules.of(room))
        val shortcuts = if (room == OrgRoom.LIVE) listOf(OrgKeys.FAVOURITES, OrgKeys.RECENT) else listOf(OrgKeys.HISTORY)
        val entries = ArrayList<ManagedGroup>()
        shortcuts.forEach { key ->
            entries += ManagedGroup(
                key, ManagedKind.SHORTCUT, "", resolver.shortcutShown(room, key), 0, 0, listOf(GroupRef("", key)), emptyList(),
                null, OrgSort.MANUAL, resolver.shortcutPosition(room, key),
            )
        }
        if (room == OrgRoom.LIVE) {
            dao.managerLists().forEach { l ->
                val key = OrgKeys.list(l.id)
                entries += ManagedGroup(
                    key, ManagedKind.LIST, l.name, resolver.shortcutShown(room, key), l.members, l.members, listOf(GroupRef("", key)), emptyList(),
                    null, OrgSort.MANUAL, resolver.shortcutPosition(room, key),
                )
            }
        }
        val rows = dao.managerGroups(room.wire).filter { scope == null || it.sourceId == scope }
        val groups = rows.groupBy { OrgKeys.nameKey(it.name) }.map { (nameKey, same) -> group(room, scope, nameKey, same.sortedBy { it.providerOrder }, resolver) }
        val ordered = when (resolver.groupOrder(room)) {
            OrgSort.TITLE_ASC -> groups.sortedBy { SortNames.of(it.label) }
            OrgSort.TITLE_DESC -> groups.sortedByDescending { SortNames.of(it.label) }
            // Live: the order groups first appear in the playlist; films and series: by name (ORG-FR-19).
            else -> if (room == OrgRoom.LIVE) groups.sortedBy { g -> rows.filter { it.id in g.groupIds }.minOf { it.providerOrder } } else groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
        entries += ordered
        if (resolver.groupOrder(room) == OrgSort.MANUAL) entries.sortedBy { it.position ?: Long.MAX_VALUE } else entries
    }

    private fun group(room: OrgRoom, scope: String?, nameKey: String, rows: List<ManagerGroupRow>, resolver: OrgResolver): ManagedGroup {
        val first = rows.first()
        val backing = listOf(GroupRef(scope.orEmpty(), nameKey)) + rows.map { GroupRef(it.sourceId, it.groupKey) }.filter { it.groupKey != nameKey || it.sourceId != scope.orEmpty() }
        val position = rows.mapNotNull { resolver.groupRule(room, it.sourceId, it.groupKey, nameKey).position }.minOrNull()
        val own = resolver.groupRule(room, first.sourceId, first.groupKey, nameKey).sort
        return ManagedGroup(
            key = nameKey, kind = ManagedKind.GROUP, label = first.name.trim(), shown = rows.any { it.shown },
            enabled = rows.sumOf { it.itemCount }, total = rows.sumOf { it.totalCount }, backing = backing, groupIds = rows.map { it.id },
            sort = own, effectiveSort = own ?: resolver.roomSort(room), position = position,
        )
    }

    /** A group's items in its order (ORG-FR-21, management includes everything); a shortcut has none. */
    suspend fun items(room: OrgRoom, group: ManagedGroup, search: String?): List<ManagedItem> = withContext(io) {
        val query = search?.trim()?.takeIf { it.isNotEmpty() }?.let(SortNames::of)
        val rows: List<ManagerItemRow> = when (group.kind) {
            ManagedKind.SHORTCUT -> return@withContext emptyList()
            ManagedKind.LIST -> dao.managerListMembers(group.key.removePrefix("@list:"), query, ITEMS)
            ManagedKind.GROUP -> group.groupIds.flatMap { id ->
                when (room) {
                    OrgRoom.MOVIES -> dao.managerFilms(id, query, ITEMS)
                    OrgRoom.SERIES -> dao.managerSeries(id, query, ITEMS)
                    OrgRoom.LIVE -> dao.managerChannels(id, query, ITEMS)
                }
            }
        }
        val sources = dao.managerSources().associateBy { it.id }
        val resolver = OrgResolver(rules.of(room))
        val view = group.key.takeIf { group.kind == ManagedKind.LIST }
        val items = rows.groupBy { it.workKey?.let(OrgKeys::film) ?: it.key }.map { (identity, copies) ->
            val row = copies.first()
            val item = OrgItem(
                room, row.sourceId, row.groupKey ?: OrgKeys.nameKey(row.groupName), OrgKeys.nameKey(row.groupName), row.key,
                identity = row.workKey?.let(OrgKeys::film), legacyHidden = row.hidden,
            )
            val sourceEnabled = copies.any { sources[it.sourceId]?.enabled == true }
            val enabled = sourceEnabled && if (view != null) resolver.enabledIn(item, view) else resolver.eligible(item)
            ManagedItem(
                identity = identity, title = row.shownName, sourceName = sources[row.sourceId]?.name.orEmpty(), image = row.image,
                copies = copies.map { ItemCopy(it.key, it.sourceId, it.groupKey ?: OrgKeys.nameKey(it.groupName)) },
                enabled = enabled, hiddenEverywhere = !resolver.shownEverywhere(item),
                groupHidden = resolver.groupRule(room, item.sourceId, item.groupKey, item.nameKey).enabled == false,
                sourceDisabled = !sourceEnabled, position = resolver.memberRule(item, view).position, legacyPosition = row.legacyPosition,
                sortName = row.sortName, year = row.year, ratingX10 = row.ratingX10, providerOrder = row.providerOrder,
            )
        }
        items.sortedWith(ManagerOrder.comparator(group.effectiveSort))
    }

    companion object {
        /** A group's items read at once (spec 42 §9.2); a search reaches the rest of a larger group. */
        const val ITEMS: Int = 2_000
    }
}

/** The comparators of ORG-FR-22 over manager items. */
object ManagerOrder {
    private val title = compareBy<ManagedItem> { it.sortName }.thenBy { it.identity }

    fun comparator(sort: OrgSort): Comparator<ManagedItem> = when (sort) {
        OrgSort.PROVIDER -> compareBy<ManagedItem> { it.providerOrder }.then(title)
        OrgSort.TITLE_ASC -> title
        OrgSort.TITLE_DESC -> compareByDescending<ManagedItem> { it.sortName }.thenBy { it.identity }
        OrgSort.NEWEST -> compareBy<ManagedItem> { it.year == null }.thenByDescending { it.year }.then(title)
        OrgSort.OLDEST -> compareBy<ManagedItem> { it.year == null }.thenBy { it.year }.then(title)
        OrgSort.RATING -> compareBy<ManagedItem> { it.ratingX10 == null }.thenByDescending { it.ratingX10 }.then(title)
        // Rank = member position, else the legacy position, else last; ties provider order, then title (ORG-FR-22).
        OrgSort.MANUAL -> compareBy<ManagedItem> { it.position ?: it.legacyPosition ?: Long.MAX_VALUE }.thenBy { it.providerOrder }.then(title)
    }

    /** Label of an unnamed group (ORG-FR-07). */
    fun isUngrouped(label: String): Boolean = label.isBlank()
}
