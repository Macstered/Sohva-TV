package com.sohva.tv.core.model.profile

import com.sohva.tv.core.model.org.OrgRoom

/**
 * One viewer of the household (spec 04 §4.1). [name] is null for the first viewer until it is
 * named, which shows as "Everyone" (PROF-FR-02).
 */
data class Profile(val id: String, val name: String?, val colorIndex: Int) {
    val isDefault: Boolean get() = id == Profiles.DEFAULT_ID

    /** The shown name: its own, else [defaultName] for the first viewer, else its id (PROF-FR-02). */
    fun displayName(defaultName: String): String = name?.takeIf { it.isNotBlank() } ?: if (isDefault) defaultName else id
}

/** The household's profiles as stored: beta 23's keys and encoding (spec 04 §6), so its data reads unchanged. */
object Profiles {
    const val DEFAULT_ID: String = "default"
    const val MAX: Int = 6
    const val NAME_MAX: Int = 24
    const val COLOURS: Int = 6

    private const val RECORD: Char = '\u001E'
    private const val FIELD: Char = '\u001F'

    /**
     * The per-profile form of a preference key (PROF-FR-01): the first viewer keeps the bare key,
     * so a TV that never adds a profile keeps its data where it was.
     */
    fun key(base: String, profileId: String): String = if (profileId == DEFAULT_ID) base else "$base:$profileId"

    fun newId(nowMillis: Long): String = "p$nowMillis"

    fun encode(profiles: List<Profile>): String = profiles.joinToString(RECORD.toString()) { p ->
        listOf(p.id, clean(p.name.orEmpty()), p.colorIndex.coerceIn(0, COLOURS - 1).toString()).joinToString(FIELD.toString())
    }

    /** Blank ids are skipped, names cut to 24, colours clamped, a repeated id kept once (spec 04 §6). */
    fun decode(stored: String?): List<Profile> {
        if (stored.isNullOrEmpty()) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Profile>()
        for (record in stored.split(RECORD)) {
            val fields = record.split(FIELD)
            val id = fields.getOrNull(0)?.trim().orEmpty()
            if (id.isEmpty() || !seen.add(id)) continue
            val name = fields.getOrNull(1)?.take(NAME_MAX)?.takeIf { it.isNotBlank() }
            val colour = fields.getOrNull(2)?.toIntOrNull()?.coerceIn(0, COLOURS - 1) ?: 0
            out += Profile(id, name, colour)
        }
        return out
    }

    /** The list as shown: the first viewer first (named or not), then the others in the order they were added. */
    fun shown(stored: List<Profile>): List<Profile> {
        val first = stored.firstOrNull { it.isDefault } ?: Profile(DEFAULT_ID, null, 0)
        return listOf(first) + stored.filterNot { it.isDefault }
    }

    /** Adds [name] (trimmed, cut to 24) with the next colour, or null when the name is blank or the household is full (PROF-FR-03). */
    fun add(stored: List<Profile>, name: String, id: String): List<Profile>? {
        val shown = shown(stored)
        val clean = clean(name).trim().take(NAME_MAX)
        if (clean.isBlank() || shown.size >= MAX) return null
        return stored + Profile(id, clean, shown.size % COLOURS)
    }

    private fun clean(name: String): String = name.replace(RECORD, ' ').replace(FIELD, ' ')
}

/**
 * What a profile may see (PROF-FR-20): the allowed organisation group keys per room; an empty
 * room means everything in it. It only narrows what the organisation rules show.
 */
data class Restriction(val live: Set<String> = emptySet(), val movies: Set<String> = emptySet(), val series: Set<String> = emptySet()) {
    val restricted: Boolean get() = live.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()

    fun of(room: OrgRoom): Set<String> = when (room) {
        OrgRoom.LIVE -> live
        OrgRoom.MOVIES -> movies
        OrgRoom.SERIES -> series
    }

    fun restricts(room: OrgRoom): Boolean = of(room).isNotEmpty()

    fun allows(room: OrgRoom, groupKey: String?): Boolean = of(room).let { it.isEmpty() || (groupKey != null && groupKey in it) }

    fun with(room: OrgRoom, keys: Set<String>): Restriction = when (room) {
        OrgRoom.LIVE -> copy(live = keys)
        OrgRoom.MOVIES -> copy(movies = keys)
        OrgRoom.SERIES -> copy(series = keys)
    }

    companion object {
        val NONE: Restriction = Restriction()
    }
}

/** Whether a channel may start (spec 01 SHELL-FR-20…22): the profile's groups first, then the lock. */
enum class ChannelAdmission { PLAY, REFUSED, LOCKED }

/** The household PIN's rules (spec 04 §4.4). */
object ParentalPin {
    const val MIN: Int = 4
    const val MAX: Int = 8

    /** 4–8 ASCII digits (PROF-FR-30). */
    fun valid(pin: String): Boolean = pin.length in MIN..MAX && pin.all { it in '0'..'9' }

    /** What a PIN field keeps of typed input: digits only, at most 8. */
    fun cut(input: String): String = input.filter { it in '0'..'9' }.take(MAX)

    /** Compares every character whatever the first difference, so the time taken says nothing (PROF-FR-32). */
    fun same(stored: String?, entered: String): Boolean {
        if (stored == null) return false
        var diff = stored.length xor entered.length
        for (i in 0 until maxOf(stored.length, entered.length)) {
            val a = if (i < stored.length) stored[i].code else 0
            val b = if (i < entered.length) entered[i].code else 0
            diff = diff or (a xor b)
        }
        return diff == 0
    }

    /**
     * Entering [target] asks for the PIN when a PIN exists, some profile is restricted and the
     * target is not (PROF-FR-34 item 2): leaving a restricted profile must not be free.
     */
    fun entryNeedsPin(pinConfigured: Boolean, anyRestricted: Boolean, targetRestricted: Boolean): Boolean =
        pinConfigured && anyRestricted && !targetRestricted
}

/**
 * The household as the preferences hold it (spec 04 §6): the stored profiles, the active one, the
 * ask-at-start switch and the mirror of "a PIN exists". Read once for the first frame.
 */
data class Household(
    val stored: List<Profile> = emptyList(),
    val activeId: String = Profiles.DEFAULT_ID,
    val askAtStart: Boolean = true,
    val pinConfigured: Boolean = false,
) {
    val shown: List<Profile> get() = Profiles.shown(stored)

    /** The active profile; an id no longer in the list falls back to the first viewer. */
    val active: Profile get() = shown.firstOrNull { it.id == activeId } ?: shown.first()

    val several: Boolean get() = shown.size > 1

    /** Asked at start when a profile was ever added and the switch is on (PROF-FR-10). */
    val askNeeded: Boolean get() = stored.any { !it.isDefault } && askAtStart
}
