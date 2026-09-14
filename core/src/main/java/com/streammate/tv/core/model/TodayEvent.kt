package com.streammate.tv.core.model

enum class SportType {
    FOOTBALL,
    ICE_HOCKEY,
    AUSTRALIAN_FOOTBALL,
    BASKETBALL,
    BASEBALL,
    HANDBALL,
    RUGBY,
    VOLLEYBALL,
    AMERICAN_FOOTBALL,
    MMA,
    FORMULA_1,
    NBA,
}

/**
 * Whether the provider organises this sport into leagues and cups a viewer
 * can follow one by one. MMA fight cards, Formula 1 weekends and the NBA's
 * own feed have no such list: every event of the day is shown once the sport
 * is followed.
 */
val SportType.hasCompetitions: Boolean
    get() = when (this) {
        SportType.MMA, SportType.FORMULA_1, SportType.NBA -> false
        else -> true
    }

enum class TodayEventStatus {
    LIVE,
    SCHEDULED,
    FINISHED,
    POSTPONED,
    CANCELLED,
    INTERRUPTED,
    UNKNOWN,
}

data class TodayEvent(
    val id: String,
    val sport: SportType,
    val competitionId: String = "",
    val competition: String,
    val competitionLogoUrl: String? = null,
    val home: String,
    val homeLogoUrl: String? = null,
    val away: String,
    val awayLogoUrl: String? = null,
    val startEpochMillis: Long = 0,
    val startMinuteOfDay: Int,
    val startLabel: String,
    val status: TodayEventStatus,
    val statusLabel: String,
    val score: String?,
    /**
     * A second, sport-specific reading of the score, such as AFL goals and
     * behinds, for screens with room for it. The cards show [score] alone.
     */
    val scoreDetail: String? = null,
    val matchingChannels: Int,
    val detailsAvailable: Boolean = false,
    val isFavourite: Boolean = false,
)
