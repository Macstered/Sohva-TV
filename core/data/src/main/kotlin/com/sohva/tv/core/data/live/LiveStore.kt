package com.sohva.tv.core.data.live

import com.sohva.tv.core.data.database.ChannelRankKey
import com.sohva.tv.core.data.database.DEFAULT_PROFILE
import com.sohva.tv.core.data.database.FavouriteChannelEntity
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.data.database.PlayableChannel
import com.sohva.tv.core.data.database.ProgrammeRow
import com.sohva.tv.core.data.database.RecentChannelEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.model.guide.ChannelDial
import com.sohva.tv.core.model.guide.Genre
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.guide.Schedules
import com.sohva.tv.core.model.org.OrgItem
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgResolver
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.org.RuleValue
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The live TV reads and the viewer's channel sets for the guide and the player (spec 20 §6, spec 30
 * §9). Every call runs on [io]; nothing here holds a catalogue: lists are [ChannelList] indexes,
 * schedules come for at most 80 guide ids and four hours at a time.
 */
/** Favourites, recents and what may be seen belong to the active profile, which [profile] names at each call (spec 04 PROF-FR-07). */
class LiveStore(
    private val db: SohvaDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val profile: () -> String = { DEFAULT_PROFILE },
) : LiveReads {
    private val live = db.live()
    private val viewer = db.viewer()

    /** Enabled sources with visible channels, by priority then name; re-emitted on every write. */
    override val sources: Flow<List<LiveSource>> = live.observeSources().flowOn(io)

    /**
     * One signal per write to the tables a guide list depends on (GUIDE-FR-37), a profile's allowed
     * groups included, so a changed restriction applies while the guide is open (GUIDE-49); the
     * first emission is the current state, not a write (spec 20 §10).
     */
    override fun changes(): Flow<Unit> = db.invalidationTracker.createFlow(
        "channel", "content_group", "source", "source_status", "channel_list", "channel_list_member", "profile_allowed_group",
    ).map { }

    /** A new guide snapshot or a source edit re-reads the visible programmes (§8 "Import while open"). */
    override fun guideChanges(): Flow<Unit> = db.invalidationTracker.createFlow("source_status", "source").map { }

    /** The source's groups the active profile may see (spec 04 PROF-FR-23). */
    override suspend fun rail(sourceId: String): List<LiveGroup> = withContext(io) { live.rail(sourceId, profile()) }

    override fun railRuleChanges(): Flow<LiveRailRules> =
        db.invalidationTracker.createFlow("organization_rule").map { railRules() }.distinctUntilChanged()

    private suspend fun railRules(): LiveRailRules = withContext(io) {
        val rules = OrgRules(db).of(OrgRoom.LIVE)
        val resolver = OrgResolver(rules)
        fun shortcut(key: String) = ShortcutRule(resolver.shortcutShown(OrgRoom.LIVE, key), resolver.shortcutPosition(OrgRoom.LIVE, key))
        val listIds = rules.map { it.key.groupKey }.filter { it.startsWith(LIST) }.map { it.removePrefix(LIST) }.toSet()
        LiveRailRules(
            order = resolver.groupOrder(OrgRoom.LIVE),
            favourites = shortcut(OrgKeys.FAVOURITES),
            recent = shortcut(OrgKeys.RECENT),
            lists = listIds.associateWith { shortcut(OrgKeys.list(it)) },
        )
    }

    /** A group the profile may not see opens empty (a remembered group after a switch, spec 04 PROF-FR-23). */
    override suspend fun open(spec: ListSpec): ChannelList = withContext(io) {
        val profileId = profile()
        val allowed = spec !is ListSpec.Group || db.profiles().groupAllowed(profileId, OrgRoom.LIVE.wire, spec.groupId)
        ChannelList.open(if (allowed) spec else ListSpec.Named(spec.sourceId, LongArray(0)), live, profileId)
    }

    /** Whether the active profile may watch [key] (spec 01 SHELL-FR-20); an unknown channel is not refused here. */
    suspend fun allowed(key: String): Boolean = withContext(io) { db.profiles().channelAllowed(profile(), key) ?: true }

    override suspend fun page(list: ChannelList, page: Int): List<LiveChannel> = withContext(io) { list.page(page) }

    override suspend fun indexOf(list: ChannelList, channel: LiveChannel): Int = withContext(io) { list.indexOf(channel.id, channel.rank) }

    override suspend fun channel(key: String): LiveChannel? = withContext(io) { live.byKey(key) }

    /** What the playback engine opens: the sealed address and the source's limit (spec 30 PLAY-FR-14). */
    suspend fun playable(key: String): PlayableChannel? = withContext(io) { live.playable(key) }

    /** Favourites of the profile in [sourceId], in display order (GUIDE-FR-33, GUIDE-NFR-12). */
    override suspend fun favourites(sourceId: String): ListSpec.Named = withContext(io) {
        val keys = viewer.favouriteKeys(profile())
        val found = keys.chunked(BATCH).flatMap { live.keysByChannelKey(sourceId, it, profile()) }
        ListSpec.Named(sourceId, found.sortedWith(compareBy({ it.rank }, { it.id })).map { it.id }.toLongArray())
    }

    /** The profile's last 20 channels of [sourceId], most recent first (GUIDE-FR-34). */
    override suspend fun recents(sourceId: String): ListSpec.Named = withContext(io) {
        val keys = viewer.recentKeys(profile())
        val byKey = live.keysByChannelKey(sourceId, keys, profile()).associateBy { it.key }
        ListSpec.Named(sourceId, keys.mapNotNull { byKey[it]?.id }.toLongArray())
    }

    override fun customLists(): Flow<List<CustomListRef>> =
        db.channelLists().lists().map { lists -> lists.map { CustomListRef(it.id, it.name) } }.flowOn(io)

    /**
     * A list's channels of [sourceId] in its view (GUIDE-FR-35, ORG-FR-21): members switched off in
     * the list's view are left out; the order is the view's sort, else the room default's, else the
     * list's own order. Lists are the household's own and small, so they are sorted here.
     */
    override suspend fun customList(listId: String, sourceId: String): ListSpec.Named = withContext(io) {
        val places = db.channelLists().members(listId, sourceId)
        val byKey = places.map { it.key }.chunked(BATCH).flatMap { live.keysByChannelKey(sourceId, it, profile()) }.associateBy { it.key }
        val resolver = OrgResolver(OrgRules(db).of(OrgRoom.LIVE))
        val view = OrgKeys.list(listId)
        val members = places.mapIndexedNotNull { i, m -> byKey[m.key]?.let { row -> ListMember(row, i, m.sortOrder, resolver.memberRule(item(sourceId, m.key), view)) } }
            .filter { it.rule.enabled != false }
        val sorted = when (resolver.ruleSort(OrgRoom.LIVE, "", view, view)) {
            OrgSort.TITLE_ASC -> members.sortedWith(compareBy({ it.row.sortName }, { it.row.key }))
            OrgSort.TITLE_DESC -> members.sortedWith(compareByDescending<ListMember> { it.row.sortName }.thenBy { it.row.key })
            OrgSort.PROVIDER -> members.sortedWith(compareBy({ it.row.rank }, { it.row.id }))
            // Manual and no rule: the manager's place, else the member's place in the list (ORG-FR-22).
            else -> members.sortedWith(compareBy({ it.rule.position ?: it.sortOrder }, { it.index }))
        }
        ListSpec.Named(sourceId, sorted.map { it.row.id }.toLongArray())
    }

    private class ListMember(val row: ChannelRankKey, val index: Int, val sortOrder: Long, val rule: RuleValue)

    // A list view names its members by channel key; the channel's own groups do not take part.
    private fun item(sourceId: String, key: String) = OrgItem(OrgRoom.LIVE, sourceId, "", "", key)

    override fun favouriteKeys(): Flow<Set<String>> =
        viewer.observeFavouriteKeys(profile()).map { it.toHashSet() }.flowOn(io)

    /** Toggles a favourite; returns whether the channel is a favourite afterwards (GUIDE-FR-30). */
    override suspend fun toggleFavourite(key: String): Boolean = withContext(io) {
        val profileId = profile()
        if (viewer.removeFavourite(profileId, key) > 0) {
            false
        } else {
            viewer.addFavourite(FavouriteChannelEntity(profileId, key, clock.wallMillis()))
            true
        }
    }

    /** Front of the recents, trimmed to 20 (spec 30 PLAY-FR-57). */
    override suspend fun recordWatched(key: String): Unit = withContext(io) {
        viewer.recordRecent(RecentChannelEntity(profile(), key, clock.wallMillis()))
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
            is ListSpec.All -> live.sourceMatches(source.id, profile(), snapshot, name, title, from, to, from - longest)
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
                    is ListSpec.All -> live.sourceByNumber(spec.sourceId, n, profile())?.let { list.indexOf(it.id, it.rank) }
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

        const val LIST = "@list:"
    }
}
