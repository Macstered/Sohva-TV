package com.streammate.tv.core.database

/** Channel-first programme lookup uses the existing source/XMLTV index. Contains matching and all imported dates remain intact. */
const val GUIDE_SEARCH_SQL = """
SELECT 'channel' AS resultType, c.sourceId AS sourceId, c.channelId AS channelId,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS title,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle, source_state.name) AS subtitle,
            COALESCE(NULLIF(preference.customLogoUrl, ''), c.logoUrl) AS logoUrl, NULL AS startEpochMillis, NULL AS stopEpochMillis
        FROM organization_visible_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId AND source_state.enabled = 1
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId AND playlist_state.kind = 'playlist'
            AND c.snapshotId = playlist_state.activeSnapshotId
        LEFT JOIN channel_preferences preference ON preference.channelId = c.channelId
        WHERE 1 = 1
            AND COALESCE(NULLIF(preference.customName, ''), c.name) LIKE '%' || :query || '%' COLLATE NOCASE
        UNION ALL
        SELECT 'programme' AS resultType, c.sourceId AS sourceId, c.channelId AS channelId,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            p.title AS title, COALESCE(NULLIF(preference.customName, ''), c.name) AS subtitle,
            COALESCE(NULLIF(preference.customLogoUrl, ''), c.logoUrl) AS logoUrl,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS startEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS stopEpochMillis

        FROM iptv_source_state source_state
        CROSS JOIN import_state playlist_state ON playlist_state.sourceId=source_state.sourceId AND playlist_state.kind='playlist'
        CROSS JOIN organization_visible_channels c ON c.sourceId=source_state.sourceId AND c.snapshotId=playlist_state.activeSnapshotId
        LEFT JOIN channel_preferences preference ON preference.channelId=c.channelId
        CROSS JOIN import_state epg_state ON epg_state.sourceId=source_state.sourceId AND epg_state.kind='epg'
        CROSS JOIN tv_programmes p ON p.sourceId=source_state.sourceId AND p.snapshotId=epg_state.activeSnapshotId
            AND p.xmltvChannelId=COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
        WHERE source_state.enabled=1
            AND (p.title LIKE '%' || :query || '%' COLLATE NOCASE
                OR COALESCE(p.subtitle, '') LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY resultType, title
        LIMIT :limit
"""
