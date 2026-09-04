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
    fun `m3u event channel name with both teams and kickoff is available`() {
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
            assertEquals(true, hasExplicitStartTime)
            assertEquals(0L, startOffsetMinutes)
        }
    }

    @Test
    fun `m3u name without a time requires both teams and conflicting time is possible`() {
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
                channelId = "wrong-time",
                source = MatchCandidateSource.M3U_CHANNEL_NAME,
            ),
        )

        val matches = matcher.match(listOf(event), candidates, emptyMap(), emptyMap())
            .getValue(event.id)

        assertEquals(listOf("both-teams", "wrong-time"), matches.map(EventChannelMatch::channelId))
        assertEquals(false, matches[0].hasExplicitStartTime)
        assertEquals(ChannelMatchConfidence.POSSIBLE, matches[1].confidence)
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
