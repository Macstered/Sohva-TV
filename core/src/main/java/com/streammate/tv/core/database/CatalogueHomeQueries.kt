package com.streammate.tv.core.database

/**
 * The queries behind the home screen's continue-watching row and the
 * catalogue's history partitions.
 *
 * They are kept as constants so a JVM test can put the exact SQL in front of
 * SQLite's planner without a device. Every one of them starts from
 * `playback_progress` and pins the join order with `CROSS JOIN`: the visible
 * movie and series views evaluate a stack of organisation-rule sub-queries per
 * row, so they must only ever be reached by a full primary-key lookup, and
 * never walked. Room never runs ANALYZE, so the planner has no statistics to
 * discover that on its own; a build in which it reached `vod_series` before
 * `vod_episodes` walked every series in the library per watched episode and
 * took seconds on a large library.
 */
const val MOVIE_HISTORY_CARDS_SQL = """
    SELECT movie.sourceId, movie.movieId, movie.name, movie.categoryName, movie.organizationGroupKey,
        movie.posterUrl, movie.year, movie.rating,
        metadata.replacementPosterUrl, metadata.replaceProviderPoster,
        metadata.replacementTitle, metadata.externalId,
        metadata.genresVersion AS metadataGenresVersion,
        (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
            WHERE genre.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        ) AS genresCsv
    FROM playback_progress progress
    CROSS JOIN iptv_source_state source
        ON source.sourceId = progress.sourceId AND source.enabled = 1
    CROSS JOIN import_state state
        ON state.sourceId = progress.sourceId AND state.kind = 'catalogue'
    CROSS JOIN organization_visible_movies movie
        ON movie.sourceId = progress.sourceId AND movie.snapshotId = state.activeSnapshotId
            AND movie.movieId = progress.itemId
    LEFT JOIN catalogue_metadata_overrides metadata
        ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
    WHERE progress.contentType = 'movie'
    ORDER BY progress.lastWatchedEpochMillis DESC
"""

const val SERIES_HISTORY_CARDS_SQL = """
    SELECT item.sourceId, item.seriesId, item.name, item.categoryName, item.organizationGroupKey,
        item.posterUrl, item.year, item.rating,
        metadata.replacementPosterUrl, metadata.replaceProviderPoster,
        metadata.replacementTitle, metadata.externalId,
        metadata.genresVersion AS metadataGenresVersion,
        (SELECT GROUP_CONCAT(genre.genre) FROM catalogue_genres genre
            WHERE genre.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
        ) AS genresCsv
    FROM (
        SELECT episode.sourceId AS sourceId, episode.seriesId AS seriesId,
            MAX(progress.lastWatchedEpochMillis) AS lastWatchedEpochMillis
        FROM playback_progress progress
        CROSS JOIN vod_episodes episode INDEXED BY index_vod_episodes_sourceId_episodeId
            ON episode.sourceId = progress.sourceId AND episode.episodeId = progress.itemId
        WHERE progress.contentType = 'episode'
        GROUP BY episode.sourceId, episode.seriesId
    ) watched
    CROSS JOIN iptv_source_state source
        ON source.sourceId = watched.sourceId AND source.enabled = 1
    CROSS JOIN import_state state
        ON state.sourceId = watched.sourceId AND state.kind = 'catalogue'
    CROSS JOIN organization_visible_series item
        ON item.sourceId = watched.sourceId AND item.snapshotId = state.activeSnapshotId
            AND item.seriesId = watched.seriesId
    LEFT JOIN catalogue_metadata_overrides metadata
        ON metadata.contentKey = 'series:' || item.sourceId || ':' || item.seriesId
    ORDER BY watched.lastWatchedEpochMillis DESC
"""

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
const val CONTINUE_WATCHING_SQL = """
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
        CROSS JOIN iptv_source_state source
            ON source.sourceId = progress.sourceId AND source.enabled = 1
        CROSS JOIN import_state state
            ON state.sourceId = progress.sourceId AND state.kind = 'catalogue'
        CROSS JOIN organization_visible_movies movie
            ON movie.sourceId = progress.sourceId AND movie.snapshotId = state.activeSnapshotId
                AND movie.movieId = progress.itemId
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
        CROSS JOIN vod_episodes episode
            ON episode.sourceId = progress.sourceId AND episode.episodeId = progress.itemId
        CROSS JOIN iptv_source_state source
            ON source.sourceId = progress.sourceId AND source.enabled = 1
        CROSS JOIN import_state state
            ON state.sourceId = progress.sourceId AND state.kind = 'catalogue'
        CROSS JOIN organization_visible_series item
            ON item.sourceId = episode.sourceId AND item.snapshotId = state.activeSnapshotId
                AND item.seriesId = episode.seriesId
        WHERE progress.contentType = 'episode' AND progress.completed = 0 AND progress.positionMillis > 0
    )
    GROUP BY COALESCE(workKey, contentKey)
    ORDER BY lastWatchedEpochMillis DESC
    LIMIT 20
"""
