package com.sohva.tv.core.model

import com.sohva.tv.core.model.player.DisplayModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Frame-rate matching's mode requests (spec 30 PLAY-FR-103..104) across channel changes. */
class DisplayModesTest {
    private val modes = listOf(DisplayModes.Mode(1, 60f), DisplayModes.Mode(2, 50f), DisplayModes.Mode(3, 24f))
    private val original = 0

    /** Plays [rates] in order from the original preference; the mode id of every request made. */
    private fun requests(vararg rates: Float?): List<Int> {
        var preferred = original
        val made = ArrayList<Int>()
        for (rate in rates) {
            DisplayModes.target(rate, modes, original, preferred)?.let {
                made += it
                preferred = it
            }
        }
        return made
    }

    @Test
    fun aKnownRateRequestsItsModeOnce() {
        assertEquals(listOf(2), requests(25f, 25f))
        assertEquals(2, DisplayModes.target(50f, modes, original, preferred = original))
        assertNull(DisplayModes.target(50f, modes, original, preferred = 2))
    }

    @Test
    fun zappingBetweenChannelsOfTheSameRateKeepsTheDisplayAlone() {
        // The old stream's tracks clear before the next channel's arrive: the rate is unknown in between.
        assertEquals(listOf(2), requests(25f, null, 25f, null, 50f))
    }

    @Test
    fun anUnknownRateNeverRequestsAMode() {
        assertNull(DisplayModes.target(null, modes, original, preferred = 2))
        assertEquals(emptyList<Int>(), requests(null, null))
    }

    @Test
    fun aDifferentRateSwitchesAndANonMatchingRateRestoresTheOriginal() {
        assertEquals(listOf(2, 3), requests(25f, null, 23.976f))
        // 30 fps fits 60 Hz; 29 fps fits nothing, so the original preference comes back.
        assertEquals(listOf(2, 1, original), requests(50f, null, 30f, null, 29f))
    }
}
