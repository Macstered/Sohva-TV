package com.sohva.tv.core.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Sources: at most 100 rows (SRC-FR-07), so whole-table reads and their sort are fine here. */
@Dao
interface SourceDao {
    @Query("SELECT * FROM source ORDER BY priority DESC, name, id")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM source ORDER BY priority DESC, name, id")
    suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM source WHERE id = :id")
    suspend fun get(id: String): SourceEntity?

    @Query("SELECT COUNT(*) FROM source")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Query("DELETE FROM source WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SourceStatusDao {
    @Query("SELECT * FROM source_status")
    fun observeAll(): Flow<List<SourceStatusEntity>>

    @Query("SELECT * FROM source_status WHERE source_id = :sourceId AND kind = :kind")
    fun get(sourceId: String, kind: String): SourceStatusEntity?

    @Upsert
    fun upsert(status: SourceStatusEntity)

    @Query("DELETE FROM source_status WHERE source_id = :sourceId")
    fun deleteForSource(sourceId: String)

    /**
     * At start-up, a refresh still `running` belonged to a process that died: it reads as failed
     * with `interrupted` (spec 10 §8 rebuild rule). The previous data is intact by construction.
     */
    @Query(
        "UPDATE source_status SET status = 'failed', error_code = 'interrupted', error_args = NULL, " +
            "last_failure_at = :now, consecutive_failures = consecutive_failures + 1 WHERE status = 'running'",
    )
    fun markInterrupted(now: Long): Int
}
