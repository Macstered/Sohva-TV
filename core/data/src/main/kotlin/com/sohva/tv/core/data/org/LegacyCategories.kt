package com.sohva.tv.core.data.org

import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgRule
import com.sohva.tv.core.model.org.RuleKey

/**
 * Beta 23's hidden categories become rules once (spec 42 ORG-15, ORG-FR-33): each hidden group name
 * a `(room, "", name:<name>, "")` rule switched off, only where no rule has that key yet, plus the
 * marker `(LIVE, "", @legacy-v1, "")` that makes it happen once. Used by the import from an old
 * installation and, in M7, after every backup restore.
 */
object LegacyCategories {
    val MARKER: RuleKey = RuleKey(OrgRoom.LIVE, "", OrgKeys.LEGACY_DONE, "")

    /** The changes for [hidden] group names per room; empty when the marker is already there. */
    fun changes(rules: List<OrgRule>, hidden: Map<OrgRoom, Set<String>>): List<RuleChange> {
        val keys = rules.mapTo(HashSet()) { it.key }
        if (MARKER in keys) return emptyList()
        val hides = hidden.flatMap { (room, names) -> names.filter { it.isNotBlank() }.map { RuleKey(room, "", OrgKeys.nameKey(it), "") } }
            .distinct()
            .filter { it !in keys }
            .map { RuleChange(it, enabled = Field.Set(false)) }
        return hides + RuleChange(MARKER, enabled = Field.Set(true))
    }
}
