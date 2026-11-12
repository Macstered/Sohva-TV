package com.sohva.tv.core.model.sport.pairing

import com.sohva.tv.core.model.sport.SportType

/** Only this scan's reviewed spellings; the complete registry stays on disk. */
data class EventNames(
    val homeId: String? = null,
    val awayId: String? = null,
    val competitionId: String? = null,
    val home: Set<String> = emptySet(),
    val away: Set<String> = emptySet(),
    val homeGuards: Set<String> = emptySet(),
    val awayGuards: Set<String> = emptySet(),
    /** Shared, dated memberships only when a legacy fixture has no competition reference/name. */
    val supportingCompetitions: Set<String> = emptySet(),
    val competitionDay: Long? = null,
)

data class DictionaryNames(
    val revision: String = "none",
    val events: Map<String, EventNames> = emptyMap(),
    val competitions: Map<String, Set<String>> = emptyMap(),
    val stored: Map<SportType, Map<String, Set<String>>> = emptyMap(),
) {
    companion object {
        val NONE: DictionaryNames = DictionaryNames()
    }
}
