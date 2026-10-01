package com.sohva.tv.core.model

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Candidate
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.MatchText
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.core.model.sport.pairing.StreamMatcher
import com.sohva.tv.core.model.sport.pairing.TeamVariants
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 60 §11 "Matcher": the 21 cases of beta 23's `EventChannelMatcherTest`, on the rebuild's matcher. */
class StreamMatcherTest {
    private val kickOff = Instant.parse("2026-08-23T18:30:00Z").toEpochMilli()
    private val game = event("Manchester United", "Liverpool", kickOff)

    private fun event(home: String, away: String, start: Long) = SportEvent(
        "api-sports:football:1", SportType.FOOTBALL, "39", "Premier League", null, Side(home, null), Side(away, null),
        start, 21 * 60 + 30, EventStatus.SCHEDULED, null, null, null,
    )

    private fun at(instant: String) = Instant.parse(instant).toEpochMilli()

    private fun guide(title: String, start: Long, channel: String = "channel-1") =
        Candidate(channel, "Sports Channel", "programme-$channel", title, null, null, start, MatchSource.GUIDE)

    private fun name(title: String, channel: String = "channel-1") = Candidate(channel, title, "m3u-name:$channel", title, null, null, 0, MatchSource.NAME)

    private fun match(
        events: List<SportEvent>,
        candidates: List<Candidate>,
        aliases: Map<String, Set<String>> = emptyMap(),
        decisions: Map<Pair<String, String>, Decision> = emptyMap(),
    ): Map<String, List<StreamMatch>> = StreamMatcher(events, aliases, decisions).apply { candidates.forEach(::add) }.finish()

    private fun one(e: SportEvent, c: Candidate): List<StreamMatch> = match(listOf(e), listOf(c)).getValue(e.id)

    @Test
    fun bothTeamsAndAMatchingStartAreAvailableIncludingAliases() {
        val m = match(listOf(game), listOf(guide("Man Utd v Liverpool", kickOff + 10 * 60_000)), mapOf("manchester united" to setOf("man utd"))).getValue(game.id)
        assertEquals(Confidence.AVAILABLE, m.single().confidence)
        assertEquals(100, m.single().score)
    }

    @Test
    fun aSingleTeamIsPossible() {
        assertEquals(Confidence.POSSIBLE, one(game, guide("Manchester United live", kickOff)).single().confidence)
    }

    @Test
    fun decisionsOverrideAndPersistPerChannel() {
        val m = match(
            listOf(game), listOf(guide("Manchester United v Liverpool", kickOff, "strong"), guide("Evening schedule", kickOff, "ambiguous")),
            decisions = mapOf((game.id to "strong") to Decision.REJECTED, (game.id to "ambiguous") to Decision.CONFIRMED),
        ).getValue(game.id)
        assertEquals(listOf("ambiguous" to Confidence.AVAILABLE, "strong" to Confidence.REJECTED), m.map { it.channelKey to it.confidence })
    }

    @Test
    fun onlyTheStrongestProgrammePerChannel() {
        val m = match(listOf(game), listOf(guide("Liverpool preview", kickOff - 30 * 60_000), guide("Manchester United v Liverpool", kickOff))).getValue(game.id)
        assertEquals("Manchester United v Liverpool", m.single().programmeTitle)
        assertEquals(Confidence.AVAILABLE, m.single().confidence)
    }

    @Test
    fun unrelatedOrDistantProgrammesAreExcluded() {
        assertTrue(match(listOf(game), listOf(guide("Evening news", kickOff), guide("Manchester United v Liverpool", kickOff + 121 * 60_000))).getValue(game.id).isEmpty())
    }

    @Test
    fun aNameWithBothTeamsIsAvailableWithoutALocalClock() {
        val m = match(listOf(game), listOf(name("EVENT: Man Utd - Liverpool | 21:30")), mapOf("manchester united" to setOf("man utd"))).getValue(game.id).single()
        assertEquals(Confidence.AVAILABLE, m.confidence)
        assertEquals(MatchSource.NAME, m.source)
        assertEquals(false, m.explicitStart)
        assertEquals(0L, m.offsetMinutes)
    }

    @Test
    fun namesNeedBothTeamsAndUnzonedClocksDoNotLowerConfidence() {
        val m = match(
            listOf(game),
            listOf(name("Manchester United v Liverpool", "both-teams"), name("Manchester United live", "one-team"), name("Manchester United v Liverpool 18.00", "provider-time")),
        ).getValue(game.id)
        assertEquals(listOf("both-teams", "provider-time"), m.map { it.channelKey })
        assertTrue(m.all { !it.explicitStart && it.confidence == Confidence.AVAILABLE })
    }

    @Test
    fun countryVariantsUseTheStatedZone() {
        val e = event("Real Betis", "Getafe", at("2026-09-16T17:00:00Z"))
        val channels = listOf("AR", "ES", "ALB").map { name("$it - REAL BETIS VS GETAFE 18:00 CET", it) }
        val m = match(listOf(e), channels).getValue(e.id)
        assertEquals(3, m.size)
        assertTrue(m.all { it.confidence == Confidence.AVAILABLE && it.offsetMinutes == 0L })
        assertEquals(m, match(listOf(e.copy(startMinuteOfDay = 12 * 60)), channels).getValue(e.id))
    }

    @Test
    fun utcOffsetsAndSummerTimeAcrossMidnight() {
        val e = game.copy(startMillis = at("2026-09-16T22:30:00Z"))
        for (time in listOf("22:30 UTC", "00:30 CEST", "00:30 EET", "01:30 EEST", "04:00 UTC+05:30", "19:30 GMT-3")) {
            assertEquals(time, 0L, one(e, name("Manchester United v Liverpool $time")).single().offsetMinutes)
        }
    }

    @Test
    fun theMonzaSassuoloListingMatchesWithoutGuessingAZone() {
        val e = event("Monza", "Sassuolo", at("2026-09-18T18:45:00Z"))
        val channel = name("Serie A: Monza vs Sassuolo En Espãnol @ Sep 18 2:30 PM :Nbc Sports 05")
        val m = one(e, channel).single()
        assertEquals(Confidence.AVAILABLE, m.confidence)
        assertEquals(false, m.explicitStart)
        assertEquals(0L, m.offsetMinutes)
        assertEquals(e.startMillis, m.programmeStartMillis)
        assertEquals(m, one(e.copy(startMinuteOfDay = 14 * 60 + 45), channel).single())
    }

    @Test
    fun datedNamesDoNotPromoteOldOrWrongOpponentListings() {
        val e = event("Monza", "Sassuolo", at("2026-09-18T18:45:00Z"))
        for (date in listOf("Sep 12", "Sep 19", "September 18, 2025", "2025-09-18", "Feb 30")) {
            assertEquals(date, Confidence.POSSIBLE, one(e, name("Serie A: Monza vs Sassuolo @ $date 2:30 PM")).single().confidence)
        }
        assertTrue(one(e, name("Serie A: Monza vs Juventus @ Sep 18 2:30 PM")).isEmpty())
    }

    @Test
    fun amPmClocksWithZonesKeepLeadInButOnlyWrongDatesLowerNameConfidence() {
        val e = game.copy(startMillis = at("2026-09-18T18:45:00Z"))
        for (date in listOf("Sep 18", "September 18, 2026", "18 Sept 2026", "2026-09-18")) {
            val m = one(e, name("Manchester United v Liverpool @ $date 2:30 PM UTC-4")).single()
            assertEquals(date, Confidence.AVAILABLE, m.confidence)
            assertEquals(date, -15L, m.offsetMinutes)
            assertEquals(true, m.explicitStart)
        }
        for (label in listOf("Sep 18 2:30 AM UTC-4", "Sep 17 2:30 PM UTC-4", "Sep 18 2:30 PM UTC")) {
            val expected = if (label.startsWith("Sep 17")) Confidence.POSSIBLE else Confidence.AVAILABLE
            assertEquals(label, expected, one(e, name("Manchester United v Liverpool @ $label")).single().confidence)
        }
    }

    @Test
    fun dateEvidenceDoesNotDependOnTheClockFormat() {
        val e = game.copy(startMillis = at("2026-09-18T18:45:00Z"))
        for (label in listOf("Sep 18", "September 18, 2026 2:30 PM", "18 Sept 2026 14:30", "2026-09-18 2:30 p.m.")) {
            val m = one(e, name("Manchester United v Liverpool @ $label")).single()
            assertEquals(label, Confidence.AVAILABLE, m.confidence)
            assertEquals(false, m.explicitStart)
        }
        assertEquals(Confidence.POSSIBLE, one(e, name("Manchester United v Liverpool @ Sep 12")).single().confidence)
    }

    @Test
    fun twelveAmAndPmAndADatedMidnightLeadIn() {
        for ((instant, label) in listOf(
            "2026-09-18T00:00:00Z" to "Sep 18 12:00 AM UTC",
            "2026-09-18T12:00:00Z" to "Sep 18 12:00 PM UTC",
            "2026-01-01T00:00:00Z" to "Dec 31 11:45 PM UTC",
        )) {
            val m = one(game.copy(startMillis = at(instant)), name("Manchester United v Liverpool @ $label")).single()
            assertEquals(label, Confidence.AVAILABLE, m.confidence)
            assertEquals(label, if (label.startsWith("Dec")) -15L else 0L, m.offsetMinutes)
        }
    }

    @Test
    fun unzonedTimesNeitherLowerBothTeamsNorPromoteOne() {
        val e = event("Monza", "Sassuolo", at("2026-09-18T18:45:00Z"))
        for (clock in listOf("18:45", "20:45", "21:45", "2:30 PM", "02:30", "11.45")) {
            val m = one(e, name("Football: Monza - Sassuolo $clock")).single()
            assertEquals(clock, Confidence.AVAILABLE, m.confidence)
            assertEquals(false, m.explicitStart)
            assertEquals(0L, m.offsetMinutes)
            assertTrue(clock, one(e, name("Monza live $clock")).isEmpty())
        }
    }

    @Test
    fun numericDatesMatchWhateverTheViewerClock() {
        val e = event("Monza", "Sassuolo", at("2026-09-18T18:45:00Z"))
        for (date in listOf("18/9", "18/09", "18/9/2026", "9/18", "09/18/2026")) {
            val channel = name("[danzDE] ($date) 18:45 Football: Monza - Sassuolo")
            val m = one(e, channel).single()
            assertEquals(date, Confidence.AVAILABLE, m.confidence)
            assertEquals(false, m.explicitStart)
            assertEquals(m, one(e.copy(startMinuteOfDay = 14 * 60 + 45), channel).single())
        }
        assertTrue(one(e, name("[danzDE] (18/9) 18:45 Football: Monza - Juventus")).isEmpty())
    }

    @Test
    fun staleAndInvalidNumericDatesDoNotConfirmALocalClock() {
        val e = game.copy(startMillis = at("2026-09-18T18:45:00Z"))
        for (date in listOf("17/9", "19/9", "18/9/2025", "9/18/2025", "30/2", "0/9", "18/0", "99/99")) {
            assertEquals(date, Confidence.POSSIBLE, one(e, name("($date) 21:45 Manchester United - Liverpool")).single().confidence)
        }
    }

    @Test
    fun ambiguousNumericDatesNeverGuessTheOrder() {
        for ((instant, date, expected) in listOf(
            Triple("2026-10-09T18:45:00Z", "9/10", Confidence.POSSIBLE),
            Triple("2026-09-10T18:45:00Z", "9/10/2026", Confidence.POSSIBLE),
            Triple("2026-09-09T18:45:00Z", "9/9", Confidence.AVAILABLE),
        )) {
            for (clock in listOf("21:45", "18:45 UTC")) {
                assertEquals("$date $clock", expected, one(game.copy(startMillis = at(instant)), name("($date) $clock Manchester United - Liverpool")).single().confidence)
            }
        }
    }

    @Test
    fun numericDatesWithZonesKeepOffsetsAndRejectStaleDates() {
        val e = game.copy(startMillis = at("2026-09-18T18:45:00Z"))
        for ((label, expected, offset) in listOf(
            Triple("(18/9) 18:30 UTC", Confidence.AVAILABLE, -15L),
            Triple("(9/18/2026) 20:45 CEST", Confidence.AVAILABLE, 0L),
            Triple("(17/9) 18:45 UTC", Confidence.POSSIBLE, -1440L),
            Triple("(18/9) 18:45 CEST", Confidence.AVAILABLE, -120L),
        )) {
            val m = one(e, name("[danzDE] $label Football: Manchester United - Liverpool")).single()
            assertEquals(label, expected, m.confidence)
            assertEquals(label, offset, m.offsetMinutes)
            assertEquals(label, true, m.explicitStart)
        }
    }

    @Test
    fun restoreReturnsTheAutomaticConfidence() {
        val possible = one(game, guide("Manchester United live", kickOff)).single()
        assertEquals(Confidence.AVAILABLE, possible.withDecision(Decision.CONFIRMED).confidence)
        assertEquals(possible, possible.withDecision(Decision.CONFIRMED).withDecision(null))
        assertEquals(possible, possible.withDecision(Decision.REJECTED).withDecision(null))
    }

    @Test
    fun pagesKeepWinnersAndFallbacksWhateverTheOrder() {
        val matcher = StreamMatcher(listOf(game), emptyMap(), mapOf((game.id to "auto") to Decision.CONFIRMED, (game.id to "fallback") to Decision.REJECTED))
        // A fallback with a better clock loses to any automatic hit.
        matcher.add(guide("Evening news", kickOff, "auto"))
        matcher.add(guide("Evening news", kickOff, "fallback"))
        repeat(10_000) { matcher.add(guide("Unrelated programme $it", kickOff, "unrelated-$it")) }
        matcher.add(guide("Liverpool preview", kickOff - 60 * 60_000, "auto"))
        // Equal evidence either side of kick-off keeps the earlier programme, whatever order pages arrive in.
        matcher.add(guide("Manchester United v Liverpool", kickOff + 15 * 60_000, "tie").copy(programmeId = "later"))
        matcher.add(guide("Manchester United v Liverpool", kickOff - 15 * 60_000, "tie").copy(programmeId = "earlier"))
        matcher.add(guide("Liverpool preview", kickOff - 90 * 60_000, "auto"))
        val m = matcher.finish().getValue(game.id)
        assertEquals(listOf("tie", "auto", "fallback"), m.map { it.channelKey })
        assertEquals("earlier", m[0].programmeId)
        assertEquals("Liverpool preview", m[1].programmeTitle)
        assertEquals(-60L, m[1].offsetMinutes)
        assertEquals(Confidence.POSSIBLE, m[1].automatic)
        assertEquals(Confidence.AVAILABLE, m[1].confidence)
        assertEquals(Confidence.REJECTED, m[2].confidence)
    }

    @Test
    fun theGuideBeatsANameOnEqualEvidenceAndWordsMatchWhole() {
        val matcher = StreamMatcher(listOf(game), emptyMap(), emptyMap())
        matcher.add(name("Manchester United v Liverpool 18:30 UTC"))
        matcher.add(guide("Live football", kickOff).copy(subtitle = "Manchester United", description = "Liverpool"))
        matcher.add(guide("Manchester Unitedtown v Liverpooltown", kickOff, "unrelated"))
        val m = matcher.finish().getValue(game.id).single()
        assertEquals(MatchSource.GUIDE, m.source)
        assertEquals("Live football", m.programmeTitle)
        assertEquals(Confidence.AVAILABLE, m.confidence)
    }

    @Test
    fun normalisingAndAliases() {
        assertEquals("bayern munchen", MatchText.normalise("Bayern München"))
        assertEquals("paris saint germain", MatchText.normalise("  Paris Saint-Germain! "))
        // Built-in aliases apply only to the exact canonical name; short variants are dropped.
        val aliases = TeamVariants.aliases(mapOf("Northbridge FC" to setOf("NB", "Northbridge")))
        assertEquals(listOf("paris saint germain", "psg", "paris sg"), TeamVariants.of("Paris Saint-Germain", aliases))
        assertEquals(listOf("northbridge fc", "northbridge"), TeamVariants.of("Northbridge FC", aliases))
        assertEquals(listOf("paris"), TeamVariants.of("Paris", aliases))
    }
}

/** Spec 60 §11 "Sections and ordering": the sports-channel row and the priority order. */
class SportsChannelsTest {
    private fun game(id: Int, status: EventStatus, start: Long) = SportEvent(
        "g$id", SportType.FOOTBALL, "39", "L", null, Side("H$id", null), Side("A$id", null), start, 0, status, null, null, null,
    )

    private fun stream(game: SportEvent, channel: String, name: String = "Channel $channel", confidence: Confidence = Confidence.AVAILABLE, score: Int = 100) = StreamMatch(
        game.id, channel, name, "p", "Programme ${game.id}", MatchSource.GUIDE, game.startMillis, 0, true, score,
        if (confidence == Confidence.REJECTED) Confidence.POSSIBLE else confidence, if (confidence == Confidence.REJECTED) Decision.REJECTED else null,
    )

    @Test
    fun onlyAvailableOneRowPerChannelLiveFirstAtMostEight() {
        val live = game(1, EventStatus.LIVE, 2_000)
        val later = game(2, EventStatus.SCHEDULED, 1_000)
        val offScreen = game(3, EventStatus.LIVE, 500)
        val streams = mapOf(
            live.id to listOf(stream(live, "a"), stream(live, "rejected", confidence = Confidence.REJECTED), stream(live, "possible", confidence = Confidence.POSSIBLE)),
            later.id to listOf(stream(later, "a"), stream(later, "b")) + (1..10).map { stream(later, "x$it") },
            offScreen.id to listOf(stream(offScreen, "z")),
        )
        val rows = com.sohva.tv.core.model.sport.pairing.SportsChannels.of(listOf(later, live), streams)
        assertEquals(8, rows.size)
        assertEquals("a", rows.first().channelKey)
        assertTrue(rows.first().live)
        assertEquals(rows.size, rows.map { it.channelKey }.distinct().size)
        assertTrue(rows.none { it.channelKey in setOf("rejected", "possible", "z") })
    }

    @Test
    fun priorityReordersOnlyWithinAConfidence() {
        val g = game(1, EventStatus.SCHEDULED, 1_000)
        val list = listOf(
            stream(g, "en", "Sports EN", score = 100),
            stream(g, "fi", "Urheilu FI HD", score = 80),
            stream(g, "possible-fi", "Kanava FI", Confidence.POSSIBLE, score = 100),
            stream(g, "untagged", "Sports", score = 90),
        )
        val ordered = com.sohva.tv.core.model.sport.pairing.StreamOrder.present(list, listOf("FI", "EN"))
        assertEquals(listOf("fi", "en", "untagged", "possible-fi"), ordered.map { it.channelKey })
    }
}
