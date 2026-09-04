package com.streammate.tv.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoFrameRateTest {

    /** What a Shield reports on a typical television. */
    private val shieldModes = listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)

    @Test
    fun `matches the rate the content is actually in`() {
        assertEquals(50f, AutoFrameRate.pick(shieldModes, 50f))
        assertEquals(60f, AutoFrameRate.pick(shieldModes, 60f))
        assertEquals(25f, AutoFrameRate.pick(shieldModes, 25f))
    }

    @Test
    fun `handles the broadcast rates that are not whole numbers`() {
        // 23.976, 29.97 and 59.94 are what film and NTSC actually arrive as.
        assertEquals(23.976f, AutoFrameRate.pick(shieldModes, 23.976f))
        assertEquals(29.97f, AutoFrameRate.pick(shieldModes, 29.97f))
        assertEquals(59.94f, AutoFrameRate.pick(shieldModes, 59.94f))
    }

    @Test
    fun `falls back to a whole multiple when the exact rate is missing`() {
        // A panel that only does 60 can still show 30 fps evenly, two refreshes
        // per frame. It cannot show 50 evenly, which is the case that matters.
        assertEquals(60f, AutoFrameRate.pick(listOf(60f), 30f))
        assertEquals(50f, AutoFrameRate.pick(listOf(50f, 60f), 25f))
    }

    @Test
    fun `prefers the lower multiple when both would work`() {
        assertEquals(24f, AutoFrameRate.pick(listOf(24f, 48f, 60f), 24f))
        assertEquals(25f, AutoFrameRate.pick(listOf(25f, 50f), 25f))
    }

    @Test
    fun `leaves the display alone when nothing divides evenly`() {
        // 50 fps on a panel that only does 60 is the stutter people notice, but
        // switching to 60 fixes nothing and costs a second of black screen.
        assertNull(AutoFrameRate.pick(listOf(60f), 50f))
        assertNull(AutoFrameRate.pick(listOf(50f, 60f), 23.976f))
    }

    @Test
    fun `an unknown frame rate is not a reason to switch`() {
        assertNull(AutoFrameRate.pick(shieldModes, 0f))
        assertNull(AutoFrameRate.pick(shieldModes, -1f))
    }

    @Test
    fun `a display with nothing to offer is handled`() {
        assertNull(AutoFrameRate.pick(emptyList(), 50f))
        assertNull(AutoFrameRate.pick(listOf(0f), 50f))
    }
}
