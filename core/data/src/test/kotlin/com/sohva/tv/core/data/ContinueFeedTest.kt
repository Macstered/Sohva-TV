package com.sohva.tv.core.data

import com.sohva.tv.core.data.home.ContinueFeed
import com.sohva.tv.core.data.home.ResumeState
import com.sohva.tv.core.data.vod.ContinueItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 02 §11 "Unit": a read stuck over 5 s is FAILED and recovers without polling; changes re-read, not during playback. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinueFeedTest {
    private fun item(key: String) = ContinueItem(key, key, key, null, null, null, false, null, null, null, null, 1, 2, 3)

    private class Fixture(scope: TestScope) {
        var answer = CompletableDeferred<List<ContinueItem>>()
        var reads = 0
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val playing = MutableStateFlow(false)
        val feed = ContinueFeed(scope.backgroundScope, { reads++; answer.await() }, changes, playing)
    }

    @Test
    fun aStuckReadFailsAfterFiveSecondsAndALateAnswerReplacesIt() = runTest {
        val f = Fixture(this)
        f.feed.start()
        runCurrent()
        assertEquals(ResumeState.Loading, f.feed.state.value)
        advanceTimeBy(ContinueFeed.FAIL_AFTER_MS + 1)
        assertEquals(ResumeState.Failed, f.feed.state.value)
        f.answer.complete(listOf(item("a")))
        runCurrent()
        assertEquals(ResumeState.Ready(listOf(item("a"))), f.feed.state.value)
        assertEquals(1, f.reads)
    }

    @Test
    fun changesReReadOncePerQuietSecondAndWaitForPlaybackToEnd() = runTest {
        val f = Fixture(this)
        f.answer.complete(emptyList())
        f.feed.start()
        runCurrent()
        assertEquals(ResumeState.Empty, f.feed.state.value)
        f.answer = CompletableDeferred(listOf(item("b")))
        f.playing.value = true
        repeat(3) { f.changes.tryEmit(Unit) }
        advanceTimeBy(ContinueFeed.QUIET_MS + 1)
        runCurrent()
        // Held while the video plays.
        assertEquals(1, f.reads)
        f.playing.value = false
        runCurrent()
        assertEquals(2, f.reads)
        assertEquals(ResumeState.Ready(listOf(item("b"))), f.feed.state.value)
    }
}
