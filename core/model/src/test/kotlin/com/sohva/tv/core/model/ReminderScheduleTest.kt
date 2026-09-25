package com.sohva.tv.core.model

import com.sohva.tv.core.model.reminder.Reminder
import com.sohva.tv.core.model.reminder.ReminderIds
import com.sohva.tv.core.model.reminder.ReminderKind
import com.sohva.tv.core.model.reminder.ReminderSchedule
import com.sohva.tv.core.model.reminder.RingingQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 22 §11 "Schedule" and "Ringing queue". Times are relative to a "now" (AGENTS §8). */
class ReminderScheduleTest {
    private val now = System.currentTimeMillis()
    private val minute = 60_000L

    private fun reminder(id: String, startIn: Long) =
        Reminder(ReminderIds.programme("src:$id", id), ReminderKind.PROGRAMME, null, "src:$id", "Title $id", "Channel", now + startIn, now - minute)

    @Test
    fun firesOneMinuteBeforeTheStart() {
        assertEquals(now + 9 * minute, reminder("a", 10 * minute).fireAt)
    }

    @Test
    fun dueExcludesStaleAndIncludesRecentPast() {
        val stale = reminder("stale", -31 * minute)
        val late = reminder("late", -29 * minute)
        val soon = reminder("soon", 30_000)
        val later = reminder("later", 10 * minute)
        val due = ReminderSchedule.due(listOf(later, soon, stale, late), now)
        assertEquals(listOf(late, soon), due)
    }

    @Test
    fun nextIsTheEarliestFutureFireTime() {
        val list = listOf(reminder("b", 20 * minute), reminder("a", 5 * minute), reminder("due", 0))
        assertEquals(now + 4 * minute, ReminderSchedule.next(list, now))
        assertNull(ReminderSchedule.next(listOf(reminder("due", 0)), now))
    }

    /** Beta 23 returned before firing when nothing lay ahead; this must fire (REM-FR-11). */
    @Test
    fun aReminderDueNowWithNothingElsePendingIsFired() {
        val only = reminder("only", 30_000)
        val plan = ReminderSchedule.plan(listOf(only), now)
        assertEquals(listOf(only), plan.due)
        assertNull(plan.nextFireAt)
    }

    @Test
    fun planDeletesStaleFiresDueAndSetsOneAlarm() {
        val stale = reminder("stale", -40 * minute)
        val due = reminder("due", 10_000)
        val ahead = reminder("ahead", 15 * minute)
        val plan = ReminderSchedule.plan(listOf(stale, due, ahead), now)
        assertEquals(listOf(stale), plan.stale)
        assertEquals(listOf(due), plan.due)
        assertEquals(ahead.fireAt, plan.nextFireAt)
    }

    @Test
    fun alertLingersTwoMinutesPastTheStartButAtLeastTwentySeconds() {
        val r = reminder("a", minute)
        assertEquals(3 * minute, ReminderSchedule.lingerMs(r, now))
        assertEquals(20_000, ReminderSchedule.lingerMs(r, now + 10 * minute))
        assertTrue(ReminderSchedule.startsSoon(r, now))
        assertFalse(ReminderSchedule.startsSoon(r, now + minute))
    }

    @Test
    fun ringingQueueRingsOnceAndDismissalMovesOn() {
        val queue = RingingQueue()
        val a = reminder("a", minute)
        val b = reminder("b", 2 * minute)
        assertFalse("empty rings change nothing", queue.ring(emptyList()))
        assertTrue(queue.ring(listOf(b, a)))
        assertFalse("a reminder rings once", queue.ring(listOf(a)))
        assertEquals(2, queue.size)
        assertEquals(a, queue.current)
        queue.dismiss(a.id)
        assertEquals(b, queue.current)
        queue.dismiss(b.id)
        assertNull(queue.current)
    }

    @Test
    fun staleQueuedAlertsAreDropped() {
        val queue = RingingQueue()
        queue.ring(listOf(reminder("old", -31 * minute), reminder("fresh", minute)))
        queue.dropStale(now)
        assertEquals(1, queue.size)
        assertEquals(ReminderKind.PROGRAMME, ReminderKind.fromStored("programme"))
        assertNull(ReminderKind.fromStored("other"))
        assertEquals("event:e1", ReminderIds.event("e1"))
    }
}
