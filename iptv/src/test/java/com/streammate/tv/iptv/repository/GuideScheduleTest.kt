package com.streammate.tv.iptv.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideScheduleTest {
    @Test
    fun `same-start provider variants become one richer programme`() {
        val older = programme(id = "old", start = 1_000L, stop = 2_000L)
        val corrected = programme(
            id = "corrected",
            start = 1_000L,
            stop = 2_000L,
            description = "Synopsis",
        )

        assertEquals(listOf(corrected), deduplicateGuideSchedule(listOf(older, corrected)))
    }

    @Test
    fun `adjacent programmes with a shared boundary remain separate`() {
        val first = programme(id = "first", start = 1_000L, stop = 2_000L)
        val second = programme(id = "second", start = 2_000L, stop = 3_000L)

        assertEquals(listOf(first, second), deduplicateGuideSchedule(listOf(second, first)))
    }

    @Test
    fun `invalid zero-length provider rows are omitted`() {
        val valid = programme(id = "valid", start = 1_000L, stop = 2_000L)
        val invalid = programme(id = "invalid", start = 2_000L, stop = 2_000L)

        assertEquals(listOf(valid), deduplicateGuideSchedule(listOf(invalid, valid)))
    }

    private fun programme(
        id: String,
        start: Long,
        stop: Long,
        description: String? = null,
    ) = GuideTimelineProgramme(
        id = id,
        title = id,
        subtitle = null,
        description = description,
        categories = emptyList(),
        startEpochMillis = start,
        stopEpochMillis = stop,
    )
}
