package com.streammate.tv.iptv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SourceConnectionLimiterTest {
    @Test
    fun `enforces each source limit independently and releases idempotently`() {
        val limiter = SourceConnectionLimiter()
        val first = limiter.tryAcquire("source-a", 1)

        assertNotNull(first)
        assertNull(limiter.tryAcquire("source-a", 1))
        assertNotNull(limiter.tryAcquire("source-b", 1))
        assertEquals(1, limiter.activeConnections("source-a"))

        first?.close()
        first?.close()

        assertEquals(0, limiter.activeConnections("source-a"))
        assertNotNull(limiter.tryAcquire("source-a", 1))
    }

    @Test
    fun `uses the latest lower limit without terminating active playback`() {
        val limiter = SourceConnectionLimiter()
        val first = limiter.tryAcquire("source-a", 2)
        val second = limiter.tryAcquire("source-a", 2)

        assertNotNull(first)
        assertNotNull(second)
        assertNull(limiter.tryAcquire("source-a", 1))

        first?.close()
        assertNull(limiter.tryAcquire("source-a", 1))
        second?.close()
        assertNotNull(limiter.tryAcquire("source-a", 1))
    }
}
