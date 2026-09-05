package com.streammate.tv.iptv.repository

import com.streammate.tv.core.database.GuideChannelRow
import com.streammate.tv.core.database.GuideDao
import com.streammate.tv.core.database.GuideTimelineRow
import com.streammate.tv.core.database.ChannelPreferenceEntity
import com.streammate.tv.core.database.CustomChannelListEntity
import com.streammate.tv.core.database.CustomChannelListMemberEntity
import com.streammate.tv.core.database.EditableChannelRow
import com.streammate.tv.core.database.IptvChannelEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.SourceRefreshStateEntity
import com.streammate.tv.core.database.TvProgrammeEntity
import com.streammate.tv.core.database.XmlTvChannelEntity
import com.streammate.tv.core.database.XmlTvChannelOptionRow
import com.streammate.tv.core.model.IptvSourceConfiguration
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class StoredIptvChannel(
    val id: String,
    val tvgId: String?,
    val name: String,
    val normalizedName: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val encryptedStreamUrl: String,
    val userAgent: String?,
    val referrer: String?,
    val catchupType: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    val xtreamStreamId: String? = null,
    val catchupTimeZone: String? = null,
    val playlistOrder: Int = Int.MAX_VALUE,
    val providerGroupId: String? = null,
)

data class StoredXmlTvChannel(
    val id: String,
    val displayName: String?,
    val iconUrl: String?,
)

data class StoredProgramme(
    val id: String,
    val channelId: String,
    val startEpochMillis: Long,
    val stopEpochMillis: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val categories: List<String>,
)

data class GuideChannel(
    val sourceId: String,
    val sourceName: String,
    val sourcePriority: Int,
    val id: String,
    val name: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val playlistOrder: Int,
    val currentProgrammeTitle: String?,
    val currentProgrammeSubtitle: String?,
    val programmeStartEpochMillis: Long?,
    val programmeStopEpochMillis: Long?,
    val organizationGroupKey: String = com.streammate.tv.core.model.organizationGroupKey(groupTitle),
    val legacyPosition: Long? = null,
)

data class GuideTimelineProgramme(
    val id: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val categories: List<String>,
    val startEpochMillis: Long,
    val stopEpochMillis: Long,
)

data class GuideTimelineChannel(
    val sourceId: String,
    val sourceName: String,
    val sourcePriority: Int,
    val id: String,
    val name: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val playlistOrder: Int,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val programmes: List<GuideTimelineProgramme>,
    val organizationGroupKey: String = com.streammate.tv.core.model.organizationGroupKey(groupTitle),
    val legacyPosition: Long? = null,
)

/**
 * Turns provider EPG rows into one valid programme per start slot.
 *
 * Exact duplicates are normally collapsed by the import key, but some feeds
 * publish a corrected and an older variant with different IDs for the same
 * channel/start time. A television channel cannot air both in one row. Keep
 * the richer variant, then leave genuine later starts intact; the grid can cap
 * the preceding block at that next start without changing catch-up timestamps.
 */
internal fun deduplicateGuideSchedule(
    programmes: List<GuideTimelineProgramme>,
): List<GuideTimelineProgramme> = programmes
    .asSequence()
    .filter { it.stopEpochMillis > it.startEpochMillis }
    .sortedWith(
        compareBy<GuideTimelineProgramme>(GuideTimelineProgramme::startEpochMillis)
            .thenBy(GuideTimelineProgramme::stopEpochMillis)
            .thenBy(GuideTimelineProgramme::id),
    )
    .groupBy(GuideTimelineProgramme::startEpochMillis)
    .values
    .map { sameStart ->
        sameStart.maxWithOrNull(
            compareBy<GuideTimelineProgramme> { programme ->
                listOf(programme.subtitle, programme.description)
                    .count { !it.isNullOrBlank() } + programme.categories.size
            }
                .thenBy { it.stopEpochMillis - it.startEpochMillis }
                .thenBy(GuideTimelineProgramme::id),
        )!!
    }
    .sortedBy(GuideTimelineProgramme::startEpochMillis)

data class GuideSearchResult(
    val type: String,
    val sourceId: String,
    val channelId: String,
    val title: String,
    val subtitle: String?,
    val logoUrl: String?,
    val startEpochMillis: Long?,
    val stopEpochMillis: Long?,
)

data class EditableChannel(
    val sourceId: String,
    val sourceName: String,
    val id: String,
    val originalName: String,
    val originalGroupTitle: String?,
    val logoUrl: String?,
    val tvgId: String?,
    val playlistOrder: Int,
    val customName: String?,
    val customGroupTitle: String?,
    val hidden: Boolean,
    val sortOrder: Int?,
    val manualXmltvChannelId: String?,
    val organizationGroupKey: String = com.streammate.tv.core.model.organizationGroupKey(customGroupTitle ?: originalGroupTitle),
    val sourceEnabled: Boolean = true,
) {
    val displayName: String get() = customName?.takeIf(String::isNotBlank) ?: originalName
    val displayGroupTitle: String? get() = customGroupTitle?.takeIf(String::isNotBlank) ?: originalGroupTitle
}

data class XmlTvChannelOption(
    val sourceId: String,
    val id: String,
    val displayName: String?,
) {
    val label: String get() = displayName?.takeIf(String::isNotBlank) ?: id
}

data class CustomChannelList(
    val id: String,
    val name: String,
    val sortOrder: Int,
)

data class ChannelCustomizationSnapshot(
    val preferences: List<ChannelPreferenceEntity>,
    val lists: List<CustomChannelListEntity>,
    val members: List<CustomChannelListMemberEntity>,
    val organization: com.streammate.tv.core.database.OrganizationSnapshot = com.streammate.tv.core.database.OrganizationSnapshot(),
)

data class ChannelListMembership(
    val listId: String,
    val channelId: String,
    val sortOrder: Int,
)

data class GuideSource(val id: String, val name: String, val enabled: Boolean)

data class GuideChannelPlacement(val sourceId: String, val groupTitle: String?)

/** One group of one source on the guide's rail, with how many channels it holds. */
data class GuideRailGroup(
    val sourceId: String,
    val sourceName: String,
    val sourcePriority: Int,
    val groupTitle: String?,
    val organizationGroupKey: String,
    val channelCount: Int,
)

data class SourceRefreshHealth(
    val sourceId: String,
    val kind: String,
    val status: String,
    val lastAttemptAtEpochMillis: Long,
    val lastSuccessAtEpochMillis: Long?,
    val lastFailureAtEpochMillis: Long?,
    val lastError: String?,
    val itemCount: Int,
    val consecutiveFailures: Int,
)

/**
 * [matchedProgrammes] is how many staged programmes attach to an active channel
 * of the source, by its EPG id or a manual mapping; [mappableChannels] is how
 * many active channels carry an id at all.
 */
data class StagedEpgMatch(
    val matchedProgrammes: Int,
    val mappableChannels: Int,
)

interface GuideStore {
    fun newSnapshotId(): String
    suspend fun insertChannels(sourceId: String, snapshotId: String, channels: List<StoredIptvChannel>)
    suspend fun insertXmlTvChannels(sourceId: String, snapshotId: String, channels: List<StoredXmlTvChannel>)
    suspend fun insertProgrammes(sourceId: String, snapshotId: String, programmes: List<StoredProgramme>)
    /**
     * The guide ids the source's active channels answer to; empty when the
     * playlist has not been imported yet, in which case nothing can be
     * skipped and every programme is kept.
     */
    suspend fun referencedXmltvChannelIds(sourceId: String): Set<String>
    suspend fun activatePlaylist(sourceId: String, snapshotId: String, itemCount: Int)
    suspend fun activateEpg(sourceId: String, snapshotId: String, itemCount: Int)
    suspend fun discardPlaylist(sourceId: String, snapshotId: String)
    suspend fun discardEpg(sourceId: String, snapshotId: String)
    /** How the staged guide lines up with the source's active channels. */
    suspend fun stagedEpgMatch(sourceId: String, snapshotId: String): StagedEpgMatch
    suspend fun markRefreshStarted(sourceId: String, kind: String)
    suspend fun markRefreshFailed(sourceId: String, kind: String, redactedError: String?)
}

class RoomGuideStore(
    private val dao: GuideDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : GuideStore {
    override fun newSnapshotId(): String = UUID.randomUUID().toString()

    override suspend fun insertChannels(
        sourceId: String,
        snapshotId: String,
        channels: List<StoredIptvChannel>,
    ) {
        val now = clock()
        dao.upsertChannels(channels.map { channel ->
            IptvChannelEntity(
                sourceId = sourceId,
                snapshotId = snapshotId,
                channelId = globalChannelId(sourceId, channel.id),
                tvgId = channel.tvgId,
                name = channel.name,
                normalizedName = channel.normalizedName,
                groupTitle = channel.groupTitle,
                logoUrl = channel.logoUrl,
                encryptedStreamUrl = channel.encryptedStreamUrl,
                userAgent = channel.userAgent,
                referrer = channel.referrer,
                lastSeenEpochMillis = now,
                playlistOrder = channel.playlistOrder,
                organizationGroupKey = com.streammate.tv.core.model.organizationGroupKey(channel.groupTitle, channel.providerGroupId),
                catchupType = channel.catchupType,
                catchupSource = channel.catchupSource,
                catchupDays = channel.catchupDays,
                xtreamStreamId = channel.xtreamStreamId,
                catchupTimeZone = channel.catchupTimeZone,
            )
        })
    }

    override suspend fun insertXmlTvChannels(
        sourceId: String,
        snapshotId: String,
        channels: List<StoredXmlTvChannel>,
    ) {
        dao.upsertXmlTvChannels(channels.map { channel ->
            XmlTvChannelEntity(sourceId, snapshotId, channel.id, channel.displayName, channel.iconUrl)
        })
    }

    override suspend fun insertProgrammes(
        sourceId: String,
        snapshotId: String,
        programmes: List<StoredProgramme>,
    ) {
        dao.insertProgrammes(programmes.map { programme ->
            TvProgrammeEntity(
                sourceId = sourceId,
                snapshotId = snapshotId,
                programmeId = programme.id,
                xmltvChannelId = programme.channelId,
                startEpochMillis = programme.startEpochMillis,
                stopEpochMillis = programme.stopEpochMillis,
                title = programme.title,
                subtitle = programme.subtitle,
                description = programme.description,
                categories = programme.categories.joinToString(CATEGORY_SEPARATOR),
            )
        })
    }

    override suspend fun activatePlaylist(sourceId: String, snapshotId: String, itemCount: Int) {
        dao.activatePlaylistSnapshot(sourceId, snapshotId, itemCount, clock())
    }

    override suspend fun referencedXmltvChannelIds(sourceId: String): Set<String> =
        dao.referencedXmltvChannelIds(sourceId).toSet()

    override suspend fun activateEpg(sourceId: String, snapshotId: String, itemCount: Int) {
        dao.activateEpgSnapshot(sourceId, snapshotId, itemCount, clock())
    }

    override suspend fun stagedEpgMatch(sourceId: String, snapshotId: String): StagedEpgMatch =
        dao.stagedEpgMatch(sourceId, snapshotId).let { StagedEpgMatch(it.matchedProgrammes, it.mappableChannels) }

    override suspend fun discardPlaylist(sourceId: String, snapshotId: String) =
        dao.deleteChannelSnapshot(sourceId, snapshotId)

    override suspend fun discardEpg(sourceId: String, snapshotId: String) {
        dao.deleteXmlTvChannelSnapshot(sourceId, snapshotId)
        dao.deleteProgrammeSnapshot(sourceId, snapshotId)
    }

    override suspend fun markRefreshStarted(sourceId: String, kind: String) {
        val now = clock()
        val previous = dao.sourceRefreshState(sourceId, kind)
        dao.upsertSourceRefreshState(
            SourceRefreshStateEntity(
                sourceId = sourceId,
                kind = kind,
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

    override suspend fun markRefreshFailed(sourceId: String, kind: String, redactedError: String?) {
        val now = clock()
        val previous = dao.sourceRefreshState(sourceId, kind)
        dao.upsertSourceRefreshState(
            SourceRefreshStateEntity(
                sourceId = sourceId,
                kind = kind,
                status = GuideDao.REFRESH_FAILED,
                lastAttemptAtEpochMillis = previous?.lastAttemptAtEpochMillis ?: now,
                lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                lastFailureAtEpochMillis = now,
                lastError = redactedError,
                itemCount = previous?.itemCount ?: 0,
                consecutiveFailures = (previous?.consecutiveFailures ?: 0) + 1,
            ),
        )
    }

    private companion object {
        const val CATEGORY_SEPARATOR = "\u001F"

        fun globalChannelId(sourceId: String, localChannelId: String): String =
            "$sourceId:$localChannelId"
    }
}

class GuideRepository(
    private val dao: GuideDao,
    private val clock: () -> Long = System::currentTimeMillis,
    val organization: OrganizationRepository? = null,
) {
    constructor(dao: GuideDao, clock: () -> Long) : this(dao, clock, null)
    // Room runs the query off the main thread but the row-to-domain mapping ran
    // wherever the flow was collected, which is the main thread for every screen
    // in this app. distinctUntilChanged also drops the repeat emissions Room
    // produces when an unrelated write invalidates the table during an import.
    fun observeGuide(nowEpochMillis: Long): Flow<List<GuideChannel>> =
        dao.observeGuide(nowEpochMillis)
            .map { rows -> rows.map { it.toDomain() }.distinctBy(GuideChannel::id) }
            .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.LIVE, GuideChannel::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** The named channels only, with their current programme; empty ids give an empty guide without a query. */
    fun observeGuideChannels(channelIds: List<String>, nowEpochMillis: Long): Flow<List<GuideChannel>> =
        if (channelIds.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            dao.observeGuideForChannels(channelIds, nowEpochMillis)
                .map { rows -> rows.map { it.toDomain() }.distinctBy(GuideChannel::id) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    /** One group by its shown title across the enabled sources; null for the channels without one. */
    fun observeGuideForGroup(groupTitle: String?, nowEpochMillis: Long): Flow<List<GuideChannel>> =
        dao.observeGuideForGroup(groupTitle, nowEpochMillis)
            .map { rows -> rows.map { it.toDomain() }.distinctBy(GuideChannel::id) }
            .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.LIVE, GuideChannel::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeTimeline(
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Flow<List<GuideTimelineChannel>> =
        dao.observeGuideTimeline(fromEpochMillis, toEpochMillis)
            .map(::timelineChannels)
            .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.LIVE, GuideTimelineChannel::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** One source, and one of its groups when [groupTitle] is given: what the guide shows at a time. */
    fun observeTimeline(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        sourceId: String,
        groupTitle: String?,
    ): Flow<List<GuideTimelineChannel>> =
        dao.observeGuideTimelineForSource(fromEpochMillis, toEpochMillis, sourceId, groupTitle)
            .map(::timelineChannels)
            .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.LIVE, GuideTimelineChannel::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** Named channels only; empty ids give an empty timeline without a query. */
    fun observeTimelineForChannels(
        channelIds: List<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Flow<List<GuideTimelineChannel>> =
        if (channelIds.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            dao.observeGuideTimelineForChannels(fromEpochMillis, toEpochMillis, channelIds)
                .map(::timelineChannels)
                .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.LIVE, GuideTimelineChannel::organizationItem) ?: it }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    /** The rail's groups and counts, cheap enough to keep observed. */
    fun observeRail(): Flow<List<GuideRailGroup>> =
        dao.observeGuideRail()
            .map { rows ->
                rows.map {
                    GuideRailGroup(it.sourceId, it.sourceName, it.sourcePriority, it.groupTitle, it.organizationGroupKey, it.channelCount)
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeEditableChannels(): Flow<List<EditableChannel>> =
        dao.observeEditableChannels().map { rows -> rows.map { it.toDomain() } }

    fun observeXmlTvChannelOptions(sourceId: String): Flow<List<XmlTvChannelOption>> =
        dao.observeXmlTvChannelOptions(sourceId).map { rows -> rows.map { it.toDomain() } }

    fun observeCustomChannelLists(): Flow<List<CustomChannelList>> =
        dao.observeCustomChannelLists().map { lists ->
            lists.map { CustomChannelList(it.listId, it.name, it.sortOrder) }
        }

    fun observeChannelListMemberships(): Flow<List<ChannelListMembership>> =
        dao.observeCustomChannelListMembers().map { members ->
            members.map { ChannelListMembership(it.listId, it.channelId, it.sortOrder) }
        }

    suspend fun activeChannel(channelId: String): IptvChannelEntity? = dao.getActiveChannel(channelId)

    /** The source and the group a channel is shown under, custom name included; null if it is not active. */
    suspend fun channelPlacement(channelId: String): GuideChannelPlacement? {
        val channel = dao.getActiveChannel(channelId) ?: return null
        val custom = dao.channelPreference(channelId)?.customGroupTitle?.takeIf(String::isNotBlank)
        return GuideChannelPlacement(channel.sourceId, custom ?: channel.groupTitle)
    }

    suspend fun search(query: String, limit: Int = 80): List<GuideSearchResult> {
        val normalized = query.trim().take(MAX_SEARCH_QUERY_LENGTH)
        if (normalized.length < MIN_SEARCH_QUERY_LENGTH) return emptyList()
        return dao.searchGuide(normalized, limit.coerceIn(1, MAX_SEARCH_RESULTS)).map { row ->
            GuideSearchResult(
                type = row.resultType,
                sourceId = row.sourceId,
                channelId = row.channelId,
                title = row.title,
                subtitle = row.subtitle,
                logoUrl = row.logoUrl,
                startEpochMillis = row.startEpochMillis,
                stopEpochMillis = row.stopEpochMillis,
            )
        }
    }

    suspend fun activeSourceState(sourceId: String): IptvSourceStateEntity? = dao.sourceState(sourceId)

    suspend fun upsertSourceState(source: IptvSourceConfiguration) {
        dao.upsertSourceState(
            IptvSourceStateEntity(
                sourceId = source.id,
                name = source.name,
                type = source.type.name,
                enabled = source.enabled,
                connectionLimit = source.connectionLimit,
                priority = source.priority,
                updatedAtEpochMillis = clock(),
                epgOffsetMinutes = source.epgOffsetMinutes,
            ),
        )
    }

    suspend fun clear() = dao.clearGuide()

    suspend fun clearSource(sourceId: String) = dao.clearSource(sourceId)

    suspend fun channelCustomizationSnapshot(): ChannelCustomizationSnapshot =
        ChannelCustomizationSnapshot(
            preferences = dao.channelPreferences(),
            lists = dao.customChannelLists(),
            members = dao.customChannelListMembers(),
            organization = organization?.snapshot() ?: com.streammate.tv.core.database.OrganizationSnapshot(),
        )

    suspend fun restoreChannelCustomization(snapshot: ChannelCustomizationSnapshot) {
        require(snapshot.preferences.size <= MAX_CUSTOMIZED_CHANNELS) { "Too many channel preferences" }
        require(snapshot.lists.size <= MAX_CUSTOM_CHANNEL_LISTS) { "Too many custom channel lists" }
        require(snapshot.members.size <= MAX_CUSTOM_LIST_MEMBERS) { "Too many custom list members" }
        require(snapshot.preferences.map(ChannelPreferenceEntity::channelId).distinct().size == snapshot.preferences.size) {
            "Duplicate channel preference"
        }
        val listIds = snapshot.lists.map(CustomChannelListEntity::listId)
        require(listIds.distinct().size == listIds.size) { "Duplicate custom channel list" }
        require(snapshot.members.all { it.listId in listIds }) { "Unknown custom channel list member" }
        com.streammate.tv.core.database.validateOrganizationSnapshot(snapshot.organization)
        dao.replaceChannelCustomization(snapshot.preferences, snapshot.lists, snapshot.members)
        organization?.restore(snapshot.organization)
    }

    suspend fun updateChannel(
        channel: EditableChannel,
        customName: String? = channel.customName,
        customGroupTitle: String? = channel.customGroupTitle,
        hidden: Boolean = channel.hidden,
        sortOrder: Int? = channel.sortOrder,
        manualXmltvChannelId: String? = channel.manualXmltvChannelId,
    ) {
        dao.upsertChannelPreference(
            ChannelPreferenceEntity(
                channelId = channel.id,
                sourceId = channel.sourceId,
                customName = customName?.trim()?.takeIf(String::isNotBlank),
                customGroupTitle = customGroupTitle?.trim()?.takeIf(String::isNotBlank),
                hidden = hidden,
                sortOrder = sortOrder,
                manualXmltvChannelId = manualXmltvChannelId?.trim()?.takeIf(String::isNotBlank),
                updatedAtEpochMillis = clock(),
            ),
        )
    }

    suspend fun reorderChannels(channels: List<EditableChannel>) {
        val now = clock()
        dao.upsertChannelPreferences(
            channels.mapIndexed { index, channel ->
                ChannelPreferenceEntity(
                    channelId = channel.id,
                    sourceId = channel.sourceId,
                    customName = channel.customName,
                    customGroupTitle = channel.customGroupTitle,
                    hidden = channel.hidden,
                    sortOrder = index,
                    manualXmltvChannelId = channel.manualXmltvChannelId,
                    updatedAtEpochMillis = now,
                )
            },
        )
    }

    suspend fun resetChannel(channelId: String) = dao.deleteChannelPreference(channelId)

    suspend fun createCustomChannelList(name: String, sortOrder: Int): String {
        val cleanedName = name.trim().take(100)
        require(cleanedName.isNotBlank()) { "List name is required" }
        val id = UUID.randomUUID().toString()
        dao.upsertCustomChannelList(CustomChannelListEntity(id, cleanedName, sortOrder, clock()))
        return id
    }

    suspend fun deleteCustomChannelList(listId: String) = dao.deleteCustomChannelList(listId)

    suspend fun setCustomListMembership(
        listId: String,
        channelId: String,
        member: Boolean,
        sortOrder: Int,
    ) {
        if (member) {
            dao.upsertCustomChannelListMember(CustomChannelListMemberEntity(listId, channelId, sortOrder))
        } else {
            dao.deleteCustomChannelListMember(listId, channelId)
        }
    }

    fun observeSourceRefreshHealth(): Flow<List<SourceRefreshHealth>> =
        dao.observeSourceRefreshStates().map { states -> states.map { it.toDomain() } }

    /** The sources the guide draws from, by name, for screens that must say which one is empty. */
    fun observeSourceStates(): Flow<List<GuideSource>> =
        dao.observeSourceStates().map { states ->
            states.map { GuideSource(id = it.sourceId, name = it.name, enabled = it.enabled) }
        }

    /** Whether a playlist import has ever completed for [sourceId]. */
    suspend fun hasImportedPlaylist(sourceId: String): Boolean =
        dao.sourceRefreshState(sourceId, GuideDao.PLAYLIST_KIND)?.lastSuccessAtEpochMillis != null

    private fun GuideChannelRow.toDomain() = GuideChannel(
        legacyPosition = legacyPosition,
        organizationGroupKey = organizationGroupKey,
        sourceId = sourceId,
        sourceName = sourceName,
        sourcePriority = sourcePriority,
        id = channelId,
        name = name,
        groupTitle = groupTitle,
        logoUrl = logoUrl,
        playlistOrder = playlistOrder,
        currentProgrammeTitle = currentProgrammeTitle,
        currentProgrammeSubtitle = currentProgrammeSubtitle,
        programmeStartEpochMillis = programmeStartEpochMillis,
        programmeStopEpochMillis = programmeStopEpochMillis,
    )

    private fun SourceRefreshStateEntity.toDomain() = SourceRefreshHealth(
        sourceId = sourceId,
        kind = kind,
        status = status,
        lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
        lastSuccessAtEpochMillis = lastSuccessAtEpochMillis,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        lastError = lastError,
        itemCount = itemCount,
        consecutiveFailures = consecutiveFailures,
    )

    private fun EditableChannelRow.toDomain() = EditableChannel(
        sourceEnabled = sourceEnabled,
        organizationGroupKey = organizationGroupKey,
        sourceId = sourceId,
        sourceName = sourceName,
        id = channelId,
        originalName = originalName,
        originalGroupTitle = originalGroupTitle,
        logoUrl = logoUrl,
        tvgId = tvgId,
        playlistOrder = playlistOrder,
        customName = customName,
        customGroupTitle = customGroupTitle,
        hidden = hidden,
        sortOrder = sortOrder,
        manualXmltvChannelId = manualXmltvChannelId,
    )

    private fun XmlTvChannelOptionRow.toDomain() = XmlTvChannelOption(
        sourceId = sourceId,
        id = xmltvChannelId,
        displayName = displayName,
    )

    private fun timelineChannels(rows: List<GuideTimelineRow>): List<GuideTimelineChannel> =
        rows.groupByTo(LinkedHashMap(), GuideTimelineRow::channelId).values.map { channelRows ->
            val channel = channelRows.first()
            GuideTimelineChannel(
                legacyPosition = channel.legacyPosition,
                organizationGroupKey = channel.organizationGroupKey,
                sourceId = channel.sourceId,
                sourceName = channel.sourceName,
                sourcePriority = channel.sourcePriority,
                id = channel.channelId,
                name = channel.channelName,
                groupTitle = channel.groupTitle,
                logoUrl = channel.logoUrl,
                playlistOrder = channel.playlistOrder,
                catchupType = channel.catchupType,
                catchupSource = channel.catchupSource,
                catchupDays = channel.catchupDays,
                programmes = deduplicateGuideSchedule(
                    channelRows.mapNotNull { row ->
                        val id = row.programmeId ?: return@mapNotNull null
                        GuideTimelineProgramme(
                            id = id,
                            title = row.programmeTitle.orEmpty(),
                            subtitle = row.programmeSubtitle,
                            description = row.programmeDescription,
                            categories = row.programmeCategories
                                ?.split(CATEGORY_SEPARATOR)
                                ?.filter(String::isNotBlank)
                                .orEmpty(),
                            startEpochMillis = row.programmeStartEpochMillis ?: return@mapNotNull null,
                            stopEpochMillis = row.programmeStopEpochMillis ?: return@mapNotNull null,
                        )
                    },
                ),
            )
        }

    private companion object {
        const val CATEGORY_SEPARATOR = "\u001F"
        const val MAX_CUSTOMIZED_CHANNELS = 100_000
        const val MAX_CUSTOM_CHANNEL_LISTS = 1_000
        const val MAX_CUSTOM_LIST_MEMBERS = 500_000
        const val MIN_SEARCH_QUERY_LENGTH = 2
        const val MAX_SEARCH_QUERY_LENGTH = 80
        const val MAX_SEARCH_RESULTS = 200
    }
}
