package com.sohva.tv.feature.library

import com.sohva.tv.core.data.database.WallRow
import com.sohva.tv.core.data.vod.Rail
import com.sohva.tv.core.data.vod.RailGroup
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallItem
import com.sohva.tv.core.data.vod.WallRoom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first visit to a wall opens on History (spec 40 VOD-FR-49) unless nothing has been watched
 * yet: then it moves on, once, to the first group, so a new viewer sees titles and not "No titles
 * in this group." Focus follows only while it is still where the screen put it (AGENTS §5 rule 2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmptyHistoryStartTest {
    private val main = StandardTestDispatcher()

    private class Env(
        private val history: List<WallItem>,
        private val groups: List<RailGroup>,
        private val titles: List<WallItem> = listOf(item(1), item(2)),
    ) : LibraryEnvironment {
        /** Completed by the test to let the groups arrive, as after a slow read. */
        val railGate = CompletableDeferred<Unit>()
        override val room: WallRoom = WallRoom.MOVIES
        override fun changes(): Flow<Unit> = emptyFlow()
        override suspend fun rail(): Rail {
            railGate.await()
            return Rail(groups, historyShown = true, historyPosition = null, manual = false)
        }
        override suspend fun genreCounts(): Map<String, Int> = emptyMap()
        override suspend fun page(destination: WallDestination, search: String, from: WallItem?, forward: Boolean, limit: Int): List<WallItem> =
            if (destination == WallDestination.History) history else titles
        override suspend fun watched(films: List<Pair<String, String?>>): Set<String> = emptySet()
        override suspend fun lookUpVisible(items: List<WallItem>) = Unit
        override suspend fun tvmazeCredit(): Boolean = false
        override suspend fun refresh(): RefreshNote = RefreshNote.NoSource
        override fun open(item: WallItem) = Unit
        override fun openManager(group: String?) = Unit
        override fun leave() = Unit
    }

    private companion object {
        fun item(n: Int) = WallItem(WallRow(n.toLong(), "movie:s:$n", "s", "T$n", "t$n", null, null, null, 0, null))
        val drama = RailGroup("Drama", listOf(1L), 2)
        val news = RailGroup("News", listOf(2L), 2)
    }

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun anEmptyHistoryMovesOnToTheFirstGroupAndFocusFollowsFromHistory() = runTest(main) {
        Dispatchers.setMain(main)
        val env = Env(history = emptyList(), groups = listOf(drama, news))
        val model = LibraryModel(env)
        runCurrent()
        assertEquals(LibraryModel.HISTORY_KEY, model.selected.value)
        // The screen has put focus on History, as on every first entry.
        model.railFocused()
        model.historyRowFocused(true)
        env.railGate.complete(Unit)
        runCurrent()
        assertEquals("group:drama", model.selected.value)
        assertEquals(2, model.wall.value.window.items.size)
        assertTrue("focus follows to the selected row, once", model.takeHistoryFollow())
        assertTrue(!model.takeHistoryFollow())
    }

    @Test
    fun focusTheViewerMovedIsNotTakenBack() = runTest(main) {
        Dispatchers.setMain(main)
        val env = Env(history = emptyList(), groups = listOf(drama, news))
        val model = LibraryModel(env)
        runCurrent()
        model.railFocused()
        model.historyRowFocused(true)
        // The viewer walked up to Options before the groups arrived.
        model.historyRowFocused(false)
        env.railGate.complete(Unit)
        runCurrent()
        assertEquals("the wall still shows titles", "group:drama", model.selected.value)
        assertTrue("but focus stays where the viewer put it", !model.takeHistoryFollow())
    }

    @Test
    fun aHistoryWithTitlesStaysSelected() = runTest(main) {
        Dispatchers.setMain(main)
        val env = Env(history = listOf(item(9)), groups = listOf(drama))
        val model = LibraryModel(env)
        runCurrent()
        model.railFocused()
        model.historyRowFocused(true)
        env.railGate.complete(Unit)
        runCurrent()
        assertEquals(LibraryModel.HISTORY_KEY, model.selected.value)
    }

    @Test
    fun aChoiceTheViewerMadeIsKept() = runTest(main) {
        Dispatchers.setMain(main)
        val env = Env(history = emptyList(), groups = listOf(drama, news))
        val model = LibraryModel(env)
        runCurrent()
        model.railFocused()
        model.historyRowFocused(true)
        // OK on History itself, before the groups arrive: the viewer asked for History.
        model.select(LibraryModel.HISTORY_KEY)
        env.railGate.complete(Unit)
        runCurrent()
        assertEquals(LibraryModel.HISTORY_KEY, model.selected.value)
    }

    /** No groups: the rail's All row is the first row, and its empty wall is where "nothing imported yet" is said. */
    @Test
    fun aLibraryWithNothingImportedEndsOnTheEmptyAllRow() = runTest(main) {
        Dispatchers.setMain(main)
        val env = Env(history = emptyList(), groups = emptyList(), titles = emptyList())
        val model = LibraryModel(env)
        runCurrent()
        env.railGate.complete(Unit)
        runCurrent()
        assertEquals(WallDestination.AllGroups, model.wall.value.destination)
        assertTrue(model.wall.value.current && model.wall.value.window.items.isEmpty())
    }
}
