package com.streammate.tv.core.database

import androidx.room.DatabaseView

const val ORGANIZATION_MEMBERSHIPS_SQL = """
SELECT 'LIVE' AS room, c.sourceId, c.channelId AS itemKey,
    c.channelId AS identity,
    CASE WHEN NULLIF(p.customGroupTitle, '') IS NOT NULL THEN p.customOrganizationGroupKey ELSE c.organizationGroupKey END AS groupKey,
    CASE WHEN NULLIF(p.customGroupTitle, '') IS NOT NULL THEN p.customOrganizationGroupKey ELSE c.organizationNameKey END AS nameKey,
    COALESCE(p.hidden, 0) AS legacyHidden
FROM iptv_channels c
JOIN iptv_source_state s ON s.sourceId = c.sourceId AND s.enabled = 1
JOIN import_state active ON active.sourceId = c.sourceId AND active.kind = 'playlist' AND active.activeSnapshotId = c.snapshotId
LEFT JOIN channel_preferences p ON p.channelId = c.channelId
UNION ALL
SELECT 'MOVIES', m.sourceId, 'vod:movie:' || m.sourceId || ':' || m.movieId,
    COALESCE(a.identity, 'vod:movie:' || m.sourceId || ':' || m.movieId),
    m.organizationGroupKey, m.organizationNameKey, 0
FROM vod_movies m
JOIN iptv_source_state s ON s.sourceId = m.sourceId AND s.enabled = 1
JOIN import_state active ON active.sourceId = m.sourceId AND active.kind = 'catalogue' AND active.activeSnapshotId = m.snapshotId
LEFT JOIN organization_aliases a ON a.alias = 'vod:movie:' || m.sourceId || ':' || m.movieId
UNION ALL
SELECT 'SERIES', v.sourceId, 'series:' || v.sourceId || ':' || v.seriesId,
    'series:' || v.sourceId || ':' || v.seriesId, v.organizationGroupKey, v.organizationNameKey, 0
FROM vod_series v
JOIN iptv_source_state s ON s.sourceId = v.sourceId AND s.enabled = 1
JOIN import_state active ON active.sourceId = v.sourceId AND active.kind = 'catalogue' AND active.activeSnapshotId = v.snapshotId
"""

const val ORGANIZATION_ELIGIBLE_SQL = """
SELECT i.room, i.sourceId, i.itemKey, i.identity, i.groupKey, i.nameKey
FROM organization_memberships i
WHERE COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = i.sourceId AND r.groupKey = '' AND r.itemKey = i.identity),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = i.sourceId AND r.groupKey = '' AND r.itemKey = i.itemKey),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = i.identity),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = i.itemKey), 1 - i.legacyHidden) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = i.sourceId AND r.groupKey = i.groupKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = i.sourceId AND r.groupKey = i.nameKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = '' AND r.groupKey = i.groupKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = '' AND r.groupKey = i.nameKey AND r.itemKey = ''), 1) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = i.sourceId AND r.groupKey = i.groupKey AND r.itemKey = i.identity),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = i.sourceId AND r.groupKey = i.groupKey AND r.itemKey = i.itemKey),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = i.sourceId AND r.groupKey = i.nameKey AND r.itemKey = i.identity),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = i.sourceId AND r.groupKey = i.nameKey AND r.itemKey = i.itemKey),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = '' AND r.groupKey = i.groupKey AND r.itemKey = i.identity),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = '' AND r.groupKey = i.groupKey AND r.itemKey = i.itemKey),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = '' AND r.groupKey = i.nameKey AND r.itemKey = i.identity),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = i.room AND r.sourceId = '' AND r.groupKey = i.nameKey AND r.itemKey = i.itemKey), 1) = 1
"""

@DatabaseView(value = ORGANIZATION_MEMBERSHIPS_SQL, viewName = "organization_memberships")
data class OrganizationMembershipView(
    val room: String, val sourceId: String, val itemKey: String, val identity: String,
    val groupKey: String, val nameKey: String, val legacyHidden: Boolean,
)

@DatabaseView(value = ORGANIZATION_ELIGIBLE_SQL, viewName = "organization_eligible_items")
data class OrganizationEligibleView(
    val room: String, val sourceId: String, val itemKey: String, val identity: String,
    val groupKey: String, val nameKey: String,
)

const val ORGANIZATION_VISIBLE_MOVIES_SQL = """
SELECT m.* FROM vod_movies m
LEFT JOIN organization_aliases a ON a.alias = 'vod:movie:' || m.sourceId || ':' || m.movieId
WHERE COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = m.sourceId AND r.groupKey = '' AND r.itemKey = COALESCE(a.identity, 'vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = m.sourceId AND r.groupKey = '' AND r.itemKey = ('vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = COALESCE(a.identity, 'vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = ('vod:movie:' || m.sourceId || ':' || m.movieId)), 1 - 0) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationGroupKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationNameKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = '' AND r.groupKey = m.organizationGroupKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = '' AND r.groupKey = m.organizationNameKey AND r.itemKey = ''), 1) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationGroupKey AND r.itemKey = COALESCE(a.identity, 'vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationGroupKey AND r.itemKey = ('vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationNameKey AND r.itemKey = COALESCE(a.identity, 'vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationNameKey AND r.itemKey = ('vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = '' AND r.groupKey = m.organizationGroupKey AND r.itemKey = COALESCE(a.identity, 'vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = '' AND r.groupKey = m.organizationGroupKey AND r.itemKey = ('vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = '' AND r.groupKey = m.organizationNameKey AND r.itemKey = COALESCE(a.identity, 'vod:movie:' || m.sourceId || ':' || m.movieId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'MOVIES' AND r.sourceId = '' AND r.groupKey = m.organizationNameKey AND r.itemKey = ('vod:movie:' || m.sourceId || ':' || m.movieId)), 1) = 1
"""

@DatabaseView(value = ORGANIZATION_VISIBLE_MOVIES_SQL, viewName = "organization_visible_movies")
data class OrganizationVisibleMovie(@androidx.room.Embedded val item: VodMovieEntity)


const val ORGANIZATION_VISIBLE_SERIES_SQL = """
SELECT m.* FROM vod_series m

WHERE COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = m.sourceId AND r.groupKey = '' AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = m.sourceId AND r.groupKey = '' AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)), 1 - 0) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationGroupKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationNameKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = '' AND r.groupKey = m.organizationGroupKey AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = '' AND r.groupKey = m.organizationNameKey AND r.itemKey = ''), 1) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationGroupKey AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationGroupKey AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationNameKey AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = m.sourceId AND r.groupKey = m.organizationNameKey AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = '' AND r.groupKey = m.organizationGroupKey AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = '' AND r.groupKey = m.organizationGroupKey AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = '' AND r.groupKey = m.organizationNameKey AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'SERIES' AND r.sourceId = '' AND r.groupKey = m.organizationNameKey AND r.itemKey = ('series:' || m.sourceId || ':' || m.seriesId)), 1) = 1
"""

@DatabaseView(value = ORGANIZATION_VISIBLE_SERIES_SQL, viewName = "organization_visible_series")
data class OrganizationVisibleSeries(@androidx.room.Embedded val item: VodSeriesEntity)


const val ORGANIZATION_VISIBLE_LIVE_SQL = """
SELECT m.* FROM iptv_channels m
LEFT JOIN channel_preferences p ON p.channelId = m.channelId
WHERE COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = m.sourceId AND r.groupKey = '' AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = m.sourceId AND r.groupKey = '' AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = m.channelId), 1 - COALESCE(p.hidden, 0)) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = m.sourceId AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationGroupKey) AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = m.sourceId AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationNameKey) AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationGroupKey) AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationNameKey) AND r.itemKey = ''), 1) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = m.sourceId AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationGroupKey) AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = m.sourceId AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationGroupKey) AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = m.sourceId AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationNameKey) AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = m.sourceId AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationNameKey) AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationGroupKey) AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationGroupKey) AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationNameKey) AND r.itemKey = m.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(p.customOrganizationGroupKey, m.organizationNameKey) AND r.itemKey = m.channelId), 1) = 1
"""

/**
 * The rule predicate of [ORGANIZATION_VISIBLE_LIVE_SQL] over a channel row
 * aliased `c` with its preference row aliased `preference`, for queries that
 * must narrow the channels before the rules run. Joined through the view,
 * SQLite evaluates the sixteen lookups for every channel of a source before an
 * outer group filter is applied; inlined after the filter, only the group's
 * channels pay. A unit test keeps this in step with the view.
 */
const val ORGANIZATION_VISIBLE_LIVE_PREDICATE = """
COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = c.sourceId AND r.groupKey = '' AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = c.sourceId AND r.groupKey = '' AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = '' AND r.itemKey = c.channelId), 1 - COALESCE(preference.hidden, 0)) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = c.sourceId AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationGroupKey) AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = c.sourceId AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationNameKey) AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationGroupKey) AND r.itemKey = ''),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationNameKey) AND r.itemKey = ''), 1) = 1
AND COALESCE((SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = c.sourceId AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationGroupKey) AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = c.sourceId AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationGroupKey) AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = c.sourceId AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationNameKey) AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = c.sourceId AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationNameKey) AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationGroupKey) AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationGroupKey) AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationNameKey) AND r.itemKey = c.channelId),
    (SELECT r.enabled FROM organization_rules r WHERE r.room = 'LIVE' AND r.sourceId = '' AND r.groupKey = COALESCE(preference.customOrganizationGroupKey, c.organizationNameKey) AND r.itemKey = c.channelId), 1) = 1
"""

@DatabaseView(value = ORGANIZATION_VISIBLE_LIVE_SQL, viewName = "organization_visible_channels")
data class OrganizationVisibleChannel(@androidx.room.Embedded val item: IptvChannelEntity)
