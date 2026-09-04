package com.streammate.tv.feature.settings

import com.streammate.tv.core.model.*
import com.streammate.tv.iptv.repository.ManagedLibrary
import com.streammate.tv.iptv.repository.OrganizationReadState
import org.junit.Assert.*
import org.junit.Test

class LibraryManagerModelsTest {
    private val room = LibraryRoom.MOVIES
    private val one = OrganizationItem("one:a", "one", "Film A", "Films", "id:1", identity = "film:a")
    private val two = one.copy(id = "two:a", sourceId = "two", groupKey = "id:2")
    private val another = one.copy(id = "one:b", title = "Film B", identity = "film:b")
    private val group = ManagedGroup("name:films", "Films", listOf(one, two, another))

    @Test fun duplicateFilmIsOneRowAndToggleTargetsAllCopiesOnlyInThisGroup() {
        assertEquals(listOf("film:a", "film:b"), group.orderedItems(room, LibraryOrganization()).map { it.identity })
        val changes = itemVisibilityChanges(room, group, listOf(one), false)
        assertEquals(2, changes.size)
        assertEquals(setOf("one", "two"), changes.map { it.key.sourceId }.toSet())
        assertTrue(changes.all { it.key.itemKey == "film:a" && it.changeEnabled && it.enabled == false })
    }

    @Test fun sourceFilteredGroupActionsDoNotTargetOtherSources() {
        val filtered = managedGroups(room, ManagedLibrary(listOf(one, two)), "one").last()
        val keys = groupVisibilityChanges(room, listOf(filtered), "one", false).map { it.key }
        assertTrue(keys.all { it.sourceId == "one" })
    }

    @Test fun manualMoveChangesRanksAndSortOnlyNotVisibility() {
        val changes = itemRankChanges(room, group, listOf("film:b", "film:a"), null)
        assertTrue(changes.none { it.changeEnabled })
        assertEquals(0L, changes.first { it.key.itemKey == "film:b" }.position)
        assertEquals(2, changes.count { it.key.itemKey == "film:a" && it.position == 1L })
    }

    @Test fun disabledGroupsRemainManageableAndRestoreKeepsDisabledMembers() {
        val rule = OrganizationRule(OrganizationKey(room, "one", "id:1", "film:b"), enabled = false)
        val hidden = OrganizationRule(OrganizationKey(room, "one", "id:1"), enabled = false)
        val state = LibraryOrganization(listOf(rule, hidden))
        val library = ManagedLibrary(listOf(one, another), state = OrganizationReadState(state))
        val groups = managedGroups(room, library, null)
        assertEquals(2, groups.last().items.size)
        assertFalse(groups.last().enabled(room, state))
        assertEquals(1, groupVisibilityChanges(room, listOf(groups.last()), "one", true).count { it.key.groupKey == "id:1" })
    }
}
