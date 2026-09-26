package com.sohva.tv.feature.sport

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.TodayLists
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.sport.provider.DemoSportDays
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 60 SPORT-57: the demo build's fictional day fills every section of Today for each followed sport. */
class DemoSportDaysTest {
    private val now = 1_790_445_600_000L
    private val clock = object : Clock {
        override fun wallMillis(): Long = now
        override fun monotonicNanos(): Long = 0
    }

    @Test
    fun aDemoDayHasLiveLaterAndFinishedGamesThatPassTheFollows() = runBlocking {
        val follows = SportFollows.DEFAULT
        val zone = ZoneId.of("Europe/Helsinki")
        val events = follows.feeds.flatMap { DemoSportDays(clock).day(it, LocalDate.of(2026, 9, 26), zone, follows.competitionIds(it)).events }
        val sections = TodayLists.sections(events)
        assertEquals(follows.feeds.size, sections.live.size)
        assertEquals(2 * follows.feeds.size, sections.later.size)
        assertEquals(follows.feeds.size, sections.finished.size)
        assertTrue(events.filter { it.sport == SportType.FOOTBALL }.all { it.competitionId in follows.competitionIds(SportType.FOOTBALL)!! })
        assertTrue(events.first { it.status == EventStatus.LIVE }.startMillis < now)
    }
}
