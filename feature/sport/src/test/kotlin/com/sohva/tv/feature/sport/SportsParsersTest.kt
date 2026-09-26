package com.sohva.tv.feature.sport

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.IncidentKind
import com.sohva.tv.core.model.sport.SportStatuses
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsException
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.feature.sport.provider.SportsParsers
import java.time.ZoneId
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * Spec 60 §11 "Unit": one parser per sport on payloads written from the spec's field table
 * (SPORT-FR-40; the owner has no captured payloads yet, open question 5), the status maps,
 * competition catalogues and the `errors` object.
 */
class SportsParsersTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")

    private fun body(json: String) = Buffer().writeUtf8(json)

    private fun events(sport: SportType, response: String) = SportsParsers.events(sport, body("""{"errors":[],"response":[$response]}"""), helsinki)

    @Test
    fun footballFixturesWithMinuteScoreAndLogos() {
        val e = events(
            SportType.FOOTBALL,
            """{"fixture":{"id":1001,"date":"2026-09-26T18:45:00+00:00","timestamp":1790448300,"status":{"short":"2H","elapsed":67}},
               "league":{"id":39,"name":"Premier League","logo":"https://media.example/39.png"},
               "teams":{"home":{"name":"Northbridge FC","logo":"https://media.example/a.png"},"away":{"name":"Harbor Town","logo":"http://media.example/b.png"}},
               "goals":{"home":2,"away":1}},
               {"fixture":{"id":1002},"league":{"id":39,"name":"Premier League"},"teams":{"home":{"name":"A"},"away":{"name":"B"}}}""",
        )
        assertEquals(1, e.size)
        val game = e.single()
        assertEquals("api-sports:football:1001", game.id)
        assertEquals(EventStatus.LIVE, game.status)
        assertEquals("67′", game.minute)
        assertEquals("2 – 1", game.score)
        assertEquals("39", game.competitionId)
        assertEquals(21 * 60 + 45, game.startMinuteOfDay)
        // Only https images are kept (SPORT-FR-37).
        assertNull(game.away.logo)
        assertEquals("https://media.example/a.png", game.home.logo)
    }

    @Test
    fun hockeyBasketballAndAmericanFootballShapes() {
        val hockey = events(SportType.ICE_HOCKEY, """{"id":7,"date":"2026-09-26T16:30:00+00:00","status":{"short":"P2"},"league":{"id":16,"name":"Liiga","logo":"http://x"},"teams":{"home":{"name":"Tappara"},"away":{"name":"Ilves"}},"scores":{"home":1,"away":3}}""").single()
        assertEquals(EventStatus.LIVE, hockey.status)
        assertEquals("1 – 3", hockey.score)
        assertNull(hockey.competitionLogo)
        val basketball = events(SportType.BASKETBALL, """{"id":9,"timestamp":"1790448300","status":{"short":"Q3"},"league":{"id":12,"name":"League"},"teams":{"home":{"name":"A"},"away":{"name":"B"}},"scores":{"home":{"total":71},"away":{"total":64}}}""").single()
        assertEquals("71 – 64", basketball.score)
        assertEquals(EventStatus.LIVE, basketball.status)
        val nfl = events(SportType.AMERICAN_FOOTBALL, """{"game":{"id":55,"date":{"timestamp":1790448300},"status":{"short":"NS"}},"league":{"id":1,"name":"NFL"},"teams":{"home":{"name":"A"},"away":{"name":"B"}},"scores":{"home":{"total":null},"away":{"total":null}}}""").single()
        assertEquals("api-sports:american-football:55", nfl.id)
        assertEquals(EventStatus.SCHEDULED, nfl.status)
        assertNull(nfl.score)
    }

    @Test
    fun aflDuplicatesCollapseToTheFinalRecordWithGoalsAndBehinds() {
        val item = { status: String, home: Int, away: Int ->
            """{"game":{"id":3},"date":"2026-09-26T09:40:00+00:00","status":{"short":"$status"},"league":{"id":1,"name":"ignored"},
               "teams":{"home":{"id":10,"name":"Carlton"},"away":{"id":11,"name":"Essendon"}},
               "scores":{"home":{"score":$home,"goals":14,"behinds":11},"away":{"score":$away,"goals":16,"behinds":9}}}"""
        }
        val e = events(SportType.AUSTRALIAN_FOOTBALL, item("Q4", 90, 100) + "," + item("FT", 95, 105) + "," + item("NS", 0, 0))
        val game = e.single()
        assertEquals(EventStatus.FINISHED, game.status)
        assertEquals("95 – 105", game.score)
        assertEquals("14.11 – 16.9", game.scoreDetail)
        assertEquals("AFL", game.competition)
        assertEquals("api-sports:afl:${java.time.Instant.parse("2026-09-26T09:40:00Z").epochSecond}:10:11", game.id)
    }

    @Test
    fun mmaFormula1AndNba() {
        val mma = events(SportType.MMA, """{"id":4,"date":"2026-09-26T20:00:00+00:00","status":{"short":"NS"},"event":"Fight Night","category":"Lightweight","fighters":{"first":{"name":"R. Stone"},"second":{"name":"K. Vale"}}}""").single()
        assertEquals("Fight Night · Lightweight", mma.competition)
        assertEquals("", mma.competitionId)
        val f1 = events(SportType.FORMULA_1, """{"id":8,"date":"2026-09-26T12:00:00+00:00","type":"Race","status":"Live","competition":{"name":"Fictional Grand Prix","location":{"city":"Harbourtown"}},"circuit":{"name":"Harbour Circuit","image":"https://media.example/c.png"},"laps":{"current":31,"total":58}}""").single()
        assertEquals(EventStatus.LIVE, f1.status)
        assertEquals("31 / 58", f1.score)
        assertEquals("Formula 1 · Race", f1.competition)
        assertEquals("Harbour Circuit", f1.away.name)
        val nba = events(
            SportType.NBA,
            """{"id":20,"league":"standard","date":{"start":"2026-09-26T23:30:00.000Z"},"status":{"short":2},"teams":{"home":{"name":"A"},"visitors":{"name":"B"}},"scores":{"home":{"points":55},"visitors":{"points":50}}},
               {"id":21,"league":"vegas","date":{"start":"2026-09-26T20:00:00.000Z"},"status":{"short":1},"teams":{"home":{"name":"C"},"visitors":{"name":"D"}}}""",
        )
        assertEquals(listOf("api-sports:nba:20"), nba.map { it.id })
        assertEquals(EventStatus.LIVE, nba.single().status)
        assertEquals("55 – 50", nba.single().score)
    }

    @Test
    fun statusMapsFollowTheSpecsTables() {
        assertEquals(EventStatus.POSTPONED, SportStatuses.of(SportType.HANDBALL, "PST"))
        assertEquals(EventStatus.LIVE, SportStatuses.of(SportType.VOLLEYBALL, "SET3"))
        assertEquals(EventStatus.LIVE, SportStatuses.of(SportType.BASEBALL, "IN5"))
        assertEquals(EventStatus.INTERRUPTED, SportStatuses.of(SportType.RUGBY, "INT"))
        assertEquals(EventStatus.CANCELLED, SportStatuses.of(SportType.FOOTBALL, " canc "))
        assertEquals(EventStatus.UNKNOWN, SportStatuses.of(SportType.FOOTBALL, "XYZ"))
        assertEquals(EventStatus.INTERRUPTED, SportStatuses.of(SportType.FORMULA_1, "Red Flag"))
    }

    @Test
    fun competitionCataloguesInBothShapes() {
        val nested = SportsParsers.competitions(
            SportType.FOOTBALL,
            body("""{"response":[{"league":{"id":39,"name":"Premier League","logo":"https://m.example/39.png"},"country":{"name":"England"}},{"league":{"id":2,"name":"UEFA Champions League"},"country":{"name":"World"}},{"league":{"id":39,"name":"dup"}},{"league":{"id":0,"name":"bad"}}]}"""),
        )
        assertEquals(listOf("FOOTBALL:39", "FOOTBALL:2"), nested.map { it.key })
        val flat = SportsParsers.competitions(SportType.ICE_HOCKEY, body("""{"response":[{"id":16,"name":"Liiga","country":{"name":"Finland"}},{"id":57,"name":"NHL","country":"USA"}]}"""))
        assertEquals(listOf("Finland", "USA"), flat.map { it.country })
    }

    @Test
    fun footballIncidentsWithTimeLabels() {
        val list = SportsParsers.incidents(
            "1001",
            body("""{"response":[{"time":{"elapsed":45,"extra":2},"team":{"name":"Northbridge FC"},"player":{"name":"A. Scorer"},"assist":{"name":"B. Helper"},"type":"Goal","detail":"Normal Goal"},{"time":{"elapsed":70},"type":"subst","player":{"name":"C"},"assist":{"name":"D"}},{"time":{"elapsed":80},"type":"weird"}]}"""),
        )
        assertEquals(listOf(IncidentKind.GOAL, IncidentKind.SUBSTITUTION, IncidentKind.OTHER), list.map { it.kind })
        assertEquals("45+2′", list[0].timeLabel)
        assertEquals("70′", list[1].timeLabel)
        assertEquals("api-sports:football:1001:incident:2", list[2].id)
        assertNull(list[2].detail)
    }

    @Test
    fun anErrorsObjectFailsAndASpentQuotaIsNamed() {
        for ((json, problem) in listOf(
            """{"errors":{"token":"Error/Missing application key."},"response":[]}""" to SportsProblem.SERVICE_ERROR,
            """{"errors":{"requests":"You have reached the request limit for the day"},"response":[]}""" to SportsProblem.QUOTA_EXHAUSTED,
            """[1,2]""" to SportsProblem.INVALID_DATA,
            """{"response":[""" to SportsProblem.INVALID_DATA,
        )) {
            try {
                SportsParsers.events(SportType.FOOTBALL, body(json), helsinki)
                fail(json)
            } catch (e: SportsException) {
                assertEquals(json, problem, e.problem)
            }
        }
    }
}
