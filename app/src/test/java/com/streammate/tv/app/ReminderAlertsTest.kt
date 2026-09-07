package com.streammate.tv.app

import com.streammate.tv.core.database.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderAlertsTest {
    @Test
    fun `a reminder rings once and stops when dismissed`() {
        val alerts = ReminderAlerts()
        alerts.ring(listOf(reminder("a"), reminder("b")))
        alerts.ring(listOf(reminder("a")))
        assertEquals(listOf("a", "b"), alerts.ringing.value.map { it.id })
        alerts.dismiss("a")
        assertEquals(listOf("b"), alerts.ringing.value.map { it.id })
        alerts.ring(emptyList())
        assertEquals(listOf("b"), alerts.ringing.value.map { it.id })
    }

    private fun reminder(id: String) = ReminderEntity(id, ReminderEntity.KIND_PROGRAMME, null, "c", "Title", null, 1_000L, 0L)
}
