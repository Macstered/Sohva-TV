package com.sohva.tv.core.model.org

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 42 §11 "Equivalence": the memoised resolver, with its unnamed-item shortcut, answers exactly
 * as a plain reading of §4.3 over random rule sets of every shape.
 */
class OrgEquivalenceTest {
    /** §4.3 read literally: no memo, no shortcut, a linear search per lookup. */
    private class Reference(private val rules: List<OrgRule>) {
        private fun rule(room: OrgRoom, s: String, g: String, i: String) = rules.lastOrNull { it.key == RuleKey(room, s, g, i) }?.value

        private fun first(values: List<RuleValue?>) = values.fold(RuleValue.NONE) { acc, v -> acc.orElse(v) }

        fun group(item: OrgItem) = first(
            listOf(item.sourceId to item.groupKey, item.sourceId to item.nameKey, "" to item.groupKey, "" to item.nameKey).distinct()
                .map { (s, g) -> rule(item.room, s, g, "") },
        )

        fun member(item: OrgItem): RuleValue {
            val out = ArrayList<RuleValue?>()
            for (s in listOf(item.sourceId, "").distinct()) for (g in listOf(item.groupKey, item.nameKey).distinct()) {
                for (id in listOfNotNull(item.identity, item.id).distinct()) out += rule(item.room, s, g, id)
            }
            return first(out)
        }

        fun shown(item: OrgItem): Boolean {
            for (s in listOf(item.sourceId, "").distinct()) for (id in listOfNotNull(item.identity, item.id).distinct()) {
                rule(item.room, s, "", id)?.enabled?.let { return it }
            }
            return !item.legacyHidden
        }

        fun eligible(item: OrgItem) = shown(item) && group(item).enabled != false && member(item).enabled != false
    }

    @Test
    fun theResolverEqualsTheReference() {
        val random = Random(42)
        val sources = listOf("a", "b")
        val groups = listOf("id:1", "id:2", "name:drama", "name:news")
        val ids = listOf("x1", "x2", "work:w1", "work:w2")
        repeat(300) {
            val rules = (0 until random.nextInt(0, 12)).map {
                val item = if (random.nextBoolean()) "" else ids.random(random)
                val group = if (item.isNotEmpty() && random.nextInt(4) == 0) "" else groups.random(random)
                OrgRule(
                    RuleKey(OrgRoom.MOVIES, if (random.nextBoolean()) "" else sources.random(random), group, item),
                    RuleValue(listOf(null, true, false).random(random), listOf(null, OrgSort.RATING, OrgSort.MANUAL).random(random), listOf(null, 1L, 5L).random(random)),
                )
            }.distinctBy { it.key }
            val resolver = OrgResolver(rules)
            val reference = Reference(rules)
            for (source in sources) for (g in groups) for (id in listOf("x1", "x2")) for (identity in listOf(null, "work:w1")) for (hidden in listOf(false, true)) {
                val item = OrgItem(OrgRoom.MOVIES, source, g, if (g.startsWith("name:")) g else "name:drama", id, identity, hidden)
                assertEquals("$rules / $item", reference.eligible(item), resolver.eligible(item))
                assertEquals("$rules / $item", reference.member(item), resolver.memberRule(item))
                assertEquals("$rules / $item", reference.group(item), resolver.groupRule(item.room, item.sourceId, item.groupKey, item.nameKey))
            }
        }
    }
}
