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
        val layout = HomeLayout.decode(" todays-sport , trakt:a-later-kind ,-todays-sport,continue-watching,watch-next,recent-channels,,")
        assertEquals(listOf("todays-sport", "continue-watching", "watch-next", "recent-channels", "recommended"), layout.rows.map { it.id })
        assertTrue("an added row is shown", layout.isShown("recommended"))
        assertTrue("the first of a repeat wins", layout.isShown("todays-sport"))
    }

    @Test
    fun addedTraktRowsAreKeptInOrderUpToEight() {
        val layout = HomeLayout.decode("trakt:boxoffice,continue-watching,-trakt:watchlist-shows")
        assertEquals(listOf("trakt:boxoffice", "continue-watching", "trakt:watchlist-shows"), layout.rows.take(3).map { it.id })
        assertFalse(layout.isShown("trakt:watchlist-shows"))
        assertEquals(listOf("trakt:boxoffice", "trakt:watchlist-shows"), layout.added)
        assertEquals(layout, HomeLayout.decode(layout.encode()))
        // Nine addable ids in the text: the ninth is dropped (HOME-FR-94).
        val all = HomeLayout.decode(HomeLayout.ADDABLE.joinToString(","))
        assertEquals(HomeLayout.ADDABLE.take(HomeLayout.MAX_ADDED), all.added)
    }

    @Test
    fun addingAndRemovingRows() {
        var layout = HomeLayout.DEFAULT.withAdded(HomeLayout.TRAKT_TRENDING_MOVIES)
        assertEquals(HomeLayout.TRAKT_TRENDING_MOVIES, layout.rows.last().id)
        assertTrue(layout.isShown(HomeLayout.TRAKT_TRENDING_MOVIES))
        assertEquals("a repeat adds nothing", layout, layout.withAdded(HomeLayout.TRAKT_TRENDING_MOVIES))
        assertEquals("an unknown id adds nothing", layout, layout.withAdded("trakt:a-later-kind"))
        assertEquals("built-in rows are never removed", layout, layout.withRemoved(HomeLayout.CONTINUE))
        assertNull(layout.withRemoved(HomeLayout.TRAKT_TRENDING_MOVIES).encode())
        HomeLayout.ADDABLE.forEach { layout = layout.withAdded(it) }
        assertEquals(HomeLayout.MAX_ADDED, layout.added.size)
    }

    /** HOME-FR-98: the library-only mark round-trips on added rows and is ignored on built-in ones. */
    @Test
    fun anAddedRowCanShowOnlyLibraryTitles() {
        val layout = HomeLayout.DEFAULT.withAdded(HomeLayout.TRAKT_TRENDING_MOVIES).withLibraryOnly(HomeLayout.TRAKT_TRENDING_MOVIES, true)
        assertTrue(layout.isLibraryOnly(HomeLayout.TRAKT_TRENDING_MOVIES))
        assertEquals("continue-watching,watch-next,todays-sport,recommended,recent-channels,trakt:trending-movies!", layout.encode())
        assertEquals(layout, HomeLayout.decode(layout.encode()))
        val hidden = layout.withShown(HomeLayout.TRAKT_TRENDING_MOVIES, false)
        assertEquals(hidden, HomeLayout.decode(hidden.encode()))
        assertTrue(hidden.encode()!!.endsWith("-trakt:trending-movies!"))
        assertEquals(layout, layout.withLibraryOnly(HomeLayout.CONTINUE, true))
        assertFalse(HomeLayout.decode("continue-watching!").isLibraryOnly(HomeLayout.CONTINUE))
        assertFalse(layout.withLibraryOnly(HomeLayout.TRAKT_TRENDING_MOVIES, false).isLibraryOnly(HomeLayout.TRAKT_TRENDING_MOVIES))
    }

    /** HOME-FR-99: a Trakt list by number is an added row like the others and counts towards the eight. */
    @Test
    fun traktListsAreAddedRowsByNumber() {
        val layout = HomeLayout.decode("trakt:list:26421!,continue-watching,trakt:list:abc,trakt:list:0,trakt:list:1234567890123")
        assertEquals(listOf("trakt:list:26421"), layout.added)
        assertTrue(layout.isLibraryOnly("trakt:list:26421"))
        assertEquals(layout, HomeLayout.decode(layout.encode()))
        assertEquals(26421L, HomeLayout.traktListId("trakt:list:26421"))
        assertEquals(null, HomeLayout.traktListId("trakt:trending-movies"))
        var many = HomeLayout.DEFAULT
        (1L..9L).forEach { many = many.withAdded(HomeLayout.traktList(it)) }
        assertEquals(HomeLayout.MAX_ADDED, many.added.size)
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
