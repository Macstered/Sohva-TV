package com.streammate.tv.core.database

/**
 * Sports matching is a whole-library job, but must never materialize the whole
 * library. Walk physical channel rows, then read programmes only for that small
 * batch. Row ids are cursors for this scan only, never persisted identities.
 * A generation check prevents caching a scan across an import/preferences change.
 */
const val SPORTS_CHANNEL_CANDIDATES_PAGE_SQL = """
    SELECT /* sports-channel-page */ c.rowid AS channelRowId,
        c.sourceId AS sourceId, c.channelId AS channelId,
        COALESCE(NULLIF(preference.customName, ''), c.name) AS channelName
    FROM iptv_channels c
    CROSS JOIN iptv_source_state source_state
    CROSS JOIN import_state playlist_state
    LEFT JOIN channel_preferences preference ON preference.channelId = c.channelId
    WHERE c.rowid > :afterRowId
        AND source_state.sourceId = c.sourceId AND source_state.enabled = 1
        AND playlist_state.sourceId = c.sourceId AND playlist_state.kind = 'playlist'
        AND playlist_state.activeSnapshotId = c.snapshotId
        AND (""" + ORGANIZATION_VISIBLE_LIVE_PREDICATE + """)
    ORDER BY c.rowid
    LIMIT :limit
"""

/**
 * Both sides of the programme/channel cursor are needed: several streams can
 * map to the same XMLTV channel. The channel batch bounds the join/sort, while
 * LIMIT bounds Room's strings (including descriptions). CROSS JOIN keeps the
 * selected channels first; the existing programme index seeks their time range.
 * Keep the offset on the bound, not the indexed start column.
 */
const val SPORTS_PROGRAMME_CANDIDATES_PAGE_SQL = """
    SELECT /* sports-programme-page */ c.rowid AS channelRowId, p.rowid AS programmeRowId,
        c.sourceId AS sourceId, c.channelId AS channelId,
        COALESCE(NULLIF(preference.customName, ''), c.name) AS channelName,
        p.programmeId AS programmeId, p.title AS programmeTitle,
        p.subtitle AS programmeSubtitle, p.description AS programmeDescription,
        p.startEpochMillis + source_state.epgOffsetMinutes * 60000 AS programmeStartEpochMillis,
        p.stopEpochMillis + source_state.epgOffsetMinutes * 60000 AS programmeStopEpochMillis
    FROM iptv_channels c
    CROSS JOIN iptv_source_state source_state
    CROSS JOIN import_state playlist_state
    LEFT JOIN channel_preferences preference ON preference.channelId = c.channelId
    CROSS JOIN import_state epg_state
    CROSS JOIN tv_programmes p INDEXED BY index_tv_programmes_sourceId_xmltvChannelId_startEpochMillis_stopEpochMillis
    WHERE c.rowid IN (:channelRowIds)
        AND source_state.sourceId = c.sourceId AND source_state.enabled = 1
        AND playlist_state.sourceId = c.sourceId AND playlist_state.kind = 'playlist'
        AND playlist_state.activeSnapshotId = c.snapshotId
        AND epg_state.sourceId = c.sourceId AND epg_state.kind = 'epg'
        AND p.sourceId = c.sourceId AND p.snapshotId = epg_state.activeSnapshotId
        AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
        AND p.startEpochMillis BETWEEN :fromEpochMillis - source_state.epgOffsetMinutes * 60000
            AND :toEpochMillis - source_state.epgOffsetMinutes * 60000
        AND (p.rowid > :afterProgrammeRowId OR
            (p.rowid = :afterProgrammeRowId AND c.rowid > :afterChannelRowId))
        AND (""" + ORGANIZATION_VISIBLE_LIVE_PREDICATE + """)
    ORDER BY p.rowid, c.rowid
    LIMIT :limit
"""
