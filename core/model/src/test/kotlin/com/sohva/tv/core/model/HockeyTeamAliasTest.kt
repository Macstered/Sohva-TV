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

/** Owner's screenshot: the event says TPS Turku, but both provider names and guide titles say TPS. */
class HockeyTeamAliasTest {
    private val game = SportEvent(
        "api-sports:hockey:1", SportType.ICE_HOCKEY, "16", "Liiga", null,
        Side("Kiekko-Espoo", null), Side("TPS Turku", null),
        Instant.parse("2026-09-30T15:30:00Z").toEpochMilli(), 18 * 60 + 30,
        EventStatus.FINISHED, "4 – 2", null, null,
    )
    private val aliases = TeamVariants.aliases(emptyMap())

    private fun matches(title: String, source: MatchSource = MatchSource.NAME, offset: Long = -15): List<StreamMatch> {
        val candidate = Candidate("channel", title, "programme", title, null, null, game.startMillis + offset * 60_000, source)
        return StreamMatcher(listOf(game), aliases, emptyMap()).apply { add(candidate) }.finish().getValue(game.id)
    }

    @Test
    fun theReportedShortTpsNameIsAvailableInNamesAndGuideTitles() {
        val ppv = matches("NEXT | KIEKKO-ESPOO - TPS | Wed 30 Sep 18:15 EEST (FI) | 8K EXCLUSIVE | FI: PLAY+ PPV 6").single()
        assertEquals(-15L, ppv.offsetMinutes)
        assertEquals(Confidence.AVAILABLE, ppv.confidence)
        assertEquals(Confidence.AVAILABLE, matches("[MTVFI] (30/9) 18:15 Kiekko-Espoo - TPS").single().confidence)
        assertEquals(Confidence.AVAILABLE, matches("Liiga: Kiekko-Espoo – TPS", MatchSource.GUIDE).single().confidence)
        assertEquals(Confidence.AVAILABLE, matches("Kiekko-Espoo – TPS Turku", MatchSource.GUIDE).single().confidence)
    }

    @Test
    fun anAliasDoesNotBypassDatesOpponentsOrWholeWordChecks() {
        for (title in listOf("Kiekko-Espoo - TPSX", "Kiekko-Espoo - Turku", "Kiekko-Espoo - Ilves", "Ilves - TPS")) {
            assertTrue(title, matches("$title | 30 Sep 18:15 EEST").none { it.confidence == Confidence.AVAILABLE })
        }
        assertEquals(Confidence.POSSIBLE, matches("Kiekko-Espoo - TPS | 29 Sep 18:15 EEST").single().confidence)
        assertEquals(Confidence.POSSIBLE, matches("Kiekko-Espoo - TPS", MatchSource.GUIDE, 45).single().confidence)
        assertTrue(matches("Kiekko-Espoo - TPS", MatchSource.GUIDE, 121).isEmpty())
    }

    @Test
    fun theAliasNeedsItsExactCanonicalNameAndMergesWithSavedAliases() {
        assertEquals(listOf("tps turku", "tps", "turun palloseura"), TeamVariants.of("TPS Turku", aliases, SportType.ICE_HOCKEY))
        assertEquals(listOf("tps turku u20"), TeamVariants.of("TPS Turku U20", aliases, SportType.ICE_HOCKEY))
        assertEquals(listOf("turku"), TeamVariants.of("Turku", aliases, SportType.ICE_HOCKEY))
        val extra = TeamVariants.aliases(mapOf("TPS Turku" to setOf("Turun Palloseura")))
        assertEquals(setOf("tps turku", "tps", "turun palloseura"), TeamVariants.of("TPS Turku", extra, SportType.ICE_HOCKEY).toSet())
    }
}
