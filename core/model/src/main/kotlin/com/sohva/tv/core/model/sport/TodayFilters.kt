package com.sohva.tv.core.model.sport

/** A Today tab (SPORT-FR-47). [key] survives leaving for the player and process recreation. */
sealed interface TodayFilter {
    val key: String

    data object All : TodayFilter {
        override val key: String = "all"
    }

    data class OfSport(val sport: SportType) : TodayFilter {
        override val key: String get() = sport.name
    }

    data object Watchable : TodayFilter {
        override val key: String = "watchable"
    }

    data object Favourites : TodayFilter {
        override val key: String = "favourites"
    }

    companion object {
        fun fromKey(key: String?): TodayFilter = when (key) {
            Watchable.key -> Watchable
            Favourites.key -> Favourites
            null, All.key -> All
            else -> SportType.fromStored(key)?.let(::OfSport) ?: All
        }
    }
}

/** A tab with its count of games of any status (SPORT-FR-47). */
data class TodayTab(val filter: TodayFilter, val count: Int)

/** The sections of a filtered list (SPORT-FR-48); every game is in exactly one. */
data class TodaySections(val live: List<SportEvent>, val later: List<SportEvent>, val finished: List<SportEvent>) {
    val isEmpty: Boolean get() = live.isEmpty() && later.isEmpty() && finished.isEmpty()

    /** SPORT-FR-60: the first live game, else the first upcoming, else the first finished. */
    val firstFocus: SportEvent? get() = live.firstOrNull() ?: later.firstOrNull() ?: finished.firstOrNull()
}

/** Today's tabs, filtering and sections (spec 60 §4.5), computed once per list change. */
object TodayLists {
    /** All, each followed sport in the fixed order, Watchable, Favourites. */
    fun tabs(events: List<SportEvent>, follows: SportFollows, watchable: Set<String>, favourites: Set<String>): List<TodayTab> {
        val bySport = events.groupingBy { it.sport }.eachCount()
        return buildList {
            add(TodayTab(TodayFilter.All, events.size))
            SportType.entries.filter { follows.follows(it) }.forEach { add(TodayTab(TodayFilter.OfSport(it), bySport[it] ?: 0)) }
            add(TodayTab(TodayFilter.Watchable, events.count { it.id in watchable }))
            add(TodayTab(TodayFilter.Favourites, events.count { it.id in favourites }))
        }
    }

    /** A sport tab whose sport is no longer followed falls back to All (SPORT-FR-47). */
    fun effective(filter: TodayFilter, follows: SportFollows): TodayFilter =
        if (filter is TodayFilter.OfSport && !follows.follows(filter.sport)) TodayFilter.All else filter

    fun filter(events: List<SportEvent>, filter: TodayFilter, watchable: Set<String>, favourites: Set<String>): List<SportEvent> = when (filter) {
        TodayFilter.All -> events
        is TodayFilter.OfSport -> events.filter { it.sport == filter.sport }
        TodayFilter.Watchable -> events.filter { it.id in watchable }
        TodayFilter.Favourites -> events.filter { it.id in favourites }
    }

    /** Live now; Later today (scheduled, postponed, interrupted, unknown); Finished (finished, cancelled). */
    fun sections(sorted: List<SportEvent>): TodaySections = TodaySections(
        live = sorted.filter { it.status == EventStatus.LIVE },
        later = sorted.filter { it.status in LATER },
        finished = sorted.filter { it.status == EventStatus.FINISHED || it.status == EventStatus.CANCELLED },
    )

    private val LATER = setOf(EventStatus.SCHEDULED, EventStatus.POSTPONED, EventStatus.INTERRUPTED, EventStatus.UNKNOWN)
}
