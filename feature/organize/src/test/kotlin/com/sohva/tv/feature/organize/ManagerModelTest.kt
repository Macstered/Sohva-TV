package com.sohva.tv.feature.organize

import com.sohva.tv.core.data.database.ManagerSourceRow
import com.sohva.tv.core.data.org.GroupRef
import com.sohva.tv.core.data.org.ManagedGroup
import com.sohva.tv.core.data.org.ManagedItem
import com.sohva.tv.core.data.org.ManagedKind
import com.sohva.tv.core.data.org.RuleChange
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgRule
import com.sohva.tv.core.model.org.OrgSort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 42 §11 "Manager model": moves, filter, one save at a time, failure and Undo, Right before the items arrive. */
@OptIn(ExperimentalCoroutinesApi::class)
class ManagerModelTest {
    private val main = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun group(key: String, shown: Boolean = true) = ManagedGroup(
        key = key, kind = ManagedKind.GROUP, label = key.removePrefix("name:"), shown = shown, enabled = 1, total = 1,
        backing = listOf(GroupRef("", key), GroupRef("s", "id:$key")), groupIds = listOf(1L), sort = null,
        effectiveSort = OrgSort.TITLE_ASC, position = null,
    )

    private class Env(val groups: List<ManagedGroup>) : ManagerEnvironment {
        val applied = mutableListOf<List<RuleChange>>()
        var answer: (List<RuleChange>) -> Outcome<List<RuleChange>> = { Outcome.Ok(it) }
        var gate: CompletableDeferred<Unit>? = null
        var itemsGate: CompletableDeferred<Unit>? = null
        val shownHidden = mutableListOf<Boolean>()

        override suspend fun sources(): List<ManagerSourceRow> = emptyList()
        override suspend fun groups(room: OrgRoom, scope: String?): List<ManagedGroup> = groups
        override suspend fun items(room: OrgRoom, group: ManagedGroup, search: String?): List<ManagedItem> {
            itemsGate?.await()
            return listOf("a", "b").map { id ->
                ManagedItem(id, id, "Fixture", null, emptyList(), true, false, false, false, null, null, id, null, null, 0)
            }
        }
        override suspend fun rules(room: OrgRoom): List<OrgRule> = emptyList()
        override suspend fun apply(changes: List<RuleChange>): Outcome<List<RuleChange>> {
            applied += changes
            gate?.await()
            return answer(changes)
        }
        override val showHidden: Flow<Boolean> = flowOf(true)
        override suspend fun setShowHidden(value: Boolean) {
            shownHidden += value
        }
        override suspend fun location(room: OrgRoom): Pair<String?, String?> = null to null
        override suspend fun saveLocation(room: OrgRoom, group: String?, source: String?) = Unit
        override fun openAdvanced() = Unit
        override fun leave() = Unit
    }

    private fun TestScope.model(env: Env, start: String? = null): ManagerModel {
        val model = ManagerModel(env, ManagerStart(OrgRoom.MOVIES, start))
        advanceUntilIdle()
        return model
    }

    @Test
    fun aMoveStepsPastTheVisibleNeighbourOnlyAndOkWritesTheWholeOrder() = runTest(main) {
        val env = Env(listOf(group("name:a"), group("name:b", shown = false), group("name:c")))
        val model = model(env)
        model.cycleFilter() // All → Enabled: b is filtered out.
        model.beginMove(ManagerPane.GROUPS, "name:a")
        model.step(up = false)
        assertEquals(listOf("name:b", "name:c", "name:a"), model.state.value.move?.order)
        model.placeMove()
        advanceUntilIdle()
        assertNull(model.state.value.move)
        // Positions 0…n−1 on every entry's keys, and nothing about visibility (ORG-FR-52).
        val changes = env.applied.single()
        assertTrue(changes.isNotEmpty())
        assertTrue(changes.all { it.enabled is com.sohva.tv.core.data.org.Field.Keep })
    }

    @Test
    fun theFilterStoresShowHiddenForAllAndEnabledButNotForDisabled() = runTest(main) {
        val env = Env(listOf(group("name:a")))
        val model = model(env)
        assertEquals(ManagerFilter.ALL, model.state.value.filter)
        model.cycleFilter()
        model.cycleFilter()
        model.cycleFilter()
        advanceUntilIdle()
        assertEquals(ManagerFilter.ALL, model.state.value.filter)
        assertEquals(listOf(false, true), env.shownHidden)
    }

    @Test
    fun oneSaveAtATimeAndAFailureChangesNothingOnScreen() = runTest(main) {
        val env = Env(listOf(group("name:a")))
        env.gate = CompletableDeferred()
        env.answer = { Outcome.Failed(AppError.Unknown) }
        val model = model(env)
        val a = model.state.value.groups!!.single()
        model.setGroupShown(a, false)
        runCurrent()
        assertEquals(ManagerFooter.SAVING, model.state.value.footer)
        model.setGroupShown(a, false)
        runCurrent()
        assertEquals(1, env.applied.size)
        env.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ManagerFooter.SAVE_ERROR, model.state.value.footer)
        assertNull(model.state.value.undo)
        assertTrue(model.state.value.groups!!.single().shown)
    }

    @Test
    fun undoWritesTheReturnedChangesAndIsNotItselfUndoable() = runTest(main) {
        val env = Env(listOf(group("name:a")))
        val model = model(env)
        model.setGroupShown(model.state.value.groups!!.single(), false)
        advanceUntilIdle()
        val undo = model.state.value.undo
        assertTrue(undo != null && undo.isNotEmpty())
        model.undo()
        advanceUntilIdle()
        assertEquals(undo, env.applied.last())
        assertNull(model.state.value.undo)
    }

    @Test
    fun rightBeforeTheItemsArriveEntersThePaneWhenTheyDo() = runTest(main) {
        val env = Env(listOf(group("name:a"), group("name:b")))
        val model = model(env, start = "name:a")
        env.itemsGate = CompletableDeferred()
        model.focusGroup("name:b")
        advanceTimeBy(ManagerModel.REST_MS + 1)
        val b = model.state.value.groups!!.last()
        assertTrue(model.enterItemsFrom(b))
        assertEquals(ManagerPane.GROUPS, model.state.value.pane)
        env.itemsGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ManagerPane.ITEMS, model.state.value.pane)
        assertFalse(model.state.value.items.isNullOrEmpty())
    }

    @Test
    fun passingThroughGroupsReadsOnlyWhereFocusRests() = runTest(main) {
        val env = Env(listOf(group("name:a"), group("name:b"), group("name:c")))
        val model = model(env, start = "name:a")
        model.focusGroup("name:b")
        advanceTimeBy(ManagerModel.REST_MS / 2)
        model.focusGroup("name:c")
        advanceTimeBy(ManagerModel.REST_MS / 2)
        assertNull(model.state.value.items)
        advanceUntilIdle()
        assertEquals("name:c", model.state.value.selectedKey)
        assertFalse(model.state.value.items.isNullOrEmpty())
    }
}
