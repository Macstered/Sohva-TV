package com.streammate.tv.sports.repository

import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import androidx.annotation.StringRes
import com.streammate.tv.core.model.FootballIncident
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.SportsCompetition
import com.streammate.tv.core.model.TodayEvent
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

val DEFAULT_FOOTBALL_COMPETITION_IDS = setOf(
    "2", // UEFA Champions League
    "3", // UEFA Europa League
    "39", // English Premier League
    "78", // Bundesliga
    "135", // Serie A
    "140", // La Liga
    "848", // UEFA Conference League
)

val DEFAULT_AFL_COMPETITION_IDS = setOf("1")
val DEFAULT_HOCKEY_COMPETITION_IDS = setOf("16") // Finnish Liiga

data class SportsEventsSnapshot(
    val events: List<TodayEvent>,
    val cacheState: String,
    val source: String,
    val quotaRemaining: Int?,
)

data class FootballIncidentsSnapshot(
    val incidents: List<FootballIncident>,
    val cacheState: String,
    val source: String,
    val quotaRemaining: Int?,
)

class SportsBackendException(
    @StringRes messageResource: Int,
    messageArguments: List<Any> = emptyList(),
    logMessage: String? = null,
    cause: Throwable? = null,
) : LocalizedException(messageResource, messageArguments, logMessage, cause)

interface SportsRepository {
    suspend fun events(
        sport: SportType,
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String>,
    ): SportsEventsSnapshot = when (sport) {
        SportType.FOOTBALL -> footballEvents(date, zoneId, selectedCompetitionIds)
        SportType.ICE_HOCKEY -> hockeyEvents(date, zoneId, selectedCompetitionIds)
        SportType.AUSTRALIAN_FOOTBALL -> aflEvents(date, zoneId, selectedCompetitionIds)
        SportType.BASKETBALL,
        SportType.BASEBALL,
        SportType.HANDBALL,
        SportType.RUGBY,
        SportType.VOLLEYBALL,
        -> throw SportsBackendException(CoreR.string.error_sport_unsupported_source)
    }
    suspend fun footballEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String> = DEFAULT_FOOTBALL_COMPETITION_IDS,
    ): SportsEventsSnapshot
    suspend fun aflEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String> = DEFAULT_AFL_COMPETITION_IDS,
    ): SportsEventsSnapshot
    suspend fun hockeyEvents(
        date: LocalDate,
        zoneId: ZoneId,
        selectedCompetitionIds: Set<String> = DEFAULT_HOCKEY_COMPETITION_IDS,
    ): SportsEventsSnapshot
    suspend fun footballIncidents(eventId: String): FootballIncidentsSnapshot
    suspend fun competitions(sport: SportType): List<SportsCompetition> = emptyList()
}
