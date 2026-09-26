package com.sohva.tv.feature.sport.provider

import com.sohva.tv.core.model.sport.Competition
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Incident
import com.sohva.tv.core.model.sport.IncidentKind
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportStatuses
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import okio.BufferedSource

/**
 * API-Sports answers into the app's model (spec 60 §4.4). Item shapes per sport follow
 * SPORT-FR-40; an item lacking a required field is skipped. Every competition of the day is kept:
 * the followed ones are chosen when the day is read back (§9 rule).
 */
object SportsParsers {
    /** A day listing of [sport], its start minutes in [zone]. */
    fun events(sport: SportType, body: BufferedSource, zone: ZoneId): List<SportEvent> {
        val out = LinkedHashMap<String, SportEvent>()
        read(body) { item ->
            val event = when (sport) {
                SportType.FOOTBALL -> football(item, zone)
                SportType.ICE_HOCKEY -> hockey(item, zone)
                SportType.AUSTRALIAN_FOOTBALL -> afl(item, zone)
                SportType.MMA -> mma(item, zone)
                SportType.FORMULA_1 -> formula1(item, zone)
                SportType.NBA -> nba(item, zone)
                else -> generic(sport, item, zone)
            } ?: return@read
            // AFL lists a game more than once: keep its most advanced record (SPORT-FR-42).
            val kept = out[event.id]
            if (kept == null || event.status.progress > kept.status.progress) out[event.id] = event
        }
        return out.values.toList()
    }

    /** A competition catalogue (SPORT-FR-44): de-duplicated by key, sorted by country then name. */
    fun competitions(sport: SportType, body: BufferedSource): List<Competition> {
        val out = LinkedHashMap<String, Competition>()
        read(body) { item ->
            val league = Fields.obj(item, "league") ?: item
            val id = Fields.long(league, "id")?.takeIf { it > 0 } ?: return@read
            val name = Fields.text(league, "name") ?: return@read
            val country = Fields.text(item, "country", "name") ?: Fields.text(league, "country", "name")
                ?: Fields.text(item, "country") ?: Fields.text(league, "country")
            val competition = Competition(sport, "$id", name, country, Fields.image(league, "logo"))
            out.putIfAbsent(competition.key, competition)
        }
        return out.values.sortedWith(compareBy({ it.country?.lowercase() ?: "" }, { it.name.lowercase() }))
    }

    /** A football game's events (SPORT-FR-92). */
    fun incidents(fixtureId: String, body: BufferedSource): List<Incident> {
        val out = ArrayList<Incident>()
        read(body) { item ->
            val kind = when (Fields.text(item, "type")?.lowercase()) {
                "goal" -> IncidentKind.GOAL
                "card" -> IncidentKind.CARD
                "subst" -> IncidentKind.SUBSTITUTION
                "var" -> IncidentKind.VAR
                else -> IncidentKind.OTHER
            }
            out += Incident(
                id = "api-sports:football:$fixtureId:incident:${out.size}",
                minute = Fields.int(item, "time", "elapsed") ?: 0,
                extra = Fields.int(item, "time", "extra") ?: 0,
                kind = kind,
                detail = Fields.text(item, "detail"),
                comments = Fields.text(item, "comments"),
                team = Fields.text(item, "team", "name"),
                actor = Fields.text(item, "player", "name"),
                related = Fields.text(item, "assist", "name"),
            )
        }
        return out
    }

    private fun read(body: BufferedSource, onItem: (Map<String, Any?>) -> Unit) {
        try {
            JsonReader.of(body).use { ResponseReader.read(it, onItem) }
        } catch (e: SportsException) {
            throw e
        } catch (e: JsonEncodingException) {
            throw SportsException(SportsProblem.INVALID_DATA, cause = e)
        } catch (e: IOException) {
            throw SportsException(SportsProblem.INVALID_DATA, cause = e)
        } catch (e: RuntimeException) {
            throw SportsException(SportsProblem.INVALID_DATA, cause = e)
        }
    }

    private fun football(item: Map<String, Any?>, zone: ZoneId): SportEvent? {
        val fixture = Fields.obj(item, "fixture") ?: return null
        val id = Fields.text(fixture, "id") ?: return null
        val start = Fields.start(fixture) ?: return null
        val status = SportStatuses.of(SportType.FOOTBALL, Fields.text(fixture, "status", "short"))
        val minute = Fields.int(fixture, "status", "elapsed")?.takeIf { status == EventStatus.LIVE }?.let { "$it′" }
        return event(
            "api-sports:football:$id", SportType.FOOTBALL, Fields.obj(item, "league"), side(item, "teams", "home") ?: return null,
            side(item, "teams", "away") ?: return null, start, zone, status,
            score(Fields.int(item, "goals", "home"), Fields.int(item, "goals", "away")), null, minute,
        )
    }

    private fun hockey(item: Map<String, Any?>, zone: ZoneId): SportEvent? {
        val id = Fields.text(item, "id") ?: return null
        val start = Fields.start(item) ?: return null
        return event(
            "api-sports:hockey:$id", SportType.ICE_HOCKEY, Fields.obj(item, "league"), side(item, "teams", "home") ?: return null,
            side(item, "teams", "away") ?: return null, start, zone, SportStatuses.of(SportType.ICE_HOCKEY, Fields.text(item, "status", "short")),
            score(Fields.int(item, "scores", "home"), Fields.int(item, "scores", "away")), null, null,
        )
    }

    /** Basketball, baseball, handball, rugby, volleyball and American football (nested `game`). */
    private fun generic(sport: SportType, item: Map<String, Any?>, zone: ZoneId): SportEvent? {
        val game = Fields.obj(item, "game")
        val id = Fields.text(item, "id") ?: Fields.text(game, "id") ?: return null
        val start = Fields.start(item) ?: Fields.start(game) ?: Fields.start(Fields.obj(game, "date")) ?: return null
        val status = Fields.text(item, "status", "short") ?: Fields.text(game, "status", "short")
        return event(
            "api-sports:${sport.provider}:$id", sport, Fields.obj(item, "league"), side(item, "teams", "home") ?: return null,
            side(item, "teams", "away") ?: return null, start, zone, SportStatuses.of(sport, status),
            score(points(item, "home"), points(item, "away")), null, null,
        )
    }

    /** An int, or an object read as `total`, else `score`, else `points`. */
    private fun points(item: Map<String, Any?>, side: String): Int? =
        Fields.int(item, "scores", side) ?: Fields.obj(item, "scores", side)?.let { Fields.int(it, "total") ?: Fields.int(it, "score") ?: Fields.int(it, "points") }

    private fun afl(item: Map<String, Any?>, zone: ZoneId): SportEvent? {
        val game = Fields.obj(item, "game")
        if ((Fields.long(game, "id") ?: 0) <= 0) return null
        val start = Fields.start(item) ?: Fields.start(game) ?: return null
        val homeId = Fields.text(item, "teams", "home", "id") ?: return null
        val awayId = Fields.text(item, "teams", "away", "id") ?: return null
        val status = SportStatuses.of(SportType.AUSTRALIAN_FOOTBALL, Fields.text(item, "status", "short") ?: Fields.text(game, "status", "short"))
        val shown = status == EventStatus.LIVE || status == EventStatus.FINISHED
        val home = Fields.obj(item, "scores", "home")
        val away = Fields.obj(item, "scores", "away")
        val goals = listOf(Fields.int(home, "goals"), Fields.int(home, "behinds"), Fields.int(away, "goals"), Fields.int(away, "behinds"))
        val detail = if (shown && goals.all { it != null }) "${goals[0]}.${goals[1]} – ${goals[2]}.${goals[3]}" else null
        return event(
            "api-sports:afl:${start.epochSecond}:$homeId:$awayId", SportType.AUSTRALIAN_FOOTBALL, Fields.obj(item, "league"),
            side(item, "teams", "home") ?: return null, side(item, "teams", "away") ?: return null, start, zone, status,
            if (shown) score(Fields.int(home, "score"), Fields.int(away, "score")) else null, detail, null, competitionName = "AFL", noLogo = true,
        )
    }

    private fun mma(item: Map<String, Any?>, zone: ZoneId): SportEvent? {
        val id = Fields.text(item, "id") ?: return null
        val start = Fields.start(item) ?: return null
        val name = listOfNotNull(Fields.text(item, "event"), Fields.text(item, "category")).joinToString(" · ").ifEmpty { "MMA" }
        return SportEvent(
            "api-sports:mma:$id", SportType.MMA, "", name, null, side(item, "fighters", "first") ?: return null, side(item, "fighters", "second") ?: return null,
            start.toEpochMilli(), minuteOfDay(start, zone), SportStatuses.of(SportType.MMA, Fields.text(item, "status", "short") ?: Fields.text(item, "status")),
            null, null, null,
        )
    }

    private fun formula1(item: Map<String, Any?>, zone: ZoneId): SportEvent? {
        val id = Fields.text(item, "id") ?: return null
        val start = Fields.start(item) ?: return null
        val status = SportStatuses.of(SportType.FORMULA_1, Fields.text(item, "status", "short") ?: Fields.text(item, "status"))
        val grandPrix = Side(Fields.text(item, "competition", "name") ?: return null, Fields.image(item, "circuit", "image"))
        val circuit = Side(Fields.text(item, "circuit", "name") ?: Fields.text(item, "competition", "location", "city") ?: "", null)
        val total = Fields.int(item, "laps", "total") ?: 0
        val laps = if (status == EventStatus.LIVE && total > 0) "${Fields.int(item, "laps", "current") ?: 0} / $total" else null
        val name = listOfNotNull("Formula 1", Fields.text(item, "type")).joinToString(" · ")
        return SportEvent(
            "api-sports:formula-1:$id", SportType.FORMULA_1, "", name, null, grandPrix, circuit,
            start.toEpochMilli(), minuteOfDay(start, zone), status, laps, null, null,
        )
    }

    private fun nba(item: Map<String, Any?>, zone: ZoneId): SportEvent? {
        val league = Fields.text(item, "league")
        if (league != null && league != "standard") return null
        val id = Fields.text(item, "id") ?: return null
        val start = Fields.instant(Fields.text(item, "date", "start")) ?: Fields.start(item) ?: return null
        val away = side(item, "teams", "visitors") ?: side(item, "teams", "away") ?: return null
        val home = Fields.int(item, "scores", "home", "points") ?: Fields.int(item, "scores", "home")
        val visitors = Fields.int(item, "scores", "visitors", "points") ?: Fields.int(item, "scores", "visitors")
        return SportEvent(
            "api-sports:nba:$id", SportType.NBA, "", "NBA", null, side(item, "teams", "home") ?: return null, away,
            start.toEpochMilli(), minuteOfDay(start, zone), SportStatuses.of(SportType.NBA, Fields.text(item, "status", "short")),
            score(home, visitors), null, null,
        )
    }

    private fun side(item: Map<String, Any?>, vararg path: String): Side? {
        val o = Fields.obj(item, *path) ?: return null
        return Side(Fields.text(o, "name") ?: return null, Fields.image(o, "logo"))
    }

    private fun event(
        id: String, sport: SportType, league: Map<String, Any?>?, home: Side, away: Side, start: Instant, zone: ZoneId, status: EventStatus,
        score: String?, detail: String?, minute: String?, competitionName: String? = null, noLogo: Boolean = false,
    ): SportEvent? {
        val competitionId = Fields.text(league, "id") ?: return null
        val name = competitionName ?: Fields.text(league, "name") ?: return null
        return SportEvent(
            id, sport, competitionId, name, if (noLogo) null else Fields.image(league, "logo"), home, away,
            start.toEpochMilli(), minuteOfDay(start, zone), status, score, detail, minute,
        )
    }

    /** "2 – 1" with an en dash, when both are known (SPORT-FR-39). */
    private fun score(home: Int?, away: Int?): String? = if (home != null && away != null) "$home – $away" else null

    private fun minuteOfDay(start: Instant, zone: ZoneId): Int = start.atZone(zone).let { it.hour * 60 + it.minute }
}
