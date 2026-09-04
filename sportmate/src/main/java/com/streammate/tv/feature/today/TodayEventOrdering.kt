package com.streammate.tv.feature.today

import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus

object TodayEventOrdering {
    private val comparator = compareBy<TodayEvent>(
        { statusRank(it.status) },
        { it.startMinuteOfDay },
        { it.competition },
    )

    fun sort(events: List<TodayEvent>): List<TodayEvent> = events.sortedWith(comparator)

    private fun statusRank(status: TodayEventStatus): Int = when (status) {
        TodayEventStatus.LIVE -> 0
        TodayEventStatus.SCHEDULED -> 1
        TodayEventStatus.POSTPONED -> 2
        TodayEventStatus.INTERRUPTED -> 2
        TodayEventStatus.UNKNOWN -> 2
        TodayEventStatus.FINISHED -> 3
        TodayEventStatus.CANCELLED -> 3
    }
}
