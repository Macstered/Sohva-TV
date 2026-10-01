package com.sohva.tv.core.model

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Candidate
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.core.model.sport.pairing.StreamMatcher
import com.sohva.tv.core.model.sport.pairing.TeamVariants
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Short names are club identities, not guesses from a city's name or a substring. */
class TeamAliasMatchingTest {
    private val start = Instant.parse("2026-09-30T15:30:00Z").toEpochMilli()

    private fun matches(
        sport: SportType,
        home: String,
        away: String,
        title: String,
        source: MatchSource = MatchSource.GUIDE,
        extra: Map<String, Set<String>> = emptyMap(),
    ): List<StreamMatch> {
        val event = SportEvent("game", sport, "league", "Fixture", null, Side(home, null), Side(away, null), start, 0,
            EventStatus.SCHEDULED, null, null, null)
        val candidate = Candidate("channel", title, "programme", title, null, null, start, source)
        return StreamMatcher(listOf(event), TeamVariants.aliases(extra), emptyMap()).apply { add(candidate) }.finish().getValue("game")
    }

    private fun available(sport: SportType, api: String, provider: String) {
        for ((home, stream) in listOf(api to provider, provider to api)) {
            for (source in MatchSource.entries) {
                val title = "$stream - Fixture Rovers | 30 Sep 18:15 EEST"
                assertEquals("$sport: $home -> $title ($source)", Confidence.AVAILABLE,
                    matches(sport, home, "Fixture Rovers", title, source).single().confidence)
            }
        }
    }

    @Test
    fun reportedFinnishHockeyAliasesWorkInBothDirections() {
        available(SportType.ICE_HOCKEY, "IFK Helsinki", "HIFK")
        available(SportType.ICE_HOCKEY, "Helsinki IFK", "HIFK")
        available(SportType.ICE_HOCKEY, "Hameenlinna", "HPK")
        available(SportType.ICE_HOCKEY, "Hämeenlinna", "HPK")
        available(SportType.ICE_HOCKEY, "Vaasan Sport", "Sport")
        available(SportType.ICE_HOCKEY, "TPS Turku", "TPS")
    }

    @Test
    fun manUAndExistingFootballAliasesWorkInBothDirections() {
        available(SportType.FOOTBALL, "Manchester United", "ManU")
        available(SportType.FOOTBALL, "Manchester United", "Man Utd")
        available(SportType.FOOTBALL, "Paris Saint-Germain", "PSG")
        available(SportType.FOOTBALL, "Tottenham Hotspur", "Spurs")
        available(SportType.FOOTBALL, "Bayern München", "Bayern Munich")
    }

    @Test
    fun internationalAliasesAreScopedToTheirSport() {
        available(SportType.NBA, "Los Angeles Lakers", "LA Lakers")
        available(SportType.BASKETBALL, "Los Angeles Clippers", "LA Clippers")
        available(SportType.NBA, "San Antonio Spurs", "Spurs")
        available(SportType.ICE_HOCKEY, "New York Rangers", "NY Rangers")
        available(SportType.ICE_HOCKEY, "New Jersey Devils", "NJ Devils")
        available(SportType.AMERICAN_FOOTBALL, "New England Patriots", "Patriots")
        available(SportType.AMERICAN_FOOTBALL, "New York Jets", "NY Jets")
        assertTrue(matches(SportType.FOOTBALL, "San Antonio Spurs", "Fixture Rovers", "Spurs - Fixture Rovers")
            .none { it.confidence == Confidence.AVAILABLE })
        assertTrue(matches(SportType.ICE_HOCKEY, "Tottenham Hotspur", "Fixture Rovers", "Spurs - Fixture Rovers")
            .none { it.confidence == Confidence.AVAILABLE })
    }

    @Test
    fun joinedWholeClubNamesDoNotNeedAHandWrittenAlias() {
        assertEquals(Confidence.AVAILABLE, matches(SportType.RUGBY, "Northbridge Rovers", "Harbor Lights",
            "NorthbridgeRovers - HarborLights").single().confidence)
        assertTrue(matches(SportType.RUGBY, "Northbridge Rovers", "Harbor Lights", "NorthbridgeRoversX - HarborLights")
            .none { it.confidence == Confidence.AVAILABLE })
    }

    @Test
    fun citiesAndYouthQualifiersDoNotBecomeSeniorClubAliases() {
        for ((sport, home, text) in listOf(Triple(SportType.FOOTBALL, "Manchester United", "Manchester"),
            Triple(SportType.ICE_HOCKEY, "IFK Helsinki U20", "HIFK"), Triple(SportType.ICE_HOCKEY, "TPS Turku Women", "TPS"))) {
            assertTrue(matches(sport, home, "Fixture Rovers", "$text - Fixture Rovers")
                .none { it.confidence == Confidence.AVAILABLE })
        }
    }

    @Test
    fun otherListedClubFormsHaveBidirectionalFixtures() {
        available(SportType.FOOTBALL, "Manchester City", "Man City")
        available(SportType.FOOTBALL, "Inter", "Internazionale")
        available(SportType.ICE_HOCKEY, "Kiekko-Espoo", "K-Espoo")
        available(SportType.ICE_HOCKEY, "Hämeenlinnan Pallokerho", "HPK")
        available(SportType.ICE_HOCKEY, "Vaasa Sport", "Sport")
        available(SportType.ICE_HOCKEY, "Los Angeles Kings", "Kings")
        available(SportType.ICE_HOCKEY, "Toronto Maple Leafs", "Maple Leafs")
        available(SportType.NBA, "New York Knicks", "Knicks")
        available(SportType.AMERICAN_FOOTBALL, "New York Giants", "Giants")
        available(SportType.AMERICAN_FOOTBALL, "Philadelphia Eagles", "Eagles")
    }

    @Test
    fun aSavedExtensionOfTheFullNameAlsoAppliesToItsKnownShortName() {
        val extra = mapOf("TPS Turku" to setOf("Fixture TPS Club"))
        assertEquals(Confidence.AVAILABLE, matches(SportType.ICE_HOCKEY, "TPS", "Fixture Rovers", "Fixture TPS Club - Fixture Rovers", extra = extra).single().confidence)
    }

    @Test
    fun aShortSavedIdentityCanResolveToItsFullNameButCannotMatchAsAShortToken() {
        val extra = mapOf("NB" to setOf("Northbridge FC"))
        assertEquals(Confidence.AVAILABLE, matches(SportType.RUGBY, "NB", "Fixture Rovers", "Northbridge FC - Fixture Rovers", extra = extra).single().confidence)
        assertEquals(Confidence.AVAILABLE, matches(SportType.RUGBY, "Northbridge FC", "Fixture Rovers", "Northbridge FC - Fixture Rovers", extra = extra).single().confidence)
        assertEquals(Confidence.POSSIBLE, matches(SportType.RUGBY, "Northbridge FC", "Fixture Rovers", "NB - Fixture Rovers", extra = extra).single().confidence)
    }

    @Test
    fun aLongerClubPhraseCannotAlsoCountAsItsOpponent() {
        assertEquals(Confidence.POSSIBLE, matches(SportType.FOOTBALL, "Milan", "Inter", "Inter Milan").single().confidence)
        assertEquals(Confidence.AVAILABLE, matches(SportType.FOOTBALL, "Milan", "Inter", "Milan - Inter Milan").single().confidence)
        assertEquals(Confidence.POSSIBLE, matches(SportType.RUGBY, "Northbridge", "Northbridge United", "Northbridge United").single().confidence)
        assertEquals(Confidence.AVAILABLE, matches(SportType.RUGBY, "Northbridge", "Northbridge United", "Northbridge - Northbridge United").single().confidence)
    }

    @Test
    fun aSharedAliasDoesNotProveBothOpponentsEvenWhenRepeated() {
        val extra = mapOf("Alpha United" to setOf("United"), "Beta United" to setOf("United"))
        for (title in listOf("United", "United - United")) {
            assertEquals(Confidence.POSSIBLE, matches(SportType.FOOTBALL, "Alpha United", "Beta United", title, extra = extra).single().confidence)
        }
        assertEquals(Confidence.AVAILABLE, matches(SportType.FOOTBALL, "Alpha United", "Beta United", "Alpha United - Beta United", extra = extra).single().confidence)
    }

    @Test
    fun savedAliasesWorkWhenTheApiUsesAnAliasInsteadOfTheCanonicalName() {
        val extra = mapOf("Northbridge Rovers" to setOf("Northbridge Reds"))
        assertEquals(Confidence.AVAILABLE, matches(SportType.RUGBY, "Northbridge Reds", "Harbor Lights", "Northbridge Rovers - Harbor Lights", extra = extra).single().confidence)
    }
}
