package com.sohva.tv.core.sync.update

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.update.OfferedUpdate
import com.sohva.tv.core.model.update.ReleaseAsset
import com.sohva.tv.core.model.update.UpdatePhase
import com.sohva.tv.core.model.update.UpdateState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** No duplicate notice on refresh/recreation/restart; deferred offers are not consumed. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateNoticesTest {
    private class Memory : UpdateNoticeMemory {
        var build = 0
        var reads = 0
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun lastNotifiedBuild(): Int { reads++; return build }
        override suspend fun setLastNotifiedBuild(build: Int) {
            gate?.await()
            writes++
            this.build = build
        }
    }
    private val memory = Memory()
    private val state = MutableStateFlow(UpdateState(UpdatePhase.IDLE))
    private val log = object : DiagnosticsLog {
        override fun info(event: String, message: String) = Unit
        override fun error(event: String, message: String?, error: Throwable?) = Unit
        override fun snapshot(): List<String> = emptyList()
    }
    private fun TestScope.notices(enabled: Boolean = true) =
        UpdateNotices(enabled, 100, memory, backgroundScope, log).also { it.start(state) }

    private fun offer(build: Int = 101) {
        val update = OfferedUpdate("0.2.0-beta.$build", build, "", ReleaseAsset("sohva.apk", "https://provider.example/app.apk", 1), null, null)
        state.value = UpdateState(UpdatePhase.AVAILABLE, update)
    }

    @Test
    fun aNewerBuildIsPendingUntilActuallyShown() = runTest {
        val notices = notices()
        runCurrent()
        assertEquals(0, memory.reads)
        offer()
        runCurrent()
        assertEquals(UpdateNotice("0.2.0-beta.101", 101), notices.pending.value)
        assertEquals(0, memory.writes)
        // An app restart before eligible Home has not consumed the deferred notice.
        val next = notices()
        runCurrent()
        assertEquals(notices.pending.value, next.pending.value)
    }

    @Test
    fun shownBuildIsNotRepeatedAfterRefreshOrProcessRestart() = runTest {
        val notices = notices()
        offer()
        runCurrent()
        notices.shown(101)
        runCurrent()
        assertEquals(101, memory.build)
        assertNull(notices.pending.value)
        state.value = UpdateState(UpdatePhase.CHECKING)
        runCurrent()
        offer()
        runCurrent()
        assertNull(notices.pending.value)
        assertNull(notices().also { runCurrent() }.pending.value)
        assertEquals(1, memory.writes)
    }

    @Test
    fun aLaterBuildGetsItsOwnNoticeAndTheMarkerNeverMovesBackwards() = runTest {
        memory.build = 101
        val notices = notices()
        offer(102)
        runCurrent()
        assertEquals(102, notices.pending.value?.build)
        notices.shown(102)
        notices.shown(101)
        runCurrent()
        assertEquals(102, memory.build)
        offer(101)
        runCurrent()
        assertNull(notices.pending.value)
    }

    @Test
    fun recreationDoesNotRepeatWhileTheMarkerWriteIsSlow() = runTest {
        memory.gate = CompletableDeferred()
        val notices = notices()
        offer()
        runCurrent()
        notices.shown(101)
        assertNull(notices.pending.value)
        state.value = UpdateState(UpdatePhase.CHECKING)
        runCurrent()
        offer()
        runCurrent()
        assertNull(notices.pending.value)
        memory.gate!!.complete(Unit)
        runCurrent()
        assertEquals(101, memory.build)
    }

    @Test
    fun acknowledgingAnOlderVisibleNoticeKeepsANewerPendingOne() = runTest {
        val notices = notices()
        offer(101)
        runCurrent()
        offer(102)
        runCurrent()
        notices.shown(101)
        runCurrent()
        assertEquals(102, notices.pending.value?.build)
        assertEquals(101, memory.build)
    }

    @Test
    fun progressDoesNotReadPreferencesAgainAndStartOnlySubscribesOnce() = runTest {
        val notices = notices()
        notices.start(state)
        offer()
        runCurrent()
        repeat(100) { state.value = state.value.copy(phase = UpdatePhase.DOWNLOADING, percent = it); runCurrent() }
        assertEquals(1, memory.reads)
        assertEquals(101, notices.pending.value?.build)
    }

    @Test
    fun disabledBuildsAndOldReleasesNeverOpenPreferencesOrAnnounce() = runTest {
        val disabled = notices(enabled = false)
        offer()
        runCurrent()
        disabled.shown(101)
        runCurrent()
        assertNull(disabled.pending.value)
        assertEquals(0, memory.reads + memory.writes)
        val enabled = notices()
        offer(100)
        runCurrent()
        assertNull(enabled.pending.value)
        assertEquals(0, memory.reads + memory.writes)
    }

    @Test
    fun upToDateAndFailedChecksClearThePendingNoticeWithoutMarkingItShown() = runTest {
        val notices = notices()
        offer()
        runCurrent()
        state.value = UpdateState(UpdatePhase.UP_TO_DATE)
        runCurrent()
        assertNull(notices.pending.value)
        state.value = UpdateState(UpdatePhase.FAILED)
        runCurrent()
        assertNull(notices.pending.value)
        assertEquals(0, memory.writes)
    }
}
