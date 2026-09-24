package com.sohva.tv.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * The writes of the diff import (plan/04 §15.1 principle 3), one DAO per table with the same four
 * steps: look up the stored hashes of a batch's keys, insert new rows, update changed ones, and
 * after a complete parse walk the source's key range deleting unseen keys. Blocking by design: they
 * run inside the import's transactions on its background-priority writer thread (spec 10 SRC-L-01).
 *
 * The SQL is in [ImportSql] so the JVM plan tests check the very statements used here.
 */
@Dao
interface ChannelImportDao {
    @Query(ImportSql.CHANNEL_HASHES)
    fun hashes(keys: List<String>): List<KeyedHash>

    @Insert
    fun insert(rows: List<ChannelEntity>)

    @Update
    fun update(rows: List<ChannelEntity>)

    @Query(ImportSql.CHANNEL_KEYS_PAGE)
    fun keysPage(from: String, until: String, limit: Int): List<KeyedId>

    @Query(ImportSql.CHANNEL_DELETE)
    fun delete(ids: List<Long>)

    @Query(ImportSql.CHANNEL_COUNT)
    fun count(from: String, until: String): Int

    @Query(ImportSql.CHANNEL_EPG_PAGE)
    fun epgIdsPage(sourceId: String, afterEpgId: String, afterId: Long, limit: Int): List<ChannelEpgId>
}

/** A channel's guide id and catch-up depth: what the guide import's keep filter needs. */
data class ChannelEpgId(val id: Long, val epgId: String, val catchupType: String?, val catchupDays: Int?)

@Dao
interface MovieImportDao {
    @Query(ImportSql.MOVIE_HASHES)
    fun hashes(keys: List<String>): List<KeyedHash>

    @Insert
    fun insert(rows: List<MovieEntity>)

    @Update
    fun update(rows: List<MovieEntity>)

    @Query(ImportSql.MOVIE_KEYS_PAGE)
    fun keysPage(from: String, until: String, limit: Int): List<KeyedId>

    @Query(ImportSql.MOVIE_DELETE)
    fun delete(ids: List<Long>)

    @Query(ImportSql.MOVIE_COUNT)
    fun count(from: String, until: String): Int
}

@Dao
interface SeriesImportDao {
    @Query(ImportSql.SERIES_HASHES)
    fun hashes(keys: List<String>): List<KeyedHash>

    @Insert
    fun insert(rows: List<SeriesEntity>): List<Long>

    @Update
    fun update(rows: List<SeriesEntity>)

    @Query(ImportSql.SERIES_KEYS_PAGE)
    fun keysPage(from: String, until: String, limit: Int): List<KeyedId>

    @Query(ImportSql.SERIES_DELETE)
    fun delete(ids: List<Long>)

    @Query(ImportSql.SERIES_COUNT)
    fun count(from: String, until: String): Int
}

@Dao
interface EpisodeImportDao {
    @Query(ImportSql.EPISODE_HASHES)
    fun hashes(keys: List<String>): List<KeyedHash>

    @Insert
    fun insert(rows: List<EpisodeEntity>)

    @Update
    fun update(rows: List<EpisodeEntity>)

    @Query(ImportSql.EPISODE_KEYS_PAGE)
    fun keysPage(from: String, until: String, limit: Int): List<KeyedId>

    @Query(ImportSql.EPISODE_DELETE)
    fun delete(ids: List<Long>)

    /** Episodes whose series is gone (a series removed by the sweep). */
    @Query(ImportSql.EPISODE_ORPHANS_DELETE)
    fun deleteOrphans(from: String, until: String, limit: Int): Int
}

@Dao
interface GroupImportDao {
    @Query(ImportSql.GROUPS_OF_SOURCE)
    fun groups(sourceId: String, room: String): List<ContentGroupEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(group: ContentGroupEntity): Long

    @Update
    fun update(groups: List<ContentGroupEntity>)

    @Query(ImportSql.GROUP_DELETE)
    fun delete(ids: List<Long>)
}

@Dao
interface GuideImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChannels(rows: List<EpgChannelEntity>)

    @Insert
    fun insertProgrammes(rows: List<ProgrammeEntity>)

    /** Deletes up to [limit] programmes of [sourceId] outside snapshot [keep]; the count deleted. */
    @Query(ImportSql.PROGRAMME_SWEEP)
    fun sweepProgrammes(sourceId: String, keep: Long, limit: Int): Int

    @Query(ImportSql.EPG_CHANNEL_SWEEP)
    fun sweepChannels(sourceId: String, keep: Long, limit: Int): Int
}

/** The import SQL, as constants for the DAOs and the plan tests. */
object ImportSql {
    const val CHANNEL_HASHES = "SELECT id, key, content_hash AS hash FROM channel WHERE key IN (:keys)"
    const val CHANNEL_KEYS_PAGE = "SELECT id, key FROM channel WHERE key > :from AND key < :until ORDER BY key LIMIT :limit"
    const val CHANNEL_DELETE = "DELETE FROM channel WHERE id IN (:ids)"
    const val CHANNEL_COUNT = "SELECT COUNT(*) FROM channel WHERE key > :from AND key < :until"
    const val CHANNEL_EPG_PAGE = "SELECT id, epg_id AS epgId, catchup_type AS catchupType, catchup_days AS catchupDays FROM channel " +
        "WHERE source_id = :sourceId AND (epg_id > :afterEpgId OR (epg_id = :afterEpgId AND id > :afterId)) " +
        "ORDER BY epg_id, id LIMIT :limit"

    const val MOVIE_HASHES = "SELECT id, key, content_hash AS hash FROM movie WHERE key IN (:keys)"
    const val MOVIE_KEYS_PAGE = "SELECT id, key FROM movie WHERE key > :from AND key < :until ORDER BY key LIMIT :limit"
    const val MOVIE_DELETE = "DELETE FROM movie WHERE id IN (:ids)"
    const val MOVIE_COUNT = "SELECT COUNT(*) FROM movie WHERE key > :from AND key < :until"

    const val SERIES_HASHES = "SELECT id, key, content_hash AS hash FROM series WHERE key IN (:keys)"
    const val SERIES_KEYS_PAGE = "SELECT id, key FROM series WHERE key > :from AND key < :until ORDER BY key LIMIT :limit"
    const val SERIES_DELETE = "DELETE FROM series WHERE id IN (:ids)"
    const val SERIES_COUNT = "SELECT COUNT(*) FROM series WHERE key > :from AND key < :until"

    const val EPISODE_HASHES = "SELECT id, key, content_hash AS hash FROM episode WHERE key IN (:keys)"
    const val EPISODE_KEYS_PAGE = "SELECT id, key FROM episode WHERE key > :from AND key < :until ORDER BY key LIMIT :limit"
    const val EPISODE_DELETE = "DELETE FROM episode WHERE id IN (:ids)"
    const val EPISODE_ORPHANS_DELETE = "DELETE FROM episode WHERE id IN (SELECT e.id FROM episode AS e " +
        "WHERE e.key > :from AND e.key < :until AND NOT EXISTS (SELECT 1 FROM series AS s WHERE s.id = e.series_id) LIMIT :limit)"

    const val GROUPS_OF_SOURCE = "SELECT * FROM content_group WHERE source_id = :sourceId AND room = :room"
    const val GROUP_DELETE = "DELETE FROM content_group WHERE id IN (:ids)"

    const val PROGRAMME_SWEEP = "DELETE FROM programme WHERE id IN (SELECT id FROM programme " +
        "WHERE source_id = :sourceId AND snapshot <> :keep LIMIT :limit)"
    const val EPG_CHANNEL_SWEEP = "DELETE FROM epg_channel WHERE rowid IN (SELECT rowid FROM epg_channel " +
        "WHERE source_id = :sourceId AND snapshot <> :keep LIMIT :limit)"
}
