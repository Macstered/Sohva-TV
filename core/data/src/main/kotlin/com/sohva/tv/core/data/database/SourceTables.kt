package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A source's non-secret fields (plan/04 §15.2): the only copy. Addresses and credentials live in
 * the secret store under [id]. Enums are stored by name.
 */
@Entity(tableName = "source")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val enabled: Boolean,
    val priority: Int,
    @ColumnInfo(name = "connection_limit") val connectionLimit: Int,
    @ColumnInfo(name = "import_scope") val importScope: String,
    @ColumnInfo(name = "epg_offset_minutes") val epgOffsetMinutes: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * One row per source and kind (`playlist`, `epg`, `catalogue`): the refresh state, the failure as
 * a stable code with its arguments (spec 10 SRC-FR-105 rebuild rule), the import counter, and for
 * the guide its active snapshot.
 */
@Entity(tableName = "source_status", primaryKeys = ["source_id", "kind"])
data class SourceStatusEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    val kind: String,
    /** `idle`, `running`, `success` or `failed`. */
    val status: String,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long?,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long?,
    @ColumnInfo(name = "last_failure_at") val lastFailureAt: Long?,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    /** The error's arguments joined with U+001F. */
    @ColumnInfo(name = "error_args") val errorArgs: String?,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "consecutive_failures") val consecutiveFailures: Int,
    /** Counts imports of this kind; the next import's snapshot and row generation. */
    val generation: Long,
    @ColumnInfo(name = "epg_snapshot") val epgSnapshot: Long?,
    @ColumnInfo(name = "epg_max_duration_ms") val epgMaxDurationMs: Long?,
)

/**
 * The groups of a source per room (`LIVE`, `MOVIES`, `SERIES`): rails read this small table and
 * never `GROUP BY` over channels. `shown`, `position` and `sort_mode` are resolved from the
 * viewer's organisation rules (M3/M5); an import sets them to the provider's defaults.
 */
@Entity(
    tableName = "content_group",
    indices = [
        Index(value = ["source_id", "room", "group_key"], unique = true),
        Index(value = ["room", "source_id", "position"]),
    ],
)
data class ContentGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val room: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
    val name: String,
    @ColumnInfo(name = "provider_order") val providerOrder: Int,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    val shown: Boolean,
    val position: Int,
    @ColumnInfo(name = "sort_mode") val sortMode: String?,
)
