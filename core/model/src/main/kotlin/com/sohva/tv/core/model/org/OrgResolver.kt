package com.sohva.tv.core.model.org

/**
 * What the rules say about groups and items (spec 42 §4.3), in memory. Rules are few (hundreds to a
 * few thousand), so they sit in one hash map; items no rule names are answered without item
 * lookups (ORG-FR-13), and group decisions are memoised per (room, source, group key, name key),
 * of which a source has a few hundred (§9.1, §9.4). Not thread-safe: one resolver per pass.
 */
class OrgResolver(rules: Collection<OrgRule>) {
    private val byKey: Map<RuleKey, RuleValue> = rules.filter { !it.value.isEmpty }.associate { it.key to it.value }
    private val named: Map<OrgRoom, Set<String>> = byKey.keys.filter { it.itemKey.isNotEmpty() }.groupBy({ it.room }, { it.itemKey }).mapValues { it.value.toSet() }
    private val groupMemo = HashMap<RuleKey, RuleValue>()

    private fun rule(room: OrgRoom, source: String, group: String, item: String): RuleValue? = byKey[RuleKey(room, source, group, item)]

    /** First fields over [keys] in order (§4.3). */
    private fun first(keys: Sequence<RuleValue?>): RuleValue = keys.fold(RuleValue.NONE) { acc, v -> acc.orElse(v) }

    /**
     * The group rule (ORG-FR-12): first fields over (source, group key), (source, name key),
     * ("", group key), ("", name key), duplicates removed.
     */
    fun groupRule(room: OrgRoom, sourceId: String, groupKey: String, nameKey: String): RuleValue =
        groupMemo.getOrPut(RuleKey(room, sourceId, groupKey, nameKey)) {
            val keys = linkedSetOf(sourceId to groupKey, sourceId to nameKey, "" to groupKey, "" to nameKey)
            first(keys.asSequence().map { (s, g) -> rule(room, s, g, "") })
        }

    /** Whether any rule of the room names [item] (ORG-FR-13): an unnamed item has no item rules. */
    private fun isNamed(item: OrgItem): Boolean {
        val keys = named[item.room] ?: return false
        return item.id in keys || (item.identity != null && item.identity in keys)
    }

    private fun ids(item: OrgItem): List<String> = listOfNotNull(item.identity, item.id).distinct()

    /**
     * The member rule (ORG-FR-14) in [view] (null: the item's own groups; else a custom list key):
     * for source in (item source, ""), for group in the view or (group key, name key), for id in
     * (identity, id).
     */
    fun memberRule(item: OrgItem, view: String? = null): RuleValue {
        if (!isNamed(item)) return RuleValue.NONE
        val groups = if (view != null) listOf(view) else listOf(item.groupKey, item.nameKey).distinct()
        val keys = sequence {
            for (source in listOf(item.sourceId, "").distinct()) for (group in groups) for (id in ids(item)) yield(rule(item.room, source, group, id))
        }
        return first(keys)
    }

    /** Shown everywhere (ORG-FR-15): the first "everywhere" rule, else not hidden by the channel's own flag. */
    fun shownEverywhere(item: OrgItem): Boolean {
        if (isNamed(item)) {
            for (source in listOf(item.sourceId, "").distinct()) {
                for (id in ids(item)) rule(item.room, source, "", id)?.enabled?.let { return it }
            }
        }
        return !item.legacyHidden
    }

    /**
     * Eligible (ORG-FR-16), apart from the source being enabled, which stays a join condition:
     * shown everywhere, its group not hidden, and its own membership not switched off. A hidden
     * group hides its items whatever their own rules say.
     */
    fun eligible(item: OrgItem): Boolean =
        shownEverywhere(item) &&
            groupRule(item.room, item.sourceId, item.groupKey, item.nameKey).enabled != false &&
            memberRule(item).enabled != false

    /** Eligible and, in a custom list [view], not switched off there (ORG-FR-16). */
    fun enabledIn(item: OrgItem, view: String): Boolean = eligible(item) && memberRule(item, view).enabled != false

    /** The room's default content order (ORG-FR-09, -20). */
    fun roomSort(room: OrgRoom): OrgSort = rule(room, "", "", "")?.sort ?: room.defaultSort

    /** The room's group order (ORG-FR-19); provider order by default. */
    fun groupOrder(room: OrgRoom): OrgSort = rule(room, "", OrgKeys.GROUPS, "")?.sort ?: OrgSort.PROVIDER

    /** A group's own sort, else the room default (ORG-FR-20; a Live legacy order is the caller's). */
    fun itemSort(room: OrgRoom, sourceId: String, groupKey: String, nameKey: String): OrgSort =
        groupRule(room, sourceId, groupKey, nameKey).sort ?: roomSort(room)

    /** A shortcut or a custom list entry (ORG-FR-09): shown unless a rule hides it. */
    fun shortcutShown(room: OrgRoom, key: String): Boolean = rule(room, "", key, "")?.enabled != false

    fun shortcutPosition(room: OrgRoom, key: String): Long? = rule(room, "", key, "")?.position
}
