package com.streammate.tv.feature.guide

import com.streammate.tv.iptv.repository.GuideTimelineChannel
import com.streammate.tv.iptv.repository.GuideTimelineProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideWindowedReadTest {
    private val ids = (0 until 1_000).map { "c$it" }

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
        assertSame(rows, mergeProgrammes(rows, emptyMap()))
    }

    private fun channel(id: String) = GuideTimelineChannel(
        sourceId = "s", sourceName = "S", sourcePriority = 0, id = id, name = id, groupTitle = "G",
        logoUrl = null, playlistOrder = 0, catchupType = null, catchupSource = null, catchupDays = null,
        programmes = emptyList(),
    )
}
