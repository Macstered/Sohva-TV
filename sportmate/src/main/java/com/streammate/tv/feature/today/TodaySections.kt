package com.streammate.tv.feature.today

import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import com.streammate.tv.matching.ChannelMatchConfidence
import com.streammate.tv.matching.EventChannelMatch
import com.streammate.tv.matching.ManualMatchDecision

/**
 * How the day divides into the sections on screen.
 *
 * Grouping is decided here rather than inside the composable, because the one
 * thing this screen must never do is put a match under the wrong heading: a
 * finished game filed under "Live now" is not a layout bug, it is a lie about
 * the score. Being a plain function, it can be checked without a television.
 */
data class TodaySections(
    val live: List<TodayEvent> = emptyList(),
    val upcoming: List<TodayEvent> = emptyList(),
    val finished: List<TodayEvent> = emptyList(),
) {
    val isEmpty: Boolean get() = live.isEmpty() && upcoming.isEmpty() && finished.isEmpty()

    /**
     * What focus should land on when the screen opens: something being played
     * now, else the next thing to come, else whatever is left.
     */
    val firstFocusEventId: String?
        get() = live.firstOrNull()?.id
            ?: upcoming.firstOrNull()?.id
            ?: finished.firstOrNull()?.id

    companion object {
        /**
         * [events] split by what is actually happening to them.
         *
         * A match that has been interrupted or whose status the provider has
         * not told us is grouped with the ones still to come, because that is
         * the honest reading: it has not finished, and it is not being played.
         */
        fun of(events: List<TodayEvent>): TodaySections = TodaySections(
            live = events.filter { it.status == TodayEventStatus.LIVE },
            upcoming = events.filter {
                it.status == TodayEventStatus.SCHEDULED ||
                    it.status == TodayEventStatus.POSTPONED ||
                    it.status == TodayEventStatus.INTERRUPTED ||
                    it.status == TodayEventStatus.UNKNOWN
            },
            finished = events.filter {
                it.status == TodayEventStatus.FINISHED || it.status == TodayEventStatus.CANCELLED
            },
        )
    }
}

/**
 * One channel currently carrying a match, as the sports-channel strip shows it.
 *
 * Everything here is read off a match the matcher actually made. There is no
 * separate list of sports channels anywhere in this app's state, so the strip
 * is a view of the same matches the cards above it count - not a second source
 * that could disagree with them.
 */
data class TodaySportsChannel(
    val channelId: String,
    val channelName: String,
    val programmeTitle: String,
    val programmeStartEpochMillis: Long,
    val live: Boolean,
)

/**
 * The channels worth listing, from the matches already on screen.
 *
 * Only matches the app is confident enough about to offer for playback appear,
 * and only once per channel: a channel carrying a match is one row whatever
 * number of events happen to point at it. Ordered by what is on now, then by
 * when the programme started.
 */
fun todaySportsChannels(
    events: List<TodayEvent>,
    matches: Map<String, List<EventChannelMatch>>,
    limit: Int = MAX_SPORTS_CHANNELS,
): List<TodaySportsChannel> {
    val liveEventIds = events.asSequence()
        .filter { it.status == TodayEventStatus.LIVE }
        .map(TodayEvent::id)
        .toSet()
    val eventIds = events.mapTo(mutableSetOf(), TodayEvent::id)
    return matches.asSequence()
        .filter { (eventId, _) -> eventId in eventIds }
        .flatMap { (eventId, eventMatches) ->
            eventMatches.asSequence()
                .filter { it.confidence.isOfferable && it.manualDecision != ManualMatchDecision.REJECTED }
                .map { match ->
                    TodaySportsChannel(
                        channelId = match.channelId,
                        channelName = match.channelName,
                        programmeTitle = match.programmeTitle,
                        programmeStartEpochMillis = match.programmeStartEpochMillis,
                        live = eventId in liveEventIds,
                    )
                }
        }
        .sortedWith(
            compareByDescending<TodaySportsChannel> { it.live }
                .thenBy { it.programmeStartEpochMillis }
                .thenBy { it.channelName.lowercase() },
        )
        .distinctBy(TodaySportsChannel::channelId)
        .take(limit)
        .toList()
}

/**
 * Confident enough to put a channel's name in front of someone.
 *
 * A "possible" match is a guess the Match Hub asks about; it has no business
 * being listed as a channel that is showing sport.
 */
private val ChannelMatchConfidence.isOfferable: Boolean
    get() = this == ChannelMatchConfidence.AVAILABLE

private const val MAX_SPORTS_CHANNELS = 8
