package com.streammate.tv.feature.common

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TickerFlowTest {

    // runBlocking, not runTest: this flow deliberately waits on real time, so a
    // virtual-time scheduler would race past every timeout without the flow
    // having advanced at all.

    @Test
    fun `emits once before the first wait by default`() = runBlocking {
        val ticks = withTimeout(REAL_TIMEOUT_MILLIS) {
            tickerFlow(periodMillis = 20, emitImmediately = true).take(3).toList()
        }

        assertEquals(3, ticks.size)
    }

    @Test
    fun `waits before the first emission when asked to`() = runBlocking {
        val ticks = withTimeout(REAL_TIMEOUT_MILLIS) {
            tickerFlow(periodMillis = 20, emitImmediately = false).take(2).toList()
        }

        assertEquals(2, ticks.size)
    }

    @Test
    fun `keeps emitting so a collector can run indefinitely`() = runBlocking {
        val ticks = withTimeout(REAL_TIMEOUT_MILLIS) {
            tickerFlow(periodMillis = 5).take(10).toList()
        }

        assertEquals(10, ticks.size)
    }

    /**
     * The whole point of the flow. A Compose test clock advances virtual time to
     * reach idle; if the wait lived on the collector's dispatcher, advancing
     * time would resume the ticker, the tick would write state, and the
     * composition would never settle.
     *
     * runTest installs a virtual-time scheduler here on purpose. Virtual time
     * must not carry the ticker forward, so the virtual timeout fires with no
     * emission having arrived at all.
     */
    @Test
    fun `virtual time does not advance the ticker`() = runTest {
        var emissions = 0
        val elapsedRealMillis = runCatching {
            withTimeout(1_000) {
                tickerFlow(periodMillis = 50, emitImmediately = false).collect { emissions++ }
            }
        }

        assertTrue("withTimeout on virtual time should have cancelled", elapsedRealMillis.isFailure)
        assertEquals("virtual time must not drive the ticker", 0, emissions)
    }

    private companion object {
        const val REAL_TIMEOUT_MILLIS = 5_000L
    }
}
