package com.streammate.tv.core.model

import org.junit.Assert.*
import org.junit.Test

class LibraryOrganizationTest {
    private val room = LibraryRoom.MOVIES
    private fun item(id: String, group: String = "Films", source: String = "one", year: Int? = null) =
        OrganizationItem(id, source, id, group, year = year)
    private fun rule(group: String = "Films", id: String = "", enabled: Boolean? = null, sort: LibrarySort? = null, rank: Long? = null, source: String = "") =
        OrganizationRule(OrganizationKey(room, source, organizationGroupKey(group), id), enabled, sort, rank)

    @Test fun disabledParentWinsWithoutErasingMemberChoices() {
        val child = rule(id = "A", enabled = true)
        assertFalse(LibraryOrganization(listOf(rule(enabled = false), child)).eligible(room, item("A")))
        assertTrue(LibraryOrganization(listOf(rule(enabled = true), child)).eligible(room, item("A")))
    }

    @Test fun localHideDoesNotHideAnotherMembership() {
        val state = LibraryOrganization(listOf(rule(id = "A", enabled = false)))
        assertFalse(state.eligible(room, item("A")))
        assertTrue(state.eligible(room, item("A", group = "Kids")))
    }

    @Test fun sourceOverrideDoesNotAffectOtherProvider() {
        val state = LibraryOrganization(listOf(rule(source = "one", enabled = false)))
        assertFalse(state.eligible(room, item("A")))
        assertTrue(state.eligible(room, item("A", source = "two")))
    }

    @Test fun providerIdentitySurvivesRenameAndExplicitOverrideBeatsLegacyAlias() {
        val keyed = item("A").copy(groupKey = "id:12")
        val state = LibraryOrganization(listOf(rule(enabled = false), OrganizationRule(OrganizationKey(room, "one", "id:12"), enabled = true)))
        assertTrue(state.eligible(room, keyed))
        assertTrue(state.eligible(room, keyed.copy(groupName = "Renamed")))
    }

    @Test fun globalFilmHideFollowsPrimaryCopyAndCannotBeBypassedByGenre() {
        val state = LibraryOrganization(listOf(OrganizationRule(OrganizationKey(room, itemKey = "film"), enabled = false)))
        assertFalse(state.enabledInView(room, item("copy1").copy(identity = "film"), "genre:comedy"))
        assertFalse(state.eligible(room, item("copy2").copy(identity = "film")))
    }

    @Test fun manualOrderRestoresAfterAutomaticAndNewEntriesAppend() {
        val positions = listOf(rule(id = "B", rank = 0), rule(id = "A", rank = 1))
        val entries = listOf(item("A"), item("B"), item("C"))
        fun order(mode: LibrarySort) = LibraryOrganization(positions + rule(sort = mode)).orderedItems(room, entries).map { it.id }
        assertEquals(listOf("B", "A", "C"), order(LibrarySort.MANUAL))
        assertEquals(listOf("A", "B", "C"), order(LibrarySort.TITLE_ASC))
        assertEquals(listOf("B", "A", "C"), order(LibrarySort.MANUAL))
    }

    @Test fun missingYearAndRatingStayLastInBothDirections() {
        val entries = listOf(item("Missing"), item("Old", year = 1990).copy(rating = "6,5/10"), item("New", year = 2020).copy(rating = "8.2"))
        fun order(mode: LibrarySort) = LibraryOrganization(listOf(rule(sort = mode))).orderedItems(room, entries).map { it.id }
        assertEquals(listOf("New", "Old", "Missing"), order(LibrarySort.NEWEST))
        assertEquals(listOf("Old", "New", "Missing"), order(LibrarySort.OLDEST))
        assertEquals(listOf("New", "Old", "Missing"), order(LibrarySort.RATING))
    }

    @Test fun libraryDefaultDoesNotReplaceExplicitGroupSort() {
        val state = LibraryOrganization(listOf(OrganizationRule(OrganizationKey(room), sort = LibrarySort.TITLE_DESC), rule(sort = LibrarySort.TITLE_ASC)))
        assertEquals(listOf("A", "B"), state.orderedItems(room, listOf(item("B"), item("A"))).map { it.id })
        assertEquals(listOf("B", "A"), state.orderedItems(room, listOf(item("B", "Kids"), item("A", "Kids"))).map { it.id })
    }

    @Test fun chronologyAndManagementKeepOriginalIdentityWhenHidden() {
        val state = LibraryOrganization(listOf(rule(id = "B", enabled = false)))
        val entries = listOf(item("B"), item("A"))
        assertEquals(listOf("A"), state.orderedItems(room, entries, chronological = true).map { it.id })
        assertEquals(2, state.orderedItems(room, entries, includeDisabled = true).size)
    }

    @Test fun legacyLiveOrderKeepsRankedBeforeUnrankedUntilExplicitSort() {
        val entries = listOf(item("A"), item("B").copy(legacyPosition = 5), item("C").copy(legacyPosition = 0))
        assertEquals(listOf("C", "B", "A"), LibraryOrganization().orderedItems(LibraryRoom.LIVE, entries).map { it.id })
        val explicit = OrganizationRule(OrganizationKey(LibraryRoom.LIVE), sort = LibrarySort.TITLE_ASC)
        assertEquals(listOf("A", "B", "C"), LibraryOrganization(listOf(explicit)).orderedItems(LibraryRoom.LIVE, entries).map { it.id })
    }

    @Test fun disabledSourceCannotBeEnabledByAMemberOverride() {
        val entry = item("A").copy(sourceEnabled = false)
        assertFalse(LibraryOrganization(listOf(rule(id = "A", enabled = true))).eligible(room, entry))
    }

    @Test fun customListHideAndOrderStayLocal() {
        val entries = listOf(item("A"), item("B"), item("C"))
        val state = LibraryOrganization(listOf(
            OrganizationRule(OrganizationKey(room, groupKey = "@list:test"), sort = LibrarySort.MANUAL),
            OrganizationRule(OrganizationKey(room, groupKey = "@list:test", itemKey = "A"), enabled = false),
            OrganizationRule(OrganizationKey(room, groupKey = "@list:test", itemKey = "C"), position = 0),
        ))
        assertEquals(listOf("C", "B"), state.orderedItems(room, entries, viewKey = "@list:test").map { it.id })
        assertEquals(listOf("A", "B", "C"), state.orderedItems(room, entries).map { it.id })
    }

    @Test fun movingFirstLastAndMissingIsSafe() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), movedOrganizationIds(ids, "a", 99))
        assertEquals(listOf("c", "a", "b"), movedOrganizationIds(ids, "c", -1))
        assertEquals(ids, movedOrganizationIds(ids, "missing", 1))
    }
}
