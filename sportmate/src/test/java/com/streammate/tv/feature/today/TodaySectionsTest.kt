package com.streammate.tv.feature.today

import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.matching.ChannelMatchConfidence
import com.streammate.tv.matching.EventChannelMatch
import com.streammate.tv.matching.ManualMatchDecision
import com.streammate.tv.matching.MatchCandidateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which heading a match ends up under, and which channels the strip lists.
 *
 * Both are decided away from the screen so they can be checked: a finished game
 * filed under "Live now" would not read as a layout slip, it would read as a
 * wrong score, and a channel listed as showing sport on the strength of a guess
 * would send someone to the wrong channel.
 */
class TodaySectionsTest {

    private fun event(
        id: String,
        status: TodayEventStatus,
        startMinuteOfDay: Int = 0,
    ) = TodayEvent(
        id = id,
        sport = SportType.FOOTBALL,
        competition = "Test league",
        home = "Home",
        away = "Away",
        startMinuteOfDay = startMinuteOfDay,
        startLabel = "20.00",
        status = status,
        statusLabel = status.name,
        score = null,
        matchingChannels = 0,
    )

    private fun match(
        eventId: String,
        channelId: String,
        confidence: ChannelMatchConfidence = ChannelMatchConfidence.AVAILABLE,
        decision: ManualMatchDecision? = null,
        startEpochMillis: Long = 0L,
        channelName: String = channelId,
    ) = EventChannelMatch(
        eventId = eventId,
        channelId = channelId,
        channelName = channelName,
        programmeId = "$channelId-programme",
        programmeTitle = "Programme on $channelName",
        programmeStartEpochMillis = startEpochMillis,
        startOffsetMinutes = 0,
        confidence = confidence,
        score = 100,
        manualDecision = decision,
        source = MatchCandidateSource.XMLTV_PROGRAMME,
        hasExplicitStartTime = true,
    )

    // ------------------------------------------------------------ grouping --

    @Test
    fun eachStatusLandsUnderTheHeadingItBelongsTo() {
        val sections = TodaySections.of(
            listOf(
                event("live", TodayEventStatus.LIVE),
                event("scheduled", TodayEventStatus.SCHEDULED),
                event("finished", TodayEventStatus.FINISHED),
            ),
        )

        assertEquals(listOf("live"), sections.live.map(TodayEvent::id))
        assertEquals(listOf("scheduled"), sections.upcoming.map(TodayEvent::id))
        assertEquals(listOf("finished"), sections.finished.map(TodayEvent::id))
    }

    @Test
    fun nothingThatHasNotStartedIsEverCalledLive() {
        val sections = TodaySections.of(
            TodayEventStatus.entries
                .filterNot { it == TodayEventStatus.LIVE }
                .map { status -> event(status.name, status) },
        )

        assertTrue("a non-live status reached the live section", sections.live.isEmpty())
    }

    @Test
    fun aCancelledMatchIsFinishedAndAnInterruptedOneIsNot() {
        val sections = TodaySections.of(
            listOf(
                event("cancelled", TodayEventStatus.CANCELLED),
                event("interrupted", TodayEventStatus.INTERRUPTED),
                event("unknown", TodayEventStatus.UNKNOWN),
            ),
        )

        assertEquals(listOf("cancelled"), sections.finished.map(TodayEvent::id))
        assertEquals(listOf("interrupted", "unknown"), sections.upcoming.map(TodayEvent::id))
    }

    @Test
    fun everyEventEndsUpInExactlyOneSection() {
        val events = TodayEventStatus.entries.map { status -> event(status.name, status) }

        val sections = TodaySections.of(events)
        val placed = sections.live + sections.upcoming + sections.finished

        assertEquals(events.size, placed.size)
        assertEquals(events.map(TodayEvent::id).toSet(), placed.map(TodayEvent::id).toSet())
    }

    // --------------------------------------------------------- initial focus --

    @Test
    fun focusPrefersSomethingBeingPlayedNow() {
        val sections = TodaySections.of(
            listOf(
                event("scheduled", TodayEventStatus.SCHEDULED),
                event("live", TodayEventStatus.LIVE),
                event("finished", TodayEventStatus.FINISHED),
            ),
        )

        assertEquals("live", sections.firstFocusEventId)
    }

    @Test
    fun focusFallsBackThroughUpcomingToFinishedAndThenToNothing() {
        assertEquals(
            "scheduled",
            TodaySections.of(
                listOf(
                    event("finished", TodayEventStatus.FINISHED),
                    event("scheduled", TodayEventStatus.SCHEDULED),
                ),
            ).firstFocusEventId,
        )
        assertEquals(
            "finished",
            TodaySections.of(listOf(event("finished", TodayEventStatus.FINISHED))).firstFocusEventId,
        )
        assertNull(TodaySections.of(emptyList()).firstFocusEventId)
        assertTrue(TodaySections.of(emptyList()).isEmpty)
    }

    // ------------------------------------------------------ sports channels --

    @Test
    fun onlyChannelsBehindAMatchTheAppWouldOfferAreListed() {
        val events = listOf(event("live", TodayEventStatus.LIVE))
        val channels = todaySportsChannels(
            events = events,
            matches = mapOf(
                "live" to listOf(
                    match("live", "offerable"),
                    match("live", "guess", confidence = ChannelMatchConfidence.POSSIBLE),
                    match("live", "ruled-out", confidence = ChannelMatchConfidence.REJECTED),
                    match("live", "declined", decision = ManualMatchDecision.REJECTED),
                ),
            ),
        )

        assertEquals(listOf("offerable"), channels.map(TodaySportsChannel::channelId))
    }

    @Test
    fun aChannelCarryingTwoMatchesIsStillOneRow() {
        val events = listOf(
            event("one", TodayEventStatus.LIVE),
            event("two", TodayEventStatus.SCHEDULED),
        )
        val channels = todaySportsChannels(
            events = events,
            matches = mapOf(
                "one" to listOf(match("one", "shared")),
                "two" to listOf(match("two", "shared")),
            ),
        )

        assertEquals(1, channels.size)
        assertTrue("the row should be the one that is on now", channels.single().live)
    }

    @Test
    fun matchesForEventsNotOnScreenAreNotListed() {
        val channels = todaySportsChannels(
            events = listOf(event("shown", TodayEventStatus.LIVE)),
            matches = mapOf("filtered-out" to listOf(match("filtered-out", "hidden"))),
        )

        assertTrue(channels.isEmpty())
    }

    @Test
    fun whatIsOnNowComesFirstAndThenTheEarliestStart() {
        val events = listOf(
            event("later", TodayEventStatus.SCHEDULED),
            event("earlier", TodayEventStatus.SCHEDULED),
            event("now", TodayEventStatus.LIVE),
        )
        val channels = todaySportsChannels(
            events = events,
            matches = mapOf(
                "later" to listOf(match("later", "c-later", startEpochMillis = 200L)),
                "earlier" to listOf(match("earlier", "b-earlier", startEpochMillis = 100L)),
                "now" to listOf(match("now", "a-now", startEpochMillis = 300L)),
            ),
        )

        assertEquals(listOf("a-now", "b-earlier", "c-later"), channels.map(TodaySportsChannel::channelId))
    }

    @Test
    fun theStripIsBounded() {
        val events = (1..20).map { event("e$it", TodayEventStatus.LIVE) }
        val matches = events.associate { it.id to listOf(match(it.id, "channel-${it.id}")) }

        assertEquals(3, todaySportsChannels(events, matches, limit = 3).size)
    }
}
