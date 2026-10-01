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
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner's 30 September report: providers' clocks must not hide a dated, exact team pairing. */
class ProviderNameMatchingTest {
    private val game = SportEvent(
        "api-sports:hockey:1", SportType.ICE_HOCKEY, "1", "Liiga", null,
        Side("Tappara", null), Side("KooKoo", null),
        Instant.parse("2026-09-30T15:30:00Z").toEpochMilli(), 18 * 60 + 30,
        EventStatus.SCHEDULED, null, null, null,
    )

    private fun matches(title: String, source: MatchSource = MatchSource.NAME, offset: Long = 0): List<StreamMatch> {
        val candidate = Candidate("channel", title, "programme", title, null, null, game.startMillis + offset * 60_000, source)
        return StreamMatcher(listOf(game), emptyMap(), emptyMap()).apply { add(candidate) }.finish().getValue(game.id)
    }

    @Test
    fun bothReportedTapparaListingsAreAvailable() {
        assertEquals(Confidence.AVAILABLE, matches("[MTVFI] (30/9) 18:15 Tappara - KooKoo").single().confidence)
        val ppv = matches("NEXT | TAPPARA - KOOKOO | Wed 30 Sep 19:15 EEST (FI) | 8K EXCLUSIVE | FI: PLAY+ PPV 6").single()
        assertEquals(45L, ppv.offsetMinutes)
        assertEquals(true, ppv.explicitStart)
        assertEquals(Confidence.AVAILABLE, ppv.confidence)
    }

    @Test
    fun dayFirstDatesDoNotReadTheFollowingClockAsAMonthFirstDate() {
        for (clock in listOf("19:15", "19.15", "9:15", "09.15")) {
            val result = matches("Tappara - KooKoo | Wed 30 Sep $clock EEST").single()
            val expected = if (clock.startsWith("19")) 45L else -555L
            assertEquals(clock, expected, result.offsetMinutes)
            assertEquals(clock, Confidence.AVAILABLE, result.confidence)
        }
    }

    @Test
    fun severalCountryStreamsCanHaveDifferentZonedStartTimes() {
        for (clock in listOf("19:15 EEST", "18:15 CEST", "17:15 CET", "16:15 UTC", "12:15 PM UTC-4", "06:00 GMT", "23:45 EET")) {
            assertEquals(clock, Confidence.AVAILABLE, matches("Tappara - KooKoo | Sep 30 $clock").single().confidence)
        }
        // A clock without a date is weak evidence as well, even when it states a zone.
        assertEquals(Confidence.AVAILABLE, matches("Tappara - KooKoo | 06:00 UTC").single().confidence)
    }

    @Test
    fun wrongInvalidAndAmbiguousDatesRemainPossible() {
        for (date in listOf("Sep 19", "29 Sep", "2025-09-30", "Feb 30", "9/10", "0/9")) {
            for (clock in listOf("19:15 EEST", "18:30")) {
                assertEquals("$date $clock", Confidence.POSSIBLE, matches("Tappara - KooKoo | $date $clock").single().confidence)
            }
        }
    }

    @Test
    fun aWrongOpponentAndOneTeamAreNeverAutomaticallyAvailable() {
        assertTrue(matches("Tappara - Ilves | 30 Sep 19:15 EEST").none { it.confidence == Confidence.AVAILABLE })
        assertTrue(matches("Tappara - Ilves | 30 Sep 19:15").isEmpty())
        assertEquals(Confidence.POSSIBLE, matches("Tappara live | 30 Sep 18:30 EEST").single().confidence)
        assertTrue(matches("Tappara live | 30 Sep 06:00 UTC").isEmpty())
    }

    @Test
    fun actualGuideTimestampsStillNeedToBeCloseToKickOff() {
        assertEquals(Confidence.AVAILABLE, matches("Tappara - KooKoo", MatchSource.GUIDE, 30).single().confidence)
        assertEquals(Confidence.POSSIBLE, matches("Tappara - KooKoo", MatchSource.GUIDE, 45).single().confidence)
        assertTrue(matches("Tappara - KooKoo", MatchSource.GUIDE, 121).isEmpty())
    }

    @Test
    fun countryDatesUseTheStatedZoneAcrossMidnight() {
        val candidate = Candidate(
            "channel", "Tappara - KooKoo", "programme", "Tappara - KooKoo | Oct 1 10:00 UTC+14",
            null, null, 0, MatchSource.NAME,
        )
        // At 15:30 UTC on Sep 30, the event day is already Oct 1 at UTC+14.
        val result = StreamMatcher(listOf(game), emptyMap(), emptyMap()).apply { add(candidate) }.finish().getValue(game.id).single()
        assertEquals(Confidence.AVAILABLE, result.confidence)
        assertEquals(270L, result.offsetMinutes)
    }

    @Test
    fun aCompatibleNameIsNotHiddenByUncertainEvidenceOnTheSameChannel() {
        val datedName = Candidate(
            "channel", "Tappara - KooKoo", "dated", "Tappara - KooKoo | 30 Sep 06:00 UTC",
            null, null, 0, MatchSource.NAME,
        )
        val possible = listOf(
            datedName.copy(programmeId = "ambiguous", title = "Tappara - KooKoo | 9/10 18:30 EEST"),
            datedName.copy(programmeId = "guide", title = "Tappara - KooKoo", source = MatchSource.GUIDE, startMillis = game.startMillis + 45 * 60_000),
        )
        for (other in possible) {
            for (candidates in listOf(listOf(datedName, other), listOf(other, datedName))) {
                val result = StreamMatcher(listOf(game), emptyMap(), emptyMap()).apply { candidates.forEach(::add) }.finish().getValue(game.id).single()
                assertEquals(other.programmeId, "dated", result.programmeId)
                assertEquals(Confidence.AVAILABLE, result.confidence)
            }
        }
    }
}
