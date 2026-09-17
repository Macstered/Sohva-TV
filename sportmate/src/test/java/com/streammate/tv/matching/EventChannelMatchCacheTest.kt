package com.streammate.tv.matching

import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.TodayEvent
import com.streammate.tv.core.model.TodayEventStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EventChannelMatchCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val event = TodayEvent(id = "event", sport = SportType.FOOTBALL, competition = "League", home = "Home", away = "Away", startEpochMillis = 1_000_000, startMinuteOfDay = 20, startLabel = "00:20", status = TodayEventStatus.SCHEDULED, statusLabel = "Upcoming", score = null, matchingChannels = 0)
    private val possible = EventChannelMatch("event", "channel", "ES - Home v Away", "programme", "Home v Away", 1_000_000, 0, ChannelMatchConfidence.POSSIBLE, 55, null, MatchCandidateSource.M3U_CHANNEL_NAME, true)

    @Test fun `restart restores matches but manual decisions remain in their own database`() = runTest {
        val file = temporary.newFile()
        EventChannelMatchCache(file) { 500 }.write(listOf(event), "generation", mapOf(event.id to listOf(possible.withDecision(ManualMatchDecision.CONFIRMED))))
        val restored = EventChannelMatchCache(file) { 501 }.read(listOf(event), "generation")
        assertEquals(listOf(possible), restored[event.id])
    }

    @Test fun `changed inputs kickoff display zone teams and expired entries invalidate matches`() = runTest {
        val file = temporary.newFile()
        val cache = EventChannelMatchCache(file) { 500 }
        cache.write(listOf(event), "old", mapOf(event.id to listOf(possible)))
        assertTrue(cache.read(listOf(event), "new").isEmpty())
        for (changed in listOf(event.copy(home = "Other"), event.copy(startEpochMillis = 2_000_000), event.copy(startMinuteOfDay = 60))) {
            assertTrue(cache.read(listOf(changed), "old").isEmpty())
        }
        assertTrue(EventChannelMatchCache(file) { 500 + 25 * 60 * 60_000L }.read(listOf(event), "old").isEmpty())
    }

    @Test fun `empty results survive restart and corruption becomes a cache miss`() = runTest {
        val file = temporary.newFile()
        EventChannelMatchCache(file).write(listOf(event), "same", mapOf(event.id to emptyList()))
        assertEquals(mapOf(event.id to emptyList<EventChannelMatch>()), EventChannelMatchCache(file).read(listOf(event), "same"))
        file.writeBytes(byteArrayOf(0, 1, 2))
        assertTrue(EventChannelMatchCache(file).read(listOf(event), "same").isEmpty())
    }

    @Test fun `country preferences only reorder matches of equal confidence`() {
        val available = possible.copy(channelId = "available", channelName = "UK - Home v Away", confidence = ChannelMatchConfidence.AVAILABLE)
        val es = possible.copy(channelId = "spanish")
        val ar = possible.copy(channelId = "argentina", channelName = "AR - Home v Away", score = 100)
        assertEquals(listOf("available", "spanish", "argentina"), EventChannelOrdering.sort(listOf(ar, es, available), listOf("ES", "AR")).map { it.channelId })
        assertEquals(listOf("available", "argentina", "spanish"), EventChannelOrdering.sort(listOf(ar, es, available), emptyList()).map { it.channelId })
    }
}
