package com.sohva.tv.core.model.sport.pairing

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.SportEvent

/** A row of "Sports channels now" (spec 60 SPORT-25, D§6): what is on, when, and whether the game is live. */
data class SportsChannel(
    val channelKey: String,
    val channelName: String,
    /** The guide programme's title, or the game for a channel-name match. */
    val title: String,
    val startMillis: Long,
    val live: Boolean,
)

object SportsChannels {
    private const val MAX = 8

    /**
     * Only Available streams (confirmed included, rejected never) of the games on screen; one row
     * per channel, live games first, then the earliest start, then the name; at most eight.
     */
    fun of(shown: List<SportEvent>, streams: Map<String, List<StreamMatch>>): List<SportsChannel> =
        shown.flatMap { e ->
            streams[e.id].orEmpty().filter { it.confidence == Confidence.AVAILABLE }.map { m ->
                SportsChannel(
                    m.channelKey, m.channelName, if (m.source == MatchSource.GUIDE) m.programmeTitle else e.title,
                    m.programmeStartMillis, e.status == EventStatus.LIVE,
                )
            }
        }.sortedWith(compareBy<SportsChannel> { !it.live }.thenBy { it.startMillis }.thenBy { it.channelName })
            .distinctBy { it.channelKey }.take(MAX)
}
