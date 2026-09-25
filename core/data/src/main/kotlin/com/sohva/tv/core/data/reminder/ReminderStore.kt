package com.sohva.tv.core.data.reminder

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sohva.tv.core.data.database.ReminderEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.reminder.Reminder
import com.sohva.tv.core.model.reminder.ReminderKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The `reminder` table: tens of rows at most (spec 22 REM-NFR-01), so whole reads are fine. */
@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY start_at, id")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT id FROM reminder")
    fun ids(): Flow<List<String>>

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun deleteOne(id: String): Int

    @Upsert
    suspend fun put(row: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

/**
 * Reminders on this TV (spec 22 §4.6–4.7): household-wide, never in backups, nothing sent
 * anywhere. The scheduling and the alarm are the app's; this store only keeps the rows.
 */
class ReminderStore(private val db: SohvaDatabase, private val io: CoroutineDispatcher) {
    private val dao get() = db.reminders()

    /** The stored ids, for the "Reminder set" state (REM-NFR-03: observed once, looked up in a set). */
    val ids: Flow<Set<String>> = dao.ids().map { it.toSet() }.distinctUntilChanged().flowOn(io)

    suspend fun all(): List<Reminder> = withContext(io) { dao.all().mapNotNull { it.toReminder() } }

    /** Adds [reminder], or removes it when one with its id exists (REM-FR-04). True when it was added. */
    suspend fun toggle(reminder: Reminder): Boolean = withContext(io) {
        if (dao.deleteOne(reminder.id) > 0) {
            false
        } else {
            dao.put(reminder.toEntity())
            true
        }
    }

    suspend fun delete(ids: Collection<String>) = withContext(io) {
        if (ids.isNotEmpty()) dao.delete(ids.toList())
    }

    private fun ReminderEntity.toReminder(): Reminder? {
        val kind = ReminderKind.fromStored(kind) ?: return null
        return Reminder(id, kind, eventId, channelKey, title, subtitle, startAt, createdAt)
    }

    private fun Reminder.toEntity() = ReminderEntity(id, kind.stored, eventId, channelKey, title, subtitle, startAt, createdAt)
}
