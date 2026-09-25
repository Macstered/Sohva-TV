package com.sohva.tv.core.model.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 42 §11 "Resolver": the rule precedence of §4.3. */
class OrgResolverTest {
    private val movies = OrgRoom.MOVIES

    private fun rule(source: String, group: String, item: String, enabled: Boolean? = null, sort: OrgSort? = null, position: Long? = null) =
        OrgRule(RuleKey(movies, source, group, item), RuleValue(enabled, sort, position))

    private fun film(source: String = "a", group: String = "id:7", name: String = "name:drama", id: String = "vod:movie:$source:1", work: String? = "work:tmdb:9") =
        OrgItem(movies, source, group, name, id, work)

    @Test
    fun aHiddenGroupWinsWithoutErasingTheMembersOwnChoices() {
        val hide = rule("", "name:drama", "", enabled = false)
        val member = rule("a", "id:7", "work:tmdb:9", enabled = true, position = 3)
        val resolver = OrgResolver(listOf(hide, member))
        assertFalse(resolver.eligible(film()))
        // Showing the group again brings the member's own choices back.
        val shown = OrgResolver(listOf(member))
        assertTrue(shown.eligible(film()))
        assertEquals(3L, shown.memberRule(film()).position)
    }

    @Test
    fun aSourceRuleComesBeforeTheCombinedOneAndTouchesNoOtherSource() {
        val resolver = OrgResolver(listOf(rule("a", "id:7", "", enabled = false), rule("", "name:drama", "", enabled = true, sort = OrgSort.RATING)))
        assertEquals(RuleValue(false, OrgSort.RATING, null), resolver.groupRule(movies, "a", "id:7", "name:drama"))
        assertTrue(resolver.eligible(film(source = "b", group = "id:7")))
    }

    @Test
    fun aNameRuleReachesAnIdKeyedGroupOfThatName() {
        val resolver = OrgResolver(listOf(rule("", "name:drama", "", enabled = false)))
        assertFalse(resolver.eligible(film(group = "id:42")))
    }

    @Test
    fun hideEverywhereFollowsTheFilmsIdentityIntoEveryGroup() {
        val resolver = OrgResolver(listOf(rule("", "", "work:tmdb:9", enabled = false)))
        assertFalse(resolver.eligible(film(group = "id:1", name = "name:new")))
        assertFalse(resolver.eligible(film(source = "b", id = "vod:movie:b:5")))
        assertTrue(resolver.eligible(film(work = "work:tmdb:10")))
        // A member rule cannot bring it back.
        val both = OrgResolver(listOf(rule("", "", "work:tmdb:9", enabled = false), rule("a", "id:7", "work:tmdb:9", enabled = true)))
        assertFalse(both.eligible(film()))
    }

    @Test
    fun aLocalHideLeavesTheItemsOtherGroupsAlone() {
        val resolver = OrgResolver(listOf(rule("a", "id:7", "work:tmdb:9", enabled = false)))
        assertFalse(resolver.eligible(film()))
        assertTrue(resolver.eligible(film(group = "id:8", name = "name:crime")))
    }

    @Test
    fun theIdentityComesBeforeTheId() {
        val resolver = OrgResolver(listOf(rule("a", "id:7", "work:tmdb:9", position = 1), rule("a", "id:7", "vod:movie:a:1", position = 5, enabled = false)))
        assertEquals(RuleValue(false, null, 1), resolver.memberRule(film()))
    }

    @Test
    fun aChannelsOwnHiddenFlagHidesItUntilARuleNamesIt() {
        val channel = OrgItem(OrgRoom.LIVE, "a", "name:news", "name:news", "a:c1", legacyHidden = true)
        assertFalse(OrgResolver(emptyList()).eligible(channel))
        val shown = OrgResolver(listOf(OrgRule(RuleKey(OrgRoom.LIVE, "", "", "a:c1"), RuleValue(enabled = true))))
        assertTrue(shown.eligible(channel))
    }

    @Test
    fun customListRulesStayInTheList() {
        val channel = OrgItem(OrgRoom.LIVE, "a", "name:news", "name:news", "a:c1")
        val list = OrgKeys.list("l1")
        val resolver = OrgResolver(listOf(OrgRule(RuleKey(OrgRoom.LIVE, "", list, "a:c1"), RuleValue(enabled = false))))
        assertTrue(resolver.eligible(channel))
        assertFalse(resolver.enabledIn(channel, list))
    }

    @Test
    fun sortsFallBackFromGroupToRoomToTheBuiltInDefault() {
        assertEquals(OrgSort.TITLE_ASC, OrgResolver(emptyList()).itemSort(movies, "a", "id:7", "name:drama"))
        assertEquals(OrgSort.PROVIDER, OrgResolver(emptyList()).itemSort(OrgRoom.LIVE, "a", "id:7", "name:drama"))
        val room = rule("", "", "", sort = OrgSort.NEWEST)
        assertEquals(OrgSort.NEWEST, OrgResolver(listOf(room)).itemSort(movies, "a", "id:7", "name:drama"))
        // A room default does not replace an explicit group sort.
        assertEquals(OrgSort.RATING, OrgResolver(listOf(room, rule("", "name:drama", "", sort = OrgSort.RATING))).itemSort(movies, "a", "id:7", "name:drama"))
        assertEquals(OrgSort.MANUAL, OrgResolver(listOf(rule("", OrgKeys.GROUPS, "", sort = OrgSort.MANUAL))).groupOrder(movies))
    }

    @Test
    fun shortcutsAreShownUntilARuleHidesThem() {
        assertTrue(OrgResolver(emptyList()).shortcutShown(movies, OrgKeys.HISTORY))
        val resolver = OrgResolver(listOf(rule("", OrgKeys.HISTORY, "", enabled = false, position = 4)))
        assertFalse(resolver.shortcutShown(movies, OrgKeys.HISTORY))
        assertEquals(4L, resolver.shortcutPosition(movies, OrgKeys.HISTORY))
    }

    @Test
    fun keysAreFormedAsTheSpecSays() {
        assertEquals("name:uutiset ja sää", OrgKeys.nameKey("  Uutiset ja SÄÄ "))
        assertEquals("name:", OrgKeys.nameKey(null))
        assertEquals("id:12", OrgKeys.groupKey("12", "News"))
        assertEquals("name:news", OrgKeys.groupKey(" ", "News"))
    }
}
