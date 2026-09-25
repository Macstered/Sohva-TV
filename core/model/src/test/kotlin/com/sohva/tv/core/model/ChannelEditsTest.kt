package com.sohva.tv.core.model

import com.sohva.tv.core.model.channel.ChannelEdits
import com.sohva.tv.core.model.channel.ChannelMove
import com.sohva.tv.core.model.channel.ChannelPositions
import com.sohva.tv.core.model.channel.MoveTarget
import com.sohva.tv.core.model.channel.Placed
import com.sohva.tv.core.model.channel.ShownValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 21 §11 unit tests: shown values, the field rules and the move rule. */
class ChannelEditsTest {
    @Test
    fun customBeatsProviderAndBlankMeansNone() {
        val edits = ChannelEdits(customName = "Mine", customLogoUrl = "https://provider.example/l.png", customNumber = 7, manualEpgId = "x.epg")
        assertEquals("Mine", ShownValues.name("Theirs", edits))
        assertEquals("Theirs", ShownValues.name("Theirs", null))
        assertEquals("https://provider.example/l.png", ShownValues.logo("https://provider.example/p.png", edits))
        assertEquals(7, ShownValues.number(3, edits))
        assertEquals(3, ShownValues.number(3, ChannelEdits()))
        assertNull(ShownValues.number(0, null))
        assertEquals("x.epg", ShownValues.epgId("tvg", edits))
        assertEquals("tvg", ShownValues.epgId("tvg", null))
        assertNull(ChannelEdits.text("   ", ChannelEdits.NAME_MAX))
        assertEquals("a".repeat(100), ChannelEdits.text("a".repeat(150), ChannelEdits.NAME_MAX))
        assertTrue(ChannelEdits().isEmpty)
    }

    @Test
    fun theGroupKeyFollowsTheCustomGroup() {
        assertEquals("name:news and sport", ChannelEdits.groupKey("  News and SPORT "))
    }

    @Test
    fun numbersAreDigitsOnlyUpToFiveAndPositive() {
        assertEquals(12, ChannelEdits.number("12"))
        assertEquals(99_999, ChannelEdits.number("99999"))
        assertNull(ChannelEdits.number("100000"))
        assertNull(ChannelEdits.number("0"))
        assertNull(ChannelEdits.number("1a"))
        assertNull(ChannelEdits.number("-3"))
        assertNull(ChannelEdits.number(""))
    }

    private fun order(vararg shown: Boolean) = shown.mapIndexed { i, s -> Placed("c$i", ChannelPositions.initial(i), s) }

    @Test
    fun movingUpLandsJustBeforeTheShownNeighbourPastHiddenOnes() {
        // c0 shown, c1 and c2 hidden by the filters, c3 moving.
        val list = order(true, false, false, true)
        val target = ChannelMove.target(list, "c3", up = true) as MoveTarget.At
        // Just before c0, the neighbour on screen; c1 and c2 keep their order after it.
        assertTrue(target.position < list[0].position)
    }

    @Test
    fun movingDownLandsJustAfterTheShownNeighbour() {
        val list = order(true, false, true, false, true)
        val target = ChannelMove.target(list, "c0", up = false) as MoveTarget.At
        assertTrue(target.position > list[2].position && target.position < list[3].position)
    }

    @Test
    fun theEndsDoNothingAndAFullGapAsksForRenumbering() {
        val list = order(true, false, true)
        assertEquals(MoveTarget.None, ChannelMove.target(list, "c0", up = true))
        assertEquals(MoveTarget.None, ChannelMove.target(list, "c2", up = false))
        assertEquals(MoveTarget.None, ChannelMove.target(order(false, true), "c1", up = true))
        val tight = listOf(Placed("a", 10, true), Placed("b", 11, true), Placed("c", 12, true))
        assertEquals(MoveTarget.Renumber, ChannelMove.target(tight, "c", up = true))
        assertEquals(1_024L, ChannelPositions.between(null, null))
        assertNull(ChannelPositions.between(5, 6))
    }
}
