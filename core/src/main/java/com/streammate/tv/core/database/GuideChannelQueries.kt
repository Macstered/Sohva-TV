package com.streammate.tv.core.database

/**
 * The channels of one source, or one of its groups, without their programmes.
 *
 * The guide reads every selection this way: a 56,000-channel source's
 * full timeline was 170,000 rows per emission. The rows come from this query
 * once; the programmes for the rows on screen come from
 * [GuideDao.observeGuideTimelineForChannels] as the grid scrolls. The columns
 * match [GuideTimelineRow] with the programme fields empty, so the same mapper
 * serves both. The rule predicate runs after the source and group filters, as
 * in the timeline query, so only the selection's channels pay for it.
 */
private const val GUIDE_CHANNEL_COLUMNS_SQL = """
        SELECT
            c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.priority AS sourcePriority,
            c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS channelName,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) AS groupTitle,
            COALESCE(NULLIF(preference.customLogoUrl, ''), c.logoUrl) AS logoUrl,
            COALESCE(preference.channelNumber, c.channelNumber) AS channelNumber,
            c.playlistOrder AS playlistOrder,
            preference.sortOrder AS legacyPosition,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            c.catchupType AS catchupType,
            c.catchupSource AS catchupSource,
            c.catchupDays AS catchupDays,
            CAST(NULL AS TEXT) AS programmeId,
            CAST(NULL AS TEXT) AS programmeTitle,
            CAST(NULL AS TEXT) AS programmeSubtitle,
            CAST(NULL AS TEXT) AS programmeDescription,
            CAST(NULL AS TEXT) AS programmeCategories,
            CAST(NULL AS INTEGER) AS programmeStartEpochMillis,
            CAST(NULL AS INTEGER) AS programmeStopEpochMillis
        """

private const val GUIDE_CHANNEL_TABLES_SQL = """
        FROM iptv_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
            AND source_state.enabled = 1
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId
            AND playlist_state.kind = 'playlist'
            AND c.snapshotId = playlist_state.activeSnapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        """

private const val GUIDE_CHANNEL_SOURCE_FILTER_SQL = """
        WHERE c.sourceId = :sourceId
        AND (:groupTitle IS NULL OR COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) = :groupTitle)
        AND (""" + ORGANIZATION_VISIBLE_LIVE_PREDICATE + """)
        """

private const val GUIDE_CHANNEL_ORDER_SQL = """
        ORDER BY source_state.priority DESC, source_state.name,
            COALESCE(preference.sortOrder, 2147483647),
            c.playlistOrder,
            COALESCE(NULLIF(preference.customName, ''), c.name)
        """

const val GUIDE_CHANNELS_FOR_SOURCE_SQL = GUIDE_CHANNEL_COLUMNS_SQL + GUIDE_CHANNEL_TABLES_SQL +
    GUIDE_CHANNEL_SOURCE_FILTER_SQL + GUIDE_CHANNEL_ORDER_SQL

/** Favourites, recent and custom lists can render without touching the EPG too. */
const val GUIDE_CHANNELS_FOR_IDS_SQL = GUIDE_CHANNEL_COLUMNS_SQL + GUIDE_CHANNEL_TABLES_SQL + """
        WHERE c.channelId IN (:channelIds)
        AND (""" + ORGANIZATION_VISIBLE_LIVE_PREDICATE + """)
        """ + GUIDE_CHANNEL_ORDER_SQL

/** Search returns channel IDs, not a materialized timeline for every matching programme. */
const val GUIDE_PROGRAMME_MATCHES_SQL = "SELECT c.channelId " + GUIDE_CHANNEL_TABLES_SQL +
    GUIDE_CHANNEL_SOURCE_FILTER_SQL + """
        AND EXISTS (
            SELECT 1 FROM import_state epg_state
            INNER JOIN tv_programmes p ON p.sourceId = c.sourceId
                AND p.snapshotId = epg_state.activeSnapshotId
                AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
                AND p.startEpochMillis < :toEpochMillis - source_state.epgOffsetMinutes * 60000
                AND p.stopEpochMillis > :fromEpochMillis - source_state.epgOffsetMinutes * 60000
            WHERE epg_state.sourceId = c.sourceId AND epg_state.kind = 'epg'
                AND p.title LIKE :pattern ESCAPE '\'
        )
        """
