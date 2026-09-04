package com.streammate.tv.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CatalogueDao {
    @Query("""
        SELECT 'vod:movie:' || movie.sourceId || ':' || movie.movieId FROM organization_visible_movies movie
        JOIN iptv_source_state source ON source.sourceId=movie.sourceId AND source.enabled=1
        JOIN import_state state ON state.sourceId=movie.sourceId AND state.kind='catalogue' AND state.activeSnapshotId=movie.snapshotId
        WHERE 'vod:movie:' || movie.sourceId || ':' || movie.movieId IN (:keys)
    """)
    abstract suspend fun visibleMovieKeys(keys: List<String>): List<String>
    @Upsert
    abstract suspend fun upsertMovies(movies: List<VodMovieEntity>)

    @Upsert
    abstract suspend fun upsertSeries(series: List<VodSeriesEntity>)

    @Upsert
    protected abstract suspend fun upsertEpisodes(episodes: List<VodEpisodeEntity>)

    @Upsert
    abstract suspend fun upsertProgress(progress: PlaybackProgressEntity)

    @Upsert
    protected abstract suspend fun upsertImportState(state: ImportStateEntity)

    @Upsert
    protected abstract suspend fun upsertRefreshState(state: SourceRefreshStateEntity)

    @Query("DELETE FROM vod_movies WHERE sourceId = :sourceId AND snapshotId = :snapshotId")
    abstract suspend fun deleteMovieSnapshot(sourceId: String, snapshotId: String)

    @Query("DELETE FROM vod_series WHERE sourceId = :sourceId AND snapshotId = :snapshotId")
    abstract suspend fun deleteSeriesSnapshot(sourceId: String, snapshotId: String)

    @Query("DELETE FROM vod_movies WHERE sourceId = :sourceId AND snapshotId != :activeSnapshotId")
    protected abstract suspend fun deleteInactiveMovieSnapshots(sourceId: String, activeSnapshotId: String)

    @Query("DELETE FROM vod_series WHERE sourceId = :sourceId AND snapshotId != :activeSnapshotId")
    protected abstract suspend fun deleteInactiveSeriesSnapshots(sourceId: String, activeSnapshotId: String)

    @Query(
        """
        DELETE FROM vod_episodes
        WHERE sourceId = :sourceId AND seriesId NOT IN (
            SELECT seriesId FROM vod_series
            WHERE sourceId = :sourceId AND snapshotId = :activeSnapshotId
        )
        """,
    )
    protected abstract suspend fun deleteOrphanEpisodes(sourceId: String, activeSnapshotId: String)

    @Query("DELETE FROM vod_episodes WHERE sourceId = :sourceId AND seriesId = :seriesId")
    protected abstract suspend fun deleteSeriesEpisodes(sourceId: String, seriesId: String)

    @Transaction
    open suspend fun replaceSeriesEpisodes(
        sourceId: String,
        seriesId: String,
        episodes: List<VodEpisodeEntity>,
    ) {
        deleteSeriesEpisodes(sourceId, seriesId)
        upsertEpisodes(episodes)
    }

    @Transaction
    open suspend fun activateCatalogueSnapshot(
        sourceId: String,
        snapshotId: String,
        itemCount: Int,
        now: Long,
    ) {
        upsertImportState(ImportStateEntity(sourceId, CATALOGUE_KIND, snapshotId, now, itemCount))
        upsertRefreshState(
            SourceRefreshStateEntity(
                sourceId = sourceId,
                kind = CATALOGUE_KIND,
                status = GuideDao.REFRESH_SUCCESS,
                lastAttemptAtEpochMillis = now,
                lastSuccessAtEpochMillis = now,
                lastFailureAtEpochMillis = null,
                lastError = null,
                itemCount = itemCount,
                consecutiveFailures = 0,
            ),
        )
        deleteInactiveMovieSnapshots(sourceId, snapshotId)
        deleteInactiveSeriesSnapshots(sourceId, snapshotId)
        deleteOrphanEpisodes(sourceId, snapshotId)
    }

    suspend fun markCatalogueRefreshStarted(sourceId: String, now: Long) {
        val previous = refreshState(sourceId, CATALOGUE_KIND)
        upsertRefreshState(
            SourceRefreshStateEntity(
                sourceId = sourceId,
                kind = CATALOGUE_KIND,
                status = GuideDao.REFRESH_RUNNING,
                lastAttemptAtEpochMillis = now,
                lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                lastFailureAtEpochMillis = previous?.lastFailureAtEpochMillis,
                lastError = null,
                itemCount = previous?.itemCount ?: 0,
                consecutiveFailures = previous?.consecutiveFailures ?: 0,
            ),
        )
    }

    suspend fun markCatalogueRefreshFailed(sourceId: String, now: Long, error: String?) {
        val previous = refreshState(sourceId, CATALOGUE_KIND)
        upsertRefreshState(
            SourceRefreshStateEntity(
                sourceId = sourceId,
                kind = CATALOGUE_KIND,
                status = GuideDao.REFRESH_FAILED,
                lastAttemptAtEpochMillis = previous?.lastAttemptAtEpochMillis ?: now,
                lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                lastFailureAtEpochMillis = now,
                lastError = error,
                itemCount = previous?.itemCount ?: 0,
                consecutiveFailures = (previous?.consecutiveFailures ?: 0) + 1,
            ),
        )
    }

    @Query("SELECT * FROM source_refresh_state WHERE sourceId = :sourceId AND kind = :kind LIMIT 1")
    protected abstract suspend fun refreshState(sourceId: String, kind: String): SourceRefreshStateEntity?

    @Query(
        """
        SELECT state.sourceId, state.activeSnapshotId, state.updatedAtEpochMillis, state.itemCount
        FROM import_state state
        INNER JOIN iptv_source_state source
            ON source.sourceId = state.sourceId AND source.enabled = 1
        WHERE state.kind = 'catalogue'
        ORDER BY state.sourceId
        """,
    )
    abstract fun observeCatalogueGenerations(): Flow<List<CatalogueGenerationRow>>

    @Query(
        """
        SELECT movie.* FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        ORDER BY movie.name
        """,
    )
    abstract fun observeMovies(): Flow<List<VodMovieEntity>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
            ) AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        ORDER BY movie.name
        """,
    )
    abstract fun observeMovieCards(): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
            ) AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        INNER JOIN playback_progress progress
            ON progress.sourceId = movie.sourceId AND progress.itemId = movie.movieId
                AND progress.contentType = 'movie'
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        ORDER BY progress.lastWatchedEpochMillis DESC
        """,
    )
    abstract fun observeMovieHistoryCards(): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.* FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        WHERE TRIM(movie.categoryName) = :category COLLATE NOCASE
        ORDER BY movie.name
        """,
    )
    abstract fun observeMoviesByCategory(category: String): Flow<List<VodMovieEntity>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
            ) AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE movie.categoryKey = :categoryKey
        ORDER BY movie.name
        """,
    )
    abstract fun observeMovieCardsByCategory(categoryKey: String): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            :genre AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE EXISTS (
            SELECT 1 FROM catalogue_genres selectedGenre
            WHERE selectedGenre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
                AND selectedGenre.genre = :genre
        )
        ORDER BY movie.name
        """,
    )
    abstract fun observeMovieCardsInGenre(genre: String): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
            ) AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE (:anyGenre OR EXISTS (
            SELECT 1 FROM catalogue_genres selectedGenre
            WHERE selectedGenre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
                AND selectedGenre.genre IN (:genres)
        ))
            AND (:fromYear IS NULL OR movie.year >= :fromYear)
            AND (:toYear IS NULL OR movie.year <= :toYear)
            AND (:minRating IS NULL OR (TRIM(movie.rating) GLOB '[0-9]*' AND
                CAST(REPLACE(REPLACE(REPLACE(TRIM(movie.rating), ',', '.'), 'e', '/'), 'E', '/') AS REAL) >= :minRating))
            AND (:search = '' OR INSTR(LOWER(movie.name), LOWER(:search)) > 0
                OR INSTR(LOWER(metadata.replacementTitle), LOWER(:search)) > 0)
        ORDER BY movie.name
        """,
    )
    abstract fun observeMovieCardsInCustomGroup(
        genres: List<String>,
        anyGenre: Boolean,
        fromYear: Int?,
        toYear: Int?,
        minRating: Double?,
        search: String,
    ): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            NULL AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE NOT EXISTS (
            SELECT 1 FROM catalogue_genres genre
            WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        )
        ORDER BY movie.name
        """,
    )
    abstract fun observeUnsortedMovieCards(): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
            ) AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE movie.name LIKE '%' || :query || '%' COLLATE NOCASE
            OR metadata.replacementTitle LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY movie.name
        """,
    )
    abstract fun observeMovieCardsMatching(query: String): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
            ) AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE movie.categoryKey = :categoryKey
            AND (
                movie.name LIKE '%' || :query || '%' COLLATE NOCASE
                OR metadata.replacementTitle LIKE '%' || :query || '%' COLLATE NOCASE
            )
        ORDER BY movie.name
        """,
    )
    abstract fun observeMovieCardsByCategoryMatching(
        categoryKey: String,
        query: String,
    ): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            :genre AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE EXISTS (
            SELECT 1 FROM catalogue_genres selectedGenre
            WHERE selectedGenre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
                AND selectedGenre.genre = :genre
        )
            AND (
                movie.name LIKE '%' || :query || '%' COLLATE NOCASE
                OR metadata.replacementTitle LIKE '%' || :query || '%' COLLATE NOCASE
            )
        ORDER BY movie.name
        """,
    )
    abstract fun observeMovieCardsInGenreMatching(
        genre: String,
        query: String,
    ): Flow<List<VodMovieCardRow>>

    @Query(
        """
        SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
            movie.posterUrl, movie.year, movie.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            NULL AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE NOT EXISTS (
            SELECT 1 FROM catalogue_genres genre
            WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        )
            AND (
                movie.name LIKE '%' || :query || '%' COLLATE NOCASE
                OR metadata.replacementTitle LIKE '%' || :query || '%' COLLATE NOCASE
            )
        ORDER BY movie.name
        """,
    )
    abstract fun observeUnsortedMovieCardsMatching(query: String): Flow<List<VodMovieCardRow>>

    // CROSS JOIN is intentional: it prevents SQLite from putting the much
    // larger genre table on the outside and scanning every catalogue row for
    // each genre row. The content-key primary key then makes this one indexed
    // genre lookup per active movie.
    @Query(
        """
        SELECT genre.genre AS genre, COUNT(DISTINCT COALESCE((SELECT alias.identity FROM organization_aliases alias WHERE alias.alias = 'vod:movie:' || movie.sourceId || ':' || movie.movieId), 'vod:movie:' || movie.sourceId || ':' || movie.movieId)) AS itemCount
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        CROSS JOIN catalogue_genres genre
        WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        GROUP BY genre.genre
        ORDER BY genre.genre
        """,
    )
    abstract fun observeMovieGenreCounts(): Flow<List<CatalogueGenreCount>>

    @Query(
        """
        SELECT COUNT(DISTINCT COALESCE((SELECT alias.identity FROM organization_aliases alias WHERE alias.alias = 'vod:movie:' || movie.sourceId || ':' || movie.movieId), 'vod:movie:' || movie.sourceId || ':' || movie.movieId)) FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        WHERE NOT EXISTS (
            SELECT 1 FROM catalogue_genres genre
            WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        )
        """,
    )
    abstract fun observeUnsortedMovieCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT COALESCE((SELECT alias.identity FROM organization_aliases alias WHERE alias.alias = 'vod:movie:' || movie.sourceId || ':' || movie.movieId), 'vod:movie:' || movie.sourceId || ':' || movie.movieId)) FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        INNER JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE metadata.genresVersion >= :targetGenresVersion
        """,
    )
    abstract fun observeSortedMovieCount(targetGenresVersion: Int): Flow<Int>

    @Query(
        """
        SELECT 'vod:movie:' || movie.sourceId || ':' || movie.movieId AS contentKey,
            movie.year AS year, movie.rating AS rating,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
            ) AS genresCsv
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        """,
    )
    abstract fun observeMovieGroupFacets(): Flow<List<CatalogueGroupFacetRow>>

    @Query(
        """
        SELECT MIN(TRIM(movie.categoryName)) AS categoryName, COUNT(DISTINCT COALESCE((SELECT alias.identity FROM organization_aliases alias WHERE alias.alias = 'vod:movie:' || movie.sourceId || ':' || movie.movieId), 'vod:movie:' || movie.sourceId || ':' || movie.movieId)) AS itemCount
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        WHERE movie.categoryKey IS NOT NULL
        GROUP BY movie.categoryKey
        ORDER BY categoryName COLLATE NOCASE
        """,
    )
    abstract fun observeMovieCategories(): Flow<List<CatalogueCategoryRow>>

    @Query(
        """
        SELECT COUNT(DISTINCT COALESCE((SELECT alias.identity FROM organization_aliases alias WHERE alias.alias = 'vod:movie:' || movie.sourceId || ':' || movie.movieId), 'vod:movie:' || movie.sourceId || ':' || movie.movieId)) FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        """,
    )
    abstract fun observeMovieCount(): Flow<Int>

    @Query(
        """
        SELECT item.* FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        ORDER BY item.name
        """,
    )
    abstract fun observeSeries(): Flow<List<VodSeriesEntity>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
            ) AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        ORDER BY item.name
        """,
    )
    abstract fun observeSeriesCards(): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
            ) AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        INNER JOIN (
            SELECT episode.sourceId AS sourceId, episode.seriesId AS seriesId,
                MAX(progress.lastWatchedEpochMillis) AS lastWatchedEpochMillis
            FROM vod_episodes episode
            INNER JOIN playback_progress progress
                ON progress.sourceId = episode.sourceId AND progress.itemId = episode.episodeId
                    AND progress.contentType = 'episode'
            GROUP BY episode.sourceId, episode.seriesId
        ) watched ON watched.sourceId = item.sourceId AND watched.seriesId = item.seriesId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        ORDER BY watched.lastWatchedEpochMillis DESC
        """,
    )
    abstract fun observeSeriesHistoryCards(): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.* FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE TRIM(item.categoryName) = :category COLLATE NOCASE
        ORDER BY item.name
        """,
    )
    abstract fun observeSeriesByCategory(category: String): Flow<List<VodSeriesEntity>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
            ) AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE item.categoryKey = :categoryKey
        ORDER BY item.name
        """,
    )
    abstract fun observeSeriesCardsByCategory(categoryKey: String): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            :genre AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE EXISTS (
            SELECT 1 FROM catalogue_genres selectedGenre
            WHERE selectedGenre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
                AND selectedGenre.genre = :genre
        )
        ORDER BY item.name
        """,
    )
    abstract fun observeSeriesCardsInGenre(genre: String): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
            ) AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE (:anyGenre OR EXISTS (
            SELECT 1 FROM catalogue_genres selectedGenre
            WHERE selectedGenre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
                AND selectedGenre.genre IN (:genres)
        ))
            AND (:fromYear IS NULL OR item.year >= :fromYear)
            AND (:toYear IS NULL OR item.year <= :toYear)
            AND (:minRating IS NULL OR (TRIM(item.rating) GLOB '[0-9]*' AND
                CAST(REPLACE(REPLACE(REPLACE(TRIM(item.rating), ',', '.'), 'e', '/'), 'E', '/') AS REAL) >= :minRating))
            AND (:search = '' OR INSTR(LOWER(item.name), LOWER(:search)) > 0
                OR INSTR(LOWER(metadata.replacementTitle), LOWER(:search)) > 0)
        ORDER BY item.name
        """,
    )
    abstract fun observeSeriesCardsInCustomGroup(
        genres: List<String>,
        anyGenre: Boolean,
        fromYear: Int?,
        toYear: Int?,
        minRating: Double?,
        search: String,
    ): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            NULL AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE NOT EXISTS (
            SELECT 1 FROM catalogue_genres genre
            WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        )
        ORDER BY item.name
        """,
    )
    abstract fun observeUnsortedSeriesCards(): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
            ) AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE item.name LIKE '%' || :query || '%' COLLATE NOCASE
            OR metadata.replacementTitle LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY item.name
        """,
    )
    abstract fun observeSeriesCardsMatching(query: String): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
            ) AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE item.categoryKey = :categoryKey
            AND (
                item.name LIKE '%' || :query || '%' COLLATE NOCASE
                OR metadata.replacementTitle LIKE '%' || :query || '%' COLLATE NOCASE
            )
        ORDER BY item.name
        """,
    )
    abstract fun observeSeriesCardsByCategoryMatching(
        categoryKey: String,
        query: String,
    ): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            :genre AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE EXISTS (
            SELECT 1 FROM catalogue_genres selectedGenre
            WHERE selectedGenre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
                AND selectedGenre.genre = :genre
        )
            AND (
                item.name LIKE '%' || :query || '%' COLLATE NOCASE
                OR metadata.replacementTitle LIKE '%' || :query || '%' COLLATE NOCASE
            )
        ORDER BY item.name
        """,
    )
    abstract fun observeSeriesCardsInGenreMatching(
        genre: String,
        query: String,
    ): Flow<List<VodSeriesCardRow>>

    @Query(
        """
        SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
            item.posterUrl, item.year, item.rating,
            metadata.replacementPosterUrl, metadata.replaceProviderPoster,
            metadata.replacementTitle, metadata.externalId,
            metadata.genresVersion AS metadataGenresVersion,
            NULL AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE NOT EXISTS (
            SELECT 1 FROM catalogue_genres genre
            WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        )
            AND (
                item.name LIKE '%' || :query || '%' COLLATE NOCASE
                OR metadata.replacementTitle LIKE '%' || :query || '%' COLLATE NOCASE
            )
        ORDER BY item.name
        """,
    )
    abstract fun observeUnsortedSeriesCardsMatching(query: String): Flow<List<VodSeriesCardRow>>

    // Keep the active series table on the outside for the same reason as the
    // movie count above; changing this back to an ordinary inner join lets the
    // query planner choose a multi-billion-comparison plan on large libraries.
    @Query(
        """
        SELECT genre.genre AS genre, COUNT(*) AS itemCount
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        CROSS JOIN catalogue_genres genre
        WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        GROUP BY genre.genre
        ORDER BY genre.genre
        """,
    )
    abstract fun observeSeriesGenreCounts(): Flow<List<CatalogueGenreCount>>

    @Query(
        """
        SELECT COUNT(*) FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE NOT EXISTS (
            SELECT 1 FROM catalogue_genres genre
            WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        )
        """,
    )
    abstract fun observeUnsortedSeriesCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        INNER JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        WHERE metadata.genresVersion >= :targetGenresVersion
        """,
    )
    abstract fun observeSortedSeriesCount(targetGenresVersion: Int): Flow<Int>

    @Query(
        """
        SELECT 'series:' || item.sourceId || ':' || item.seriesId AS contentKey,
            item.year AS year, item.rating AS rating,
            (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
                WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
            ) AS genresCsv
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        """,
    )
    abstract fun observeSeriesGroupFacets(): Flow<List<CatalogueGroupFacetRow>>

    @Query(
        """
        SELECT MIN(TRIM(item.categoryName)) AS categoryName, COUNT(*) AS itemCount
        FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE item.categoryKey IS NOT NULL
        GROUP BY item.categoryKey
        ORDER BY categoryName COLLATE NOCASE
        """,
    )
    abstract fun observeSeriesCategories(): Flow<List<CatalogueCategoryRow>>

    @Query(
        """
        SELECT COUNT(*) FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        """,
    )
    abstract fun observeSeriesCount(): Flow<Int>

    @Query(
        """
        SELECT episode.* FROM vod_episodes episode
        INNER JOIN vod_series item ON item.sourceId = episode.sourceId AND item.seriesId = episode.seriesId
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE episode.sourceId = :sourceId AND episode.seriesId = :seriesId
        ORDER BY episode.seasonNumber, episode.episodeNumber
        """,
    )
    abstract fun observeEpisodes(sourceId: String, seriesId: String): Flow<List<VodEpisodeEntity>>

    @Query(
        """
        SELECT episode.* FROM vod_episodes episode
        INNER JOIN vod_series item ON item.sourceId = episode.sourceId AND item.seriesId = episode.seriesId
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        ORDER BY episode.sourceId, episode.seriesId, episode.seasonNumber, episode.episodeNumber
        """,
    )
    abstract fun observeAllActiveEpisodes(): Flow<List<VodEpisodeEntity>>

    @Query(
        """
        SELECT movie.* FROM vod_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        WHERE movie.sourceId = :sourceId AND movie.movieId = :movieId
        LIMIT 1
        """,
    )
    abstract suspend fun activeMovie(sourceId: String, movieId: String): VodMovieEntity?

    @Query(
        """
        SELECT item.* FROM vod_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE item.sourceId = :sourceId AND item.seriesId = :seriesId
        LIMIT 1
        """,
    )
    abstract suspend fun activeSeries(sourceId: String, seriesId: String): VodSeriesEntity?

    @Query(
        """
        SELECT movie.*, metadata.replacementTitle AS replacementTitle
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE movie.name LIKE '%' || :title || '%' COLLATE NOCASE
            OR metadata.replacementTitle LIKE '%' || :title || '%' COLLATE NOCASE
        ORDER BY movie.name
        LIMIT :limit
        """,
    )
    abstract suspend fun activeMoviesMatchingTitle(
        title: String,
        limit: Int,
    ): List<AvailableMovieMatchRow>

    /**
     * Everything that could be another copy of one film: a wide net cast by
     * name, carrying the playlist each came from and the record each matched.
     *
     * Only candidates. Whether two of these really are one film is decided
     * above this, by the same rule the wall groups by - the database has no
     * opinion on what a title reduces to.
     */
    @Query(
        """
        SELECT movie.*, source.name AS sourceName, metadata.externalId AS externalId
        FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        WHERE movie.name LIKE '%' || :title || '%' COLLATE NOCASE
        ORDER BY source.priority, source.name, movie.name
        LIMIT :limit
        """,
    )
    abstract suspend fun activeMovieCopies(title: String, limit: Int): List<CatalogueCopyRow>

    /**
     * One compact row per active movie or series for rebuilding the durable
     * metadata queue. This runs once after a catalogue change (or the v20
     * upgrade), not once per worker page, and deliberately leaves out plots,
     * encrypted URLs and every other field enrichment does not need.
     */
    @Query(
        """
        SELECT 'vod:movie:' || movie.sourceId || ':' || movie.movieId AS contentKey,
            'movie' AS mediaType, movie.name AS title, movie.year AS year,
            movie.posterUrl AS providerPosterUrl,
            COALESCE(metadata.genresVersion, 0) AS genresVersion
        FROM vod_movies movie
        INNER JOIN iptv_source_state source
            ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        UNION ALL
        SELECT 'series:' || item.sourceId || ':' || item.seriesId AS contentKey,
            'series' AS mediaType, item.name AS title, item.year AS year,
            item.posterUrl AS providerPosterUrl,
            COALESCE(metadata.genresVersion, 0) AS genresVersion
        FROM vod_series item
        INNER JOIN iptv_source_state source
            ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata
            ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        ORDER BY contentKey
        """,
    )
    abstract suspend fun catalogueMetadataCandidates(): List<CatalogueMetadataCandidateRow>

    @Query(
        """
        SELECT episode.* FROM vod_episodes episode
        INNER JOIN vod_series item ON item.sourceId = episode.sourceId AND item.seriesId = episode.seriesId
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE episode.sourceId = :sourceId AND episode.episodeId = :episodeId
        LIMIT 1
        """,
    )
    abstract suspend fun activeEpisode(sourceId: String, episodeId: String): VodEpisodeEntity?

    @Query(
        """
        SELECT episode.* FROM vod_episodes episode
        INNER JOIN vod_series item ON item.sourceId = episode.sourceId AND item.seriesId = episode.seriesId
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE episode.sourceId = :sourceId AND episode.seriesId = :seriesId
            AND (
                episode.seasonNumber > :seasonNumber OR
                (episode.seasonNumber = :seasonNumber AND episode.episodeNumber > :episodeNumber)
            )
        ORDER BY episode.seasonNumber, episode.episodeNumber
        LIMIT 1
        """,
    )
    abstract suspend fun nextActiveEpisode(
        sourceId: String,
        seriesId: String,
        seasonNumber: Int,
        episodeNumber: Int,
    ): VodEpisodeEntity?

    @Query(
        """
        SELECT movie.* FROM organization_visible_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        WHERE movie.name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY movie.name LIMIT :limit
        """,
    )
    abstract suspend fun searchMovies(query: String, limit: Int): List<VodMovieEntity>

    @Query(
        """
        SELECT item.* FROM organization_visible_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE item.name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY item.name LIMIT :limit
        """,
    )
    abstract suspend fun searchSeries(query: String, limit: Int): List<VodSeriesEntity>

    @Query(
        """
        SELECT episode.sourceId AS sourceId, episode.seriesId AS seriesId,
            item.name AS seriesName, item.posterUrl AS seriesPosterUrl,
            episode.episodeId AS episodeId, episode.seasonNumber AS seasonNumber,
            episode.episodeNumber AS episodeNumber, episode.name AS episodeName
        FROM vod_episodes episode
        INNER JOIN organization_visible_series item ON item.sourceId = episode.sourceId AND item.seriesId = episode.seriesId
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId AND source.enabled = 1
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        WHERE episode.name LIKE '%' || :query || '%' COLLATE NOCASE
            OR item.name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY item.name, episode.seasonNumber, episode.episodeNumber LIMIT :limit
        """,
    )
    abstract suspend fun searchEpisodes(query: String, limit: Int): List<VodEpisodeSearchRow>

    @Query("SELECT * FROM playback_progress ORDER BY lastWatchedEpochMillis DESC")
    abstract fun observeProgress(): Flow<List<PlaybackProgressEntity>>

    @Query(
        "SELECT * FROM playback_progress WHERE contentType = 'movie' " +
            "ORDER BY lastWatchedEpochMillis DESC",
    )
    abstract fun observeMovieProgress(): Flow<List<PlaybackProgressEntity>>

    /**
     * One row per film, not per copy.
     *
     * Two playlists carrying the same film would otherwise put the same
     * half-watched title on the home screen twice, which is the duplicate
     * problem where it is most annoying. The most recently watched copy wins:
     * SQLite takes the bare columns from the row that produced the MAX. Rows
     * with no work key - anything watched before that existed, and every
     * episode - group by their own content key and so stand alone.
     */
    @Query(
        """
        SELECT contentKey, contentType, title, year, posterUrl, seriesName,
            seasonNumber, episodeNumber, positionMillis, durationMillis, completed,
            MAX(lastWatchedEpochMillis) AS lastWatchedEpochMillis
        FROM (
            SELECT progress.contentKey AS contentKey, progress.contentType AS contentType,
                movie.name AS title, movie.year AS year, movie.posterUrl AS posterUrl,
                NULL AS seriesName, NULL AS seasonNumber, NULL AS episodeNumber,
                progress.positionMillis AS positionMillis, progress.durationMillis AS durationMillis,
                progress.completed AS completed, progress.lastWatchedEpochMillis AS lastWatchedEpochMillis,
                progress.workKey AS workKey
            FROM playback_progress progress
            INNER JOIN organization_visible_movies movie
                ON movie.sourceId = progress.sourceId AND movie.movieId = progress.itemId
            INNER JOIN iptv_source_state source
                ON source.sourceId = movie.sourceId AND source.enabled = 1
            INNER JOIN import_state state ON state.sourceId = movie.sourceId
                AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
            WHERE progress.contentType = 'movie' AND progress.completed = 0 AND progress.positionMillis > 0
            UNION ALL
            SELECT progress.contentKey AS contentKey, progress.contentType AS contentType,
                episode.name AS title, NULL AS year, item.posterUrl AS posterUrl,
                item.name AS seriesName, episode.seasonNumber AS seasonNumber,
                episode.episodeNumber AS episodeNumber,
                progress.positionMillis AS positionMillis, progress.durationMillis AS durationMillis,
                progress.completed AS completed, progress.lastWatchedEpochMillis AS lastWatchedEpochMillis,
                progress.workKey AS workKey
            FROM playback_progress progress
            INNER JOIN vod_episodes episode
                ON episode.sourceId = progress.sourceId AND episode.episodeId = progress.itemId
            INNER JOIN organization_visible_series item
                ON item.sourceId = episode.sourceId AND item.seriesId = episode.seriesId
            INNER JOIN iptv_source_state source
                ON source.sourceId = item.sourceId AND source.enabled = 1
            INNER JOIN import_state state ON state.sourceId = item.sourceId
                AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
            WHERE progress.contentType = 'episode' AND progress.completed = 0 AND progress.positionMillis > 0
        )
        GROUP BY COALESCE(workKey, contentKey)
        ORDER BY lastWatchedEpochMillis DESC
        LIMIT 20
        """,
    )
    abstract fun observeContinueWatching(): Flow<List<ContinueWatchingRow>>

    @Query("SELECT * FROM playback_progress WHERE contentKey = :contentKey LIMIT 1")
    abstract suspend fun progress(contentKey: String): PlaybackProgressEntity?

    /**
     * The furthest-on position anyone has reached in this film, whichever copy
     * of it recorded that. A copy that has never been played has no row of its
     * own and still resumes where the film was left.
     */
    @Query(
        """
        SELECT * FROM playback_progress WHERE workKey = :workKey
        ORDER BY lastWatchedEpochMillis DESC LIMIT 1
        """,
    )
    abstract suspend fun progressForWork(workKey: String): PlaybackProgressEntity?

    /** The record a title turned out to be, where a match has been made. */
    @Query("SELECT externalId FROM catalogue_metadata_overrides WHERE contentKey = :contentKey LIMIT 1")
    abstract suspend fun metadataExternalId(contentKey: String): String?

    companion object {
        const val CATALOGUE_KIND = "catalogue"
    }
}
