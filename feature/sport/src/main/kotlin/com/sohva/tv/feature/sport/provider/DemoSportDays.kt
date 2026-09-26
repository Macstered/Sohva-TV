package com.sohva.tv.feature.sport.provider

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The demo build's games (spec 60 SPORT-57): fictional teams anchored to now — one live, two later,
 * one finished per followed sport — and never a request, so the demo needs no key and no network.
 */
class DemoSportDays(private val clock: Clock) : SportDays {
    override suspend fun savedDay(sport: SportType, date: LocalDate, zone: ZoneId, competitions: Set<String>?): SportDay = day(sport, date, zone, competitions)

    override suspend fun day(sport: SportType, date: LocalDate, zone: ZoneId, competitions: Set<String>?): SportDay {
        val now = clock.wallMillis()
        // The first followed competition, so the games pass the follow filter; sports without competitions take "1".
        val competition = competitions?.minOrNull() ?: "1"
        val teams = TEAMS.getValue(sport.ordinal % TEAMS.size)
        val games = GAMES.mapIndexed { i, (minutesFromNow, status) ->
            val start = (now / MINUTE + minutesFromNow) * MINUTE
            val (home, away) = teams[i]
            SportEvent(
                id = "demo:${sport.provider}:$i", sport = sport, competitionId = competition, competition = "Demo League",
                competitionLogo = null, home = Side(home, null), away = Side(away, null), startMillis = start,
                startMinuteOfDay = Instant.ofEpochMilli(start).atZone(zone).let { it.hour * 60 + it.minute },
                status = status, score = SCORES[i], scoreDetail = null, minute = if (status == EventStatus.LIVE) "52′" else null,
            )
        }
        return SportDay(sport, games, CacheState.HIT, null)
    }

    override suspend fun quotas(): Map<SportType, Int> = emptyMap()

    private companion object {
        const val MINUTE = 60_000L
        val GAMES = listOf(-55L to EventStatus.LIVE, 120L to EventStatus.SCHEDULED, 240L to EventStatus.SCHEDULED, -240L to EventStatus.FINISHED)
        val SCORES = listOf("1 – 0", null, null, "2 – 2")
        val TEAMS = mapOf(
            0 to listOf("Northbridge" to "Harbor City", "Summit United" to "Lumen Rovers", "Pulse Athletic" to "Meridian", "Cobalt Town" to "Ember Park"),
            1 to listOf("Frost Wolves" to "Ember Hawks", "Glacier Bay" to "Northern Lights", "Iron Pines" to "Silver Lake", "Aurora" to "Polar City"),
            2 to listOf("Coast Kites" to "Valley Suns", "Harbor Hawks" to "Ridge Rangers", "Bay Lions" to "Stone Hill", "Delta Swans" to "Red Cape"),
        )
    }
}
