package com.streammate.tv.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GuideDao {
    @Upsert
    abstract suspend fun upsertSourceState(source: IptvSourceStateEntity)

    @Upsert
    abstract suspend fun upsertChannelPreference(preference: ChannelPreferenceEntity)

    @Upsert
    abstract suspend fun upsertChannelPreferences(preferences: List<ChannelPreferenceEntity>)

    @Upsert
    abstract suspend fun upsertCustomChannelList(list: CustomChannelListEntity)

    @Upsert
    abstract suspend fun upsertCustomChannelListMember(member: CustomChannelListMemberEntity)

    @Upsert
    abstract suspend fun upsertChannels(channels: List<IptvChannelEntity>)

    @Upsert
    abstract suspend fun upsertXmlTvChannels(channels: List<XmlTvChannelEntity>)

    @Upsert
    abstract suspend fun upsertProgrammes(programmes: List<TvProgrammeEntity>)

    /**
     * A staged guide snapshot is new, so nothing can conflict except a
     * duplicate inside the feed itself, which the first copy wins. Plain
     * inserts in one transaction per batch are several times cheaper than
     * upserts of the same rows.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertProgrammes(programmes: List<TvProgrammeEntity>)

    /** The guide ids the source's active channels answer to, by id or manual mapping. */
    @Query(
        """
        SELECT DISTINCT COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId) AS xmltvChannelId
        FROM iptv_channels c
        INNER JOIN import_state state
            ON state.sourceId = c.sourceId
            AND state.kind = 'playlist'
            AND c.snapshotId = state.activeSnapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        WHERE c.sourceId = :sourceId
        AND COALESCE(NULLIF(preference.manualXmltvChannelId, ''), NULLIF(c.tvgId, '')) IS NOT NULL
        """,
    )
    abstract suspend fun referencedXmltvChannelIds(sourceId: String): List<String>

    @Upsert
    protected abstract suspend fun upsertImportState(state: ImportStateEntity)

    @Upsert
    abstract suspend fun upsertSourceRefreshState(state: SourceRefreshStateEntity)

    @Upsert
    abstract suspend fun upsertTeamAlias(alias: TeamAliasEntity)

    @Upsert
    abstract suspend fun upsertEventChannelDecision(decision: EventChannelDecisionEntity)

    @Query("DELETE FROM iptv_channels WHERE sourceId = :sourceId AND snapshotId = :snapshotId")
    abstract suspend fun deleteChannelSnapshot(sourceId: String, snapshotId: String)

    @Query("DELETE FROM xmltv_channels WHERE sourceId = :sourceId AND snapshotId = :snapshotId")
    abstract suspend fun deleteXmlTvChannelSnapshot(sourceId: String, snapshotId: String)

    @Query("DELETE FROM tv_programmes WHERE sourceId = :sourceId AND snapshotId = :snapshotId")
    abstract suspend fun deleteProgrammeSnapshot(sourceId: String, snapshotId: String)

    @Query("DELETE FROM iptv_channels WHERE sourceId = :sourceId AND snapshotId != :activeSnapshotId")
    protected abstract suspend fun deleteInactiveChannelSnapshots(sourceId: String, activeSnapshotId: String)

    @Query("DELETE FROM xmltv_channels WHERE sourceId = :sourceId AND snapshotId != :activeSnapshotId")
    protected abstract suspend fun deleteInactiveXmlTvChannelSnapshots(sourceId: String, activeSnapshotId: String)

    @Query("DELETE FROM tv_programmes WHERE sourceId = :sourceId AND snapshotId != :activeSnapshotId")
    protected abstract suspend fun deleteInactiveProgrammeSnapshots(sourceId: String, activeSnapshotId: String)

    @Transaction
    open suspend fun activatePlaylistSnapshot(sourceId: String, snapshotId: String, itemCount: Int, now: Long) {
        upsertImportState(ImportStateEntity(sourceId, PLAYLIST_KIND, snapshotId, now, itemCount))
        upsertSourceRefreshState(
            SourceRefreshStateEntity(
                sourceId = sourceId,
                kind = PLAYLIST_KIND,
                status = REFRESH_SUCCESS,
                lastAttemptAtEpochMillis = now,
                lastSuccessAtEpochMillis = now,
                lastFailureAtEpochMillis = null,
                lastError = null,
                itemCount = itemCount,
                consecutiveFailures = 0,
            ),
        )
        deleteInactiveChannelSnapshots(sourceId, snapshotId)
    }

    @Transaction
    open suspend fun activateEpgSnapshot(sourceId: String, snapshotId: String, itemCount: Int, now: Long) {
        upsertImportState(ImportStateEntity(sourceId, EPG_KIND, snapshotId, now, itemCount))
        upsertSourceRefreshState(
            SourceRefreshStateEntity(
                sourceId = sourceId,
                kind = EPG_KIND,
                status = REFRESH_SUCCESS,
                lastAttemptAtEpochMillis = now,
                lastSuccessAtEpochMillis = now,
                lastFailureAtEpochMillis = null,
                lastError = null,
                itemCount = itemCount,
                consecutiveFailures = 0,
            ),
        )
        deleteInactiveXmlTvChannelSnapshots(sourceId, snapshotId)
        deleteInactiveProgrammeSnapshots(sourceId, snapshotId)
    }

    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.priority AS sourcePriority,
            c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS name,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) AS groupTitle,
            c.logoUrl AS logoUrl,
            c.playlistOrder AS playlistOrder,
            preference.sortOrder AS legacyPosition,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            p.title AS currentProgrammeTitle,
            p.subtitle AS currentProgrammeSubtitle,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStartEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStopEpochMillis
        FROM organization_visible_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
            AND source_state.enabled = 1
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId
            AND playlist_state.kind = 'playlist'
            AND c.snapshotId = playlist_state.activeSnapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        LEFT JOIN import_state epg_state
            ON epg_state.sourceId = c.sourceId
            AND epg_state.kind = 'epg'
        LEFT JOIN tv_programmes p
            ON p.sourceId = c.sourceId
            AND p.snapshotId = epg_state.activeSnapshotId
            AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
            AND p.startEpochMillis + source_state.epgOffsetMinutes * 60000 <= :nowEpochMillis
            AND p.stopEpochMillis + source_state.epgOffsetMinutes * 60000 > :nowEpochMillis
        WHERE 1 = 1
        ORDER BY source_state.priority DESC, source_state.name,
            COALESCE(preference.sortOrder, 2147483647),
            c.playlistOrder,
            COALESCE(NULLIF(preference.customName, ''), c.name)
        """,
    )
    abstract fun observeGuide(nowEpochMillis: Long): Flow<List<GuideChannelRow>>

    /** [observeGuide] for a handful of channels: the home rows, not the whole library. */
    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.priority AS sourcePriority,
            c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS name,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) AS groupTitle,
            c.logoUrl AS logoUrl,
            c.playlistOrder AS playlistOrder,
            preference.sortOrder AS legacyPosition,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            p.title AS currentProgrammeTitle,
            p.subtitle AS currentProgrammeSubtitle,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStartEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStopEpochMillis
        FROM organization_visible_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
            AND source_state.enabled = 1
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId
            AND playlist_state.kind = 'playlist'
            AND c.snapshotId = playlist_state.activeSnapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        LEFT JOIN import_state epg_state
            ON epg_state.sourceId = c.sourceId
            AND epg_state.kind = 'epg'
        LEFT JOIN tv_programmes p
            ON p.sourceId = c.sourceId
            AND p.snapshotId = epg_state.activeSnapshotId
            AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
            AND p.startEpochMillis + source_state.epgOffsetMinutes * 60000 <= :nowEpochMillis
            AND p.stopEpochMillis + source_state.epgOffsetMinutes * 60000 > :nowEpochMillis
        WHERE c.channelId IN (:channelIds)
        ORDER BY source_state.priority DESC, source_state.name,
            COALESCE(preference.sortOrder, 2147483647),
            c.playlistOrder,
            COALESCE(NULLIF(preference.customName, ''), c.name)
        """,
    )
    abstract fun observeGuideForChannels(channelIds: List<String>, nowEpochMillis: Long): Flow<List<GuideChannelRow>>

    /**
     * [observeGuide] for one group by its shown title, across every enabled
     * source: the player's channel browser. A null title gives the channels
     * that have no group. Read from the channel table with the rule predicate
     * inlined after the group filter, as [observeGuideTimelineForSource] is.
     */
    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.priority AS sourcePriority,
            c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS name,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) AS groupTitle,
            c.logoUrl AS logoUrl,
            c.playlistOrder AS playlistOrder,
            preference.sortOrder AS legacyPosition,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            p.title AS currentProgrammeTitle,
            p.subtitle AS currentProgrammeSubtitle,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStartEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStopEpochMillis
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
        LEFT JOIN import_state epg_state
            ON epg_state.sourceId = c.sourceId
            AND epg_state.kind = 'epg'
        LEFT JOIN tv_programmes p
            ON p.sourceId = c.sourceId
            AND p.snapshotId = epg_state.activeSnapshotId
            AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
            AND p.startEpochMillis + source_state.epgOffsetMinutes * 60000 <= :nowEpochMillis
            AND p.stopEpochMillis + source_state.epgOffsetMinutes * 60000 > :nowEpochMillis
        WHERE ((:groupTitle IS NULL AND COALESCE(NULLIF(preference.customGroupTitle, ''), NULLIF(c.groupTitle, '')) IS NULL)
            OR COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) = :groupTitle)
        AND (""" + ORGANIZATION_VISIBLE_LIVE_PREDICATE + """)
        ORDER BY source_state.priority DESC, source_state.name,
            COALESCE(preference.sortOrder, 2147483647),
            c.playlistOrder,
            COALESCE(NULLIF(preference.customName, ''), c.name)
        """,
    )
    abstract fun observeGuideForGroup(groupTitle: String?, nowEpochMillis: Long): Flow<List<GuideChannelRow>>

    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.priority AS sourcePriority,
            c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS channelName,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) AS groupTitle,
            c.logoUrl AS logoUrl,
            c.playlistOrder AS playlistOrder,
            preference.sortOrder AS legacyPosition,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            c.catchupType AS catchupType,
            c.catchupSource AS catchupSource,
            c.catchupDays AS catchupDays,
            p.programmeId AS programmeId,
            p.title AS programmeTitle,
            p.subtitle AS programmeSubtitle,
            p.description AS programmeDescription,
            p.categories AS programmeCategories,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStartEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStopEpochMillis
        FROM organization_visible_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
            AND source_state.enabled = 1
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId
            AND playlist_state.kind = 'playlist'
            AND c.snapshotId = playlist_state.activeSnapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        LEFT JOIN import_state epg_state
            ON epg_state.sourceId = c.sourceId
            AND epg_state.kind = 'epg'
        LEFT JOIN tv_programmes p
            ON p.sourceId = c.sourceId
            AND p.snapshotId = epg_state.activeSnapshotId
            AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
            AND p.startEpochMillis + source_state.epgOffsetMinutes * 60000 < :toEpochMillis
            AND p.stopEpochMillis + source_state.epgOffsetMinutes * 60000 > :fromEpochMillis
        WHERE 1 = 1
        ORDER BY source_state.priority DESC, source_state.name,
            COALESCE(preference.sortOrder, 2147483647),
            c.playlistOrder,
            COALESCE(NULLIF(preference.customName, ''), c.name),
            p.startEpochMillis + source_state.epgOffsetMinutes * 60000
        """,
    )
    abstract fun observeGuideTimeline(
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Flow<List<GuideTimelineRow>>

    /**
     * [observeGuideTimeline] for one source, and one of its groups when [groupTitle]
     * is given. The organisation view runs its rule lookups per channel, so the
     * filter belongs in the query: the guide shows one group, and reading a
     * library of fifty thousand channels to show forty took a minute.
     */
    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.priority AS sourcePriority,
            c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS channelName,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) AS groupTitle,
            c.logoUrl AS logoUrl,
            c.playlistOrder AS playlistOrder,
            preference.sortOrder AS legacyPosition,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            c.catchupType AS catchupType,
            c.catchupSource AS catchupSource,
            c.catchupDays AS catchupDays,
            p.programmeId AS programmeId,
            p.title AS programmeTitle,
            p.subtitle AS programmeSubtitle,
            p.description AS programmeDescription,
            p.categories AS programmeCategories,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStartEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStopEpochMillis
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
        LEFT JOIN import_state epg_state
            ON epg_state.sourceId = c.sourceId
            AND epg_state.kind = 'epg'
        LEFT JOIN tv_programmes p
            ON p.sourceId = c.sourceId
            AND p.snapshotId = epg_state.activeSnapshotId
            AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
            AND p.startEpochMillis + source_state.epgOffsetMinutes * 60000 < :toEpochMillis
            AND p.stopEpochMillis + source_state.epgOffsetMinutes * 60000 > :fromEpochMillis
        WHERE c.sourceId = :sourceId
        AND (:groupTitle IS NULL OR COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) = :groupTitle)
        AND (""" + ORGANIZATION_VISIBLE_LIVE_PREDICATE + """)
        ORDER BY source_state.priority DESC, source_state.name,
            COALESCE(preference.sortOrder, 2147483647),
            c.playlistOrder,
            COALESCE(NULLIF(preference.customName, ''), c.name),
            p.startEpochMillis + source_state.epgOffsetMinutes * 60000
        """,
    )
    abstract fun observeGuideTimelineForSource(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        sourceId: String,
        groupTitle: String?,
    ): Flow<List<GuideTimelineRow>>

    /** [observeGuideTimeline] for named channels: favourites, recent, a custom list, the one playing. */
    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.priority AS sourcePriority,
            c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS channelName,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) AS groupTitle,
            c.logoUrl AS logoUrl,
            c.playlistOrder AS playlistOrder,
            preference.sortOrder AS legacyPosition,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            c.catchupType AS catchupType,
            c.catchupSource AS catchupSource,
            c.catchupDays AS catchupDays,
            p.programmeId AS programmeId,
            p.title AS programmeTitle,
            p.subtitle AS programmeSubtitle,
            p.description AS programmeDescription,
            p.categories AS programmeCategories,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStartEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStopEpochMillis
        FROM organization_visible_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
            AND source_state.enabled = 1
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId
            AND playlist_state.kind = 'playlist'
            AND c.snapshotId = playlist_state.activeSnapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        LEFT JOIN import_state epg_state
            ON epg_state.sourceId = c.sourceId
            AND epg_state.kind = 'epg'
        LEFT JOIN tv_programmes p
            ON p.sourceId = c.sourceId
            AND p.snapshotId = epg_state.activeSnapshotId
            AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
            AND p.startEpochMillis + source_state.epgOffsetMinutes * 60000 < :toEpochMillis
            AND p.stopEpochMillis + source_state.epgOffsetMinutes * 60000 > :fromEpochMillis
        WHERE c.channelId IN (:channelIds)
        ORDER BY source_state.priority DESC, source_state.name,
            COALESCE(preference.sortOrder, 2147483647),
            c.playlistOrder,
            COALESCE(NULLIF(preference.customName, ''), c.name),
            p.startEpochMillis + source_state.epgOffsetMinutes * 60000
        """,
    )
    abstract fun observeGuideTimelineForChannels(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        channelIds: List<String>,
    ): Flow<List<GuideTimelineRow>>

    /**
     * The rail: every group of every enabled source with its channel count,
     * read from the channel table without the organisation view, so it costs
     * one pass over the channels rather than sixteen lookups per channel.
     * Rules that hide a whole group are applied by the screen.
     */
    @Query(
        """
        SELECT c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.priority AS sourcePriority,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle) AS groupTitle,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            COUNT(*) AS channelCount
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
        WHERE COALESCE(preference.hidden, 0) = 0
        GROUP BY c.sourceId, groupTitle, organizationGroupKey
        ORDER BY source_state.priority DESC, source_state.name, MIN(c.playlistOrder)
        """,
    )
    abstract fun observeGuideRail(): Flow<List<GuideRailRow>>

    @Query(
        """
        SELECT 'channel' AS resultType, c.sourceId AS sourceId, c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS title,
            COALESCE(NULLIF(preference.customGroupTitle, ''), c.groupTitle, source_state.name) AS subtitle,
            c.logoUrl AS logoUrl, NULL AS startEpochMillis, NULL AS stopEpochMillis
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
            p.title AS title, COALESCE(NULLIF(preference.customName, ''), c.name) AS subtitle,
            c.logoUrl AS logoUrl,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS startEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS stopEpochMillis
        FROM tv_programmes p
        INNER JOIN import_state epg_state
            ON epg_state.sourceId = p.sourceId AND epg_state.kind = 'epg'
            AND p.snapshotId = epg_state.activeSnapshotId
        INNER JOIN organization_visible_channels c ON c.sourceId = p.sourceId
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId AND playlist_state.kind = 'playlist'
            AND c.snapshotId = playlist_state.activeSnapshotId
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId AND source_state.enabled = 1
        LEFT JOIN channel_preferences preference ON preference.channelId = c.channelId
        WHERE 1 = 1
            AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
            AND (p.title LIKE '%' || :query || '%' COLLATE NOCASE
                OR COALESCE(p.subtitle, '') LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY resultType, title
        LIMIT :limit
        """,
    )
    abstract suspend fun searchGuide(query: String, limit: Int): List<GuideSearchResultRow>

    @Query(
        """
        SELECT c.* FROM iptv_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
            AND source_state.enabled = 1
        INNER JOIN import_state state
            ON state.sourceId = c.sourceId
            AND state.kind = 'playlist'
            AND c.snapshotId = state.activeSnapshotId
        WHERE c.channelId = :channelId
        LIMIT 1
        """,
    )
    abstract suspend fun getActiveChannel(channelId: String): IptvChannelEntity?

    /**
     * How a staged guide snapshot lines up with the source's active channels,
     * read before the snapshot is allowed to replace the one on screen.
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM tv_programmes p
                WHERE p.sourceId = :sourceId AND p.snapshotId = :snapshotId
                AND p.xmltvChannelId IN (
                    SELECT COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
                    FROM iptv_channels c
                    INNER JOIN import_state state
                        ON state.sourceId = c.sourceId
                        AND state.kind = 'playlist'
                        AND c.snapshotId = state.activeSnapshotId
                    LEFT JOIN channel_preferences preference
                        ON preference.channelId = c.channelId
                    WHERE c.sourceId = :sourceId
                )
            ) AS matchedProgrammes,
            (SELECT COUNT(*) FROM iptv_channels c
                INNER JOIN import_state state
                    ON state.sourceId = c.sourceId
                    AND state.kind = 'playlist'
                    AND c.snapshotId = state.activeSnapshotId
                LEFT JOIN channel_preferences preference
                    ON preference.channelId = c.channelId
                WHERE c.sourceId = :sourceId
                AND COALESCE(NULLIF(preference.manualXmltvChannelId, ''), NULLIF(c.tvgId, '')) IS NOT NULL
            ) AS mappableChannels
        """,
    )
    abstract suspend fun stagedEpgMatch(sourceId: String, snapshotId: String): StagedEpgMatchRow

    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS channelName,
            p.programmeId AS programmeId,
            p.title AS programmeTitle,
            p.subtitle AS programmeSubtitle,
            p.description AS programmeDescription,
            (p.startEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStartEpochMillis,
            (p.stopEpochMillis + source_state.epgOffsetMinutes * 60000) AS programmeStopEpochMillis
        FROM organization_visible_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
            AND source_state.enabled = 1
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId
            AND playlist_state.kind = 'playlist'
            AND playlist_state.activeSnapshotId = c.snapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        INNER JOIN import_state epg_state
            ON epg_state.sourceId = c.sourceId
            AND epg_state.kind = 'epg'
        INNER JOIN tv_programmes p
            ON p.sourceId = c.sourceId
            AND p.snapshotId = epg_state.activeSnapshotId
            AND p.xmltvChannelId = COALESCE(NULLIF(preference.manualXmltvChannelId, ''), c.tvgId)
        WHERE p.startEpochMillis + source_state.epgOffsetMinutes * 60000
            BETWEEN :fromEpochMillis AND :toEpochMillis
            AND 1 = 1
        ORDER BY p.startEpochMillis + source_state.epgOffsetMinutes * 60000,
            COALESCE(NULLIF(preference.customName, ''), c.name)
        """,
    )
    abstract suspend fun programmeCandidates(
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): List<ProgrammeCandidateRow>

    @Query(
        """
        SELECT c.sourceId AS sourceId, c.channelId AS channelId,
            COALESCE(NULLIF(preference.customName, ''), c.name) AS channelName
        FROM organization_visible_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
            AND source_state.enabled = 1
        INNER JOIN import_state state
            ON state.sourceId = c.sourceId
            AND state.kind = 'playlist'
            AND c.snapshotId = state.activeSnapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        WHERE 1 = 1
        ORDER BY COALESCE(preference.sortOrder, 2147483647),
            COALESCE(NULLIF(preference.customName, ''), c.name)
        """,
    )
    abstract suspend fun channelNameCandidates(): List<ChannelNameCandidateRow>

    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            source_state.name AS sourceName,
            source_state.enabled AS sourceEnabled,
            c.channelId AS channelId,
            c.name AS originalName,
            c.groupTitle AS originalGroupTitle,
            c.logoUrl AS logoUrl,
            c.tvgId AS tvgId,
            c.playlistOrder AS playlistOrder,
            CASE WHEN NULLIF(preference.customGroupTitle, '') IS NOT NULL THEN preference.customOrganizationGroupKey ELSE c.organizationGroupKey END AS organizationGroupKey,
            preference.customName AS customName,
            preference.customGroupTitle AS customGroupTitle,
            COALESCE(preference.hidden, 0) AS hidden,
            preference.sortOrder AS sortOrder,
            preference.manualXmltvChannelId AS manualXmltvChannelId
        FROM iptv_channels c
        INNER JOIN iptv_source_state source_state
            ON source_state.sourceId = c.sourceId
        INNER JOIN import_state playlist_state
            ON playlist_state.sourceId = c.sourceId
            AND playlist_state.kind = 'playlist'
            AND playlist_state.activeSnapshotId = c.snapshotId
        LEFT JOIN channel_preferences preference
            ON preference.channelId = c.channelId
        ORDER BY source_state.priority DESC, source_state.name,
            COALESCE(preference.sortOrder, 2147483647),
            c.playlistOrder,
            COALESCE(NULLIF(preference.customName, ''), c.name)
        """,
    )
    abstract fun observeEditableChannels(): Flow<List<EditableChannelRow>>

    @Query(
        """
        SELECT x.sourceId AS sourceId, x.xmltvChannelId AS xmltvChannelId,
            x.displayName AS displayName
        FROM xmltv_channels x
        INNER JOIN import_state state
            ON state.sourceId = x.sourceId
            AND state.kind = 'epg'
            AND state.activeSnapshotId = x.snapshotId
        WHERE x.sourceId = :sourceId
        ORDER BY COALESCE(x.displayName, x.xmltvChannelId)
        """,
    )
    abstract fun observeXmlTvChannelOptions(sourceId: String): Flow<List<XmlTvChannelOptionRow>>

    @Query("SELECT * FROM channel_preferences WHERE channelId = :channelId LIMIT 1")
    abstract suspend fun channelPreference(channelId: String): ChannelPreferenceEntity?

    @Query("SELECT * FROM channel_preferences ORDER BY sourceId, channelId")
    abstract suspend fun channelPreferences(): List<ChannelPreferenceEntity>

    @Query("DELETE FROM channel_preferences WHERE channelId = :channelId")
    abstract suspend fun deleteChannelPreference(channelId: String)

    @Query("SELECT * FROM channel_lists ORDER BY sortOrder, name")
    abstract fun observeCustomChannelLists(): Flow<List<CustomChannelListEntity>>

    @Query("SELECT * FROM channel_lists ORDER BY sortOrder, name")
    abstract suspend fun customChannelLists(): List<CustomChannelListEntity>

    @Query("SELECT * FROM channel_list_members ORDER BY listId, sortOrder")
    abstract fun observeCustomChannelListMembers(): Flow<List<CustomChannelListMemberEntity>>

    @Query("SELECT * FROM channel_list_members ORDER BY listId, sortOrder")
    abstract suspend fun customChannelListMembers(): List<CustomChannelListMemberEntity>

    @Query("DELETE FROM channel_preferences")
    protected abstract suspend fun deleteAllChannelPreferences()

    @Query("DELETE FROM channel_list_members")
    protected abstract suspend fun deleteAllCustomChannelListMembers()

    @Query("DELETE FROM channel_lists")
    protected abstract suspend fun deleteAllCustomChannelLists()

    @Transaction
    open suspend fun replaceChannelCustomization(
        preferences: List<ChannelPreferenceEntity>,
        lists: List<CustomChannelListEntity>,
        members: List<CustomChannelListMemberEntity>,
    ) {
        deleteAllCustomChannelListMembers()
        deleteAllCustomChannelLists()
        deleteAllChannelPreferences()
        upsertChannelPreferences(preferences)
        lists.forEach { upsertCustomChannelList(it) }
        members.forEach { upsertCustomChannelListMember(it) }
    }

    @Query("DELETE FROM channel_list_members WHERE listId = :listId AND channelId = :channelId")
    abstract suspend fun deleteCustomChannelListMember(listId: String, channelId: String)

    @Query("DELETE FROM channel_list_members WHERE listId = :listId")
    protected abstract suspend fun deleteCustomChannelListMembers(listId: String)

    @Query("DELETE FROM channel_lists WHERE listId = :listId")
    protected abstract suspend fun deleteCustomChannelListRecord(listId: String)

    @Transaction
    open suspend fun deleteCustomChannelList(listId: String) {
        deleteCustomChannelListMembers(listId)
        deleteCustomChannelListRecord(listId)
    }

    @Query("SELECT * FROM source_refresh_state WHERE sourceId = :sourceId AND kind = :kind LIMIT 1")
    abstract suspend fun sourceRefreshState(sourceId: String, kind: String): SourceRefreshStateEntity?

    @Query("SELECT * FROM source_refresh_state ORDER BY sourceId, kind")
    abstract fun observeSourceRefreshStates(): Flow<List<SourceRefreshStateEntity>>

    @Query("SELECT * FROM iptv_source_state ORDER BY priority DESC, name")
    abstract fun observeSourceStates(): Flow<List<IptvSourceStateEntity>>

    @Query("SELECT * FROM iptv_source_state WHERE sourceId = :sourceId LIMIT 1")
    abstract suspend fun sourceState(sourceId: String): IptvSourceStateEntity?

    @Query("SELECT * FROM team_aliases WHERE sport = :sport")
    abstract suspend fun teamAliases(sport: String): List<TeamAliasEntity>

    @Query("SELECT * FROM event_channel_decisions WHERE eventId IN (:eventIds)")
    abstract suspend fun eventChannelDecisions(eventIds: List<String>): List<EventChannelDecisionEntity>

    @Query("DELETE FROM event_channel_decisions WHERE eventId = :eventId AND channelId = :channelId")
    abstract suspend fun deleteEventChannelDecision(eventId: String, channelId: String)

    @Query("DELETE FROM iptv_channels")
    protected abstract suspend fun deleteAllChannels()

    @Query("DELETE FROM xmltv_channels")
    protected abstract suspend fun deleteAllXmlTvChannels()

    @Query("DELETE FROM tv_programmes")
    protected abstract suspend fun deleteAllProgrammes()

    @Query("DELETE FROM import_state")
    protected abstract suspend fun deleteAllImportState()

    @Query("DELETE FROM source_refresh_state")
    protected abstract suspend fun deleteAllSourceRefreshState()

    @Query("DELETE FROM playback_progress")
    protected abstract suspend fun deleteAllPlaybackProgress()

    @Query("DELETE FROM vod_episodes")
    protected abstract suspend fun deleteAllEpisodes()

    @Query("DELETE FROM vod_movies")
    protected abstract suspend fun deleteAllMovies()

    @Query("DELETE FROM vod_series")
    protected abstract suspend fun deleteAllSeries()

    @Query(
        "DELETE FROM event_channel_decisions WHERE channelId IN " +
            "(SELECT channelId FROM iptv_channels WHERE sourceId = :sourceId)",
    )
    protected abstract suspend fun deleteDecisionsForSource(sourceId: String)

    @Query("DELETE FROM iptv_channels WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteChannelsForSource(sourceId: String)

    @Query("DELETE FROM xmltv_channels WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteXmlTvChannelsForSource(sourceId: String)

    @Query("DELETE FROM tv_programmes WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteProgrammesForSource(sourceId: String)

    @Query("DELETE FROM import_state WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteImportStateForSource(sourceId: String)

    @Query("DELETE FROM source_refresh_state WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteRefreshStateForSource(sourceId: String)

    @Query("DELETE FROM playback_progress WHERE sourceId = :sourceId")
    protected abstract suspend fun deletePlaybackProgressForSource(sourceId: String)

    @Query("DELETE FROM vod_episodes WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteEpisodesForSource(sourceId: String)

    @Query("DELETE FROM vod_movies WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteMoviesForSource(sourceId: String)

    @Query("DELETE FROM vod_series WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteSeriesForSource(sourceId: String)

    @Query("DELETE FROM channel_preferences WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteChannelPreferencesForSource(sourceId: String)

    @Query(
        "DELETE FROM channel_list_members WHERE channelId IN " +
            "(SELECT channelId FROM iptv_channels WHERE sourceId = :sourceId)",
    )
    protected abstract suspend fun deleteChannelListMembersForSource(sourceId: String)

    @Query("DELETE FROM iptv_source_state WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteSourceState(sourceId: String)

    @Transaction
    open suspend fun clearSource(sourceId: String) {
        deleteDecisionsForSource(sourceId)
        deleteChannelPreferencesForSource(sourceId)
        deleteChannelListMembersForSource(sourceId)
        deleteChannelsForSource(sourceId)
        deleteXmlTvChannelsForSource(sourceId)
        deleteProgrammesForSource(sourceId)
        deletePlaybackProgressForSource(sourceId)
        deleteEpisodesForSource(sourceId)
        deleteMoviesForSource(sourceId)
        deleteSeriesForSource(sourceId)
        deleteImportStateForSource(sourceId)
        deleteRefreshStateForSource(sourceId)
        deleteSourceState(sourceId)
    }

    @Transaction
    open suspend fun clearGuide() {
        deleteAllChannels()
        deleteAllXmlTvChannels()
        deleteAllProgrammes()
        deleteAllPlaybackProgress()
        deleteAllEpisodes()
        deleteAllMovies()
        deleteAllSeries()
        deleteAllImportState()
        deleteAllSourceRefreshState()
    }

    companion object {
        const val PLAYLIST_KIND = "playlist"
        const val EPG_KIND = "epg"
        const val REFRESH_RUNNING = "running"
        const val REFRESH_SUCCESS = "success"
        const val REFRESH_FAILED = "failed"
    }
}
