package com.sohva.tv.core.model.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 02 HOME-FR-86: the stored text, its reading rules and the edits Settings › Home makes. */
class HomeLayoutTest {
    @Test
    fun noValueIsTheDefaultAndTheDefaultIsStoredAsNoValue() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.decode(null))
        assertEquals(HomeLayout.DEFAULT, HomeLayout.decode(""))
        assertEquals(HomeLayout.BUILT_IN, HomeLayout.DEFAULT.shownIds)
        assertNull(HomeLayout.DEFAULT.encode())
    }

    @Test
    fun theStoredOrderAndHiddenRowsRoundTrip() {
        val text = "recent-channels,-watch-next,continue-watching,todays-sport,-recommended"
        val layout = HomeLayout.decode(text)
        assertEquals(listOf("recent-channels", "watch-next", "continue-watching", "todays-sport", "recommended"), layout.rows.map { it.id })
        assertEquals(listOf("recent-channels", "continue-watching", "todays-sport"), layout.shownIds)
        assertFalse(layout.isShown("watch-next"))
        assertEquals(text, layout.encode())
        assertEquals(layout, HomeLayout.decode(layout.encode()))
    }

    @Test
    fun unknownAndRepeatedIdsAreDroppedAndAMissingBuiltInRowIsAddedAtTheEnd() {
        // A later version's row kind, a repeat and a row this text lacks (Recommended).
        val layout = HomeLayout.decode(" todays-sport , trakt:list:42 ,-todays-sport,continue-watching,watch-next,recent-channels,,")
        assertEquals(listOf("todays-sport", "continue-watching", "watch-next", "recent-channels", "recommended"), layout.rows.map { it.id })
        assertTrue("an added row is shown", layout.isShown("recommended"))
        assertTrue("the first of a repeat wins", layout.isShown("todays-sport"))
    }

    @Test
    fun textThatIsNoLayoutIsTheDefault() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.decode("x".repeat(5_000)))
        assertEquals(HomeLayout.DEFAULT, HomeLayout.decode("nothing,we,know"))
    }

    @Test
    fun settingsEditsKeepEveryRow() {
        val hidden = HomeLayout.DEFAULT.withShown("todays-sport", false)
        assertEquals("continue-watching,watch-next,-todays-sport,recommended,recent-channels", hidden.encode())
        val moved = hidden.withOrder(listOf("recent-channels", "continue-watching"))
        assertEquals(listOf("recent-channels", "continue-watching", "watch-next", "todays-sport", "recommended"), moved.rows.map { it.id })
        assertFalse("the order keeps each row's switch", moved.isShown("todays-sport"))
        // Showing everything again in the default order is the default: stored as no value.
        assertNull(moved.withShown("todays-sport", true).withOrder(HomeLayout.BUILT_IN).encode())
    }
}
