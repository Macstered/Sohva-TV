package com.sohva.tv.core.model.sport

/** One side of a game: a team, a fighter, or for Formula 1 the Grand Prix and the circuit. */
data class Side(val name: String, val logo: String?) {
    /** The mark drawn under the crest (SPORT-FR-50): the first letters of the first two words, "?" if none. */
    val initials: String
        get() = name.split(WHITESPACE).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/**
 * A game of today's feed (spec 60 SPORT-FR-38…40), normalised from the provider. [id] is the
 * stable identity decisions, reminders and favourites use. [startMinuteOfDay] is in the zone the
 * feed was read for. [minute] is football's elapsed minute while live ("67′").
 */
data class SportEvent(
    val id: String,
    val sport: SportType,
    val competitionId: String,
    val competition: String,
    val competitionLogo: String?,
    val home: Side,
    val away: Side,
    val startMillis: Long,
    val startMinuteOfDay: Int,
    val status: EventStatus,
    val score: String?,
    val scoreDetail: String?,
    val minute: String?,
) {
    /** Only football has match events (SPORT-FR-90). */
    val detailsAvailable: Boolean get() = sport == SportType.FOOTBALL

    /** "Home – Away" for reminders, Search and the ticker. */
    val title: String get() = "${home.name} – ${away.name}"
}

/** A competition of the follow menu (SPORT-FR-44); [key] is the stored follow key (SPORT-FR-08). */
data class Competition(val sport: SportType, val id: String, val name: String, val country: String?, val logo: String?) {
    val key: String get() = SportFollows.key(sport, id)
}

/** What the viewer follows (spec 60 §4.1), with beta 23's defaults while nothing was saved (SPORT-FR-09). */
data class SportFollows(val sports: Set<SportType>, val competitions: Set<String>) {
    fun follows(sport: SportType): Boolean = sport in sports

    /**
     * The competition ids to keep for [sport], or null for "every event" (sports without
     * competitions). A sport with competitions and none followed has no feed (SPORT-FR-21).
     */
    fun competitionIds(sport: SportType): Set<String>? =
        if (!sport.hasCompetitions) null else competitions.mapNotNullTo(HashSet()) { it.removePrefix("${sport.name}:").takeIf { id -> id != it } }

    /** The sports that make a feed now: followed, and with a followed competition when they have any. */
    val feeds: List<SportType>
        get() = SportType.entries.filter { it in sports && (!it.hasCompetitions || competitionIds(it)!!.isNotEmpty()) }

    companion object {
        fun key(sport: SportType, id: String): String = "${sport.name}:$id"

        val DEFAULT: SportFollows = SportFollows(
            sports = setOf(SportType.FOOTBALL, SportType.ICE_HOCKEY, SportType.AUSTRALIAN_FOOTBALL),
            competitions = setOf(2, 3, 39, 78, 135, 140, 848).map { key(SportType.FOOTBALL, "$it") }.toSet() +
                key(SportType.ICE_HOCKEY, "16") + key(SportType.AUSTRALIAN_FOOTBALL, "1"),
        )
    }
}
