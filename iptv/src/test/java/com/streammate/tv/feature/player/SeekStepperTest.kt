package com.streammate.tv.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekStepperTest {
    @Test
    fun `single presses use the chosen step`() {
        val stepper = SeekStepper()
        assertEquals(10_000L, stepper.step(10_000L, 1, 0L))
        assertEquals(10_000L, stepper.step(10_000L, 1, 5_000L))
        assertEquals(30_000L, stepper.step(30_000L, -1, 10_000L))
    }

    @Test
    fun `held, the step climbs a rung every three presses and stops at two minutes`() {
        val stepper = SeekStepper()
        val steps = (0 until 12).map { i -> stepper.step(10_000L, 1, i * 300L) }
        assertEquals(
            listOf(10_000L, 10_000L, 10_000L, 30_000L, 30_000L, 30_000L, 60_000L, 60_000L, 60_000L, 120_000L, 120_000L, 120_000L),
            steps,
        )
        assertEquals(120_000L, stepper.step(10_000L, 1, 12 * 300L))
    }

    @Test
    fun `a pause or the other direction starts over`() {
        val stepper = SeekStepper()
        repeat(4) { stepper.step(10_000L, 1, it * 300L) }
        assertEquals(10_000L, stepper.step(10_000L, -1, 1_500L))
        repeat(3) { stepper.step(10_000L, -1, 1_800L + it * 300L) }
        assertEquals(30_000L, stepper.step(10_000L, -1, 2_700L))
        assertEquals(10_000L, stepper.step(10_000L, -1, 2_700L + 1_201L))
    }

    @Test
    fun `a larger chosen step starts higher on the ladder`() {
        val stepper = SeekStepper()
        val steps = (0 until 4).map { i -> stepper.step(60_000L, 1, i * 300L) }
        assertEquals(listOf(60_000L, 60_000L, 60_000L, 120_000L), steps)
    }

    @Test
    fun `labels say seconds below a minute and minutes at whole minutes`() {
        assertEquals("+10 s", seekStepLabel(10_000L))
        assertEquals("\u221230 s", seekStepLabel(-30_000L))
        assertEquals("+1 min", seekStepLabel(60_000L))
        assertEquals("\u22122 min", seekStepLabel(-120_000L))
    }

    @Test
    fun `the button label names the step without a sign`() {
        assertEquals("10 s", seekAmountLabel(10_000L))
        assertEquals("30 s", seekAmountLabel(30_000L))
        assertEquals("1 min", seekAmountLabel(60_000L))
        assertEquals("2 min", seekAmountLabel(120_000L))
    }
}
