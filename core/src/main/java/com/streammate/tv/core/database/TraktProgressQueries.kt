package com.streammate.tv.core.database

/** Full overlays are keyed by identities; detail/wall queries start at the requested metadata keys. */
const val TRAKT_MOVIE_OVERLAY_SQL = """
    SELECT metadata.contentKey AS contentKey, NULL AS durationSeconds, state.progress, state.watched, state.updatedAtMillis
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
"""

const val TRAKT_EPISODE_OVERLAY_SQL = """
    SELECT 'vod:episode:' || episode.sourceId || ':' || episode.episodeId AS contentKey, episode.durationSeconds, state.progress, state.watched, state.updatedAtMillis
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
        AND EXISTS (SELECT 1 FROM metadata_cache cache WHERE cache.provider = 'tmdb' AND cache.externalId = metadata.externalId)
"""

const val TRAKT_SELECTED_MOVIES_SQL = """
    SELECT metadata.contentKey AS contentKey, NULL AS durationSeconds, state.progress, state.watched, state.updatedAtMillis
    FROM catalogue_metadata_overrides metadata
    CROSS JOIN trakt_state state ON state.profileId = :profileId AND state.kind = 'movie'
        AND state.tmdb = CAST(metadata.externalId AS INTEGER)
    WHERE metadata.contentKey IN (:contentKeys) AND metadata.contentKey LIKE 'vod:movie:%'
"""

const val TRAKT_SELECTED_SERIES_SQL = """
    SELECT 'vod:episode:' || episode.sourceId || ':' || episode.episodeId AS contentKey,
        episode.durationSeconds, state.progress, state.watched, state.updatedAtMillis
    FROM catalogue_metadata_overrides metadata
    CROSS JOIN trakt_state state ON state.profileId = :profileId AND state.kind = 'episode'
        AND state.tmdb = CAST(metadata.externalId AS INTEGER)
    CROSS JOIN vod_episodes episode ON episode.sourceId = :sourceId AND episode.seriesId = :seriesId
        AND episode.seasonNumber = state.season AND episode.episodeNumber = state.number
    WHERE metadata.contentKey = 'series:' || :sourceId || ':' || :seriesId
        AND EXISTS (SELECT 1 FROM metadata_cache cache WHERE cache.provider = 'tmdb' AND cache.externalId = metadata.externalId)
"""
