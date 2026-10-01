package com.sohva.tv.feature.sport

import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import com.sohva.tv.feature.sport.pairing.PairingCache
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 60 §11 "Result cache" (mirrors beta 23's `EventChannelMatchCacheTest`). */
class PairingCacheTest {
    private val dir: File = Files.createTempDirectory("pairing").toFile().apply { deleteOnExit() }
    private val file = File(dir, PairingCache.FILE_NAME)
    private val now = 1_790_400_000_000L
    private val hour = 60 * 60_000L

    private fun game(id: Int, start: Long = now + hour) = SportEvent(
        "api-sports:football:$id", SportType.FOOTBALL, "39", "Premier League", null, Side("Home $id", null), Side("Away $id", null),
        start, 20 * 60, EventStatus.SCHEDULED, null, null, null,
    )

    private fun stream(game: SportEvent, channel: String) = StreamMatch(
        game.id, channel, "Channel $channel", "p-$channel", "Home – Away", MatchSource.GUIDE, game.startMillis, 0, true, 100, Confidence.POSSIBLE,
    )

    @Test
    fun aRestartRestoresResultsWithoutTheirDecisions() {
        val g = game(1)
        PairingCache(file).write("gen", listOf(g), mapOf(g.id to listOf(stream(g, "a").withDecision(Decision.CONFIRMED))), now)
        // A new instance is a restart: the file is all there is.
        val read = PairingCache(file).read("gen", listOf(g), now + hour)
        assertEquals(listOf(stream(g, "a")), read.getValue(g.id))
        assertEquals(null, read.getValue(g.id).single().decision)
    }

    @Test
    fun changedInputsKickOffZoneOrAgeAreMisses() {
        val g = game(1)
        val cache = PairingCache(file)
        cache.write("gen", listOf(g), mapOf(g.id to listOf(stream(g, "a"))), now)
        assertTrue(cache.read("other", listOf(g), now).isEmpty())
        assertTrue(cache.read("gen", listOf(g.copy(startMillis = g.startMillis + hour)), now).isEmpty())
        assertTrue(cache.read("gen", listOf(g.copy(startMinuteOfDay = 18 * 60)), now).isEmpty())
        assertTrue(cache.read("gen", listOf(g.copy(home = Side("Other", null))), now).isEmpty())
        assertTrue(cache.read("gen", listOf(g), now + 25 * hour).isEmpty())
        assertTrue("saved in the future", cache.read("gen", listOf(g), now - hour).isEmpty())
    }

    @Test
    fun emptyResultsSurviveAndOtherGamesAreKept() {
        val a = game(1)
        val b = game(2)
        val cache = PairingCache(file)
        cache.write("gen", listOf(a), mapOf(a.id to emptyList()), now)
        cache.write("gen", listOf(b), mapOf(b.id to listOf(stream(b, "x"))), now)
        val read = PairingCache(file).read("gen", listOf(a, b), now)
        assertEquals(emptyList<StreamMatch>(), read.getValue(a.id))
        assertEquals(1, read.getValue(b.id).size)
        // A new generation drops the old one's games.
        cache.write("gen2", listOf(b), mapOf(b.id to emptyList()), now)
        assertTrue(cache.read("gen2", listOf(a), now).isEmpty())
    }

    @Test
    fun corruptOrOldFilesAreMisses() {
        val g = game(1)
        val cache = PairingCache(file)
        cache.write("gen", listOf(g), mapOf(g.id to listOf(stream(g, "a"))), now)
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size / 2))
        assertTrue(cache.read("gen", listOf(g), now).isEmpty())
        // An older format version.
        bytes[7] = 0
        file.writeBytes(bytes)
        assertTrue(cache.read("gen", listOf(g), now).isEmpty())
        file.writeBytes(ByteArray(3) { 1 })
        assertTrue(cache.read("gen", listOf(g), now).isEmpty())
    }

    @Test
    fun resultsFromBeforeTheProviderClockFixMustBeRescored() {
        val g = game(1)
        val cache = PairingCache(file)
        cache.write("gen", listOf(g), mapOf(g.id to listOf(stream(g, "a"))), now)
        val bytes = file.readBytes()
        // The frozen beta.12 cache header is SSPC + version 1. Payload layout is unchanged.
        bytes[7] = 1
        file.writeBytes(bytes)
        assertTrue("old automatic confidence must not survive the update", cache.read("gen", listOf(g), now).isEmpty())
    }

    @Test
    fun resultsFromBeforeTheBidirectionalClubAliasesMustBeRescored() {
        val g = game(1)
        val cache = PairingCache(file)
        cache.write("gen", listOf(g), mapOf(g.id to listOf(stream(g, "a"))), now)
        val bytes = file.readBytes()
        // Frozen beta.13 and beta.14 use format 2, before the TPS Turku alias existed.
        bytes[7] = 2
        file.writeBytes(bytes)
        assertTrue("older automatic results must be rematched", cache.read("gen", listOf(g), now).isEmpty())
    }

    @Test
    fun theOldestGamesGoFirstPastTheLimit() {
        val cache = PairingCache(file)
        val games = (1..520).map { game(it) }
        games.chunked(40).forEach { chunk -> cache.write("gen", chunk, chunk.associate { it.id to emptyList() }, now) }
        val read = cache.read("gen", games, now)
        assertEquals(512, read.size)
        assertTrue(games.take(8).none { it.id in read })
    }
}
