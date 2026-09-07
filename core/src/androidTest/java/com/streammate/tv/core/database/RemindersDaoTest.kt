package com.streammate.tv.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemindersDaoTest {
    @Test
    fun remindersAreKeptInStartOrderAndPrunedByStart() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        try {
            val dao = db.remindersDao()
            dao.upsert(reminder("event:2", 2_000L))
            dao.upsert(reminder("event:1", 1_000L))
            dao.upsert(reminder("programme:c:p", 3_000L).copy(kind = ReminderEntity.KIND_PROGRAMME, eventId = null, channelId = "c"))
            assertEquals(listOf("event:1", "event:2", "programme:c:p"), dao.observeAll().first().map { it.id })
            // Upsert replaces, it does not duplicate.
            dao.upsert(reminder("event:1", 1_500L))
            assertEquals(3, dao.all().size)
            assertEquals(1_500L, dao.all().first { it.id == "event:1" }.startEpochMillis)
            dao.deleteStartedBefore(2_000L)
            assertEquals(listOf("event:2", "programme:c:p"), dao.all().map { it.id })
            dao.delete("event:2")
            assertEquals(listOf("programme:c:p"), dao.all().map { it.id })
        } finally {
            db.close()
        }
    }

    private fun reminder(id: String, start: Long) = ReminderEntity(
        id = id, kind = ReminderEntity.KIND_EVENT, eventId = id.removePrefix("event:"), channelId = null,
        title = id, subtitle = null, startEpochMillis = start, createdAtEpochMillis = 0L,
    )
}
