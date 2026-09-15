package com.streammate.tv.core.database

/**
 * Resolve the small paused set before touching the catalogue. CROSS JOIN keeps SQLite from
 * starting with every movie/series. Split the existing route key only on these matched rows,
 * then reach titles by their full indexed keys; no new identity table or migration is needed.
 * Collapse provider copies before LIMIT. SQLite takes the bare display columns from the
 * single MIN(contentKey) row, giving a deterministic playable copy for each Trakt identity.
 */
const val TRAKT_CONTINUE_WATCHING_SQL = """
    SELECT MIN(contentKey) AS contentKey, contentType, title, year, posterUrl, seriesName, seriesKey,
        seasonNumber, episodeNumber, durationSeconds, progress, updatedAtMillis, tmdbId, imdbId
    FROM (
    SELECT metadata.contentKey AS contentKey, 'movie' AS contentType, movie.name AS title, movie.year AS year,
        movie.posterUrl AS posterUrl, NULL AS seriesName, NULL AS seriesKey, NULL AS seasonNumber, NULL AS episodeNumber,
        NULL AS durationSeconds, state.progress AS progress, state.updatedAtMillis AS updatedAtMillis, state.tmdb AS tmdbId, state.imdb AS imdbId
    FROM trakt_state state
    CROSS JOIN catalogue_metadata_overrides metadata
        ON metadata.externalId = CAST(state.tmdb AS TEXT) AND metadata.contentKey LIKE 'vod:movie:%'
    CROSS JOIN iptv_source_state source
        ON source.sourceId = SUBSTR(metadata.contentKey, 11, INSTR(SUBSTR(metadata.contentKey, 11), ':') - 1)
            AND source.enabled = 1
    CROSS JOIN import_state import ON import.sourceId = source.sourceId AND import.kind = 'catalogue'
    CROSS JOIN organization_visible_movies movie
        ON movie.sourceId = source.sourceId AND movie.snapshotId = import.activeSnapshotId
            AND movie.movieId = SUBSTR(metadata.contentKey, 11 + INSTR(SUBSTR(metadata.contentKey, 11), ':'))
    WHERE state.profileId = :profileId AND state.kind = 'movie' AND state.tmdb IS NOT NULL
        AND state.progress > 0 AND state.progress < 100
    UNION ALL
    SELECT 'vod:episode:' || episode.sourceId || ':' || episode.episodeId AS contentKey, 'episode' AS contentType,
        episode.name AS title, NULL AS year, item.posterUrl AS posterUrl, item.name AS seriesName, metadata.contentKey AS seriesKey,
        episode.seasonNumber AS seasonNumber, episode.episodeNumber AS episodeNumber,
        episode.durationSeconds AS durationSeconds, state.progress AS progress, state.updatedAtMillis AS updatedAtMillis, state.tmdb AS tmdbId, state.imdb AS imdbId
    FROM trakt_state state
    CROSS JOIN catalogue_metadata_overrides metadata
        ON metadata.externalId = CAST(state.tmdb AS TEXT) AND metadata.contentKey LIKE 'series:%'
    CROSS JOIN iptv_source_state source
        ON source.sourceId = SUBSTR(metadata.contentKey, 8, INSTR(SUBSTR(metadata.contentKey, 8), ':') - 1)
            AND source.enabled = 1
    CROSS JOIN import_state import ON import.sourceId = source.sourceId AND import.kind = 'catalogue'
    CROSS JOIN organization_visible_series item
        ON item.sourceId = source.sourceId AND item.snapshotId = import.activeSnapshotId
            AND item.seriesId = SUBSTR(metadata.contentKey, 8 + INSTR(SUBSTR(metadata.contentKey, 8), ':'))
    CROSS JOIN vod_episodes episode
        ON episode.sourceId = item.sourceId AND episode.seriesId = item.seriesId
            AND episode.seasonNumber = state.season AND episode.episodeNumber = state.number
    WHERE state.profileId = :profileId AND state.kind = 'episode' AND state.tmdb IS NOT NULL
        AND state.progress > 0 AND state.progress < 100
        AND EXISTS (SELECT 1 FROM metadata_cache cache WHERE cache.provider = 'tmdb' AND cache.externalId = metadata.externalId)
    )
    GROUP BY contentType, tmdbId, seasonNumber, episodeNumber
    ORDER BY updatedAtMillis DESC
    LIMIT 20
"""
