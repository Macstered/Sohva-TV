package com.streammate.tv.feature.player

import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTickerTest {
    private val now = 1_000_000_000L

    private fun event(id: String, status: TodayEventStatus, startsIn: Long) = TodayEvent(
        id = id, sport = SportType.FOOTBALL, competition = "League", home = "Home $id", away = "Away $id",
        startEpochMillis = now + startsIn, startMinuteOfDay = 0, startLabel = "20.00", status = status,
        statusLabel = if (status == TodayEventStatus.LIVE) "67'" else "", score = if (status == TodayEventStatus.LIVE) "1–0" else null,
        matchingChannels = 0,
    )

    @Test
    fun `live matches come first, then the ones starting within three hours, a few at most`() {
        val shown = scoreTickerEvents(
            listOf(
                event("later", TodayEventStatus.SCHEDULED, 5 * 3_600_000L),
                event("soon", TodayEventStatus.SCHEDULED, 40 * 60_000L),
                event("done", TodayEventStatus.FINISHED, -3 * 3_600_000L),
                event("live2", TodayEventStatus.LIVE, -30 * 60_000L),
                event("live1", TodayEventStatus.LIVE, -60 * 60_000L),
                event("sooner", TodayEventStatus.SCHEDULED, 10 * 60_000L),
            ),
            now,
        )
        assertEquals(listOf("live1", "live2", "sooner", "soon"), shown.map { it.id })
    }

    @Test
    fun `the ticker never grows past its row count`() {
        val many = (1..10).map { event("l$it", TodayEventStatus.LIVE, -it * 60_000L) }
        assertEquals(SCORE_TICKER_MAX_ROWS, scoreTickerEvents(many, now).size)
    }
}
