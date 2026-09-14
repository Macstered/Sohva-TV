package com.streammate.tv.sports.repository

import com.streammate.tv.core.model.FootballIncidentKind
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEventStatus
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectSportsRepositoryTest {
    private val parser = ApiSportsParser()
    private val zone = ZoneId.of("Europe/Helsinki")

    @Test
    fun footballFixturesAndIncidentsAreNormalizedDirectly() {
        val snapshot = parser.events(
            SportType.FOOTBALL,
            FOOTBALL_PAYLOAD,
            zone,
            "miss",
            "api-sports-football",
            91,
        )

        val event = snapshot.events.single()
        assertEquals("api-sports:football:12001", event.id)
        assertEquals(TodayEventStatus.LIVE, event.status)
        assertEquals("1 – 2", event.score)
        assertEquals("https://media.example/epl.png", event.competitionLogoUrl)
        assertEquals(91, snapshot.quotaRemaining)

        val incidents = parser.footballIncidents(
            INCIDENT_PAYLOAD,
            event.id,
            "miss",
            "api-sports-football",
            90,
        )
        assertEquals(FootballIncidentKind.GOAL, incidents.incidents.single().kind)
        assertEquals("M. De Cuyper", incidents.incidents.single().actorName)
        assertEquals("G. Rutter", incidents.incidents.single().relatedName)
    }

    @Test
    fun hockeyLiigaGameIsNormalizedAndNonHttpsArtworkIsRejected() {
        val event = parser.events(
            SportType.ICE_HOCKEY,
            HOCKEY_PAYLOAD,
            zone,
            "hit",
            "api-sports-hockey",
            83,
        ).events.single()

        assertEquals("api-sports:hockey:8279", event.id)
        assertEquals(TodayEventStatus.LIVE, event.status)
        assertEquals("2 – 1", event.score)
        assertNull(event.awayLogoUrl)
        assertEquals(false, event.detailsAvailable)
    }

    @Test
    fun aflDuplicatesCollapseToFinalRecordWithAflScore() {
        val events = parser.events(
            SportType.AUSTRALIAN_FOOTBALL,
            AFL_PAYLOAD,
            zone,
            "miss",
            "api-sports-afl",
            99,
        ).events

        assertEquals(1, events.size)
        assertEquals(TodayEventStatus.FINISHED, events.single().status)
        assertEquals("95 – 105", events.single().score)
        assertEquals("14.11 – 16.9", events.single().scoreDetail)
    }

    @Test
    fun competitionCataloguesNormalizeNestedAndTopLevelProviderShapes() {
        val football = parser.competitions(SportType.FOOTBALL, FOOTBALL_LEAGUES_PAYLOAD).single()
        val hockey = parser.competitions(SportType.ICE_HOCKEY, HOCKEY_LEAGUES_PAYLOAD).single()

        assertEquals("39", football.id)
        assertEquals("Premier League", football.name)
        assertEquals("England", football.country)
        assertEquals("https://media.example/epl.png", football.logoUrl)
        assertEquals("16", hockey.id)
        assertEquals("Liiga", hockey.name)
        assertEquals("Finland", hockey.country)
    }

    @Test
    fun eventParserUsesCallerSelectedCompetitionIds() {
        val events = parser.events(
            SportType.FOOTBALL,
            FOOTBALL_PAYLOAD,
            zone,
            "hit",
            "api-sports-football",
            90,
            selectedCompetitionIds = setOf("140"),
        ).events

        assertEquals(0, events.size)
    }

    @Test
    fun onlyFootballUsesTheCurrentLeagueCatalogueFilter() {
        assertEquals(mapOf("current" to "true"), SportType.FOOTBALL.competitionQuery)
        assertEquals(emptyMap<String, String>(), SportType.ICE_HOCKEY.competitionQuery)
        assertEquals(emptyMap<String, String>(), SportType.BASKETBALL.competitionQuery)
    }

    @Test
    fun basketballGameWithNestedTotalsIsNormalized() {
        val event = parser.events(
            SportType.BASKETBALL,
            BASKETBALL_PAYLOAD,
            zone,
            "miss",
            "api-sports-basketball",
            98,
            selectedCompetitionIds = setOf("12"),
        ).events.single()

        assertEquals("api-sports:basketball:4401", event.id)
        assertEquals(TodayEventStatus.LIVE, event.status)
        assertEquals("61 – 58", event.score)
        assertEquals("EuroLeague", event.competition)
    }

    @Test
    fun commonTeamSportHalfAndSetStatusCodesAreLive() {
        val handball = parser.events(
            SportType.HANDBALL,
            teamSportPayload("1H"),
            zone,
            "miss",
            "api-sports-handball",
            98,
            selectedCompetitionIds = setOf("55"),
        ).events.single()
        val volleyball = parser.events(
            SportType.VOLLEYBALL,
            teamSportPayload("S2"),
            zone,
            "miss",
            "api-sports-volleyball",
            98,
            selectedCompetitionIds = setOf("55"),
        ).events.single()

        assertEquals(TodayEventStatus.LIVE, handball.status)
        assertEquals(TodayEventStatus.LIVE, volleyball.status)
    }

    private fun teamSportPayload(status: String): String = """
        {"errors":[],"response":[{"id":99,"date":"2026-08-23T20:00:00+03:00",
        "timestamp":1787504400,"status":{"long":"Live","short":"$status"},
        "league":{"id":55,"name":"Test League"},
        "teams":{"home":{"id":1,"name":"Home"},"away":{"id":2,"name":"Away"}},
        "scores":{"home":8,"away":7}}]}
    """.trimIndent()

    private companion object {
        val FOOTBALL_PAYLOAD = """
            {"errors":[],"response":[{"fixture":{"id":12001,
            "date":"2026-08-23T18:30:00+03:00","timestamp":1787499000,
            "status":{"long":"Second Half","short":"2H","elapsed":67}},
            "league":{"id":39,"name":"Premier League","country":"England",
            "logo":"https://media.example/epl.png","season":2026},
            "teams":{"home":{"id":33,"name":"Manchester United","logo":"https://media.example/home.png"},
            "away":{"id":40,"name":"Liverpool","logo":"https://media.example/away.png"}},
            "goals":{"home":1,"away":2},"score":{}}]}
        """.trimIndent()

        val INCIDENT_PAYLOAD = """
            {"errors":[],"response":[{"time":{"elapsed":18,"extra":null},
            "team":{"id":51,"name":"Brighton"},"player":{"id":100,"name":"M. De Cuyper"},
            "assist":{"id":101,"name":"G. Rutter"},"type":"Goal",
            "detail":"Normal Goal","comments":null}]}
        """.trimIndent()

        val HOCKEY_PAYLOAD = """
            {"errors":[],"response":[{"id":8279,"date":"2026-08-23T18:30:00+03:00",
            "timestamp":1787499000,"status":{"long":"Second Period","short":"P2"},
            "country":{"name":"Finland"},"league":{"id":16,"name":"Liiga",
            "logo":"https://media.example/liiga.png","season":2026},
            "teams":{"home":{"id":10,"name":"Tappara","logo":"https://media.example/tappara.png"},
            "away":{"id":11,"name":"Ilves","logo":"http://media.example/ilves.png"}},
            "scores":{"home":2,"away":1},"periods":{"first":"1-0"},"events":true}]}
        """.trimIndent()

        val AFL_PAYLOAD = """
            {"errors":[],"response":[
            {"game":{"id":3546},"date":"2026-08-23T05:20:00+03:00","timestamp":"1787451600",
            "status":{"long":"Not Started","short":"NS"},"league":{"id":1,"season":2026},
            "teams":{"home":{"id":5,"name":"Essendon Bombers","logo":"https://media.example/e.png"},
            "away":{"id":11,"name":"Port Adelaide Power","logo":"https://media.example/p.png"}},
            "scores":{"home":{"score":0,"goals":0,"behinds":0},"away":{"score":0,"goals":0,"behinds":0}}},
            {"game":{"id":3620},"date":"2026-08-23T05:20:00+03:00","timestamp":"1787451600",
            "status":{"long":"Finished","short":"FT"},"league":{"id":1,"season":2026},
            "teams":{"home":{"id":5,"name":"Essendon Bombers","logo":"https://media.example/e.png"},
            "away":{"id":11,"name":"Port Adelaide Power","logo":"https://media.example/p.png"}},
            "scores":{"home":{"score":95,"goals":14,"behinds":11},
            "away":{"score":105,"goals":16,"behinds":9}}}]}
        """.trimIndent()

        val FOOTBALL_LEAGUES_PAYLOAD = """
            {"errors":[],"response":[{"league":{"id":39,"name":"Premier League",
            "type":"League","logo":"https://media.example/epl.png"},
            "country":{"name":"England","code":"GB"}}]}
        """.trimIndent()

        val HOCKEY_LEAGUES_PAYLOAD = """
            {"errors":[],"response":[{"id":16,"name":"Liiga","type":"League",
            "logo":"https://media.example/liiga.png","country":{"name":"Finland"}}]}
        """.trimIndent()

        val BASKETBALL_PAYLOAD = """
            {"errors":[],"response":[{"id":4401,"date":"2026-08-23T20:00:00+03:00",
            "timestamp":1787504400,"status":{"long":"Third Quarter","short":"Q3"},
            "league":{"id":12,"name":"EuroLeague","logo":"https://media.example/euroleague.png"},
            "teams":{"home":{"id":1,"name":"Olympiacos","logo":"https://media.example/o.png"},
            "away":{"id":2,"name":"Real Madrid","logo":"https://media.example/r.png"}},
            "scores":{"home":{"quarter_1":30,"quarter_2":31,"total":61},
            "away":{"quarter_1":27,"quarter_2":31,"total":58}}}]}
        """.trimIndent()
        const val NFL_PAYLOAD = """
        {"errors":[],"response":[{"game":{"id":7701,"stage":"Regular Season","week":"Week 2",
        "date":{"timezone":"Europe/Helsinki","date":"2026-09-14","time":"20:20","timestamp":1789406400},
        "status":{"short":"Q2","long":"Second Quarter","timer":"08:12"}},
        "league":{"id":1,"name":"NFL","season":2026,"logo":"https://media.example/nfl.png"},
        "teams":{"home":{"id":1,"name":"Kansas City Chiefs","logo":"https://media.example/kc.png"},
        "away":{"id":2,"name":"Baltimore Ravens","logo":"https://media.example/bal.png"}},
        "scores":{"home":{"quarter_1":7,"quarter_2":7,"total":14},"away":{"quarter_1":3,"quarter_2":7,"total":10}}}]}
        """
        const val MMA_PAYLOAD = """
        {"errors":[],"response":[{"id":501,"date":"2026-09-14T22:00:00+00:00","time":"22:00","timestamp":1789423200,
        "slug":"a-fighter-vs-b-fighter","is_main":true,"category":"Lightweight",
        "status":{"long":"Not Started","short":"NS"},
        "fighters":{"first":{"id":11,"name":"A. Fighter","logo":"https://media.example/a.png","winner":null},
        "second":{"id":12,"name":"B. Fighter","logo":"https://media.example/b.png","winner":null}}}]}
        """
        const val FORMULA_1_PAYLOAD = """
        {"errors":[],"response":[
        {"id":901,"competition":{"id":7,"name":"Monaco Grand Prix","location":{"country":"Monaco","city":"Monte Carlo"}},
        "circuit":{"id":7,"name":"Circuit de Monaco","image":"https://media.example/monaco.png"},
        "season":2026,"type":"Race","laps":{"current":23,"total":78},"date":"2026-09-14T13:00:00+00:00",
        "timezone":"UTC","status":"Live"},
        {"id":900,"competition":{"id":7,"name":"Monaco Grand Prix","location":{"country":"Monaco","city":"Monte Carlo"}},
        "circuit":{"id":7,"name":"Circuit de Monaco","image":"https://media.example/monaco.png"},
        "season":2026,"type":"Qualifying","laps":{"current":null,"total":null},"date":"2026-09-13T14:00:00+00:00",
        "timezone":"UTC","status":"Completed"}]}
        """
        const val NBA_PAYLOAD = """
        {"errors":[],"response":[
        {"id":13001,"league":"standard","season":2026,"date":{"start":"2026-09-14T23:30:00.000Z","end":null},
        "status":{"clock":"4:12","halftime":false,"short":2,"long":"In Play"},
        "teams":{"home":{"id":2,"name":"Boston Celtics","logo":"https://media.example/bos.png"},
        "visitors":{"id":20,"name":"Miami Heat","logo":"https://media.example/mia.png"}},
        "scores":{"home":{"points":54},"visitors":{"points":49}}},
        {"id":13002,"league":"vegas","season":2026,"date":{"start":"2026-09-14T20:00:00.000Z"},
        "status":{"short":1,"long":"Scheduled"},
        "teams":{"home":{"id":3,"name":"Summer A"},"visitors":{"id":4,"name":"Summer B"}},
        "scores":{"home":{"points":null},"visitors":{"points":null}}}]}
        """
    }

    @Test
    fun nflGameWithNestedGameObjectIsNormalized() {
        val event = parser.events(
            SportType.AMERICAN_FOOTBALL,
            NFL_PAYLOAD,
            zone,
            "miss",
            "api-sports-american-football",
            97,
            selectedCompetitionIds = setOf("1"),
        ).events.single()

        assertEquals("api-sports:american-football:7701", event.id)
        assertEquals(TodayEventStatus.LIVE, event.status)
        assertEquals("14 – 10", event.score)
        assertEquals("NFL", event.competition)
        assertEquals("Kansas City Chiefs", event.home)
        assertEquals(20 * 60 + 20, event.startMinuteOfDay)
    }

    @Test
    fun mmaFightNeedsNoCompetitionSelectionAndCarriesBothFighters() {
        val event = parser.events(
            SportType.MMA,
            MMA_PAYLOAD,
            zone,
            "miss",
            "api-sports-mma",
            97,
            selectedCompetitionIds = emptySet(),
        ).events.single()

        assertEquals("api-sports:mma:501", event.id)
        assertEquals(TodayEventStatus.SCHEDULED, event.status)
        assertEquals("Lightweight", event.competition)
        assertEquals("A. Fighter", event.home)
        assertEquals("B. Fighter", event.away)
        assertNull(event.score)
    }

    @Test
    fun formulaOneSessionUsesWordStatusesAndLapsAsScore() {
        val events = parser.events(
            SportType.FORMULA_1,
            FORMULA_1_PAYLOAD,
            zone,
            "miss",
            "api-sports-formula-1",
            97,
            selectedCompetitionIds = emptySet(),
        ).events

        val race = events.single { it.id == "api-sports:formula-1:901" }
        assertEquals(TodayEventStatus.LIVE, race.status)
        assertEquals("Monaco Grand Prix", race.home)
        assertEquals("Circuit de Monaco", race.away)
        assertEquals("Formula 1 · Race", race.competition)
        assertEquals("23 / 78", race.score)
        val qualifying = events.single { it.id == "api-sports:formula-1:900" }
        assertEquals(TodayEventStatus.FINISHED, qualifying.status)
        assertNull(qualifying.score)
    }

    @Test
    fun nbaGameReadsVisitorsNumericStatusAndSkipsSummerLeague() {
        val events = parser.events(
            SportType.NBA,
            NBA_PAYLOAD,
            zone,
            "miss",
            "api-sports-nba",
            97,
            selectedCompetitionIds = emptySet(),
        ).events

        val game = events.single()
        assertEquals("api-sports:nba:13001", game.id)
        assertEquals(TodayEventStatus.LIVE, game.status)
        assertEquals("Boston Celtics", game.home)
        assertEquals("Miami Heat", game.away)
        assertEquals("54 – 49", game.score)
    }
}
