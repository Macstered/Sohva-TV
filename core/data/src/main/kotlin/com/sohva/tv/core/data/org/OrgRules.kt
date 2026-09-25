package com.sohva.tv.core.data.org

import com.sohva.tv.core.data.database.OrganizationRuleEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgRule
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.org.RuleKey
import com.sohva.tv.core.model.org.RuleValue

/**
 * One field of a partial change (spec 42 ORG-FR-10): [Keep] leaves the stored value alone, [Set]
 * replaces it (null clears it).
 */
sealed interface Field<out T> {
    data object Keep : Field<Nothing>

    data class Set<T>(val value: T?) : Field<T>
}

/** A partial change of one rule: only the fields it names are written. */
data class RuleChange(
    val key: RuleKey,
    val enabled: Field<Boolean> = Field.Keep,
    val sort: Field<OrgSort> = Field.Keep,
    val position: Field<Long> = Field.Keep,
)

/**
 * The organisation rules (spec 42 §4.2): read whole (they are few: hundreds to a few thousand,
 * at most 300,000 by validation) and changed in one transaction per action, partial fields only.
 * A change returns the values its keys had before, for Undo (ORG-FR-56). Blocking: callers run it
 * on the database dispatcher.
 */
class OrgRules(private val db: SohvaDatabase) {
    private val dao get() = db.organization()

    fun all(): List<OrgRule> = dao.rules().mapNotNull(::toRule)

    fun of(room: OrgRoom): List<OrgRule> = dao.rulesOf(room.wire).mapNotNull(::toRule)

    /**
     * Applies [changes] (ORG-FR-10, -11): validated first, then written in one transaction; rules
     * left with no opinion are removed, which reads the same as an empty rule. Returns the changes
     * that would put back what was there, or the failure.
     */
    fun change(changes: List<RuleChange>): Outcome<List<RuleChange>> {
        if (changes.isEmpty()) return Outcome.Ok(emptyList())
        if (changes.size > MAX_CHANGES || changes.any { !valid(it) } || changes.map { it.key }.toSet().size != changes.size) {
            return Outcome.Failed(AppError.Unknown)
        }
        return db.runInTransaction<Outcome<List<RuleChange>>> {
            val existing = dao.rules().mapNotNull(::toRule).associate { it.key to it.value }
            val undo = ArrayList<RuleChange>(changes.size)
            val put = ArrayList<OrganizationRuleEntity>()
            val delete = ArrayList<OrganizationRuleEntity>()
            for (c in changes) {
                val before = existing[c.key] ?: RuleValue.NONE
                val after = RuleValue(
                    enabled = pick(c.enabled, before.enabled),
                    sort = pick(c.sort, before.sort),
                    position = pick(c.position, before.position),
                )
                undo += RuleChange(
                    c.key,
                    if (c.enabled is Field.Set) Field.Set(before.enabled) else Field.Keep,
                    if (c.sort is Field.Set) Field.Set(before.sort) else Field.Keep,
                    if (c.position is Field.Set) Field.Set(before.position) else Field.Keep,
                )
                if (after.isEmpty) delete += entity(c.key, after) else put += entity(c.key, after)
            }
            if (existing.size - delete.size + put.count { it.toKey() !in existing } > MAX_RULES) return@runInTransaction Outcome.Failed(AppError.Unknown)
            if (put.isNotEmpty()) dao.putRules(put)
            if (delete.isNotEmpty()) dao.deleteRules(delete)
            Outcome.Ok(undo)
        }
    }

    private fun <T> pick(field: Field<T>, before: T?): T? = when (field) {
        Field.Keep -> before
        is Field.Set -> field.value
    }

    /** ORG-FR-11: keys at most 2,048 characters, positions not negative. */
    private fun valid(c: RuleChange): Boolean {
        val k = c.key
        if (k.sourceId.length > KEY_MAX || k.groupKey.length > KEY_MAX || k.itemKey.length > KEY_MAX) return false
        val position = (c.position as? Field.Set)?.value
        return position == null || position >= 0
    }

    private fun toRule(e: OrganizationRuleEntity): OrgRule? {
        val room = OrgRoom.of(e.room) ?: return null
        return OrgRule(RuleKey(room, e.sourceId, e.groupKey, e.itemKey), RuleValue(e.enabled, OrgSort.of(e.sortMode), e.position))
    }

    private fun entity(k: RuleKey, v: RuleValue) = OrganizationRuleEntity(k.room.wire, k.sourceId, k.groupKey, k.itemKey, v.enabled, v.sort?.wire, v.position)

    private fun OrganizationRuleEntity.toKey(): RuleKey? = OrgRoom.of(room)?.let { RuleKey(it, sourceId, groupKey, itemKey) }

    companion object {
        const val MAX_RULES: Int = 300_000
        const val MAX_CHANGES: Int = 100_000
        private const val KEY_MAX = 2_048
    }
}
