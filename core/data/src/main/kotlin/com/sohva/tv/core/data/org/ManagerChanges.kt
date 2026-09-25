package com.sohva.tv.core.data.org

import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgRule
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.org.RuleKey

/**
 * The rule changes behind each library manager action (spec 42 ORG-FR-47…54), pure so they are
 * tested on the JVM. Positions are sparse, 1,024 apart (§9.3), so a move writes one rule unless
 * its neighbours have no room between them.
 */
object ManagerChanges {
    const val STEP: Long = 1_024

    /** OK on an item: on or off in this group (each copy's own group key) or in the list (ORG-FR-48, -32). */
    fun toggle(room: OrgRoom, group: ManagedGroup, item: ManagedItem): List<RuleChange> {
        val on = !item.enabled
        return memberKeys(room, group, item).map { RuleChange(it, enabled = Field.Set(on)) }
    }

    /** Disable / Enable group: every backing key; a shortcut or list its own key (ORG-FR-49). */
    fun groupShown(room: OrgRoom, group: ManagedGroup, shown: Boolean): List<RuleChange> =
        group.backing.map { RuleChange(RuleKey(room, it.sourceId, it.groupKey, ""), enabled = Field.Set(shown)) }

    /** Hide / Show everywhere (ORG-FR-50). */
    fun everywhere(room: OrgRoom, item: ManagedItem, shown: Boolean): List<RuleChange> =
        listOf(RuleChange(RuleKey(room, "", "", item.identity), enabled = Field.Set(shown)))

    /**
     * A group's content order (ORG-FR-51): null is "Use library default". Manual on a group without
     * places seeds its items' places in the order on screen (ORG-10).
     */
    fun groupSort(room: OrgRoom, group: ManagedGroup, sort: OrgSort?, items: List<ManagedItem>): List<RuleChange> {
        val sorts = group.backing.map { RuleChange(RuleKey(room, it.sourceId, it.groupKey, ""), sort = Field.Set(sort)) }
        if (sort != OrgSort.MANUAL || items.any { it.position != null }) return sorts
        return sorts + places(room, group, items)
    }

    /** The room's default content order (ORG-FR-51). */
    fun roomSort(room: OrgRoom, sort: OrgSort): List<RuleChange> = listOf(RuleChange(RuleKey(room, "", "", ""), sort = Field.Set(sort)))

    /** The group order; Manual without places seeds them in the order on screen (ORG-FR-51). */
    fun groupOrder(room: OrgRoom, sort: OrgSort, groups: List<ManagedGroup>): List<RuleChange> {
        val order = RuleChange(RuleKey(room, "", OrgKeys.GROUPS, ""), sort = Field.Set(sort))
        if (sort != OrgSort.MANUAL || groups.any { it.position != null }) return listOf(order)
        return listOf(order) + groups.flatMapIndexed { i, g -> groupPlace(room, g, (i + 1) * STEP) }
    }

    /** "Reset this group": every rule of the room on a backing key, group and member rules alike (ORG-FR-49, spec 42 Q3). */
    fun resetGroup(group: ManagedGroup, rules: List<OrgRule>): List<RuleChange> {
        val refs = group.backing.toSet()
        return rules.filter { GroupRef(it.key.sourceId, it.key.groupKey) in refs }.map {
            RuleChange(it.key, enabled = Field.Set(null), sort = Field.Set(null), position = Field.Set(null))
        }
    }

    /** "Use default in every group…": the sort of every group rule of the room (ORG-FR-51). */
    fun resetSorts(room: OrgRoom, rules: List<OrgRule>): List<RuleChange> = rules
        .filter { it.key.room == room && it.key.itemKey.isEmpty() && it.key.groupKey.isNotEmpty() && !it.key.groupKey.startsWith("@") && it.value.sort != null }
        .map { RuleChange(it.key, sort = Field.Set(null)) }

    /**
     * Items placed in [order] after a move (ORG-FR-52): the moved one takes the midpoint between its
     * new neighbours when they are placed and have room; otherwise the whole order is placed
     * again. The group becomes manually ordered.
     */
    fun moveItem(room: OrgRoom, group: ManagedGroup, order: List<ManagedItem>, moved: ManagedItem): List<RuleChange> {
        val sort = if (group.kind == ManagedKind.LIST) {
            listOf(RuleChange(RuleKey(room, "", group.key, ""), sort = Field.Set(OrgSort.MANUAL)))
        } else {
            group.backing.map { RuleChange(RuleKey(room, it.sourceId, it.groupKey, ""), sort = Field.Set(OrgSort.MANUAL)) }
        }
        val at = order.indexOfFirst { it.identity == moved.identity }
        val between = midpoint(order.getOrNull(at - 1)?.let { it.position ?: it.legacyPosition }, order.getOrNull(at + 1)?.let { it.position ?: it.legacyPosition }, first = at == 0)
        val places = if (between != null && order.all { (it.position ?: it.legacyPosition) != null }) {
            memberKeys(room, group, moved).map { RuleChange(it, position = Field.Set(between)) }
        } else {
            places(room, group, order)
        }
        return sort + places
    }

    /** Groups placed in [order] after a move; the group order becomes manual (ORG-FR-52). */
    fun moveGroup(room: OrgRoom, order: List<ManagedGroup>): List<RuleChange> =
        listOf(RuleChange(RuleKey(room, "", OrgKeys.GROUPS, ""), sort = Field.Set(OrgSort.MANUAL))) +
            order.flatMapIndexed { i, g -> groupPlace(room, g, (i + 1) * STEP) }

    /** Bulk on or off for the chosen items in this group, or the chosen groups (ORG-FR-53). */
    fun bulkItems(room: OrgRoom, group: ManagedGroup, items: List<ManagedItem>, on: Boolean): List<RuleChange> =
        items.flatMap { memberKeys(room, group, it) }.distinct().map { RuleChange(it, enabled = Field.Set(on)) }

    fun bulkGroups(room: OrgRoom, groups: List<ManagedGroup>, on: Boolean): List<RuleChange> =
        groups.flatMap { g -> g.backing.map { RuleKey(room, it.sourceId, it.groupKey, "") } }.distinct().map { RuleChange(it, enabled = Field.Set(on)) }

    private fun places(room: OrgRoom, group: ManagedGroup, items: List<ManagedItem>): List<RuleChange> =
        items.flatMapIndexed { i, item -> memberKeys(room, group, item).map { RuleChange(it, position = Field.Set((i + 1) * STEP)) } }

    private fun groupPlace(room: OrgRoom, g: ManagedGroup, position: Long): List<RuleChange> =
        // The first backing key (combined or scoped) carries the place; its sources inherit it (ORG-FR-12).
        listOf(RuleChange(RuleKey(room, g.backing.first().sourceId, g.backing.first().groupKey, ""), position = Field.Set(position)))

    /** An item's member keys in the view: each copy's (source, group key) in a group, the list key in a list. */
    private fun memberKeys(room: OrgRoom, group: ManagedGroup, item: ManagedItem): List<RuleKey> =
        if (group.kind == ManagedKind.LIST) {
            listOf(RuleKey(room, "", group.key, item.identity))
        } else {
            item.copies.map { RuleKey(room, it.sourceId, it.groupKey, item.identity) }.distinct()
        }

    /** A place strictly between [before] and [after], or null when they leave no room. */
    private fun midpoint(before: Long?, after: Long?, first: Boolean): Long? = when {
        before == null && after == null -> null
        before == null -> if (first && after!! > 1) after / 2 else null
        after == null -> before + STEP
        after - before >= 2 -> before + (after - before) / 2
        else -> null
    }
}
