package com.streammate.tv.feature.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideSourceSelectionTest {
    @Test
    fun `current channel source takes priority`() {
        assertEquals(
            "current",
            preferredGuideSourceId(
                sourceIds = listOf("default", "saved", "current"),
                initialChannelSourceId = "current",
                savedSourceId = "saved",
                lastChannelSourceId = "default",
            ),
        )
    }

    @Test
    fun `saved source is restored before last watched fallback`() {
        assertEquals(
            "saved",
            preferredGuideSourceId(
                sourceIds = listOf("default", "saved", "watched"),
                initialChannelSourceId = null,
                savedSourceId = "saved",
                lastChannelSourceId = "watched",
            ),
        )
    }

    @Test
    fun `removed sources fall back to last watched then first available`() {
        assertEquals(
            "watched",
            preferredGuideSourceId(
                sourceIds = listOf("default", "watched"),
                initialChannelSourceId = "removed-current",
                savedSourceId = "removed-saved",
                lastChannelSourceId = "watched",
            ),
        )
        assertEquals(
            "default",
            preferredGuideSourceId(
                sourceIds = listOf("default"),
                initialChannelSourceId = null,
                savedSourceId = "removed",
                lastChannelSourceId = null,
            ),
        )
        assertNull(preferredGuideSourceId(emptyList(), null, null, null))
    }
}
