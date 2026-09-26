package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/**
 * The reads of Sohva Sport's stream pairing (spec 60 §4.10, §9 "Pairing"): channels in keyset pages
 * of the primary key, then only that batch's programmes in the games' window, also in keyset pages.
 * Nothing here returns a whole catalogue; beta 23 read every channel and programme at once and ran
 * out of memory (lessons, preview 48).
 */
@Dao
interface PairingDao {
    /** A visible live channel (SPORT-FR-103): its effective name and guide id. */
    data class ScanChannel(
        val id: Long,
        val key: String,
        @ColumnInfo(name = "source_id") val sourceId: String,
        val name: String,
        @ColumnInfo(name = "epg_id") val epgId: String?,
    )

    /** A programme in the window; its text is released as soon as it is scored. */
    data class ScanProgramme(
        val id: Long,
        @ColumnInfo(name = "epg_id") val epgId: String,
        @ColumnInfo(name = "start_at") val startAt: Long,
        val title: String,
        val subtitle: String?,
        val description: String?,
        @ColumnInfo(name = "programme_key") val programmeKey: String,
    )

    /** An enabled source's guide: its active snapshot and offset (SPORT-FR-104). */
    data class GuideSource(
        val id: String,
        @ColumnInfo(name = "epg_offset_minutes") val epgOffsetMinutes: Int,
        @ColumnInfo(name = "epg_snapshot") val epgSnapshot: Long?,
    )

    /**
     * One page of channels after [after] in row-id order. `CROSS JOIN` keeps the channel table the
     * outer loop (a primary-key range read); source and group are single-row lookups.
     */
    @Query(PairingSql.CHANNELS)
    suspend fun channels(after: Long, limit: Int): List<ScanChannel>

    /**
     * One page of a batch's programmes starting inside [from, to] (already shifted back by the
     * source's offset, so the indexed column is compared as stored), in row-id order after [after].
     */
    @Query(PairingSql.PROGRAMMES)
    suspend fun programmes(sourceId: String, snapshot: Long, epgIds: List<String>, from: Long, to: Long, after: Long, limit: Int): List<ScanProgramme>

    @Query(
        "SELECT s.id, s.epg_offset_minutes, st.epg_snapshot FROM source s " +
            "LEFT JOIN source_status st ON st.source_id = s.id AND st.kind = 'epg' WHERE s.enabled = 1",
    )
    suspend fun guideSources(): List<GuideSource>

    // The generation (SPORT-FR-101): small tables only, each row as one stable line.

    @Query("SELECT id || '|' || enabled || '|' || epg_offset_minutes FROM source ORDER BY id")
    suspend fun sourceLines(): List<String>

    @Query(
        "SELECT source_id || '|' || kind || '|' || generation || '|' || IFNULL(epg_snapshot, '') FROM source_status " +
            "WHERE kind IN ('playlist', 'epg') ORDER BY source_id, kind",
    )
    suspend fun snapshotLines(): List<String>

    /** The household's channel edits (at most a few thousand rows: edits, not the catalogue). */
    @Query(
        "SELECT channel_key || '|' || IFNULL(custom_name, '') || '|' || hidden || '|' || IFNULL(manual_epg_id, '') || '|' || " +
            "IFNULL(custom_group_key, '') FROM channel_custom WHERE channel_key > :after ORDER BY channel_key LIMIT :limit",
    )
    suspend fun customLines(after: String, limit: Int): List<String>

    @Query(
        "SELECT source_id || '|' || group_key || '|' || item_key || '|' || IFNULL(enabled, '') FROM organization_rule " +
            "WHERE room = 'LIVE' ORDER BY source_id, group_key, item_key",
    )
    suspend fun liveRuleLines(): List<String>

    @Query("SELECT sport || '|' || canonical || '|' || alias FROM team_alias ORDER BY sport, canonical, alias")
    suspend fun aliasLines(): List<String>

    /** Of [keys], the channels [profile] may watch (spec 60 §10 Q6: other streams are not offered). */
    @Query("SELECT c.key FROM channel c WHERE c.key IN (:keys) AND ${AllowedSql.LIVE_C}")
    suspend fun allowed(keys: List<String>, profile: String): List<String>
}

/** The scan's two hot queries, shared with their query-plan test (AGENTS.md §4 rule 3). */
object PairingSql {
    const val CHANNELS: String =
        "SELECT c.id, c.key, c.source_id, c.name, c.epg_id FROM channel c CROSS JOIN source s ON s.id = c.source_id " +
            "WHERE c.id > :after AND c.visible = 1 AND s.enabled = 1 " +
            "AND (c.group_id IS NULL OR EXISTS (SELECT 1 FROM content_group g WHERE g.id = c.group_id AND g.shown = 1)) " +
            "ORDER BY c.id LIMIT :limit"

    const val PROGRAMMES: String =
        "SELECT p.id, p.epg_id, p.start_at, p.title, p.subtitle, p.description, p.programme_key FROM programme p " +
            "WHERE p.source_id = :sourceId AND p.snapshot = :snapshot AND p.epg_id IN (:epgIds) " +
            "AND p.start_at >= :from AND p.start_at <= :to AND p.id > :after ORDER BY p.id LIMIT :limit"
}
