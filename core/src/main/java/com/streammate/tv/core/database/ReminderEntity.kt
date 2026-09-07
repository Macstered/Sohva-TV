package com.streammate.tv.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Something the viewer asked to be told about when it starts: a match from
 * Sohva Sport or a programme from the guide. The channel is what the
 * notification opens; a match whose channel is not known yet opens its card
 * instead, where the channel picker lives.
 */
@Entity(tableName = "reminders", indices = [Index("startEpochMillis")])
data class ReminderEntity(
    /** "event:<eventId>" or "programme:<channelId>:<programmeId>". */
    @PrimaryKey val id: String,
    val kind: String,
    val eventId: String?,
    val channelId: String?,
    val title: String,
    val subtitle: String?,
    val startEpochMillis: Long,
    val createdAtEpochMillis: Long,
) {
    companion object {
        const val KIND_EVENT = "event"
        const val KIND_PROGRAMME = "programme"

        fun eventId(eventId: String) = "event:$eventId"
        fun programmeId(channelId: String, programmeId: String) = "programme:$channelId:$programmeId"
    }
}

@Dao
abstract class RemindersDao {
    @Upsert
    abstract suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("SELECT * FROM reminders ORDER BY startEpochMillis")
    abstract fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY startEpochMillis")
    abstract suspend fun all(): List<ReminderEntity>

    /** Reminders whose start is so far past that firing them would only confuse. */
    @Query("DELETE FROM reminders WHERE startEpochMillis < :cutoffEpochMillis")
    abstract suspend fun deleteStartedBefore(cutoffEpochMillis: Long)
}
