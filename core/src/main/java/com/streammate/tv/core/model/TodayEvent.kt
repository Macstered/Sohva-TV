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
    val matchingChannels: Int,
    val detailsAvailable: Boolean = false,
    val isFavourite: Boolean = false,
)
