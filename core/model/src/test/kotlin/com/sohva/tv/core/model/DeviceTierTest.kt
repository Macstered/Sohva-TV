package com.sohva.tv.core.model

import com.sohva.tv.core.model.device.DeviceTier
import com.sohva.tv.core.model.device.DeviceTierInputs
import com.sohva.tv.core.model.device.MemoryTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTierTest {
    private val shield = DeviceTierInputs(
        isLowRamDevice = false,
        memoryClassMb = 192,
        totalMemBytes = 3L * 1024 * 1024 * 1024,
        animatorDurationScale = 1f,
    )

    @Test
    fun shieldIsStandardTier() {
        val tier = DeviceTier.decide(shield)
        assertEquals(MemoryTier.STANDARD, tier.memory)
        assertFalse(tier.reducedMotion)
    }

    @Test
    fun twoGigabyteBoxIsLowTierWithReducedMotion() {
        val tier = DeviceTier.decide(shield.copy(totalMemBytes = 2L * 1024 * 1024 * 1024))
        assertEquals(MemoryTier.LOW, tier.memory)
        assertTrue(tier.reducedMotion)
    }

    @Test
    fun memoryClassBelow192OrLowRamFlagIsLowTier() {
        assertEquals(MemoryTier.LOW, DeviceTier.decide(shield.copy(memoryClassMb = 191)).memory)
        assertEquals(MemoryTier.LOW, DeviceTier.decide(shield.copy(isLowRamDevice = true)).memory)
    }

    @Test
    fun zeroAnimatorScaleOrViewerSwitchReducesMotionOnly() {
        val zeroScale = DeviceTier.decide(shield.copy(animatorDurationScale = 0f))
        assertEquals(MemoryTier.STANDARD, zeroScale.memory)
        assertTrue(zeroScale.reducedMotion)
        val viewer = DeviceTier.decide(shield, viewerReducedMotion = true)
        assertEquals(MemoryTier.STANDARD, viewer.memory)
        assertTrue(viewer.reducedMotion)
    }
}
