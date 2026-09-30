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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

/** VOD-FR-18: a slow refresh cannot remove a page that arrived while its read was pending. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RefreshPagingRaceTest {
    private val main = StandardTestDispatcher()

    private class Env : LibraryEnvironment {
        override val room: WallRoom = WallRoom.SERIES
        val updates = MutableSharedFlow<Unit>()
        val refreshEntered = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        var gateRefresh = false

        override fun changes(): Flow<Unit> = updates
        override suspend fun rail(): Rail = Rail(listOf(RailGroup("Drama", listOf(1L), 400)), true, null, false)
        override suspend fun genreCounts(): Map<String, Int> = emptyMap()
        override suspend fun page(destination: WallDestination, search: String, from: WallItem?, forward: Boolean, limit: Int): List<WallItem> {
            if (destination == WallDestination.History) return emptyList()
            if (gateRefresh && from?.row?.id == -1L) {
                refreshEntered.complete(Unit)
                releaseRefresh.await()
            }
            val end = if (forward) 400 else from?.row?.id?.toInt() ?: 400
            val first = if (forward) (from?.row?.id?.toInt()?.plus(1) ?: 0).coerceAtLeast(0) else maxOf(0, end - limit)
            return (first until minOf(end, first + limit)).map { n ->
                WallItem(WallRow(n.toLong(), "series:s:$n", "s", "T$n", "t$n", null, null, null, 0, null))
            }
        }
        override suspend fun watched(films: List<Pair<String, String?>>): Set<String> = emptySet()
        override suspend fun lookUpVisible(items: List<WallItem>) = Unit
        override suspend fun tvmazeCredit(): Boolean = false
        override suspend fun refresh(): RefreshNote = RefreshNote.NoSource
        override fun open(item: WallItem) = Unit
        override fun openManager(group: String?) = Unit
        override fun leave() = Unit
    }

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun aRefreshFinishingAfterTheNextPageKeepsThatPage() = runTest(main) {
        Dispatchers.setMain(main)
        val env = Env()
        val model = LibraryModel(env)
        runCurrent()
        model.select("group:drama")
        runCurrent()
        assertEquals(120, model.wall.value.window.end)

        env.gateRefresh = true
        env.updates.emit(Unit)
        advanceTimeBy(501)
        runCurrent()
        assertTrue(env.refreshEntered.isCompleted)

        model.wantPage(forward = true)
        runCurrent()
        assertEquals(240, model.wall.value.window.end)

        env.releaseRefresh.complete(Unit)
        runCurrent()
        assertEquals("the late refresh keeps the newly loaded page", 240, model.wall.value.window.end)
    }

    @Test
    fun cachedRailRestoresTheGroupAndCardDuringConstruction() = runTest {
        val immediate = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(immediate)
        val env = Env()
        val item = WallItem(WallRow(151L, "series:s:151", "s", "T151", "t151", null, null, null, 0, null))
        val session = BrowseSession().apply {
            selected = "group:drama"
            focused = item
            focusedIndex = 151
            focusOnWall = true
            entered = true
        }
        val model = LibraryModel(env, session)
        assertEquals("group:drama", model.selected.value)
        assertEquals(item.row.key, model.wall.value.window.itemAt(model.focusedIndex)?.row?.key)
        assertTrue(model.focusOnWall)
    }
}
