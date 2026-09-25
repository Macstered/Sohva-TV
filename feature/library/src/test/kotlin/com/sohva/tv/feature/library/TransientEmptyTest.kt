package com.sohva.tv.feature.library

import com.sohva.tv.core.data.database.WallRow
import com.sohva.tv.core.data.vod.RailGroup
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallItem
import com.sohva.tv.core.data.vod.WallRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 40 VOD-FR-16: a group whose count says it has titles does not flash "No titles" on a transient empty read. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransientEmptyTest {
    private val main = StandardTestDispatcher()
    private val drama = WallDestination.Group("Drama", listOf(1L))

    private class Env(var answers: MutableList<List<WallItem>>) : LibraryEnvironment {
        var reads = 0
        override val room: WallRoom = WallRoom.SERIES
        override fun changes(): Flow<Unit> = emptyFlow()
        override suspend fun groups(): List<RailGroup> = listOf(RailGroup("Drama", listOf(1L), 3))
        override suspend fun genreCounts(): Map<String, Int> = emptyMap()
        override suspend fun page(destination: WallDestination, search: String, from: WallItem?, forward: Boolean, limit: Int): List<WallItem> {
            if (destination == WallDestination.History) return emptyList()
            reads++
            return if (answers.size > 1) answers.removeAt(0) else answers.first()
        }
        override suspend fun watched(films: List<Pair<String, String?>>): Set<String> = emptySet()
        override suspend fun lookUpVisible(items: List<WallItem>) = Unit
        override suspend fun tvmazeCredit(): Boolean = false
        override suspend fun refresh(): RefreshNote = RefreshNote.NoSource
        override fun open(item: WallItem) = Unit
        override fun openManager() = Unit
        override fun leave() = Unit
    }

    private fun item(n: Int) = WallItem(WallRow(n.toLong(), "series:s:$n", "s", "T$n", "t$n", null, null, null, 0, null))

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun anEmptyReadIsAskedAgainBeforeItIsShown() = runTest(main) {
        Dispatchers.setMain(main)
        val env = Env(mutableListOf(emptyList(), listOf(item(1), item(2), item(3))))
        val model = LibraryModel(env)
        runCurrent()
        model.select("group:drama")
        runCurrent()
        // The empty answer is held back: the wall is not yet current, so "No titles" is not shown.
        assertFalse(model.wall.value.current)
        advanceTimeBy(600)
        runCurrent()
        assertEquals(3, model.wall.value.window.items.size)
        assertEquals(2, env.reads)
    }
}
