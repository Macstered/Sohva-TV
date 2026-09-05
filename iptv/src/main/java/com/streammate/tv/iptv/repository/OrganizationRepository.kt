package com.streammate.tv.iptv.repository

import com.streammate.tv.app.AppPreferences
import com.streammate.tv.core.database.OrganizationChange
import com.streammate.tv.core.database.OrganizationDao
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
    ) { snapshots, matched -> snapshots to matched }.distinctUntilChanged().map {
        val groups = dao.movies().groupBy { catalogueWorkKey(it.name, it.year, it.externalId) }
        dao.registerFilmAliases(groups.map { (key, copies) ->
            listOf("work:$key") + copies.map { "vod:movie:${it.sourceId}:${it.itemId}" }
        })
    }.flowOn(Dispatchers.Default)

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
    ): Flow<List<T>> = combine(identified(flow, room, item), state) { pairs, current ->
        val byId = pairs.associate { it.second.id to it.first }
        current.organization.orderedItems(room, pairs.map { it.second }, viewKey, chronological = chronological)
            .mapNotNull { byId[it.id] }
    }.flowOn(Dispatchers.Default)

    suspend fun change(changes: List<OrganizationChange>) = dao.change(changes)

    fun allCategoryGroups(room: LibraryRoom): Flow<List<CatalogueCategory>> = dao.observeGroups().map { rows ->
        rows.filter { it.room == room.name && !it.name.isNullOrBlank() }.distinctBy { it.nameKey }
            .map { CatalogueCategory(it.name!!, 0) }
    }.flowOn(Dispatchers.Default)

    fun orderedCategories(flow: Flow<List<CatalogueCategory>>, room: LibraryRoom): Flow<List<CatalogueCategory>> =
        combine(flow, state, dao.observeGroups()) { categories, current, backing ->
            val names = backing.filter { it.room == room.name }.groupBy { it.nameKey }
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
