package com.sohva.tv.core.model.org

import java.util.Locale

/** The library's three rooms, each with its own rules (spec 42 ORG-01). */
enum class OrgRoom(val wire: String) {
    LIVE("LIVE"), MOVIES("MOVIES"), SERIES("SERIES");

    /** The room's built-in content order (ORG-FR-09): Live in provider order, films and series A–Z. */
    val defaultSort: OrgSort get() = if (this == LIVE) OrgSort.PROVIDER else OrgSort.TITLE_ASC

    companion object {
        fun of(wire: String?): OrgRoom? = entries.firstOrNull { it.wire == wire }
    }
}

/** Orders a group or a room can take (ORG-FR-08). */
enum class OrgSort(val wire: String) {
    PROVIDER("PROVIDER"), TITLE_ASC("TITLE_ASC"), TITLE_DESC("TITLE_DESC"), NEWEST("NEWEST"), OLDEST("OLDEST"), RATING("RATING"), MANUAL("MANUAL");

    companion object {
        fun of(wire: String?): OrgSort? = entries.firstOrNull { it.wire == wire }

        /** The orders a room offers (ORG-07): Live has no release or rating orders. */
        fun offered(room: OrgRoom): List<OrgSort> =
            if (room == OrgRoom.LIVE) listOf(PROVIDER, TITLE_ASC, TITLE_DESC, MANUAL) else listOf(TITLE_ASC, TITLE_DESC, NEWEST, OLDEST, RATING, MANUAL)
    }
}

/**
 * A rule's key (ORG-FR-08): an empty [sourceId] means every source, an empty [itemKey] the group
 * itself, an empty [groupKey] with an item "everywhere".
 */
data class RuleKey(val room: OrgRoom, val sourceId: String, val groupKey: String, val itemKey: String)

/** What a rule sets; null fields have no opinion, so rules combine field by field. */
data class RuleValue(val enabled: Boolean? = null, val sort: OrgSort? = null, val position: Long? = null) {
    val isEmpty: Boolean get() = enabled == null && sort == null && position == null

    /** This value, with the fields it leaves open taken from [later] ("first fields", §4.3). */
    fun orElse(later: RuleValue?): RuleValue = if (later == null) {
        this
    } else {
        RuleValue(enabled ?: later.enabled, sort ?: later.sort, position ?: later.position)
    }

    companion object {
        val NONE: RuleValue = RuleValue()
    }
}

data class OrgRule(val key: RuleKey, val value: RuleValue)

/** The special and derived keys of ORG-FR-05, -09. */
object OrgKeys {
    const val GROUPS: String = "@groups"
    const val HISTORY: String = "@history"
    const val FAVOURITES: String = "@favourites"
    const val RECENT: String = "@recent"
    const val LEGACY_DONE: String = "@legacy-v1"
    private const val LIST = "@list:"

    fun list(listId: String): String = LIST + listId

    /** `name:<trimmed, lower-cased name>`, locale-independent; a missing name gives `name:`. */
    fun nameKey(name: String?): String = "name:" + name.orEmpty().trim().lowercase(Locale.ROOT)

    /** A provider id when there is one, else the name key (ORG-FR-05). */
    fun groupKey(providerId: String?, name: String?): String = providerId?.takeIf { it.isNotBlank() }?.let { "id:$it" } ?: nameKey(name)

    /** A film's identity in rules: its work key (decision "Film identity in rules"). */
    /** A film rule's item key prefix: the film's work key follows. */
    const val WORK_PREFIX: String = "work:"

    fun film(workKey: String): String = "$WORK_PREFIX$workKey"
}

/** An item as the resolver sees it (ORG-FR-06): its room, source, group keys, id and identity. */
data class OrgItem(
    val room: OrgRoom,
    val sourceId: String,
    val groupKey: String,
    val nameKey: String,
    val id: String,
    /** Films: `work:<work key>`; others: null (the id is the identity). */
    val identity: String? = null,
    /** Live: the channel's own hidden flag (channel management, CHAN-FR-27). */
    val legacyHidden: Boolean = false,
)
