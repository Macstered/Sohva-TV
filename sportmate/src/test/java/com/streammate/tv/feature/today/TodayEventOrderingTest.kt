package com.streammate.tv.feature.today

import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayEventOrderingTest {
    @Test
    fun `sorts live then scheduled then disrupted then finished`() {
        val events = listOf(
            event("finished", TodayEventStatus.FINISHED, 900),
            event("postponed", TodayEventStatus.POSTPONED, 500),
            event("scheduled", TodayEventStatus.SCHEDULED, 600),
            event("live", TodayEventStatus.LIVE, 1_000),
        )

        val sorted = TodayEventOrdering.sort(events)

        assertEquals(listOf("live", "scheduled", "postponed", "finished"), sorted.map { it.id })
    }

    @Test
    fun `sorts events in the same state by start time`() {
        val events = listOf(
            event("late", TodayEventStatus.SCHEDULED, 1_200),
            event("early", TodayEventStatus.SCHEDULED, 700),
        )

        val sorted = TodayEventOrdering.sort(events)

        assertEquals(listOf("early", "late"), sorted.map { it.id })
    }

    private fun event(id: String, status: TodayEventStatus, startMinute: Int) = TodayEvent(
        id = id,
        sport = SportType.ICE_HOCKEY,
        competition = "Example League",
        home = "Example Home",
        away = "Example Away",
        startMinuteOfDay = startMinute,
        startLabel = "18:00",
        status = status,
        statusLabel = status.name,
        score = null,
        matchingChannels = 0,
    )
}
