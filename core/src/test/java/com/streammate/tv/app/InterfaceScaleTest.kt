package com.streammate.tv.app

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceScaleTest {
    @Test
    fun `the stored name round-trips and anything else falls back to normal`() {
        InterfaceScale.entries.forEach { assertEquals(it, InterfaceScale.fromStored(it.name)) }
        assertEquals(InterfaceScale.NORMAL, InterfaceScale.fromStored(null))
        assertEquals(InterfaceScale.NORMAL, InterfaceScale.fromStored("huge"))
    }

    @Test
    fun `each step is smaller than the last and normal is unscaled`() {
        assertEquals(1f, InterfaceScale.NORMAL.factor)
        val factors = InterfaceScale.entries.map { it.factor }
        assertEquals(factors.sortedDescending(), factors)
        assertEquals(factors.distinct().size, factors.size)
    }

    @Test
    fun `the ladder ends at seventy per cent, a step below small`() {
        assertEquals(InterfaceScale.SMALLER, InterfaceScale.entries.last())
        assertEquals(listOf(100, 90, 80, 70), InterfaceScale.entries.map { it.percent })
    }
}
