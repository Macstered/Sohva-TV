package com.streammate.tv.feature.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTimeWindowTest {

    private val halfHour = 30 * 60_000L
    private val hour = 60 * 60_000L

    // 21:07 on an arbitrary day, so nothing lands on a boundary by luck.
    private val now = 1_756_500_420_000L

    @Test
    fun `the window starts half an hour before now, on a half hour`() {
        val start = GuideTimeWindow.nowStart(now)

        assertEquals(0L, start % halfHour)
        assertTrue("start is before now", start < now)
        assertTrue("but not by more than an hour", now - start <= hour)
    }

    @Test
    fun `paging forward and back returns to where it started`() {
        val start = GuideTimeWindow.nowStart(now)
        val forward = GuideTimeWindow.shifted(start, GuideTimeWindow.PAGE_MILLIS, now)
        val andBack = GuideTimeWindow.shifted(forward, -GuideTimeWindow.PAGE_MILLIS, now)

        assertEquals(start, andBack)
    }

    @Test
    fun `a window paged away does not drift when the clock crosses a half hour`() {
        // The reason this is an absolute start and not an offset from now: an
        // offset would slide tomorrow's listings half an hour sideways while
        // someone was reading them.
        val tomorrow = GuideTimeWindow.shifted(
            GuideTimeWindow.nowStart(now),
            GuideTimeWindow.DAY_MILLIS,
            now,
        )
        val laterNow = now + hour

        assertEquals(tomorrow, GuideTimeWindow.shifted(tomorrow, 0L, laterNow))
        assertFalse(GuideTimeWindow.isAtNow(tomorrow, laterNow))
    }

    @Test
    fun `the window at now follows the clock`() {
        assertTrue(GuideTimeWindow.isAtNow(GuideTimeWindow.nowStart(now), now))
        assertTrue(
            "an hour later, now has moved on",
            GuideTimeWindow.nowStart(now + hour) > GuideTimeWindow.nowStart(now),
        )
    }

    @Test
    fun `the window stops a day back, which is as far as catch-up reaches`() {
        var start = GuideTimeWindow.nowStart(now)
        repeat(40) { start = GuideTimeWindow.shifted(start, -hour, now) }

        assertEquals(GuideTimeWindow.nowStart(now) - GuideTimeWindow.DAY_MILLIS, start)
    }

    @Test
    fun `the window stops a week out, where the listings run out`() {
        var start = GuideTimeWindow.nowStart(now)
        repeat(400) { start = GuideTimeWindow.shifted(start, hour, now) }

        assertEquals(GuideTimeWindow.nowStart(now) + 7 * GuideTimeWindow.DAY_MILLIS, start)
    }

    @Test
    fun `jumping to a moment puts it at the start of the window`() {
        val start = GuideTimeWindow.startFor(now + GuideTimeWindow.DAY_MILLIS, now)

        assertEquals(0L, start % halfHour)
        assertEquals(GuideTimeWindow.nowStart(now) + GuideTimeWindow.DAY_MILLIS, start)
    }

    @Test
    fun `jumping beyond the range lands at the edge rather than nowhere`() {
        val start = GuideTimeWindow.startFor(now + 30 * GuideTimeWindow.DAY_MILLIS, now)

        assertEquals(GuideTimeWindow.nowStart(now) + 7 * GuideTimeWindow.DAY_MILLIS, start)
    }
}
