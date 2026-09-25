package com.sohva.tv.feature.live

import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.live.CustomListRef
import com.sohva.tv.core.data.live.LiveRailRules
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.text.SortNames

/**
 * The guide rail's entries in the Live room's order (spec 20 GUIDE-FR-21…23, spec 42 ORG-FR-19):
 * Favourites, All channels, Recently watched, the custom lists, then the groups, leaving out the
 * shortcuts and lists switched off. Groups follow the group order (provider, A–Z, Z–A, manual);
 * under manual order every entry but All channels (always first) takes its place, and unplaced
 * entries keep their relative order after the placed ones.
 */
internal object GuideRailOrder {
    fun entries(groups: List<LiveGroup>, lists: List<CustomListRef>, rules: LiveRailRules, count: (RailEntry) -> Int?): List<RailItem> {
        val ordered = when (rules.order) {
            OrgSort.TITLE_ASC -> groups.sortedBy { SortNames.of(it.name) }
            OrgSort.TITLE_DESC -> groups.sortedByDescending { SortNames.of(it.name) }
            else -> groups
        }
        val placed = ArrayList<Pair<RailEntry, Long?>>()
        if (rules.favourites.shown) placed += RailEntry.Favourites to rules.favourites.position
        if (rules.recent.shown) placed += RailEntry.Recent to rules.recent.position
        lists.filter { rules.list(it.id).shown }.forEach { placed += RailEntry.CustomList(it.id, it.name) to rules.list(it.id).position }
        ordered.forEach { g -> placed += RailEntry.Group(g) to g.position.takeIf { it != Int.MAX_VALUE }?.toLong() }
        val rest = if (rules.order == OrgSort.MANUAL) placed.sortedBy { it.second ?: Long.MAX_VALUE } else placed
        val entries = buildList {
            // Default order: Favourites, All channels, then the rest (GUIDE-FR-21); All is pinned first under manual order.
            if (rules.order == OrgSort.MANUAL) {
                add(RailEntry.All)
                rest.forEach { add(it.first) }
            } else {
                rest.forEach { (entry, _) ->
                    add(entry)
                    if (entry == RailEntry.Favourites) add(RailEntry.All)
                }
                if (RailEntry.All !in this) add(0, RailEntry.All)
            }
        }
        return entries.map { RailItem(it, count(it)) }
    }
}
