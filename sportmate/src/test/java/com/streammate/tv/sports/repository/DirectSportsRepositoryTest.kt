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
    }
}
