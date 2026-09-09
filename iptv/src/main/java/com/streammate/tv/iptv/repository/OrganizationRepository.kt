package com.streammate.tv.iptv.repository

import com.streammate.tv.core.diagnostics.DiagnosticsLog
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.activeRestriction
import com.streammate.tv.app.ProfileRestriction
import com.streammate.tv.core.database.OrganizationChange
import com.streammate.tv.core.database.OrganizationDao
import com.streammate.tv.core.database.OrganizationGroupRow
import com.streammate.tv.core.database.OrganizationSnapshot
import com.streammate.tv.core.database.toEntity
import com.streammate.tv.core.model.*
import com.streammate.tv.iptv.metadata.catalogueWorkKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class OrganizationReadState(
    val organization: LibraryOrganization = LibraryOrganization(),
    val identities: Map<String, String> = emptyMap(),
) {
    fun identify(item: OrganizationItem) = item.copy(identity = identities[item.id] ?: item.identity)
}

data class ManagedLibrary(
    val items: List<OrganizationItem> = emptyList(),
    val sourceNames: Map<String, String> = emptyMap(),
    val state: OrganizationReadState = OrganizationReadState(),
    val customLists: List<CustomChannelList> = emptyList(),
    val listMemberships: List<ChannelListMembership> = emptyList(),
    val loading: Boolean = false,
    val loadError: Boolean = false,
)

/** How long the metadata worker's matches are left to settle before identities are folded in. */
private const val METADATA_MATCH_SETTLE_MILLIS = 60_000L

/** Films per page of the identity pass: a few thousand keeps each page's aliases to a few chunks. */
private const val IDENTITY_PAGE_SIZE = 2_000

class OrganizationRepository(
    private val dao: OrganizationDao,
    private val preferences: com.streammate.tv.app.AppPreferencesRepository? = null,
) {
    suspend fun managerLocation(room: LibraryRoom): Pair<String?, String?> =
        preferences?.managerLocation(room.name)?.first() ?: (null to null)
    suspend fun saveManagerLocation(room: LibraryRoom, group: String?, source: String?) {
        preferences?.setManagerLocation(room.name, group, source)
    }
    /**
     * The rules alone: a few hundred rows at most. Film identities are looked
     * up per list by [identified], because the alias table holds one row per
     * film copy and reading it whole for every guide read took ten seconds
     * with a large provider, and every alias write during an import re-read it.
     */
    val state: Flow<OrganizationReadState> = dao.observeRules().map { rules ->
        OrganizationReadState(LibraryOrganization(rules.map { it.toRule() }))
    }.flowOn(Dispatchers.Default)

    /**
     * What the active profile may see: everything, until a profile has been
     * restricted in Settings. Applied on top of the rules by [organize],
     * [orderedCategories] and [allCategoryGroups], so a profile only ever sees
     * less than the device does.
     */
    val restriction: Flow<ProfileRestriction> =
        preferences?.preferences?.map { it.activeRestriction }?.distinctUntilChanged() ?: flowOf(ProfileRestriction.NONE)

    suspend fun currentRestriction(): ProfileRestriction = restriction.first()

    /** [flow] without the rows the active profile may not see; the device's own rules are not consulted here. */
    fun <T> restrict(flow: Flow<List<T>>, room: LibraryRoom, groupKey: (T) -> String): Flow<List<T>> =
        combine(flow, restriction) { rows, current ->
            if (!current.restricted) rows else rows.filter { current.allows(room, groupKey(it)) }
        }

    /** The groups a room's items belong to, one row per source and key: what a profile can be limited to. */
    fun groupChoices(room: LibraryRoom): Flow<List<OrganizationGroupRow>> =
        dao.observeGroups().map { rows -> rows.filter { it.room == room.name } }.flowOn(Dispatchers.Default)

    /**
     * The rows paired with their organisation items, carrying the film
     * identity for the movie room. Only movies have aliases; the other rooms
     * pass through without a lookup, and only the movie room re-runs on an
     * alias write.
     */
    private fun <T> identified(flow: Flow<List<T>>, room: LibraryRoom, item: (T) -> OrganizationItem): Flow<List<Pair<T, OrganizationItem>>> =
        if (room == LibraryRoom.MOVIES) {
            combine(flow, dao.observeAliasCount()) { rows, _ -> rows }.map { rows ->
                val items = rows.map(item)
                val identities = dao.identities(items.map { it.id })
                rows.zip(items) { row, current -> row to current.copy(identity = identities[current.id] ?: current.identity) }
            }
        } else {
            flow.map { rows -> rows.map { it to item(it) } }
        }

    /** Idempotent compatibility import. Old values stay intact for portable old backups. */
    suspend fun migrateLegacy(preferences: AppPreferences) {
        val existing = dao.rules().associateBy { it.toRule().key }
        val marker = OrganizationKey(LibraryRoom.LIVE, groupKey = "@legacy-v1")
        if (marker in existing) return
        val rules = buildList {
            listOf(
                LibraryRoom.LIVE to preferences.hiddenLiveCategories,
                LibraryRoom.MOVIES to preferences.hiddenMovieCategories,
                LibraryRoom.SERIES to preferences.hiddenSeriesCategories,
            ).forEach { (room, names) -> names.forEach { name ->
                val key = OrganizationKey(room, groupKey = organizationGroupKey(name))
                if (key !in existing) add(OrganizationRule(key, enabled = false).toEntity())
            } }
            add(OrganizationRule(marker, enabled = true).toEntity())
        }
        dao.upsertRules(rules)
    }

    suspend fun registerImportedSnapshot(sourceId: String, snapshotId: String) {
        val movies = dao.importedMovies(sourceId, snapshotId)
        dao.registerFilmAliases(movies.groupBy { catalogueWorkKey(it.name, it.year, null) }.map { (key, copies) ->
            listOf("work:$key") + copies.map { "vod:movie:$sourceId:${it.itemId}" }
        })
    }

    suspend fun registerImportedMovies(sourceId: String, movies: List<com.streammate.tv.iptv.xtream.XtreamMovie>) {
        dao.registerFilmAliases(movies.groupBy { catalogueWorkKey(it.name, it.year, null) }.map { (key, copies) ->
            listOf("work:$key") + copies.map { "vod:movie:$sourceId:${it.streamId}" }
        })
    }

    /**
     * Folds the metadata worker's matches into the film identities. Triggered
     * by a catalogue activation or, settling after a minute, by the count of
     * matched titles: observing the film list itself re-read every film on
     * every batch an import wrote and on every title the worker matched.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun movieIdentityUpdates(): Flow<Unit> = combine(
        dao.observeActiveCatalogueSnapshots().distinctUntilChanged(),
        dao.observeMatchedMetadataCount().distinctUntilChanged().debounce(METADATA_MATCH_SETTLE_MILLIS),
    ) { snapshots, matched -> snapshots to matched }.distinctUntilChanged().map { (snapshots, matched) ->
        // A pass over a large catalogue is minutes of disk work, so one that
        // completed for this same state is not repeated at the next start.
        val mark = "${snapshots.joinToString("|")}#$matched"
        if (preferences?.movieIdentityMark() != mark) {
            registerAllMovieIdentities()
            preferences?.setMovieIdentityMark(mark)
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Folds every active film into the alias table, a page at a time. A copy
     * of a film on another page still meets its fellows through the shared
     * `work:` alias every group carries, so the pages need no overlap. This
     * used to load the whole catalogue and, with a large provider, ran the
     * app out of memory a minute after every start.
     */
    suspend fun registerAllMovieIdentities(pageSize: Int = IDENTITY_PAGE_SIZE) {
        val started = System.currentTimeMillis()
        var offset = 0
        var pages = 0
        while (true) {
            val rows = dao.movieIdentityRows(pageSize, offset)
            if (rows.isEmpty()) break
            pages++
            val groups = rows.groupBy { catalogueWorkKey(it.name, it.year, it.externalId) }
            dao.registerFilmAliases(groups.map { (key, copies) ->
                listOf("work:$key") + copies.map { "vod:movie:${it.sourceId}:${it.itemId}" }
            })
            offset += rows.size
            if (rows.size < pageSize) break
        }
        DiagnosticsLog.i("Identity", "film identities: $offset films in $pages pages, ${System.currentTimeMillis() - started} ms")
    }

    fun observeLibrary(room: LibraryRoom, guide: GuideRepository): Flow<ManagedLibrary> {
        val content = when (room) {
            LibraryRoom.LIVE -> guide.observeEditableChannels().map { channels ->
                ManagedLibrary(channels.map(EditableChannel::organizationItem), channels.associate { it.sourceId to it.sourceName })
            }
            LibraryRoom.MOVIES, LibraryRoom.SERIES -> (if (room == LibraryRoom.MOVIES) dao.observeMovies() else dao.observeSeries()).map { rows ->
                ManagedLibrary(rows.map { row ->
                    OrganizationItem(
                        id = if (room == LibraryRoom.MOVIES) "vod:movie:${row.sourceId}:${row.itemId}" else "series:${row.sourceId}:${row.itemId}",
                        sourceId = row.sourceId, title = row.name, groupName = row.categoryName,
                        groupKey = row.organizationGroupKey, imageUrl = row.posterUrl, year = row.year, rating = row.rating, sourceEnabled = row.sourceEnabled,
                    )
                }, rows.associate { it.sourceId to it.sourceName })
            }
        }
        // The manager works on every item of a room, so its identities are
        // looked up once per emission and carried in the state it hands on.
        val identified: Flow<Pair<ManagedLibrary, Map<String, String>>> =
            if (room == LibraryRoom.MOVIES) {
                combine(content, dao.observeAliasCount()) { library, _ -> library }.map { library ->
                    library to dao.identities(library.items.map { it.id })
                }
            } else {
                content.map { it to emptyMap() }
            }
        return combine(identified, state, guide.observeCustomChannelLists(), guide.observeChannelListMemberships()) { (library, identities), rules, lists, members ->
            val current = rules.copy(identities = identities)
            library.copy(items = library.items.map(current::identify), state = current,
                customLists = if (room == LibraryRoom.LIVE) lists else emptyList(),
                listMemberships = if (room == LibraryRoom.LIVE) members else emptyList())
        }.flowOn(Dispatchers.Default).catch { emit(ManagedLibrary(loadError = true)) }
    }

    fun <T> organize(
        flow: Flow<List<T>>, room: LibraryRoom, item: (T) -> OrganizationItem,
        viewKey: String? = null, chronological: Boolean = false,
    ): Flow<List<T>> = combine(identified(flow, room, item), state, restriction) { pairs, current, allowed ->
        val byId = pairs.associate { it.second.id to it.first }
        current.organization.orderedItems(room, pairs.map { it.second }, viewKey, chronological = chronological)
            .filter { allowed.allows(room, it.groupKey) }
            .mapNotNull { byId[it.id] }
    }.flowOn(Dispatchers.Default)

    suspend fun change(changes: List<OrganizationChange>) = dao.change(changes)

    fun allCategoryGroups(room: LibraryRoom): Flow<List<CatalogueCategory>> =
        combine(dao.observeGroups(), restriction) { rows, allowed ->
            rows.filter { it.room == room.name && !it.name.isNullOrBlank() && allowed.allows(room, it.groupKey) }
                .distinctBy { it.nameKey }
                .map { CatalogueCategory(it.name!!, 0) }
        }.flowOn(Dispatchers.Default)

    fun orderedCategories(flow: Flow<List<CatalogueCategory>>, room: LibraryRoom): Flow<List<CatalogueCategory>> =
        combine(flow, state, dao.observeGroups(), restriction) { all, current, backing, allowed ->
            val names = backing.filter { it.room == room.name }.groupBy { it.nameKey }
            // A restricted profile's rail carries only the categories whose
            // groups it may see, under any of the sources that hold them.
            val categories = all.filter { category ->
                !allowed.restricted || names[organizationGroupKey(category.name)].orEmpty().any { allowed.allows(room, it.groupKey) }
            }
            val ordered = current.organization.orderedGroups(room, categories.map { category ->
                category.name to names[organizationGroupKey(category.name)].orEmpty().map {
                    OrganizationItem("", it.sourceId, category.name, category.name, it.groupKey)
                }
            })
            val byName = categories.associateBy { it.name }
            ordered.mapNotNull { name -> byName[name]?.copy(manualPosition = names[organizationGroupKey(name)].orEmpty().mapNotNull {
                current.organization.groupRule(room, OrganizationItem("", it.sourceId, name, name, it.groupKey)).position
            }.minOrNull()) }
        }.flowOn(Dispatchers.Default)
    suspend fun snapshot(): OrganizationSnapshot = dao.snapshot()
    suspend fun restore(snapshot: OrganizationSnapshot) = dao.restore(snapshot)

    suspend fun resetGroup(room: LibraryRoom, source: String, key: String) = dao.resetGroup(room.name, source, key)
}

fun EditableChannel.organizationItem() = OrganizationItem(
    id, sourceId, displayName, displayGroupTitle, organizationGroupKey, logoUrl,
    providerOrder = playlistOrder, legacyHidden = hidden, legacyPosition = sortOrder?.toLong(), sourceEnabled = sourceEnabled,
)

fun GuideChannel.organizationItem() = OrganizationItem(id, sourceId, name, groupTitle, organizationGroupKey, logoUrl, providerOrder = playlistOrder, legacyPosition = legacyPosition)
fun GuideTimelineChannel.organizationItem() = OrganizationItem(id, sourceId, name, groupTitle, organizationGroupKey, logoUrl, providerOrder = playlistOrder, legacyPosition = legacyPosition)
fun VodMovie.organizationItem() = OrganizationItem(contentKey, sourceId, name, categoryName, organizationGroupKey, posterUrl, year, rating)
fun VodMovieCard.organizationItem() = OrganizationItem(contentKey, sourceId, name, categoryName, organizationGroupKey, posterUrl, year, rating)
fun VodSeries.organizationItem() = OrganizationItem(seriesContentKey(sourceId, seriesId), sourceId, name, categoryName, organizationGroupKey, posterUrl, year, rating)
fun VodSeriesCard.organizationItem() = OrganizationItem(seriesContentKey(sourceId, seriesId), sourceId, name, categoryName, organizationGroupKey, posterUrl, year, rating)
