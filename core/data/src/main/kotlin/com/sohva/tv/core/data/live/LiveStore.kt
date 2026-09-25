package com.sohva.tv.core.data.live

import com.sohva.tv.core.data.database.DEFAULT_PROFILE
import com.sohva.tv.core.data.database.FavouriteChannelEntity
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.data.database.PlayableChannel
import com.sohva.tv.core.data.database.ProgrammeRow
import com.sohva.tv.core.data.database.RecentChannelEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.guide.ChannelDial
import com.sohva.tv.core.model.guide.Genre
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.guide.Schedules
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The live TV reads and the viewer's channel sets for the guide and the player (spec 20 §6, spec 30
 * §9). Every call runs on [io]; nothing here holds a catalogue: lists are [ChannelList] indexes,
 * schedules come for at most 80 guide ids and four hours at a time.
 */
class LiveStore(private val db: SohvaDatabase, private val io: CoroutineDispatcher, private val clock: Clock) : LiveReads {
    private val live = db.live()
    private val viewer = db.viewer()

    /** Enabled sources with visible channels, by priority then name; re-emitted on every write. */
    override val sources: Flow<List<LiveSource>> = live.observeSources().flowOn(io)

    /**
     * One signal per write to the tables a guide list depends on (GUIDE-FR-37); the first emission
     * is the current state, not a write (spec 20 §10).
     */
    override fun changes(): Flow<Unit> =
        db.invalidationTracker.createFlow("channel", "content_group", "source", "source_status", "channel_list", "channel_list_member").map { }

    /** A new guide snapshot or a source edit re-reads the visible programmes (§8 "Import while open"). */
    override fun guideChanges(): Flow<Unit> = db.invalidationTracker.createFlow("source_status", "source").map { }

    override suspend fun rail(sourceId: String): List<LiveGroup> = withContext(io) { live.rail(sourceId) }

    override suspend fun open(spec: ListSpec): ChannelList = withContext(io) { ChannelList.open(spec, live) }

    override suspend fun page(list: ChannelList, page: Int): List<LiveChannel> = withContext(io) { list.page(page) }

    override suspend fun indexOf(list: ChannelList, channel: LiveChannel): Int = withContext(io) { list.indexOf(channel.id, channel.rank) }

    override suspend fun channel(key: String): LiveChannel? = withContext(io) { live.byKey(key) }

    /** What the playback engine opens: the sealed address and the source's limit (spec 30 PLAY-FR-14). */
    suspend fun playable(key: String): PlayableChannel? = withContext(io) { live.playable(key) }

    /** Favourites of the profile in [sourceId], in display order (GUIDE-FR-33, GUIDE-NFR-12). */
    override suspend fun favourites(sourceId: String, profileId: String): ListSpec.Named = withContext(io) {
        val keys = viewer.favouriteKeys(profileId)
        val found = keys.chunked(BATCH).flatMap { live.keysByChannelKey(sourceId, it) }
        ListSpec.Named(sourceId, found.sortedWith(compareBy({ it.rank }, { it.id })).map { it.id }.toLongArray())
    }

    /** The profile's last 20 channels of [sourceId], most recent first (GUIDE-FR-34). */
    override suspend fun recents(sourceId: String, profileId: String): ListSpec.Named = withContext(io) {
        val keys = viewer.recentKeys(profileId)
        val byKey = live.keysByChannelKey(sourceId, keys).associateBy { it.key }
        ListSpec.Named(sourceId, keys.mapNotNull { byKey[it]?.id }.toLongArray())
    }

    override fun customLists(): Flow<List<CustomListRef>> =
        db.channelLists().lists().map { lists -> lists.map { CustomListRef(it.id, it.name) } }.flowOn(io)

    override suspend fun customList(listId: String, sourceId: String): ListSpec.Named = withContext(io) {
        val keys = db.channelLists().memberKeys(listId, sourceId)
        val byKey = keys.chunked(BATCH).flatMap { live.keysByChannelKey(sourceId, it) }.associateBy { it.key }
        ListSpec.Named(sourceId, keys.mapNotNull { byKey[it]?.id }.toLongArray())
    }

    override fun favouriteKeys(profileId: String): Flow<Set<String>> =
        viewer.observeFavouriteKeys(profileId).map { it.toHashSet() }.flowOn(io)

    fun recentsChanged(profileId: String = DEFAULT_PROFILE): Flow<List<String>> = viewer.observeRecentKeys(profileId).flowOn(io)

    /** Toggles a favourite; returns whether the channel is a favourite afterwards (GUIDE-FR-30). */
    override suspend fun toggleFavourite(key: String, profileId: String): Boolean = withContext(io) {
        if (viewer.removeFavourite(profileId, key) > 0) {
            false
        } else {
            viewer.addFavourite(FavouriteChannelEntity(profileId, key, clock.wallMillis()))
            true
        }
    }

    /** Front of the recents, trimmed to 20 (spec 30 PLAY-FR-57). */
    override suspend fun recordWatched(key: String, profileId: String): Unit = withContext(io) {
        viewer.recordRecent(RecentChannelEntity(profileId, key, clock.wallMillis()))
    }

    /**
     * Schedules for [epgIds] of one source around [windowStart] (GUIDE-FR-51..53): read with the
     * source's EPG offset undone in the predicate and added to the result, cleaned per channel.
     */
    override suspend fun schedules(source: LiveSource, epgIds: Collection<String>, windowStart: Long): Map<String, List<GuideProgramme>> =
        withContext(io) {
            if (epgIds.isEmpty()) return@withContext emptyMap()
            val state = live.epgState(source.id)
            val snapshot = state?.snapshot ?: return@withContext emptyMap()
            val offset = source.epgOffsetMinutes * GuideWindow.MINUTE_MS
            val from = windowStart - GuideWindow.READ_MARGIN_MS - offset
            val to = windowStart + GuideWindow.LENGTH_MS + GuideWindow.READ_MARGIN_MS - offset
            val longest = state.maxDurationMs ?: DEFAULT_LONGEST
            val rows = epgIds.chunked(BATCH).flatMap { live.window(source.id, snapshot, it, from, to, from - longest) }
            rows.groupBy { it.epgId }.mapValues { (_, list) -> Schedules.clean(list.map { it.toProgramme(offset) }) }
        }

    override suspend fun description(programmeId: Long): String? = withContext(io) { live.description(programmeId) }

    /**
     * Find programme (GUIDE-FR-92): the rows of [spec] (a group or All channels) whose name contains
     * [query] or that air a matching title in the window, in list order.
     */
    override suspend fun search(spec: ListSpec, source: LiveSource, query: String, windowStart: Long): ListSpec.Named = withContext(io) {
        val snapshot = live.epgState(source.id)?.snapshot ?: -1L
        val offset = source.epgOffsetMinutes * GuideWindow.MINUTE_MS
        val from = windowStart - offset
        val to = windowStart + GuideWindow.LENGTH_MS - offset
        val longest = live.epgState(source.id)?.maxDurationMs ?: DEFAULT_LONGEST
        val name = "%" + escape(SortNames.of(query)) + "%"
        val title = "%" + escape(query.trim()) + "%"
        val keys = when (spec) {
            is ListSpec.Group -> live.groupMatches(spec.groupId, source.id, snapshot, name, title, from, to, from - longest)
            is ListSpec.All -> live.sourceMatches(source.id, snapshot, name, title, from, to, from - longest)
            is ListSpec.Ungrouped, is ListSpec.Named -> emptyList()
        }
        ListSpec.Named(source.id, keys.map { it.id }.toLongArray())
    }

    /** Number dialling within [list] (GUIDE-FR-81, -84): own number first, then position. Returns the list index or -1. */
    override suspend fun dial(list: ChannelList, number: Int): Int = withContext(io) {
        val spec = list.spec
        val found = ChannelDial.resolve(
            number,
            byOwnNumber = { n ->
                when (spec) {
                    is ListSpec.Group -> live.groupByNumber(spec.groupId, n)?.let { list.indexOf(it.id, it.rank) }
                    is ListSpec.All -> live.sourceByNumber(spec.sourceId, n)?.let { list.indexOf(it.id, it.rank) }
                    is ListSpec.Named -> spec.ids.toList().chunked(BATCH).firstNotNullOfOrNull { live.idsByNumber(it, n).firstOrNull() }
                        ?.let { id -> spec.ids.indexOf(id) }
                    is ListSpec.Ungrouped -> null
                }?.takeIf { it >= 0 }
            },
            atPosition = { position -> position.takeIf { it < list.size } },
            ownNumber = { index -> numberAt(list, index) },
        )
        found ?: -1
    }

    private suspend fun numberAt(list: ChannelList, index: Int): Int? {
        val row = list.page(index / ChannelList.PAGE).getOrNull(index % ChannelList.PAGE) ?: return null
        return row.number
    }

    private fun ProgrammeRow.toProgramme(offset: Long): GuideProgramme {
        val categories = Schedules.categories(categories)
        return GuideProgramme(
            id = id, start = startAt + offset, stop = stopAt + offset, title = title, subtitle = subtitle,
            firstCategory = categories.firstOrNull(), genre = Genre.of(categories), hasDescription = hasDescription,
            categoryCount = categories.size, key = programmeKey,
        )
    }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            if (c == '\\' || c == '%' || c == '_') append('\\')
            append(c)
        }
    }

    private companion object {
        /** SQLite's 999-variable limit on older Android (GUIDE-FR-30). */
        const val BATCH = 500

        /** Until an import records the longest programme, assume a day. */
        const val DEFAULT_LONGEST: Long = 24 * 60 * 60 * 1000L
    }
}
