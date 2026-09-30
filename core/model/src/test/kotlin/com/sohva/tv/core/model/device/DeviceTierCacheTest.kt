package com.sohva.tv.core.model.device

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTierCacheTest {
    @Test
    fun beforeTheFirstReadConsumersUseConservativeLimits() = runTest {
        var reads = 0
        val cache = DeviceTierCache { reads++; STANDARD }
        assertEquals(MemoryTier.LOW, cache.current.memory)
        assertTrue(cache.current.reducedMotion)
        assertEquals(0f, cache.animationScale, 0f)
        assertEquals(0, reads)
        assertEquals(MemoryTier.STANDARD, cache.load().memory)
        assertFalse(cache.current.reducedMotion)
        assertEquals(1, reads)
    }

    @Test
    fun concurrentConsumersShareOneSlowRead() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var reads = 0
        val cache = DeviceTierCache { reads++; started.complete(Unit); finish.await(); STANDARD }
        val first = async { cache.load() }
        started.await()
        val second = async { cache.load() }
        finish.complete(Unit)
        assertEquals(first.await(), second.await())
        repeat(3) { assertEquals(first.await(), cache.load()) }
        assertEquals(1, reads)
    }

    @Test
    fun twoGigabytesWithALargeHeapUsesLowMemoryLimits() = runTest {
        val cache = DeviceTierCache { STANDARD.copy(totalMemBytes = 2L * 1024 * 1024 * 1024) }
        assertEquals(MemoryTier.LOW, cache.load().memory)
        assertTrue(cache.current.reducedMotion)
        assertEquals(0f, cache.animationScale, 0f)
    }

    @Test
    fun standardMemoryKeepsTheSystemAnimationScale() = runTest {
        val cache = DeviceTierCache { STANDARD.copy(animatorDurationScale = 1.5f) }
        cache.load()
        assertEquals(1.5f, cache.animationScale, 0f)
    }

    @Test
    fun systemAnimationsOffUsesZeroEvenWithStandardMemory() = runTest {
        val cache = DeviceTierCache { STANDARD.copy(animatorDurationScale = 0f) }
        assertEquals(MemoryTier.STANDARD, cache.load().memory)
        assertTrue(cache.current.reducedMotion)
        assertEquals(0f, cache.animationScale, 0f)
    }

    private companion object {
        val STANDARD = DeviceTierInputs(false, 256, 3L * 1024 * 1024 * 1024, 1f)
    }
}
