package com.sohva.tv.core.model

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.TodayFilter
import com.sohva.tv.core.model.sport.TodayLists
import com.sohva.tv.core.model.sport.TodayRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 60 §11 "Sections and ordering", "Polling policy" intervals and "Ticker selection". */
class TodayRulesTest {
    private val now = 1_790_400_000_000L

    private fun game(id: String, status: EventStatus, minute: Int, sport: SportType = SportType.FOOTBALL, competition: String = "C", startIn: Long = 0) =
        SportEvent(id, sport, "1", competition, null, Side("H", null), Side("A", null), now + startIn, minute, status, null, null, null)

    @Test
    fun theOrderAndTheSections() {
        val events = listOf(
            game("f", EventStatus.FINISHED, 600), game("c", EventStatus.CANCELLED, 500), game("l2", EventStatus.LIVE, 700),
            game("s", EventStatus.SCHEDULED, 1200), game("i", EventStatus.INTERRUPTED, 900), game("l1", EventStatus.LIVE, 650),
            game("u", EventStatus.UNKNOWN, 100), game("p", EventStatus.POSTPONED, 100, competition = "A"),
        ).sortedWith(TodayRules.ORDER)
        assertEquals(listOf("l1", "l2", "s", "p", "u", "i", "c", "f"), events.map { it.id })
        val sections = TodayLists.sections(events)
        assertEquals(listOf("l1", "l2"), sections.live.map { it.id })
        // Interrupted is later today; cancelled is finished (SPORT-FR-48).
        assertEquals(listOf("s", "p", "u", "i"), sections.later.map { it.id })
        assertEquals(listOf("c", "f"), sections.finished.map { it.id })
        assertEquals("l1", sections.firstFocus?.id)
        assertEquals("s", TodayLists.sections(events.filter { it.status != EventStatus.LIVE }).firstFocus?.id)
        assertEquals("c", TodayLists.sections(events.filter { it.status == EventStatus.FINISHED || it.status == EventStatus.CANCELLED }).firstFocus?.id)
        assertNull(TodayLists.sections(emptyList()).firstFocus)
    }

    @Test
    fun tabsCountEveryStatusAndAnUnfollowedSportFallsBackToAll() {
        val events = listOf(game("a", EventStatus.LIVE, 1), game("b", EventStatus.FINISHED, 2), game("m", EventStatus.SCHEDULED, 3, SportType.MMA))
        val follows = SportFollows(setOf(SportType.MMA, SportType.FOOTBALL), emptySet())
        val tabs = TodayLists.tabs(events, follows, watchable = setOf("a"), favourites = emptySet())
        assertEquals(listOf("all" to 3, "FOOTBALL" to 2, "MMA" to 1, "watchable" to 1, "favourites" to 0), tabs.map { it.filter.key to it.count })
        assertEquals(TodayFilter.All, TodayLists.effective(TodayFilter.OfSport(SportType.NBA), follows))
        assertEquals(TodayFilter.OfSport(SportType.MMA), TodayFilter.fromKey("MMA"))
        assertEquals(TodayFilter.All, TodayFilter.fromKey("CURLING"))
        assertEquals(listOf("a"), TodayLists.filter(events, TodayFilter.Watchable, setOf("a"), emptySet()).map { it.id })
    }

    @Test
    fun pollingIntervalsAndTheTicker() {
        assertEquals(5, TodayRules.pollingMinutes(listOf(game("a", EventStatus.LIVE, 1)), now))
        assertEquals(10, TodayRules.pollingMinutes(listOf(game("a", EventStatus.SCHEDULED, 1, startIn = 90 * 60_000L)), now))
        assertEquals(30, TodayRules.pollingMinutes(listOf(game("a", EventStatus.SCHEDULED, 1, startIn = 3 * 60 * 60_000L)), now))
        val ticker = TodayRules.ticker(
            listOf(
                game("soon2", EventStatus.SCHEDULED, 1, startIn = 2 * 60 * 60_000L), game("live2", EventStatus.LIVE, 1, startIn = -10 * 60_000L),
                game("soon1", EventStatus.SCHEDULED, 1, startIn = 60 * 60_000L), game("late", EventStatus.SCHEDULED, 1, startIn = 4 * 60 * 60_000L),
                game("live1", EventStatus.LIVE, 1, startIn = -60 * 60_000L), game("soon3", EventStatus.SCHEDULED, 1, startIn = 150 * 60_000L),
            ),
            now,
        )
        assertEquals(listOf("live1", "live2", "soon1", "soon2"), ticker.map { it.id })
    }
}
