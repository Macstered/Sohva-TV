package com.sohva.tv.core.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** A title the queue synchronisation reads (spec 41 META-FR-54): its lookup facts and priority inputs. */
data class QueueCandidate(
    val key: String,
    val name: String,
    val year: Int?,
    val visible: Boolean,
    val watched: Boolean,
)

object MetadataSql {
    const val CACHE_GET = "SELECT * FROM metadata_cache WHERE lookup_key = :lookupKey AND provider = :provider"
    const val CACHE_SWEEP = "DELETE FROM metadata_cache WHERE expires_at < :now"
    const val CACHE_OLDEST = "DELETE FROM metadata_cache WHERE rowid IN (SELECT rowid FROM metadata_cache ORDER BY expires_at LIMIT :count)"
    const val FILM_CANDIDATES = "SELECT m.key, m.name, m.year, m.visible, " +
        "EXISTS (SELECT 1 FROM watch_progress w WHERE w.content_key = m.key) AS watched " +
        "FROM movie m WHERE m.key > :after AND m.key < :until ORDER BY m.key LIMIT :limit"
    const val SERIES_CANDIDATES = "SELECT s.key, s.name, s.year, s.visible, " +
        "EXISTS (SELECT 1 FROM watch_progress w WHERE w.series_key = s.key) AS watched " +
        "FROM series s WHERE s.key > :after AND s.key < :until ORDER BY s.key LIMIT :limit"
    const val QUEUE_OF = "SELECT * FROM metadata_queue WHERE content_key IN (:keys)"
    const val MATCHES_OF = "SELECT * FROM metadata_match WHERE content_key IN (:keys)"
    const val QUEUE_SWEEP = "DELETE FROM metadata_queue WHERE stamp < :stamp"
    const val QUEUE_PENDING = "SELECT * FROM metadata_queue WHERE state = 'pending' ORDER BY priority, content_key LIMIT :limit"
    const val QUEUE_RETRY_DUE = "SELECT * FROM metadata_queue WHERE state = 'retry' AND next_attempt_at <= :now " +
        "ORDER BY priority, content_key LIMIT :limit"
}

@Dao
interface MetadataDao {
    @Query(MetadataSql.CACHE_GET)
    fun cached(lookupKey: String, provider: String): MetadataCacheEntity?

    @Upsert
    fun putCache(row: MetadataCacheEntity)

    @Query("DELETE FROM metadata_cache WHERE lookup_key = :lookupKey")
    fun deleteCacheKey(lookupKey: String)

    @Query("DELETE FROM metadata_cache WHERE status = 'negative'")
    fun deleteNegativeCache()

    @Query("DELETE FROM metadata_cache")
    fun deleteAllCache()

    @Query(MetadataSql.CACHE_SWEEP)
    fun sweepCache(now: Long): Int

    @Query("SELECT COUNT(*) FROM metadata_cache")
    fun cacheCount(): Int

    @Query(MetadataSql.CACHE_OLDEST)
    fun deleteOldestCache(count: Int)

    @Query("SELECT * FROM metadata_pin WHERE content_key = :contentKey")
    fun pin(contentKey: String): MetadataPinEntity?

    @Query("SELECT * FROM metadata_pin WHERE work_key = :workKey LIMIT 1")
    fun pinOfWork(workKey: String): MetadataPinEntity?

    @Upsert
    fun putPin(row: MetadataPinEntity)

    @Query("DELETE FROM metadata_pin WHERE content_key = :contentKey")
    fun deletePin(contentKey: String)

    @Query("DELETE FROM metadata_pin WHERE work_key = :workKey")
    fun deletePinsOfWork(workKey: String)

    @Upsert
    fun putMatch(row: MetadataMatchEntity)

    @Query("SELECT * FROM metadata_match WHERE content_key = :contentKey")
    fun match(contentKey: String): MetadataMatchEntity?

    @Query(MetadataSql.MATCHES_OF)
    fun matchesOf(keys: List<String>): List<MetadataMatchEntity>

    @Query("DELETE FROM metadata_match WHERE content_key = :contentKey")
    fun deleteMatch(contentKey: String)

    @Query("DELETE FROM metadata_match")
    fun deleteAllMatches()

    @Query(MetadataSql.FILM_CANDIDATES)
    fun filmCandidates(after: String, until: String, limit: Int): List<QueueCandidate>

    @Query(MetadataSql.SERIES_CANDIDATES)
    fun seriesCandidates(after: String, until: String, limit: Int): List<QueueCandidate>

    @Query(MetadataSql.QUEUE_OF)
    fun queueOf(keys: List<String>): List<MetadataQueueEntity>

    @Upsert
    fun putQueue(rows: List<MetadataQueueEntity>)

    @Query(MetadataSql.QUEUE_SWEEP)
    fun sweepQueue(stamp: Long): Int

    @Query("SELECT MAX(stamp) FROM metadata_queue")
    fun newestStamp(): Long?

    @Query("SELECT COUNT(*) FROM metadata_queue")
    fun queueSize(): Int

    @Query("SELECT COUNT(*) FROM metadata_queue WHERE target_version != :version")
    fun outdatedQueueRows(version: Int): Int

    @Query(MetadataSql.QUEUE_PENDING)
    fun pending(limit: Int): List<MetadataQueueEntity>

    @Query(MetadataSql.QUEUE_RETRY_DUE)
    fun retryDue(now: Long, limit: Int): List<MetadataQueueEntity>

    @Query("DELETE FROM metadata_queue")
    fun deleteQueue()

    // Clearing runs in pages of ≤ 2,000 rows by id, so no single write holds the lock for the catalogue.
    @Query(
        "UPDATE movie SET replacement_title = NULL, replacement_sort = NULL, replacement_poster = NULL, replace_poster = 0, " +
            "replacement_key = NULL, genre = CASE WHEN :genres THEN NULL ELSE genre END WHERE id IN " +
            "(SELECT id FROM movie WHERE id > :after ORDER BY id LIMIT :limit)",
    )
    fun clearFilmPage(after: Long, limit: Int, genres: Boolean): Int

    @Query("SELECT MAX(id) FROM (SELECT id FROM movie WHERE id > :after ORDER BY id LIMIT :limit)")
    fun lastFilmId(after: Long, limit: Int): Long?

    @Query(
        "UPDATE series SET replacement_title = NULL, replacement_sort = NULL, replacement_poster = NULL, replace_poster = 0, " +
            "replacement_key = NULL, genre = CASE WHEN :genres THEN NULL ELSE genre END WHERE id IN " +
            "(SELECT id FROM series WHERE id > :after ORDER BY id LIMIT :limit)",
    )
    fun clearSeriesPage(after: Long, limit: Int, genres: Boolean): Int

    @Query("SELECT MAX(id) FROM (SELECT id FROM series WHERE id > :after ORDER BY id LIMIT :limit)")
    fun lastSeriesId(after: Long, limit: Int): Long?

    @Query("SELECT work_key FROM movie WHERE key = :key")
    fun workKeyOf(key: String): String?

    @Query("SELECT COALESCE((SELECT poster_url FROM movie WHERE key = :key), (SELECT poster_url FROM series WHERE key = :key))")
    fun providerPoster(key: String): String?
}
