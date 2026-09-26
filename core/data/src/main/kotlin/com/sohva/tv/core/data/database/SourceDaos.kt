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

    /** Enabled live-TV sources without a successful playlist import yet (spec 10 SRC-FR-98 deferral). */
    @Query(
        "SELECT COUNT(*) FROM source AS s WHERE s.enabled = 1 AND s.import_scope <> 'VOD' AND NOT EXISTS " +
            "(SELECT 1 FROM source_status AS st WHERE st.source_id = s.id AND st.kind = 'playlist' AND st.last_success_at IS NOT NULL)",
    )
    suspend fun liveSourcesNeverImported(): Int
}

@Dao
interface SourceStatusDao {
    @Query("SELECT * FROM source_status")
    fun observeAll(): Flow<List<SourceStatusEntity>>

    @Query("SELECT * FROM source_status")
    fun all(): List<SourceStatusEntity>

    @Query("SELECT * FROM source_status WHERE source_id = :sourceId AND kind = :kind")
    fun get(sourceId: String, kind: String): SourceStatusEntity?

    @Upsert
    fun upsert(status: SourceStatusEntity)

    @Query("SELECT COUNT(*) FROM source_status WHERE status = 'failed' AND last_failure_at >= :since AND kind IN (:kinds)")
    suspend fun failuresSince(since: Long, kinds: List<String>): Int

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
