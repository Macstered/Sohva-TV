package com.streammate.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [LibraryOrganization] decides what is hidden and in what order everything
 * is shown, so the shortcuts it takes for a source of fifty thousand channels
 * are held to [ReferenceLibraryOrganization], which takes none, over rules and
 * items of every shape: every answer either can give must be the same answer.
 */
class LibraryOrganizationEquivalenceTest {
    @Test
    fun `every answer is the one the rules gave before the shortcuts`() {
        var shortcutsTaken = 0
        var looked = 0
        var hidden = 0
        repeat(ROUNDS) { round ->
            val random = Random(round)
            val room = if (round % 3 == 0) LibraryRoom.MOVIES else LibraryRoom.LIVE
            val items = items(random, room)
            val rules = rules(random, room, items, namesItems = round % 4 != 0)
            val quick = LibraryOrganization(rules)
            val reference = ReferenceLibraryOrganization(rules)
            val named = rules.filter { it.key.room == room }.map { it.key.itemKey }.toSet()
            for (viewKey in listOf(null, "@list:mine")) {
                items.forEach { item ->
                    val context = "round $round, view $viewKey, $item, $rules"
                    assertEquals(context, reference.memberRule(room, item, viewKey), quick.memberRule(room, item, viewKey))
                    assertEquals(context, reference.enabledInView(room, item, viewKey), quick.enabledInView(room, item, viewKey))
                    assertEquals(context, reference.itemSort(room, item, viewKey), quick.itemSort(room, item, viewKey))
                }
                for (chronological in listOf(false, true)) {
                    assertEquals(
                        "round $round, view $viewKey, $rules",
                        reference.orderedItems(room, items, viewKey, chronological = chronological),
                        quick.orderedItems(room, items, viewKey, chronological = chronological),
                    )
                }
                assertEquals(reference.orderedItems(room, items, viewKey, includeDisabled = true), quick.orderedItems(room, items, viewKey, includeDisabled = true))
            }
            items.forEach { item ->
                val context = "round $round, $item, $rules"
                assertEquals(context, reference.groupRule(room, item), quick.groupRule(room, item))
                // Asked twice: the second answer is the remembered one.
                assertEquals(context, reference.groupRule(room, item), quick.groupRule(room, item))
                assertEquals(context, reference.globallyEnabled(room, item), quick.globallyEnabled(room, item))
                assertEquals(context, reference.eligible(room, item), quick.eligible(room, item))
                if (item.id in named || item.identity in named) looked++ else shortcutsTaken++
                if (!reference.eligible(room, item)) hidden++
            }
            val groups = items.groupBy { it.groupName.orEmpty() }.entries.map { it.key to it.value }
            assertEquals(reference.orderedGroups(room, groups), quick.orderedGroups(room, groups))
        }
        // Both roads were travelled, and the rules did hide things along each.
        assertTrue("only $shortcutsTaken items took the shortcut", shortcutsTaken > ROUNDS * 3)
        assertTrue("only $looked items were looked up", looked > ROUNDS)
        assertTrue("only $hidden items were hidden", hidden > ROUNDS)
    }

    private fun items(random: Random, room: LibraryRoom): List<OrganizationItem> = buildList {
        val groups = listOf("News" to "id:1", "News" to "name:news", "Sport" to "id:2", null to "name:", "Kids" to "name:kids")
        repeat(random.nextInt(6, 16)) { index ->
            val (groupName, groupKey) = groups.random(random)
            val id = "item:$index"
            add(
                OrganizationItem(
                    id = id,
                    sourceId = listOf("a", "b").random(random),
                    title = listOf("Alpha", "beta", "Ärrä", "Zulu", "alpha").random(random) + " ${random.nextInt(4)}",
                    groupName = groupName,
                    groupKey = groupKey,
                    year = listOf(null, 1999, 2024).random(random),
                    rating = listOf(null, "7.5", "8,1", "n/a").random(random),
                    providerOrder = random.nextInt(5),
                    // A film is known by the work it is a copy of; two copies share one.
                    identity = if (room == LibraryRoom.MOVIES && random.nextBoolean()) "work:${index % 3}" else id,
                    legacyHidden = random.nextInt(5) == 0,
                    legacyPosition = if (room == LibraryRoom.LIVE && random.nextInt(4) == 0) random.nextLong(10) else null,
                    sourceEnabled = random.nextInt(8) != 0,
                ),
            )
        }
        // What the rail asks a group's rule with: an item that is no item.
        add(OrganizationItem("", "a", "News", "News", "id:1"))
    }

    private fun rules(random: Random, room: LibraryRoom, items: List<OrganizationItem>, namesItems: Boolean): List<OrganizationRule> {
        val rooms = listOf(room, room, room, if (room == LibraryRoom.LIVE) LibraryRoom.MOVIES else LibraryRoom.LIVE)
        val groupKeys = items.flatMap { listOf(it.groupKey, organizationGroupKey(it.groupName)) }.distinct() + listOf("@list:mine", "@list:other")
        val itemKeys = items.flatMap { listOf(it.id, it.identity) }.distinct() + "item:absent"
        val rules = buildList {
            repeat(random.nextInt(0, 18)) {
                val key = when (if (namesItems) random.nextInt(5) else random.nextInt(2)) {
                    0 -> OrganizationKey(rooms.random(random), groupKey = if (random.nextBoolean()) "" else ORGANIZATION_GROUP_ORDER)
                    1 -> OrganizationKey(rooms.random(random), listOf("a", "b", "").random(random), groupKeys.random(random))
                    2 -> OrganizationKey(rooms.random(random), listOf("a", "b", "").random(random), itemKey = itemKeys.random(random))
                    else -> OrganizationKey(rooms.random(random), listOf("a", "b", "").random(random), groupKeys.random(random), itemKeys.random(random))
                }
                add(
                    OrganizationRule(
                        key,
                        enabled = listOf(null, true, false).random(random),
                        sort = listOf(null, null, LibrarySort.MANUAL, LibrarySort.TITLE_ASC, LibrarySort.TITLE_DESC, LibrarySort.PROVIDER, LibrarySort.NEWEST, LibrarySort.RATING).random(random),
                        position = if (random.nextBoolean()) random.nextLong(10) else null,
                    ),
                )
            }
        }
        return rules.distinctBy { it.key }
    }

    private companion object {
        const val ROUNDS = 600
    }
}
