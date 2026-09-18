package com.streammate.tv.matching

import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventChannelMatcherTest {
    private val matcher = EventChannelMatcher()
    private val kickoff = Instant.parse("2026-08-23T18:30:00Z").toEpochMilli()
    private val event = TodayEvent(
        id = "fixture-1",
        sport = SportType.FOOTBALL,
        competitionId = "39",
        competition = "Premier League",
        home = "Manchester United",
        away = "Liverpool",
        startEpochMillis = kickoff,
        startMinuteOfDay = 21 * 60 + 30,
        startLabel = "21:30",
        status = TodayEventStatus.SCHEDULED,
        statusLabel = "Upcoming",
        score = null,
        matchingChannels = 0,
    )

    @Test
    fun `both teams and matching start are available including aliases`() {
        val matches = matcher.match(
            events = listOf(event),
            candidates = listOf(candidate("Man Utd v Liverpool", kickoff + 10 * 60_000)),
            aliases = mapOf("manchester united" to setOf("man utd")),
            decisions = emptyMap(),
        ).getValue(event.id)

        assertEquals(1, matches.size)
        assertEquals(ChannelMatchConfidence.AVAILABLE, matches.single().confidence)
        assertEquals(100, matches.single().score)
    }

    @Test
    fun `single team reference is possible rather than available`() {
        val matches = matcher.match(
            events = listOf(event),
            candidates = listOf(candidate("Manchester United live", kickoff)),
            aliases = emptyMap(),
            decisions = emptyMap(),
        ).getValue(event.id)

        assertEquals(ChannelMatchConfidence.POSSIBLE, matches.single().confidence)
    }

    @Test
    fun `manual decisions override and persist independently per channel`() {
        val candidates = listOf(
            candidate("Manchester United v Liverpool", kickoff, channelId = "strong"),
            candidate("Evening schedule", kickoff, channelId = "ambiguous"),
        )
        val decisions = mapOf(
            (event.id to "strong") to ManualMatchDecision.REJECTED,
            (event.id to "ambiguous") to ManualMatchDecision.CONFIRMED,
        )

        val matches = matcher.match(listOf(event), candidates, emptyMap(), decisions)
            .getValue(event.id)

        assertEquals(ChannelMatchConfidence.AVAILABLE, matches[0].confidence)
        assertEquals("ambiguous", matches[0].channelId)
        assertEquals(ChannelMatchConfidence.REJECTED, matches[1].confidence)
        assertEquals("strong", matches[1].channelId)
    }

    @Test
    fun `only the strongest programme per channel is returned`() {
        val matches = matcher.match(
            events = listOf(event),
            candidates = listOf(
                candidate("Liverpool preview", kickoff - 30 * 60_000),
                candidate("Manchester United v Liverpool", kickoff),
            ),
            aliases = emptyMap(),
            decisions = emptyMap(),
        ).getValue(event.id)

        assertEquals(1, matches.size)
        assertEquals("Manchester United v Liverpool", matches.single().programmeTitle)
        assertEquals(ChannelMatchConfidence.AVAILABLE, matches.single().confidence)
    }

    @Test
    fun `unrelated or distant programmes are excluded`() {
        val matches = matcher.match(
            events = listOf(event),
            candidates = listOf(
                candidate("Evening news", kickoff),
                candidate("Manchester United v Liverpool", kickoff + 121 * 60_000),
            ),
            aliases = emptyMap(),
            decisions = emptyMap(),
        ).getValue(event.id)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `m3u event channel name with both teams is available without assuming a local clock`() {
        val matches = matcher.match(
            events = listOf(event),
            candidates = listOf(
                candidate(
                    title = "EVENT: Man Utd - Liverpool | 21:30",
                    startEpochMillis = 0,
                    source = MatchCandidateSource.M3U_CHANNEL_NAME,
                ),
            ),
            aliases = mapOf("manchester united" to setOf("man utd")),
            decisions = emptyMap(),
        ).getValue(event.id)

        with(matches.single()) {
            assertEquals(ChannelMatchConfidence.AVAILABLE, confidence)
            assertEquals(MatchCandidateSource.M3U_CHANNEL_NAME, source)
            assertEquals(false, hasExplicitStartTime)
            assertEquals(0L, startOffsetMinutes)
        }
    }

    @Test
    fun `m3u names require both teams and unzoned clocks do not reduce confidence`() {
        val candidates = listOf(
            candidate(
                title = "Manchester United v Liverpool",
                startEpochMillis = 0,
                channelId = "both-teams",
                source = MatchCandidateSource.M3U_CHANNEL_NAME,
            ),
            candidate(
                title = "Manchester United live",
                startEpochMillis = 0,
                channelId = "one-team",
                source = MatchCandidateSource.M3U_CHANNEL_NAME,
            ),
            candidate(
                title = "Manchester United v Liverpool 18.00",
                startEpochMillis = 0,
                channelId = "provider-time",
                source = MatchCandidateSource.M3U_CHANNEL_NAME,
            ),
        )

        val matches = matcher.match(listOf(event), candidates, emptyMap(), emptyMap())
            .getValue(event.id)

        assertEquals(listOf("both-teams", "provider-time"), matches.map(EventChannelMatch::channelId))
        assertTrue(matches.all { !it.hasExplicitStartTime && it.confidence == ChannelMatchConfidence.AVAILABLE })
    }

    @Test
    fun `country variants use the explicit channel timezone instead of the display timezone`() {
        val fixture = event.copy(home = "Real Betis", away = "Getafe", startEpochMillis = Instant.parse("2026-09-16T17:00:00Z").toEpochMilli(), startMinuteOfDay = 19 * 60)
        val channels = listOf("AR", "ES", "ALB").map { country ->
            candidate("$country - REAL BETIS VS GETAFE 18:00 CET", 0, country, MatchCandidateSource.M3U_CHANNEL_NAME)
        }
        val matches = matcher.match(listOf(fixture), channels, emptyMap(), emptyMap()).getValue(fixture.id)
        assertEquals(3, matches.size)
        assertTrue(matches.all { it.confidence == ChannelMatchConfidence.AVAILABLE && it.startOffsetMinutes == 0L })
        val differentDisplayZone = matcher.match(listOf(fixture.copy(startMinuteOfDay = 12 * 60)), channels, emptyMap(), emptyMap()).getValue(fixture.id)
        assertEquals(matches, differentDisplayZone)
    }

    @Test
    fun `explicit UTC offsets and summer time work across midnight`() {
        val fixture = event.copy(startEpochMillis = Instant.parse("2026-09-16T22:30:00Z").toEpochMilli())
        for (time in listOf("22:30 UTC", "00:30 CEST", "00:30 EET", "01:30 EEST", "04:00 UTC+05:30", "19:30 GMT-3")) {
            val matches = matcher.match(listOf(fixture), listOf(candidate("Manchester United v Liverpool $time", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)), emptyMap(), emptyMap()).getValue(fixture.id)
            assertEquals(time, 0L, matches.single().startOffsetMinutes)
        }
    }

    @Test
    fun `dated Monza Sassuolo listing matches without guessing its provider timezone`() {
        val fixture = event.copy(
            home = "Monza", away = "Sassuolo", competition = "Serie A",
            startEpochMillis = Instant.parse("2026-09-18T18:45:00Z").toEpochMilli(),
            startMinuteOfDay = 21 * 60 + 45, startLabel = "21:45",
        )
        val channel = candidate("Serie A: Monza vs Sassuolo En Espãnol @ Sep 18 2:30 PM :Nbc Sports 05", 0,
            source = MatchCandidateSource.M3U_CHANNEL_NAME)
        val match = matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single()
        assertEquals(ChannelMatchConfidence.AVAILABLE, match.confidence)
        // A clock with no zone cannot substantiate an exact offset in the UI.
        assertEquals(false, match.hasExplicitStartTime)
        assertEquals(0L, match.startOffsetMinutes)
        assertEquals(fixture.startEpochMillis, match.programmeStartEpochMillis)
        val otherDisplayZone = matcher.match(listOf(fixture.copy(startMinuteOfDay = 14 * 60 + 45)), listOf(channel), emptyMap(), emptyMap())
        assertEquals(match, otherDisplayZone.getValue(fixture.id).single())
    }

    @Test
    fun `dated channel names do not promote old or wrong-opponent listings`() {
        val fixture = event.copy(home = "Monza", away = "Sassuolo",
            startEpochMillis = Instant.parse("2026-09-18T18:45:00Z").toEpochMilli(), startMinuteOfDay = 21 * 60 + 45)
        for (date in listOf("Sep 12", "Sep 19", "September 18, 2025", "2025-09-18", "Feb 30")) {
            val channel = candidate("Serie A: Monza vs Sassuolo @ $date 2:30 PM", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            assertEquals(date, ChannelMatchConfidence.POSSIBLE,
                matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single().confidence)
        }
        val otherOpponent = candidate("Serie A: Monza vs Juventus @ Sep 18 2:30 PM", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
        assertTrue(matcher.match(listOf(fixture), listOf(otherOpponent), emptyMap(), emptyMap()).getValue(fixture.id).isEmpty())
    }

    @Test
    fun `am pm clocks and explicit zones retain broadcast lead-in and conflict checks`() {
        val fixture = event.copy(startEpochMillis = Instant.parse("2026-09-18T18:45:00Z").toEpochMilli(), startMinuteOfDay = 21 * 60 + 45)
        for (date in listOf("Sep 18", "September 18, 2026", "18 Sept 2026", "2026-09-18")) {
            val channel = candidate("Manchester United v Liverpool @ $date 2:30 PM UTC-4", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            val match = matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single()
            assertEquals(date, ChannelMatchConfidence.AVAILABLE, match.confidence)
            assertEquals(-15L, match.startOffsetMinutes)
            assertEquals(true, match.hasExplicitStartTime)
        }
        for (label in listOf("Sep 18 2:30 AM UTC-4", "Sep 17 2:30 PM UTC-4", "Sep 18 2:30 PM UTC")) {
            val channel = candidate("Manchester United v Liverpool @ $label", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            assertEquals(label, ChannelMatchConfidence.POSSIBLE,
                matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single().confidence)
        }
    }

    @Test
    fun `date evidence does not depend on the provider clock format`() {
        val fixture = event.copy(startEpochMillis = Instant.parse("2026-09-18T18:45:00Z").toEpochMilli(), startMinuteOfDay = 21 * 60 + 45)
        for (label in listOf("Sep 18", "September 18, 2026 2:30 PM", "18 Sept 2026 14:30", "2026-09-18 2:30 p.m.")) {
            val channel = candidate("Manchester United v Liverpool @ $label", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            val match = matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single()
            assertEquals(label, ChannelMatchConfidence.AVAILABLE, match.confidence)
            assertEquals(false, match.hasExplicitStartTime)
        }
        val staleWithoutClock = candidate("Manchester United v Liverpool @ Sep 12", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
        assertEquals(ChannelMatchConfidence.POSSIBLE,
            matcher.match(listOf(fixture), listOf(staleWithoutClock), emptyMap(), emptyMap()).getValue(fixture.id).single().confidence)
    }

    @Test
    fun `twelve am and pm and dated midnight lead-in are resolved correctly`() {
        for ((instant, label) in listOf(
            "2026-09-18T00:00:00Z" to "Sep 18 12:00 AM UTC",
            "2026-09-18T12:00:00Z" to "Sep 18 12:00 PM UTC",
            "2026-01-01T00:00:00Z" to "Dec 31 11:45 PM UTC",
        )) {
            val fixture = event.copy(startEpochMillis = Instant.parse(instant).toEpochMilli())
            val channel = candidate("Manchester United v Liverpool @ $label", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            val match = matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single()
            assertEquals(label, ChannelMatchConfidence.AVAILABLE, match.confidence)
            assertEquals(if (label.startsWith("Dec")) -15L else 0L, match.startOffsetMinutes)
        }
    }

    @Test
    fun `unlabelled provider times neither downgrade both teams nor promote a single team`() {
        val fixture = event.copy(home = "Monza", away = "Sassuolo",
            startEpochMillis = Instant.parse("2026-09-18T18:45:00Z").toEpochMilli(), startMinuteOfDay = 21 * 60 + 45)
        for (clock in listOf("18:45", "20:45", "21:45", "2:30 PM", "02:30", "11.45")) {
            val channel = candidate("Football: Monza - Sassuolo $clock", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            val match = matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single()
            assertEquals(clock, ChannelMatchConfidence.AVAILABLE, match.confidence)
            assertEquals(false, match.hasExplicitStartTime)
            assertEquals(0L, match.startOffsetMinutes)
            assertEquals(match, matcher.match(listOf(fixture.copy(startMinuteOfDay = 14 * 60 + 45)), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single())
            val singleTeam = channel.copy(title = "Monza live $clock", channelName = "Monza live $clock")
            assertTrue(matcher.match(listOf(fixture), listOf(singleTeam), emptyMap(), emptyMap()).getValue(fixture.id).isEmpty())
        }
    }

    @Test
    fun `numeric dated Monza Sassuolo channel matches independently of the viewer clock`() {
        val fixture = event.copy(home = "Monza", away = "Sassuolo", competition = "Serie A",
            startEpochMillis = Instant.parse("2026-09-18T18:45:00Z").toEpochMilli(), startMinuteOfDay = 21 * 60 + 45)
        for (date in listOf("18/9", "18/09", "18/9/2026", "9/18", "09/18/2026")) {
            val channel = candidate("[danzDE] ($date) 18:45 Football: Monza - Sassuolo", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            val match = matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single()
            assertEquals(date, ChannelMatchConfidence.AVAILABLE, match.confidence)
            assertEquals(false, match.hasExplicitStartTime)
            assertEquals(0L, match.startOffsetMinutes)
            val otherDisplayZone = fixture.copy(startMinuteOfDay = 14 * 60 + 45)
            assertEquals(match, matcher.match(listOf(otherDisplayZone), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single())
        }
        val wrongOpponent = candidate("[danzDE] (18/9) 18:45 Football: Monza - Juventus", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
        assertTrue(matcher.match(listOf(fixture), listOf(wrongOpponent), emptyMap(), emptyMap()).getValue(fixture.id).isEmpty())
    }

    @Test
    fun `stale and invalid numeric dates do not confirm a matching local clock`() {
        val fixture = event.copy(startEpochMillis = Instant.parse("2026-09-18T18:45:00Z").toEpochMilli(), startMinuteOfDay = 21 * 60 + 45)
        for (date in listOf("17/9", "19/9", "18/9/2025", "9/18/2025", "30/2", "0/9", "18/0", "99/99")) {
            val channel = candidate("($date) 21:45 Manchester United - Liverpool", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            assertEquals(date, ChannelMatchConfidence.POSSIBLE,
                matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single().confidence)
        }
    }

    @Test
    fun `ambiguous numeric month and day never guess the provider date order`() {
        for ((instant, date, confidence) in listOf(
            Triple("2026-10-09T18:45:00Z", "9/10", ChannelMatchConfidence.POSSIBLE),
            Triple("2026-09-10T18:45:00Z", "9/10/2026", ChannelMatchConfidence.POSSIBLE),
            Triple("2026-09-09T18:45:00Z", "9/9", ChannelMatchConfidence.AVAILABLE),
        )) {
            val fixture = event.copy(startEpochMillis = Instant.parse(instant).toEpochMilli(), startMinuteOfDay = 21 * 60 + 45)
            for (clock in listOf("21:45", "18:45 UTC")) {
                val channel = candidate("($date) $clock Manchester United - Liverpool", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
                assertEquals("$date $clock", confidence,
                    matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single().confidence)
            }
        }
    }

    @Test
    fun `numeric dated explicit zones still enforce the actual broadcast instant`() {
        val fixture = event.copy(startEpochMillis = Instant.parse("2026-09-18T18:45:00Z").toEpochMilli(), startMinuteOfDay = 21 * 60 + 45)
        for ((label, confidence, offset) in listOf(
            Triple("(18/9) 18:30 UTC", ChannelMatchConfidence.AVAILABLE, -15L),
            Triple("(9/18/2026) 20:45 CEST", ChannelMatchConfidence.AVAILABLE, 0L),
            Triple("(17/9) 18:45 UTC", ChannelMatchConfidence.POSSIBLE, -1440L),
            Triple("(18/9) 18:45 CEST", ChannelMatchConfidence.POSSIBLE, -120L),
        )) {
            val channel = candidate("[danzDE] $label Football: Manchester United - Liverpool", 0, source = MatchCandidateSource.M3U_CHANNEL_NAME)
            val match = matcher.match(listOf(fixture), listOf(channel), emptyMap(), emptyMap()).getValue(fixture.id).single()
            assertEquals(label, confidence, match.confidence)
            assertEquals(label, offset, match.startOffsetMinutes)
            assertEquals(true, match.hasExplicitStartTime)
        }
    }

    @Test
    fun `restoring a confirmed or rejected row uses its original confidence`() {
        val possible = matcher.match(listOf(event), listOf(candidate("Manchester United live", kickoff)), emptyMap(), emptyMap()).getValue(event.id).single()
        assertEquals(ChannelMatchConfidence.AVAILABLE, possible.withDecision(ManualMatchDecision.CONFIRMED).confidence)
        assertEquals(possible, possible.withDecision(ManualMatchDecision.CONFIRMED).withDecision(null))
        assertEquals(possible, possible.withDecision(ManualMatchDecision.REJECTED).withDecision(null))
    }

    private fun candidate(
        title: String,
        startEpochMillis: Long,
        channelId: String = "channel-1",
        source: MatchCandidateSource = MatchCandidateSource.XMLTV_PROGRAMME,
    ) = ProgrammeCandidate(
        channelId = channelId,
        channelName = if (source == MatchCandidateSource.M3U_CHANNEL_NAME) title else "Sports Channel",
        programmeId = "programme-$channelId",
        title = title,
        subtitle = null,
        description = null,
        startEpochMillis = startEpochMillis,
        stopEpochMillis = startEpochMillis + 2 * 60 * 60_000,
        source = source,
    )
}
