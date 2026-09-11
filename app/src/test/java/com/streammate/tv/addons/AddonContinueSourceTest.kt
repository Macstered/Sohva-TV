package com.streammate.tv.addons

import com.sohva.tv.addons.*
import org.junit.Assert.*
import org.junit.Test

class AddonContinueSourceTest {
    private val video = AddonMediaKey("movie", "fixture")
    private val playable = AddonSourceParser.streams("""{"streams":[{"name":"Fixture source","url":"https://example.invalid/video"}]}""").single()
    private val unsupported = AddonSourceParser.streams("""{"streams":[{"name":"Torrent","infoHash":"0123456789012345678901234567890123456789"}]}""").single()
    private fun result(id: String, position: Int, items: List<AddonStream> = listOf(playable), status: AddonSourceStatus = AddonSourceStatus.READY) =
        AddonSourceResult(id, "Fixture provider", position, status, items, revision = 4)

    @Test fun providerPriorityWinsOverResponseArrivalOrder() {
        val choice = firstPlayableSource(listOf(result("second", 1), result("first", 0)), video, "")!!
        assertEquals("first", choice.installationId)
        assertEquals(4L, choice.revision)
        assertSame(video, choice.video)
    }
    @Test fun explicitScraperFilterIsRespected() {
        val results = listOf(result("first", 0), result("second", 1))
        assertEquals("second", firstPlayableSource(results, video, "second")!!.installationId)
        assertNull(firstPlayableSource(results, video, "missing"))
    }
    @Test fun unsupportedAndFailedResultsAreNotStarted() {
        val results = listOf(result("unsupported", 0, listOf(unsupported)), result("failed", 1, status = AddonSourceStatus.FAILED), result("ready", 2))
        assertEquals("ready", firstPlayableSource(results, video, "")!!.installationId)
        assertNull(firstPlayableSource(results, video, "unsupported"))
        assertNull(firstPlayableSource(emptyList(), video, ""))
    }
    @Test fun providerResultOrderSelectsFirstPlayableOption() {
        val next = AddonSourceParser.streams("""{"streams":[{"name":"Alternative","url":"https://example.invalid/alternative"}]}""").single()
        assertSame(playable, firstPlayableSource(listOf(result("provider", 0, listOf(unsupported, playable, next))), video, "")!!.stream)
    }
}
