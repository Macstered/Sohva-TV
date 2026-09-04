package com.streammate.tv.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataDao {
    @Query(
        "SELECT * FROM metadata_cache " +
            "WHERE lookupKey = :lookupKey AND provider = :provider LIMIT 1",
    )
    suspend fun cached(lookupKey: String, provider: String): MetadataCacheEntity?

    @Upsert
    suspend fun upsert(entry: MetadataCacheEntity)

    @Query("SELECT * FROM catalogue_metadata_overrides")
    fun observeCatalogueMetadataOverrides(): Flow<List<CatalogueMetadataOverrideEntity>>

    @Query("SELECT * FROM catalogue_metadata_overrides WHERE contentKey GLOB 'vod:movie:*'")
    fun observeMovieCatalogueMetadataOverrides(): Flow<List<CatalogueMetadataOverrideEntity>>

    @Query("SELECT * FROM catalogue_metadata_overrides WHERE contentKey GLOB 'series:*'")
    fun observeSeriesCatalogueMetadataOverrides(): Flow<List<CatalogueMetadataOverrideEntity>>

    @Upsert
    suspend fun upsertCatalogueMetadataOverride(entry: CatalogueMetadataOverrideEntity)

    @Upsert
    suspend fun upsertCatalogueMetadataOverrides(entries: List<CatalogueMetadataOverrideEntity>)

    @Query("SELECT * FROM catalogue_metadata_overrides WHERE contentKey = :contentKey LIMIT 1")
    suspend fun catalogueMetadataOverride(contentKey: String): CatalogueMetadataOverrideEntity?

    @Query("SELECT genre FROM catalogue_genres WHERE contentKey = :contentKey")
    suspend fun genres(contentKey: String): List<String>

    /**
     * What the rail is built from: every genre present in the library and how
     * many titles are in it. Each enriched title has at most one row here, so
     * these counts do not inflate the library by repeating titles across rails.
     */
    @Query(
        "SELECT genre AS genre, COUNT(*) AS itemCount FROM catalogue_genres " +
            "GROUP BY genre ORDER BY genre",
    )
    fun observeGenreCounts(): Flow<List<CatalogueGenreCount>>

    @Query("SELECT * FROM catalogue_genres")
    fun observeGenres(): Flow<List<CatalogueGenreEntity>>

    @Query("SELECT * FROM catalogue_genres WHERE contentKey GLOB 'vod:movie:*'")
    fun observeMovieGenres(): Flow<List<CatalogueGenreEntity>>

    @Query("SELECT * FROM catalogue_genres WHERE contentKey GLOB 'series:*'")
    fun observeSeriesGenres(): Flow<List<CatalogueGenreEntity>>

    @Query("SELECT contentKey FROM catalogue_genres WHERE genre = :genre")
    fun observeContentKeysInGenre(genre: String): Flow<List<String>>

    @Upsert
    suspend fun upsertGenres(rows: List<CatalogueGenreEntity>)

    @Query("DELETE FROM catalogue_genres WHERE contentKey = :contentKey")
    suspend fun deleteGenres(contentKey: String)

    @Query("DELETE FROM catalogue_genres WHERE contentKey IN (:contentKeys)")
    suspend fun deleteGenres(contentKeys: List<String>)

    /**
     * Replaces rather than adds, so a title rematched to something else does not
     * keep the genres of what it used to be thought to be.
     */
    @Transaction
    suspend fun replaceGenres(contentKey: String, rows: List<CatalogueGenreEntity>) {
        deleteGenres(contentKey)
        rows.firstOrNull()?.let { upsertGenres(listOf(it)) }
    }

    @Query("SELECT * FROM catalogue_metadata_work")
    suspend fun catalogueMetadataWork(): List<CatalogueMetadataWorkEntity>

    @Query(
        "SELECT * FROM catalogue_metadata_work " +
            "WHERE (state = 'pending' OR state = 'retry') " +
            "AND nextAttemptAtEpochMillis <= :nowEpochMillis " +
            "ORDER BY contentKey LIMIT :limit",
    )
    suspend fun nextCatalogueMetadataWork(
        nowEpochMillis: Long,
        limit: Int,
    ): List<CatalogueMetadataWorkEntity>

    @Query("SELECT COUNT(*) FROM catalogue_metadata_work")
    suspend fun catalogueMetadataWorkCount(): Int

    @Query(
        "SELECT COUNT(*) FROM catalogue_metadata_work " +
            "WHERE targetGenresVersion != :targetGenresVersion",
    )
    suspend fun outdatedCatalogueMetadataWorkCount(targetGenresVersion: Int): Int

    @Upsert
    suspend fun upsertCatalogueMetadataWork(rows: List<CatalogueMetadataWorkEntity>)

    @Query("DELETE FROM catalogue_metadata_work")
    suspend fun clearCatalogueMetadataWork()

    @Query(
        "UPDATE catalogue_metadata_work SET state = 'complete', " +
            "targetGenresVersion = :genresVersion, attemptCount = 0, " +
            "nextAttemptAtEpochMillis = 0, updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE contentKey = :contentKey",
    )
    suspend fun markCatalogueMetadataWorkComplete(
        contentKey: String,
        genresVersion: Int,
        nowEpochMillis: Long,
    )

    @Transaction
    suspend fun replaceCatalogueMetadataWork(rows: List<CatalogueMetadataWorkEntity>) {
        clearCatalogueMetadataWork()
        if (rows.isNotEmpty()) upsertCatalogueMetadataWork(rows)
    }

    /** One transaction and therefore one invalidation per table for a worker page. */
    @Transaction
    suspend fun applyCatalogueMetadataBatch(
        replacedGenreContentKeys: List<String>,
        genreRows: List<CatalogueGenreEntity>,
        overrides: List<CatalogueMetadataOverrideEntity>,
        workRows: List<CatalogueMetadataWorkEntity>,
    ) {
        if (replacedGenreContentKeys.isNotEmpty()) deleteGenres(replacedGenreContentKeys)
        val primaryGenreRows = genreRows.distinctBy(CatalogueGenreEntity::contentKey)
        if (primaryGenreRows.isNotEmpty()) upsertGenres(primaryGenreRows)
        if (overrides.isNotEmpty()) upsertCatalogueMetadataOverrides(overrides)
        if (workRows.isNotEmpty()) upsertCatalogueMetadataWork(workRows)
    }

    @Transaction
    suspend fun applyCatalogueMetadata(
        contentKey: String,
        genreRows: List<CatalogueGenreEntity>,
        override: CatalogueMetadataOverrideEntity,
        genresVersion: Int,
        nowEpochMillis: Long,
    ) {
        deleteGenres(contentKey)
        genreRows.firstOrNull()?.let { upsertGenres(listOf(it)) }
        upsertCatalogueMetadataOverride(override)
        markCatalogueMetadataWorkComplete(contentKey, genresVersion, nowEpochMillis)
    }

    @Query("DELETE FROM catalogue_genres")
    suspend fun clearGenres()

    /** A match somebody chose does not expire, so the sweep steps over it. */
    @Query(
        "DELETE FROM metadata_cache " +
            "WHERE expiresAtEpochMillis <= :nowEpochMillis AND pinned = 0",
    )
    suspend fun deleteExpired(nowEpochMillis: Long)

    @Query("DELETE FROM metadata_cache WHERE lookupKey = :lookupKey")
    suspend fun deleteCached(lookupKey: String)

    @Query("DELETE FROM metadata_cache")
    suspend fun clear()

    @Query("DELETE FROM catalogue_metadata_overrides")
    suspend fun clearCatalogueMetadataOverrides()

    @Transaction
    suspend fun clearAllMetadata() {
        clear()
        clearGenres()
        clearCatalogueMetadataOverrides()
        clearCatalogueMetadataWork()
    }
}

/** One row of the genre rail: the genre, and how many titles carry it. */
data class CatalogueGenreCount(
    val genre: String,
    val itemCount: Int,
)
