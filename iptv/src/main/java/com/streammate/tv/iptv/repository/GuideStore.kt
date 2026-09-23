package com.streammate.tv.iptv.repository

import com.streammate.tv.core.database.GuideChannelRow
import com.streammate.tv.core.database.GuideDao
import com.streammate.tv.core.database.GuideRosterRow
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
import com.streammate.tv.core.diagnostics.DiagnosticsLog
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.LibraryRoom
import com.streammate.tv.app.ProfileRestriction
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.withIndex

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
    /** The playlist's own channel number, when it states one. */
    val channelNumber: Int? = null,
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
    /** The viewer's number when set, the playlist's otherwise; null when neither gives one. */
    val channelNumber: Int? = null,
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
    val channelNumber: Int? = null,
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
    val organizationGroupKey: String = "",
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
    /** The viewer's own logo: an address, or a file the phone page saved on this TV. */
    val customLogoUrl: String? = null,
    /** The number the playlist gives the channel, if any. */
    val providerChannelNumber: Int? = null,
    /** The number the viewer gave the channel, if any. */
    val channelNumber: Int? = null,
) {
    val displayName: String get() = customName?.takeIf(String::isNotBlank) ?: originalName
    val displayGroupTitle: String? get() = customGroupTitle?.takeIf(String::isNotBlank) ?: originalGroupTitle
    val displayLogoUrl: String? get() = customLogoUrl?.takeIf(String::isNotBlank) ?: logoUrl
    val displayChannelNumber: Int? get() = channelNumber ?: providerChannelNumber
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
                channelNumber = channel.channelNumber,
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
    /**
     * Where the last channel rows are kept between two visits to the guide.
     * Without one nothing is kept and every visit reads, as the tests expect.
     */
    private val rosterScope: CoroutineScope? = null,
    private val rosterKeptMillis: Long = ROSTER_KEPT_MILLIS,
) {
    constructor(dao: GuideDao, clock: () -> Long) : this(dao, clock, null)

    // Leaving the guide for a channel and coming back is the commonest thing
    // done on it, and each return read its rows again: three seconds of
    // "Loading" for a source of 56,000 on the Shield. The rows last read are
    // kept instead, for as long as nothing behind them has been written.
    // Room only tells an observer, and between two visits the guide has none,
    // so this one counts the writes for it.
    private val rosterWrites = AtomicLong()
    @Volatile private var keptRoster: KeptRoster? = null
    private var rosterRelease: Job? = null
    private var rosterReaders = 0

    init {
        // The flow's first value is the state it found, not a write. Counted,
        // it arrived after the first read often enough to make the kept rows
        // look stale and be read again.
        rosterScope?.launch { dao.observeGuideChannelTables().drop(1).collect { rosterWrites.incrementAndGet() } }
    }
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
                .let { organization?.restrict(it, LibraryRoom.LIVE, GuideChannel::organizationGroupKey) ?: it }
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

    /**
     * The channels of one source, or one of its groups, with no programmes:
     * the first read for every selection, whose programmes the screen reads
     * for the rows in view with [observeTimelineForChannels].
     *
     * Read a page at a time, and again after a write to a table behind it.
     * A collector that has gone, because the viewer chose another group, ends
     * the read at its next page instead of leaving it to run for nobody, and
     * conflate folds a burst of import writes into one re-read after the read
     * in hand rather than abandoning that read for each of them.
     */
    fun observeChannelsForSource(sourceId: String, groupTitle: String?): Flow<List<GuideTimelineChannel>> =
        dao.observeGuideChannelTables()
            .conflate()
            .withIndex()
            // The first value is the state of things as the guide opens, which
            // the kept rows may still be. Every later one is a write.
            .map { (index, _) -> (if (index == 0) keptChannels(sourceId, groupTitle) else null) ?: readChannels(sourceId, groupTitle) }
            .onStart { rosterReaderArrived() }
            .onCompletion { rosterReaderLeft() }
            .let { organization?.organize(it, com.streammate.tv.core.model.LibraryRoom.LIVE, GuideTimelineChannel::organizationItem) ?: it }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    private suspend fun readChannels(sourceId: String, groupTitle: String?): List<GuideTimelineChannel> {
        val started = System.nanoTime()
        // Counted before the read: a write that lands during it leaves the
        // rows looking older than they are, never newer.
        val writes = rosterWrites.get()
        var attempts = 0
        var read: ChannelPages
        do {
            attempts++
            read = readChannelPages(sourceId, groupTitle, stopWhenSpliced = attempts < CHANNEL_READ_ATTEMPTS)
        } while (read.spliced && attempts < CHANNEL_READ_ATTEMPTS)
        val channels = read.rows.sortedWith(GuideRosterRow.DISPLAY_ORDER).map { it.toTimelineChannel() }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        // Counts and timings only: what a capture needs to tell a slow read
        // from a slow screen, with nothing of the playlist in it.
        if (channels.size >= CHANNEL_PAGE_SIZE || elapsedMillis >= SLOW_CHANNEL_READ_MILLIS) {
            DiagnosticsLog.i(
                "Guide",
                "channel rows: ${channels.size} of a ${if (groupTitle == null) "source" else "group"} in ${read.pages} pages, " +
                    "$elapsedMillis ms" + if (attempts > 1) ", $attempts attempts" else "",
            )
        }
        // An empty read costs nothing to repeat and has no snapshot to be checked against.
        keptRoster = read.rows.firstOrNull()?.takeIf { rosterScope != null && !read.spliced }
            ?.let { KeptRoster(sourceId, groupTitle, writes, it.snapshotId, channels) }
        return channels
    }

    private class KeptRoster(
        val sourceId: String,
        val groupTitle: String?,
        val writes: Long,
        val snapshotId: String,
        val channels: List<GuideTimelineChannel>,
    )

    /**
     * The rows of the last read, if they are of this selection and nothing has
     * been written since. The count of writes arrives a moment after the write
     * itself, so the one write that replaces every row, a playlist's
     * activation, is asked after directly.
     */
    private suspend fun keptChannels(sourceId: String, groupTitle: String?): List<GuideTimelineChannel>? {
        val kept = keptRoster ?: return null
        if (kept.sourceId != sourceId || kept.groupTitle != groupTitle || kept.writes != rosterWrites.get()) return null
        if (dao.activePlaylistSnapshotId(sourceId) != kept.snapshotId) return null
        return kept.channels
    }

    private fun rosterReaderArrived() = synchronized(rosterWrites) {
        rosterReaders++
        rosterRelease?.cancel()
        rosterRelease = null
    }

    /** A source's rows are tens of megabytes: kept for a return to the guide, not for the evening. */
    private fun rosterReaderLeft() = synchronized(rosterWrites) {
        if (--rosterReaders > 0) return
        rosterRelease?.cancel()
        rosterRelease = rosterScope?.launch {
            delay(rosterKeptMillis)
            synchronized(rosterWrites) { if (rosterReaders == 0) keptRoster = null }
        }
    }

    private class ChannelPages(val rows: List<GuideRosterRow>, val pages: Int, val spliced: Boolean)

    /**
     * Every page of the selection. Each page is its own statement, so a
     * playlist activated between two of them would splice the old snapshot's
     * first channels onto the new one's last, or end the old one early; the
     * caller reads again, and only a last attempt is read through regardless,
     * since the activation's own write brings a fresh read straight after.
     */
    private suspend fun readChannelPages(sourceId: String, groupTitle: String?, stopWhenSpliced: Boolean): ChannelPages {
        val rows = ArrayList<GuideRosterRow>()
        val pool = HashMap<String, String>()
        var after = ""
        var pages = 0
        while (true) {
            val page = dao.guideChannelPage(sourceId, groupTitle, after, CHANNEL_PAGE_SIZE)
            pages++
            val spliced = page.isNotEmpty() && rows.isNotEmpty() && page.first().snapshotId != rows.first().snapshotId
            if (spliced && stopWhenSpliced) return ChannelPages(rows, pages, spliced = true)
            page.mapTo(rows) { it.sharing(pool) }
            if (page.size < CHANNEL_PAGE_SIZE) break
            after = page.last().channelId
        }
        // One page is one statement and cannot straddle an activation. After
        // several, the snapshot that was read has to be the one still active.
        val moved = pages > 1 && dao.activePlaylistSnapshotId(sourceId) != rows.first().snapshotId
        return ChannelPages(rows, pages, spliced = moved)
    }

    /** Named rows without EPG; batches stay below older Android SQLite's bind limit. */
    fun observeChannelsForIds(channelIds: List<String>): Flow<List<GuideTimelineChannel>> =
        if (channelIds.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            combine(channelIds.distinct().chunked(500).map(dao::observeGuideChannelsForIds)) { batches ->
                batches.flatMap { rows -> rows.map { it.toTimelineChannel() } }
            }
                .let { organization?.organize(it, LibraryRoom.LIVE, GuideTimelineChannel::organizationItem) ?: it }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    /** Used only by the explicit guide search, including programmes on rows not yet scrolled to. */
    fun observeProgrammeMatches(
        sourceId: String,
        groupTitle: String?,
        query: String,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Flow<Set<String>> {
        val pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return dao.observeGuideProgrammeMatches(sourceId, groupTitle, pattern, fromEpochMillis, toEpochMillis)
            .map { it.toSet() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

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
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    fun observeChannelListMemberships(): Flow<List<ChannelListMembership>> =
        dao.observeCustomChannelListMembers().map { members ->
            members.map { ChannelListMembership(it.listId, it.channelId, it.sortOrder) }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** The guide needs members only for the list currently selected. */
    fun observeChannelListMemberships(listId: String): Flow<List<ChannelListMembership>> =
        dao.observeCustomChannelListMembers(listId).map { members ->
            members.map { ChannelListMembership(it.listId, it.channelId, it.sortOrder) }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun activeChannel(channelId: String): IptvChannelEntity? = dao.getActiveChannel(channelId)

    /** The source and the group a channel is shown under, custom name included; null if it is not active. */
    /** Whether the active profile may open [channelId]; an unknown channel is nobody's to refuse. */
    suspend fun channelAllowed(channelId: String): Boolean {
        val restriction = organization?.currentRestriction() ?: return true
        if (!restriction.restricted) return true
        val channel = dao.getActiveChannel(channelId) ?: return true
        // The same choice the guide queries make: a renamed group's own key, else the provider's.
        val preference = dao.channelPreference(channelId)
        val groupKey = preference?.customGroupTitle?.takeIf(String::isNotBlank)
            ?.let { preference.customOrganizationGroupKey } ?: channel.organizationGroupKey
        return restriction.allows(LibraryRoom.LIVE, groupKey)
    }

    suspend fun channelPlacement(channelId: String): GuideChannelPlacement? {
        val channel = dao.getActiveChannel(channelId) ?: return null
        val custom = dao.channelPreference(channelId)?.customGroupTitle?.takeIf(String::isNotBlank)
        return GuideChannelPlacement(channel.sourceId, custom ?: channel.groupTitle)
    }

    suspend fun search(query: String, limit: Int = 80): List<GuideSearchResult> {
        val normalized = query.trim().take(MAX_SEARCH_QUERY_LENGTH)
        if (normalized.length < MIN_SEARCH_QUERY_LENGTH) return emptyList()
        val restriction = organization?.currentRestriction() ?: ProfileRestriction.NONE
        return dao.searchGuide(normalized, limit.coerceIn(1, MAX_SEARCH_RESULTS)).filter { row ->
            restriction.allows(LibraryRoom.LIVE, row.organizationGroupKey)
        }.map { row ->
            GuideSearchResult(
                type = row.resultType,
                sourceId = row.sourceId,
                channelId = row.channelId,
                organizationGroupKey = row.organizationGroupKey,
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
            organization = organization?.backupSnapshot() ?: com.streammate.tv.core.database.OrganizationSnapshot(),
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
        customLogoUrl: String? = channel.customLogoUrl,
        channelNumber: Int? = channel.channelNumber,
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
                customLogoUrl = customLogoUrl?.trim()?.takeIf(String::isNotBlank),
                channelNumber = channelNumber?.takeIf { it > 0 },
            ),
        )
    }

    /**
     * Keeps [customLogoUrl] for a channel the viewer may never have opened in
     * the editor: what the phone page saved lands here, on the preference the
     * channel already has or on a fresh one for its source.
     */
    suspend fun setChannelLogo(channelId: String, customLogoUrl: String?) {
        val existing = dao.channelPreference(channelId)
        val sourceId = existing?.sourceId ?: dao.channelSourceId(channelId) ?: return
        dao.upsertChannelPreference(
            existing?.copy(customLogoUrl = customLogoUrl, updatedAtEpochMillis = clock())
                ?: ChannelPreferenceEntity(
                    channelId = channelId,
                    sourceId = sourceId,
                    customName = null,
                    customGroupTitle = null,
                    hidden = false,
                    sortOrder = null,
                    manualXmltvChannelId = null,
                    updatedAtEpochMillis = clock(),
                    customLogoUrl = customLogoUrl,
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
                    customLogoUrl = channel.customLogoUrl,
                    channelNumber = channel.channelNumber,
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
        channelNumber = channelNumber,
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
        customLogoUrl = customLogoUrl,
        providerChannelNumber = providerChannelNumber,
        channelNumber = channelNumber,
    )

    private fun XmlTvChannelOptionRow.toDomain() = XmlTvChannelOption(
        sourceId = sourceId,
        id = xmltvChannelId,
        displayName = displayName,
    )

    private fun timelineChannels(rows: List<GuideTimelineRow>): List<GuideTimelineChannel> =
        rows.groupByTo(LinkedHashMap(), GuideTimelineRow::channelId).values.map { channelRows ->
            val channel = channelRows.first()
            channel.toTimelineChannel(
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

    // Channel-only reads are already one row per channel. Do not build a map,
    // a singleton list and an empty schedule for every channel in the lineup.
    private fun GuideTimelineRow.toTimelineChannel(
        programmes: List<GuideTimelineProgramme> = emptyList(),
    ) = GuideTimelineChannel(
        legacyPosition = legacyPosition,
        organizationGroupKey = organizationGroupKey,
        sourceId = sourceId,
        sourceName = sourceName,
        sourcePriority = sourcePriority,
        id = channelId,
        name = channelName,
        groupTitle = groupTitle,
        logoUrl = logoUrl,
        playlistOrder = playlistOrder,
        catchupType = catchupType,
        catchupSource = catchupSource,
        catchupDays = catchupDays,
        channelNumber = channelNumber,
        programmes = programmes,
    )

    private fun GuideRosterRow.toTimelineChannel() = GuideTimelineChannel(
        legacyPosition = legacyPosition,
        organizationGroupKey = organizationGroupKey ?: com.streammate.tv.core.model.organizationGroupKey(groupTitle),
        sourceId = sourceId,
        sourceName = sourceName,
        sourcePriority = sourcePriority,
        id = channelId,
        name = channelName,
        groupTitle = groupTitle,
        logoUrl = logoUrl,
        playlistOrder = playlistOrder,
        catchupType = catchupType,
        catchupSource = catchupSource,
        catchupDays = catchupDays,
        channelNumber = channelNumber,
        programmes = emptyList(),
    )

    private companion object {
        const val CATEGORY_SEPARATOR = "\u001F"
        const val MAX_CUSTOMIZED_CHANNELS = 100_000
        /** Channel rows per statement: about a megabyte, inside the two a cursor window holds. */
        const val CHANNEL_PAGE_SIZE = 2_000
        const val CHANNEL_READ_ATTEMPTS = 3
        const val SLOW_CHANNEL_READ_MILLIS = 250L
        const val ROSTER_KEPT_MILLIS = 10 * 60_000L
        const val MAX_CUSTOM_CHANNEL_LISTS = 1_000
        const val MAX_CUSTOM_LIST_MEMBERS = 500_000
        const val MIN_SEARCH_QUERY_LENGTH = 2
        const val MAX_SEARCH_QUERY_LENGTH = 80
        const val MAX_SEARCH_RESULTS = 200
    }
}
