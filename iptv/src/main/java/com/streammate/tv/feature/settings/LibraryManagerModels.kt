package com.streammate.tv.feature.settings

import com.streammate.tv.core.database.OrganizationChange
import com.streammate.tv.core.model.*
import com.streammate.tv.iptv.repository.ManagedLibrary

internal data class ManagedGroup(
    val key: String,
    val name: String,
    val items: List<OrganizationItem>,
    val automatic: Boolean = false,
    val custom: Boolean = false,
)

internal fun managedGroups(room: LibraryRoom, library: ManagedLibrary, sourceId: String?): List<ManagedGroup> {
    val items = library.items.filter { sourceId == null || it.sourceId == sourceId }
    val groups = items.groupBy { organizationGroupKey(it.groupName) }.map { (key, members) ->
        ManagedGroup(key, members.first().groupName.orEmpty(), members)
    }
    val byName = groups.associateBy(ManagedGroup::name)
    val ordered = library.state.organization.orderedGroups(room, groups.map { it.name to it.items }).mapNotNull(byName::get)
    val custom = library.customLists.map { list ->
        val ranks = library.listMemberships.filter { it.listId == list.id }.associate { it.channelId to it.sortOrder }
        ManagedGroup("@list:${list.id}", list.name, items.filter { it.id in ranks }.map { it.copy(legacyPosition = ranks[it.id]?.toLong()) }, custom = true)
    }
    val shortcuts = if (room == LibraryRoom.LIVE) listOf(ORGANIZATION_FAVOURITES, ORGANIZATION_RECENT) else listOf(ORGANIZATION_HISTORY)
    val combined = shortcuts.map { ManagedGroup(it, it, emptyList(), automatic = true) } + custom + ordered
    return if (library.state.organization.groupSort(room) == LibrarySort.MANUAL) combined.sortedBy { group ->
        group.groupKeys(room, sourceId).mapNotNull { library.state.organization.rule(it)?.position }.minOrNull() ?: Long.MAX_VALUE
    } else combined
}

/**
 * What the rail shows for a group: whether it is on, and how many of its
 * items are on out of how many. Counted once, off the main thread, for every
 * group; the rail's rows used to count their own items, sixteen rule lookups
 * apiece, on every focus move.
 */
internal data class ManagedGroupSummary(val enabled: Boolean, val total: Int, val enabledCount: Int)

internal data class ManagedGroups(
    val groups: List<ManagedGroup> = emptyList(),
    val summaries: Map<String, ManagedGroupSummary> = emptyMap(),
)

/** One group's items in their shown order, with each item's eligibility already decided. */
internal data class ManagedItems(
    val groupKey: String? = null,
    val items: List<OrganizationItem> = emptyList(),
    val enabled: Map<String, Boolean> = emptyMap(),
)

internal fun ManagedGroup.summary(room: LibraryRoom, state: LibraryOrganization): ManagedGroupSummary = ManagedGroupSummary(
    enabled = enabled(room, state),
    total = items.distinctBy { it.identity }.size,
    enabledCount = items.filter { state.enabledInView(room, it, key.takeIf { custom }) }.distinctBy { it.identity }.size,
)

internal fun ManagedGroup.managedItems(room: LibraryRoom, state: LibraryOrganization): ManagedItems {
    val ordered = orderedItems(room, state)
    return ManagedItems(key, ordered, ordered.associate { it.identity to state.enabledInView(room, it, key.takeIf { custom }) })
}

internal fun ManagedGroup.enabled(room: LibraryRoom, state: LibraryOrganization): Boolean =
    if (automatic || custom) state.shortcutEnabled(room, key) else items.any { state.groupRule(room, it).enabled != false }

internal fun ManagedGroup.groupKeys(room: LibraryRoom, sourceId: String?): List<OrganizationKey> =
    if (automatic || custom) listOf(OrganizationKey(room, groupKey = key)) else
        (listOf(OrganizationKey(room, sourceId.orEmpty(), key)) + items.map { OrganizationKey(room, it.sourceId, it.groupKey) }).distinct()

internal fun ManagedGroup.itemKeys(room: LibraryRoom, item: OrganizationItem): List<OrganizationKey> =
    if (custom) listOf(OrganizationKey(room, groupKey = key, itemKey = item.identity)) else
        items.filter { it.identity == item.identity }.map { OrganizationKey(room, it.sourceId, it.groupKey, item.identity) }.distinct()

internal fun ManagedGroup.orderedItems(room: LibraryRoom, state: LibraryOrganization): List<OrganizationItem> =
    state.orderedItems(room, items, viewKey = key.takeIf { custom }, includeDisabled = true).distinctBy { it.identity }

internal fun groupVisibilityChanges(room: LibraryRoom, groups: List<ManagedGroup>, sourceId: String?, enabled: Boolean): List<OrganizationChange> =
    groups.flatMap { group -> group.groupKeys(room, sourceId).map { OrganizationChange(it, enabled, changeEnabled = true) } }

internal fun itemVisibilityChanges(room: LibraryRoom, group: ManagedGroup, items: List<OrganizationItem>, enabled: Boolean): List<OrganizationChange> =
    items.flatMap { item -> group.itemKeys(room, item).map { OrganizationChange(it, enabled, changeEnabled = true) } }

internal fun groupRankChanges(room: LibraryRoom, groups: List<ManagedGroup>, orderedIds: List<String>, sourceId: String?): List<OrganizationChange> =
    listOf(OrganizationChange(OrganizationKey(room, groupKey = ORGANIZATION_GROUP_ORDER), sort = LibrarySort.MANUAL, changeSort = true)) +
        orderedIds.flatMapIndexed { index, id -> groups.firstOrNull { it.key == id }?.groupKeys(room, sourceId).orEmpty().map {
            OrganizationChange(it, position = index.toLong(), changePosition = true)
        } }

internal fun itemRankChanges(room: LibraryRoom, group: ManagedGroup, orderedIds: List<String>, sourceId: String?): List<OrganizationChange> =
    group.groupKeys(room, sourceId).map { OrganizationChange(it, sort = LibrarySort.MANUAL, changeSort = true) } +
        orderedIds.flatMapIndexed { index, id -> group.items.firstOrNull { it.identity == id }?.let { item ->
            group.itemKeys(room, item).map { OrganizationChange(it, position = index.toLong(), changePosition = true) }
        }.orEmpty() }
