package com.streammate.tv.feature.guide

import com.streammate.tv.iptv.repository.GuideTimelineChannel
import com.streammate.tv.iptv.repository.GuideTimelineProgramme
import com.streammate.tv.feature.common.ChannelDial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideWindowedReadTest {
    private val ids = (0 until 1_000).map { "c$it" }

    @Test
    fun `no EPG query is requested before channel rows are laid out`() {
        assertTrue(programmeWindowIds(ids, firstVisible = 0, visibleCount = 0).isEmpty())
    }

    @Test
    fun `every time page reads four hours including the entire visible grid`() {
        val now = 1_800_000_000_000L
        val start = GuideTimeWindow.nowStart(now)
        listOf(start, start - GuideTimeWindow.PAGE_MILLIS, start + GuideTimeWindow.DAY_MILLIS).forEach { page ->
            val range = programmeReadWindow(page)
            assertEquals(4 * 3_600_000L, range.last + 1 - range.first)
            assertTrue(page in range)
            assertTrue(page + TIMELINE_WINDOW_MILLIS - 1 in range)
        }
    }

    @Test
    fun `the window covers the visible rows and a margin, snapped to the step`() {
        val window = programmeWindowIds(ids, firstVisible = 205, visibleCount = 12, margin = 30, step = 10)
        assertEquals("c170", window.first())
        assertEquals("c249", window.last())
    }

    @Test
    fun `the window clamps to the list at both ends`() {
        assertEquals(ids.take(50), programmeWindowIds(ids, firstVisible = 0, visibleCount = 10, margin = 30, step = 10))
        val tail = programmeWindowIds(ids, firstVisible = 995, visibleCount = 5, margin = 30, step = 10)
        assertEquals("c999", tail.last())
        assertTrue(tail.size in 30..50)
        assertTrue(programmeWindowIds(emptyList(), 0, 10).isEmpty())
    }

    @Test
    fun `a row-by-row scroll inside the step reads the same window`() {
        val a = programmeWindowIds(ids, firstVisible = 203, visibleCount = 12)
        val b = programmeWindowIds(ids, firstVisible = 207, visibleCount = 12)
        assertEquals(a, b)
    }

    @Test
    fun `merging fills only the rows read for and leaves the rest as they are`() {
        val rows = listOf(channel("a"), channel("b"), channel("c"))
        val programme = GuideTimelineProgramme(
            id = "p", title = "Now", subtitle = null, description = null, categories = emptyList(),
            startEpochMillis = 0L, stopEpochMillis = 1L,
        )
        val merged = mergeProgrammes(rows, mapOf("b" to listOf(programme)))
        assertTrue(merged[0].programmes.isEmpty())
        assertEquals(listOf(programme), merged[1].programmes)
        assertTrue(merged[2].programmes.isEmpty())
        assertSame(rows[0], mergeProgrammes(rows, emptyMap())[0])
    }

    @Test fun largeGuideCopiesOnlyRequestedRowsAndBoundsProgrammeRetention() {
        var reads = 0
        val channels = object : AbstractList<GuideTimelineChannel>() {
            override val size = 56_000
            override fun get(index: Int): GuideTimelineChannel { reads++; return channel("c$index") }
        }
        var cache = emptyMap<String, List<GuideTimelineProgramme>>()
        repeat(100) { window -> cache = retainProgrammeWindow(cache, (window * 10 until window * 10 + 80).map { channel("c$it") }) }
        assertEquals(240, cache.size)
        assertTrue("c0" !in cache)
        assertTrue("c1069" in cache)
        val started = System.nanoTime()
        val merged = mergeProgrammes(channels, cache)
        assertEquals(0, reads)
        repeat(10) { merged[1000 + it] }
        assertEquals(10, reads)
        repeat(100) { assertSame(merged[1000], merged[1000]) }
        assertEquals("Re-reading a prefetched row must reuse it", 10, reads)
        println("56000-channel guide: overlay plus ten visible rows ${(System.nanoTime() - started) / 1_000_000.0} ms; retained ${cache.size} programme rows")
    }

    @Test fun focusLookupsAtTheEndOfALargeGuideDoNotScanChannelRows() {
        var reads = 0
        val channels = object : AbstractList<GuideTimelineChannel>() {
            override val size = 56_000
            override fun get(index: Int): GuideTimelineChannel { reads++; return channel("c$index") }
        }
        val indexed = GuideChannelRows(channels)
        reads = 0 // Index construction belongs to the background ordering pipeline.
        val merged = mergeProgrammes(channels, emptyMap())
        repeat(100) {
            assertEquals(55_999, indexed.indexOf("c55999"))
            assertEquals("c55999", merged[indexed.indexOf("c55999")!!].id)
            assertEquals(null, indexed.indexOf("missing"))
        }
        assertEquals("One row access per focus change, independent of lineup size", 100, reads)
    }

    @Test fun indexedDialKeepsProviderNumberAndPositionalFallbackSemantics() {
        val channels = listOf(channel("a").copy(channelNumber = 4), channel("b"),
            channel("c").copy(channelNumber = 4), channel("d"), channel("e").copy(channelNumber = 7))
        val indexed = GuideChannelRows(channels)
        for (number in 0..8) {
            assertEquals(ChannelDial.indexFor(channels.map { it.channelNumber }, number), indexed.indexForNumber(number))
        }
    }

    @Test fun aNewProgrammeWindowCannotReuseRowsFromTheOldWindow() {
        val rows = listOf(channel("a"))
        val programme = GuideTimelineProgramme("p", "Now", null, null, emptyList(), 0, 1)
        val first = mergeProgrammes(rows, mapOf("a" to listOf(programme)))
        assertEquals("Now", first[0].programmes.single().title)
        val updated = mergeProgrammes(rows, mapOf("a" to listOf(programme.copy(title = "Updated"))))
        assertEquals("Updated", updated[0].programmes.single().title)
        assertSame(rows[0], mergeProgrammes(rows, emptyMap())[0])
    }

    @Test fun aWindowReadAgainKeepsTheSchedulesAlreadyHeld() {
        val held = listOf(programme("p1"), programme("p2"))
        val cache = retainProgrammeWindow(emptyMap(), listOf(channel("a").copy(programmes = held), channel("b")))
        // The window moved a step: "a" comes back equal but as new objects, "b" with a listing it lacked.
        val again = listOf(programme("p1"), programme("p2"))
        val arrived = listOf(programme("p3"))
        val next = retainProgrammeWindow(cache, listOf(channel("a").copy(programmes = again), channel("b").copy(programmes = arrived)))
        assertSame("An unchanged schedule must stay the object the grid holds", held, next["a"])
        assertSame(arrived, next["b"])
        // A listing that did change replaces the one held, and recency still moves.
        val corrected = listOf(programme("p1"), programme("p2").copy(title = "Corrected"))
        val last = retainProgrammeWindow(next, listOf(channel("a").copy(programmes = corrected)))
        assertSame(corrected, last["a"])
        assertEquals(listOf("b", "a"), last.keys.toList())
    }

    @Test fun rowsWhoseProgrammesDidNotChangeKeepTheirObjectBetweenArrivals() {
        val rows = listOf(channel("a"), channel("b"), channel("c"))
        val merger = ProgrammeMerger()
        var cache = retainProgrammeWindow(emptyMap(), listOf(channel("b").copy(programmes = listOf(programme("p1")))))
        val first = merger.merge(rows, cache)
        val shown = first[1]
        assertEquals("p1", shown.programmes.single().id)

        // The next window re-reads "b" unchanged and brings "c" its programmes.
        cache = retainProgrammeWindow(cache, listOf(
            channel("b").copy(programmes = listOf(programme("p1"))),
            channel("c").copy(programmes = listOf(programme("p2"))),
        ))
        val second = merger.merge(rows, cache)
        assertTrue("Each arrival is a new list, so the grid sees it", first !== second)
        assertSame("A row nothing happened to must be the object already on screen", shown, second[1])
        assertSame(rows[0], second[0])
        assertEquals("p2", second[2].programmes.single().id)

        // A changed listing is a new row, and so is any row of a reordered or reloaded list.
        cache = retainProgrammeWindow(cache, listOf(channel("b").copy(programmes = listOf(programme("p1").copy(title = "Corrected")))))
        val third = merger.merge(rows, cache)
        assertEquals("Corrected", third[1].programmes.single().title)
        assertSame(second[2], third[2])
        val reloaded = merger.merge(rows.toList(), cache)
        assertEquals(third[2], reloaded[2])
        assertTrue("Rows carried over by index must never cross into another list", third[2] !== reloaded[2])
    }

    @Test fun overlaysDoNotKeepEveryOverlayBeforeThemAlive() {
        val rows = listOf(channel("a"))
        val cache = mapOf("a" to listOf(programme("p1")))
        var overlay = mergeProgrammes(rows, cache)
        val references = ArrayList<java.lang.ref.WeakReference<Any>>()
        repeat(50) {
            overlay[0]
            references += java.lang.ref.WeakReference<Any>(overlay)
            overlay = mergeProgrammes(rows, cache, overlay)
        }
        overlay[0]
        // A collection is only ever requested, so ask until it has happened.
        var collected = 0
        for (attempt in 1..40) {
            System.gc()
            collected = references.dropLast(1).count { it.get() == null }
            if (collected > 40) break
            Thread.sleep(25)
        }
        assertTrue("Earlier overlays must be collectable, $collected of 49 were", collected > 40)
    }

    private fun programme(id: String) = GuideTimelineProgramme(
        id = id, title = id, subtitle = null, description = null, categories = listOf("News"),
        startEpochMillis = 0L, stopEpochMillis = 1L,
    )

    private fun channel(id: String) = GuideTimelineChannel(
        sourceId = "s", sourceName = "S", sourcePriority = 0, id = id, name = id, groupTitle = "G",
        logoUrl = null, playlistOrder = 0, catchupType = null, catchupSource = null, catchupDays = null,
        programmes = emptyList(),
    )
}
