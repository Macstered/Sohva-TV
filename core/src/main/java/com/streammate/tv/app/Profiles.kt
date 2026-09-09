package com.streammate.tv.app

import com.streammate.tv.core.model.LibraryRoom

/** One viewer of this TV: their favourites, recents, watched positions and locked channels are theirs alone. */
data class Profile(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
)

/**
 * The household's profiles. The first viewer is the profile "default", which
 * exists whether or not it has ever been named: its data lives under the
 * preference keys the app always used, so a TV that never adds a second
 * profile changes nothing. Every other profile keeps its data under the same
 * keys suffixed with its id.
 */
object Profiles {
    const val DEFAULT_ID = "default"
    const val MAX_PROFILES = 6
    const val MAX_NAME_LENGTH = 24
    const val COLOR_COUNT = 6

    /** The preference key for [base] as seen by [profileId]; the default profile keeps the bare key. */
    fun keyName(base: String, profileId: String): String =
        if (profileId == DEFAULT_ID) base else "$base:$profileId"

    /**
     * One line per profile, fields split by the ASCII unit separator. Not
     * JSON, so it needs no Android class and a plain JVM test can read it.
     */
    fun encode(profiles: List<Profile>): String = profiles.joinToString(RECORD) { profile ->
        listOf(profile.id, profile.name.replace(RECORD, " ").replace(FIELD, " "), profile.colorIndex.toString()).joinToString(FIELD)
    }

    fun decode(stored: String?): List<Profile> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.split(RECORD).mapNotNull { record ->
            val fields = record.split(FIELD)
            val id = fields.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Profile(id, fields.getOrElse(1) { "" }.take(MAX_NAME_LENGTH), (fields.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, COLOR_COUNT - 1))
        }.distinctBy { it.id }
    }

    private const val RECORD = ""
    private const val FIELD = ""

    fun newId(nowEpochMillis: Long = System.currentTimeMillis()): String = "p$nowEpochMillis"

    /** The list as shown, the default profile first under [defaultName] until someone names it. */
    fun withDefault(profiles: List<Profile>, defaultName: String): List<Profile> =
        if (profiles.any { it.id == DEFAULT_ID }) {
            listOf(profiles.first { it.id == DEFAULT_ID }) + profiles.filter { it.id != DEFAULT_ID }
        } else {
            listOf(Profile(DEFAULT_ID, defaultName)) + profiles
        }

    /** A profile's display name: its own, or [defaultName] for a default profile nobody has named. */
    fun displayName(profiles: List<Profile>, profileId: String, defaultName: String): String =
        profiles.firstOrNull { it.id == profileId }?.name?.takeIf { it.isNotBlank() }
            ?: if (profileId == DEFAULT_ID) defaultName else profileId

    /**
     * Whether choosing [profileId] asks for the parental PIN. As soon as the
     * household has a restricted profile and a PIN, every unrestricted
     * profile sits behind the PIN; otherwise a child would leave the
     * restricted profile with one press and the restriction would be
     * decoration. A restricted profile is always free to enter.
     */
    fun entryNeedsPin(profileId: String, restrictions: Map<String, ProfileRestriction>, pinConfigured: Boolean): Boolean =
        pinConfigured && restrictions.values.any { it.restricted } && restrictions[profileId]?.restricted != true
}

/**
 * What a profile is allowed to see, per room, as organisation group keys; an
 * empty set means everything the device shows. A restriction only narrows
 * the device's own rules, never widens them.
 */
data class ProfileRestriction(
    val live: Set<String> = emptySet(),
    val movies: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
) {
    val restricted: Boolean get() = live.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()

    fun allowed(room: LibraryRoom): Set<String> = when (room) {
        LibraryRoom.LIVE -> live
        LibraryRoom.MOVIES -> movies
        LibraryRoom.SERIES -> series
    }

    /** Whether a group with [groupKey] may be shown in [room]. */
    fun allows(room: LibraryRoom, groupKey: String): Boolean {
        val keys = allowed(room)
        return keys.isEmpty() || groupKey in keys
    }

    fun with(room: LibraryRoom, keys: Set<String>): ProfileRestriction = when (room) {
        LibraryRoom.LIVE -> copy(live = keys)
        LibraryRoom.MOVIES -> copy(movies = keys)
        LibraryRoom.SERIES -> copy(series = keys)
    }

    companion object {
        val NONE = ProfileRestriction()
    }
}

/** What the active profile may see; everything, for a profile nobody restricted. */
val AppPreferences.activeRestriction: ProfileRestriction
    get() = profileRestrictions[activeProfileId] ?: ProfileRestriction.NONE

/** What a profile keeps for itself, as one bundle for backups and removal. */
data class ProfileData(
    val favouriteEventIds: Set<String> = emptySet(),
    val favouriteChannelIds: Set<String> = emptySet(),
    val recentChannelIds: List<String> = emptyList(),
    val lastChannelId: String? = null,
    val lockedChannelIds: Set<String> = emptySet(),
    val restriction: ProfileRestriction = ProfileRestriction.NONE,
)
