package com.streammate.tv.core.reminders

import com.streammate.tv.core.database.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {
    private val now = 1_000_000_000L
    private fun reminder(id: String, startsIn: Long) = ReminderEntity(
        id = id, kind = ReminderEntity.KIND_EVENT, eventId = id, channelId = null,
        title = id, subtitle = null, startEpochMillis = now + startsIn, createdAtEpochMillis = 0L,
    )

    @Test
    fun `a reminder fires a minute before its start`() {
        assertEquals(now + 9 * 60_000L, ReminderSchedule.fireAt(reminder("a", 10 * 60_000L)))
    }

    @Test
    fun `due reminders are those whose fire time has passed and that are not stale`() {
        val due = ReminderSchedule.due(
            listOf(
                reminder("later", 5 * 60_000L),
                reminder("starting", 30_000L),
                reminder("just started", -5 * 60_000L),
                reminder("long gone", -45 * 60_000L),
            ),
            now,
        )
        assertEquals(listOf("just started", "starting"), due.map { it.id })
    }

    @Test
    fun `the next alarm is the earliest fire time still ahead`() {
        val next = ReminderSchedule.nextFireAt(
            listOf(reminder("a", 40 * 60_000L), reminder("b", 12 * 60_000L), reminder("c", 30_000L)),
            now,
        )
        assertEquals(now + 11 * 60_000L, next)
        assertNull(ReminderSchedule.nextFireAt(listOf(reminder("c", 30_000L)), now))
        assertNull(ReminderSchedule.nextFireAt(emptyList(), now))
    }

    @Test
    fun `stale means more than half an hour after the start`() {
        assertTrue(ReminderSchedule.isStale(reminder("old", -31 * 60_000L), now))
        assertTrue(!ReminderSchedule.isStale(reminder("recent", -29 * 60_000L), now))
        assertEquals(now - 30 * 60_000L, ReminderSchedule.staleCutoff(now))
    }
}
