package com.sohva.tv.feature.live

import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.live.CustomListRef
import com.sohva.tv.core.data.live.LiveRailRules
import com.sohva.tv.core.data.live.ShortcutRule
import com.sohva.tv.core.model.org.OrgSort
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 20 GUIDE-FR-21, -23 and spec 42 ORG-11: the rail's entries in the Live room's order. */
class GuideRailOrderTest {
    private val news = LiveGroup(1, "name:news", "News", 5, Int.MAX_VALUE)
    private val sport = LiveGroup(2, "name:sport", "Sport", 3, 2048)
    private val kids = LiveGroup(3, "name:kids", "Kids", 2, Int.MAX_VALUE)
    private val list = CustomListRef("l1", "Evening")

    private fun ids(rules: LiveRailRules) = GuideRailOrder.entries(listOf(news, sport, kids), listOf(list), rules) { null }.map { it.entry.id }

    @Test
    fun theDefaultOrderPutsTheShortcutsFirstAndTheGroupsInProviderOrder() {
        assertEquals(listOf("favourites", "all", "recent", RailEntry.CustomList("l1", "Evening").id, RailEntry.Group(news).id, RailEntry.Group(sport).id, RailEntry.Group(kids).id), ids(LiveRailRules()))
    }

    @Test
    fun switchedOffShortcutsLeaveTheRailAndAllChannelsStays() {
        val rules = LiveRailRules(favourites = ShortcutRule(false), recent = ShortcutRule(false), lists = mapOf("l1" to ShortcutRule(false)))
        assertEquals(listOf("all", RailEntry.Group(news).id, RailEntry.Group(sport).id, RailEntry.Group(kids).id), ids(rules))
    }

    @Test
    fun aToZSortsTheGroupsOnly() {
        assertEquals(listOf(RailEntry.Group(kids).id, RailEntry.Group(news).id, RailEntry.Group(sport).id), ids(LiveRailRules(order = OrgSort.TITLE_ASC)).takeLast(3))
    }

    @Test
    fun manualOrderPlacesEveryEntryAfterAllChannels() {
        val rules = LiveRailRules(order = OrgSort.MANUAL, recent = ShortcutRule(true, 1024), lists = mapOf("l1" to ShortcutRule(true, 4096)))
        // Placed: Recent 1024, Sport 2048, the list 4096; then unplaced in their order: Favourites, News, Kids.
        assertEquals(
            listOf("all", "recent", RailEntry.Group(sport).id, RailEntry.CustomList("l1", "Evening").id, "favourites", RailEntry.Group(news).id, RailEntry.Group(kids).id),
            ids(rules),
        )
    }
}
