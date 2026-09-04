package com.streammate.tv.demo

import com.streammate.tv.core.model.FootballIncidentKind
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSportsRepositoryTest {
    @Test
    fun fictionalFeedIsCompleteAndNetworkIndependent() = runBlocking {
        val repository = DemoSportsRepository().apply {
            updateArtwork(
                summit = "android.resource://demo/summit",
                aurora = "android.resource://demo/aurora",
                harbour = "android.resource://demo/harbour",
                frostholm = "android.resource://demo/frostholm",
            )
        }
        val zoneId = ZoneId.of("Europe/Helsinki")
        val snapshot = repository.footballEvents(
            date = LocalDate.now(zoneId),
            zoneId = zoneId,
            selectedCompetitionIds = setOf("demo-premier", "demo-cup"),
        )

        assertEquals("Sohva TV Demo", snapshot.source)
        assertEquals("hit", snapshot.cacheState)
        assertTrue(snapshot.events.any { it.id == DemoSportsRepository.LIVE_EVENT_ID })
        assertTrue(snapshot.events.all { it.homeLogoUrl?.startsWith("android.resource://") == true })
        assertTrue(snapshot.events.all { it.awayLogoUrl?.startsWith("android.resource://") == true })

        val details = repository.footballIncidents(DemoSportsRepository.LIVE_EVENT_ID)
        assertEquals(3, details.incidents.count { it.kind == FootballIncidentKind.GOAL })
        assertTrue(details.incidents.all { it.eventId == DemoSportsRepository.LIVE_EVENT_ID })
    }

    @Test
    fun scheduleAnchorAlwaysFallsOnAnExactHour() {
        val input = 1_725_281_177_321L
        val result = DemoTime.currentHourStart(input)

        assertEquals(0L, result % (60 * 60_000L))
        assertTrue(result <= input)
        assertTrue(input - result < 60 * 60_000L)
    }
}
