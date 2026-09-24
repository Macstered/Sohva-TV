package com.sohva.tv.core.model

import com.sohva.tv.core.model.concurrent.PauseGate
import com.sohva.tv.core.model.concurrent.WorkOrigin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PauseGateTest {
    private val playing = MutableStateFlow(false)
    private val foreground = MutableStateFlow(false)
    private val gate = PauseGate(playing, foreground)

    @Test
    fun automaticWorkWaitsForPlaybackAndForegroundToEnd() = runTest {
        playing.value = true
        foreground.value = true
        var passed = false
        launch { gate.awaitTurn(WorkOrigin.AUTOMATIC); passed = true }
        runCurrent()
        assertFalse(passed)
        playing.value = false
        runCurrent()
        assertFalse("still in the foreground", passed)
        foreground.value = false
        runCurrent()
        assertTrue(passed)
    }

    @Test
    fun viewerWorkIsPacedDuringPlaybackOnly() = runTest {
        gate.awaitTurn(WorkOrigin.VIEWER)
        assertEquals(0L, currentTime)
        playing.value = true
        foreground.value = true
        gate.awaitTurn(WorkOrigin.VIEWER)
        advanceUntilIdle()
        assertEquals(PauseGate.VIEWER_PAGE_PAUSE_MS, currentTime)
    }
}
