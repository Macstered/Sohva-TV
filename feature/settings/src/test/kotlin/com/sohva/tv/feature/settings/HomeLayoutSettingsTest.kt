package com.sohva.tv.feature.settings

import com.sohva.tv.core.model.home.HomeLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 02 HOME-FR-90: moves are drafts written once on OK, Back restores, switches and Reset write at once. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeLayoutSettingsTest {
    private class Fake(unavailable: Set<String> = emptySet()) : HomeLayoutServices {
        val stored = MutableStateFlow(HomeLayout.DEFAULT)
        val writes = mutableListOf<HomeLayout>()
        private val view = MutableStateFlow(HomeLayoutView("Kids", HomeLayout.DEFAULT, unavailable))
        override val current = view

        override suspend fun save(layout: HomeLayout) {
            writes += layout
            stored.value = layout
            view.value = view.value.copy(layout = layout)
        }

        val searches = mutableListOf<String>()

        override suspend fun findLists(text: String): ListFinding {
            searches += text
            return if (text == "private") ListFinding.Private else ListFinding.Found(listOf(ListChoice(26421, "Fictional favourites", "viewer-one", 30, 5)))
        }

        override suspend fun addList(list: ListChoice) = save(stored.value.withAdded(HomeLayout.traktList(list.id)))
    }

    private fun TestScope.settings(fake: Fake) = HomeLayoutSettings(fake, backgroundScope)

    @Test
    fun movesAreWrittenOnceWhenPlaced() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake()
        val s = settings(fake)
        s.pickUp(HomeLayout.RECENT)
        s.move(-1)
        s.move(0, to = 0)
        assertEquals(listOf("recent-channels", "continue-watching", "watch-next", "todays-sport", "recommended"), s.state.value.order)
        assertEquals("nothing is written while moving", 0, fake.writes.size)
        s.place()
        assertEquals(1, fake.writes.size)
        assertEquals("recent-channels", fake.stored.value.rows.first().id)
        assertNull(s.state.value.moving)
    }

    @Test
    fun backRestoresTheOrderAndWritesNothing() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake()
        val s = settings(fake)
        s.pickUp(HomeLayout.CONTINUE)
        s.move(3)
        s.cancelMove()
        assertEquals(HomeLayout.BUILT_IN, s.state.value.order)
        assertEquals(0, fake.writes.size)
        // Placing without a change writes nothing either.
        s.pickUp(HomeLayout.SPORT)
        s.place()
        assertEquals(0, fake.writes.size)
    }

    @Test
    fun aSwitchAndResetWriteAtOnce() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake()
        val s = settings(fake)
        s.toggle(HomeLayout.RECOMMENDED)
        assertFalse(fake.stored.value.isShown(HomeLayout.RECOMMENDED))
        s.reset()
        assertEquals(HomeLayout.DEFAULT, fake.stored.value)
        assertEquals(2, fake.writes.size)
    }

    @Test
    fun rowsTheProfileNeverHasAreNotListed() = runTest(UnconfinedTestDispatcher()) {
        val s = settings(Fake(unavailable = setOf(HomeLayout.WATCH_NEXT, HomeLayout.RECOMMENDED)))
        assertEquals(listOf("continue-watching", "todays-sport", "recent-channels"), s.state.value.order)
    }

    /** HOME-FR-99: the dialog searches what was typed and OK on a result adds that list, closing the dialog. */
    @Test
    fun aListIsFoundAndAdded() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake()
        val s = settings(fake)
        s.openListDialog()
        s.searchLists()
        assertEquals("nothing typed, nothing asked", 0, fake.searches.size)
        s.setListQuery("private")
        s.searchLists()
        assertEquals(ListFinding.Private, s.state.value.listDialog?.finding)
        s.setListQuery("https://trakt.tv/lists/26421")
        s.searchLists()
        val found = (s.state.value.listDialog?.finding as ListFinding.Found).lists.single()
        s.chooseList(found)
        assertNull(s.state.value.listDialog)
        assertEquals(listOf("trakt:list:26421"), fake.stored.value.added)
    }

    /** HOME-FR-94: Trakt rows are added at the end and removed again; at 8 nothing more is added. */
    @Test
    fun traktRowsAreAddedAndRemovedUpToEight() = runTest(UnconfinedTestDispatcher()) {
        val fake = Fake()
        val s = settings(fake)
        s.toggleAdded(HomeLayout.TRAKT_BOX_OFFICE)
        assertEquals(HomeLayout.TRAKT_BOX_OFFICE, s.state.value.order.last())
        assertEquals(listOf(HomeLayout.TRAKT_BOX_OFFICE), fake.stored.value.added)
        s.toggleAdded(HomeLayout.TRAKT_BOX_OFFICE)
        assertEquals(HomeLayout.DEFAULT, fake.stored.value)
        HomeLayout.ADDABLE.forEach(s::toggleAdded)
        assertEquals(HomeLayout.MAX_ADDED, fake.stored.value.added.size)
        assertEquals("the ninth changes nothing, so nothing is written", 2 + HomeLayout.MAX_ADDED, fake.writes.size)
        // HOME-FR-98: library only, on and off again, written at once.
        s.toggleLibraryOnly(HomeLayout.TRAKT_WATCHLIST_MOVIES)
        assertTrue(fake.stored.value.isLibraryOnly(HomeLayout.TRAKT_WATCHLIST_MOVIES))
        s.toggleLibraryOnly(HomeLayout.TRAKT_WATCHLIST_MOVIES)
        assertFalse(fake.stored.value.isLibraryOnly(HomeLayout.TRAKT_WATCHLIST_MOVIES))
        // Every row id Settings and Home can list has a title of its own.
        val titles = (HomeLayout.BUILT_IN + HomeLayout.ADDABLE).map { com.sohva.tv.ui.design.components.homeRowTitle(it) }
        assertEquals(titles.size, titles.toSet().size)
    }
}
