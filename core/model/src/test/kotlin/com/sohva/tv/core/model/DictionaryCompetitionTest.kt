package com.sohva.tv.core.model

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Candidate
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.DictionaryNames
import com.sohva.tv.core.model.sport.pairing.EventNames
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatcher
import com.sohva.tv.core.model.sport.pairing.StreamOrder
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryCompetitionTest {
    private val kickoff = LocalDate.now(ZoneOffset.UTC).atTime(15, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val game = SportEvent("fixture", SportType.FOOTBALL, "1", "Fixture Cup", null,
        Side("Northbridge", null), Side("Harbor", null), kickoff, 15 * 60 + 30, EventStatus.SCHEDULED, null, null, null)
    private val names = DictionaryNames(events = mapOf(game.id to EventNames(competitionId = "cup")),
        competitions = mapOf("cup" to setOf("fixture cup"), "league" to setOf("fixture league")))

    private fun candidate(id: String, suffix: String, channel: String = id, offset: Long = 0): Candidate = Candidate(
        channel, "Channel $channel", id, "Northbridge - Harbor $suffix", null, null, kickoff + offset * 60_000, MatchSource.GUIDE,
    )

    private fun matches(vararg candidates: Candidate, context: DictionaryNames = names) =
        StreamMatcher(listOf(game), emptyMap(), emptyMap(), context).apply { candidates.forEach(::add) }.finish().getValue(game.id)

    @Test
    fun agreementRanksEquivalentCandidatesWithoutChangingScoresOrConfidence() {
        val result = matches(candidate("a-conflict", "Fixture League"), candidate("b-neutral", "FI DE"), candidate("c-agree", "Fixture Cup"))
        assertEquals(listOf("c-agree", "b-neutral", "a-conflict"), result.map { it.programmeId })
        assertEquals(listOf(1, 0, -1), result.map { it.competitionEvidence })
        assertEquals(setOf(100), result.map { it.score }.toSet())
        assertEquals(setOf(Confidence.AVAILABLE), result.map { it.automatic }.toSet())
        val baseline = matches(candidate("a-conflict", "Fixture League"), candidate("b-neutral", "FI DE"), candidate("c-agree", "Fixture Cup"),
            context = names.copy(competitions = emptyMap()))
        assertEquals(listOf("a-conflict", "b-neutral", "c-agree"), baseline.map { it.programmeId })
    }

    @Test
    fun winnerOnOneChannelUsesContextBeforeStableProgrammeTies() {
        val result = matches(candidate("a-neutral", "", "same"), candidate("z-agree", "Fixture Cup", "same"))
        assertEquals("z-agree", result.single().programmeId)
    }

    @Test
    fun hintsNeverPromoteOneTeamOrAProgrammeOutsideTheGuideWindow() {
        val single = candidate("one", "Fixture Cup").copy(title = "Northbridge Fixture Cup")
        assertEquals(Confidence.POSSIBLE, matches(single).single().automatic)
        assertEquals(emptyList<Any>(), matches(candidate("outside", "Fixture Cup", offset = 121)))
        assertEquals(Confidence.POSSIBLE, matches(candidate("early", "Fixture Cup", offset = -45)).single().automatic)
    }

    @Test
    fun ambiguousCompetitionNamesAreNeutralAndLanguagePriorityStillWins() {
        val ambiguous = names.copy(competitions = mapOf("cup" to setOf("fixture"), "league" to setOf("fixture")))
        assertEquals(0, matches(candidate("ambiguous", "Fixture"), context = ambiguous).single().competitionEvidence)
        val result = matches(candidate("a", "Fixture Cup"), candidate("b", ""))
        val streams = result.map { it.copy(channelName = if (it.channelKey == "a") "DE: Sport" else "FI: Sport") }
        assertEquals(listOf("b", "a"), StreamOrder.present(streams, listOf("FI", "DE")).map { it.channelKey })
    }

    @Test
    fun legacySharedCupAndLeagueMembershipsAreBothEligibleWithoutPromotingOneTeam() {
        val legacy = names.copy(events = mapOf(game.id to EventNames(supportingCompetitions = setOf("cup", "league"))))
        assertEquals(listOf(1, 1), matches(candidate("cup", "Fixture Cup"), candidate("league", "Fixture League"), context = legacy).map { it.competitionEvidence })
        val single = candidate("one", "Fixture Cup").copy(title = "Northbridge Fixture Cup")
        assertEquals(Confidence.POSSIBLE, matches(single, context = legacy).single().automatic)
    }

    @Test
    fun competitionAliasesFromAnotherFixtureDateAreNotEvidence() {
        val dated = names.copy(events = mapOf(game.id to EventNames(competitionId = "cup", competitionDay = 2)),
            competitions = mapOf("cup@1" to setOf("old cup"), "cup@2" to setOf("new cup"), "league@1" to setOf("old league")))
        assertEquals(listOf(1, 0, 0), matches(candidate("a", "New Cup"), candidate("b", "Old Cup"), candidate("c", "Old League"), context = dated).map { it.competitionEvidence })
    }
}
