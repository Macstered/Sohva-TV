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

class OrganizationRepository(
    private val dao: OrganizationDao,
    private val preferences: com.streammate.tv.app.AppPreferencesRepository? = null,
) {
    suspend fun managerLocation(room: LibraryRoom): Pair<String?, String?> =
        preferences?.managerLocation(room.name)?.first() ?: (null to null)
    suspend fun saveManagerLocation(room: LibraryRoom, group: String?, source: String?) {
        preferences?.setManagerLocation(room.name, group, source)
    }
    val state: Flow<OrganizationReadState> = dao.observeRecords().map { records ->
        OrganizationReadState(LibraryOrganization(records.filter { it.kind == 0 }.map {
            OrganizationRule(OrganizationKey(LibraryRoom.valueOf(it.room), it.sourceId, it.groupKey, it.itemKey), it.enabled, LibrarySort.parse(it.sortMode), it.position)
        }), records.filter { it.kind == 1 }.associate { it.alias to it.identity })
    }.flowOn(Dispatchers.Default)

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

    fun movieIdentityUpdates(): Flow<Unit> = dao.observeMovies().distinctUntilChanged().map { movies ->
        val groups = movies.groupBy { catalogueWorkKey(it.name, it.year, it.externalId) }
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
        return combine(content, state, guide.observeCustomChannelLists(), guide.observeChannelListMemberships()) { library, current, lists, members ->
            library.copy(items = library.items.map(current::identify), state = current,
                customLists = if (room == LibraryRoom.LIVE) lists else emptyList(),
                listMemberships = if (room == LibraryRoom.LIVE) members else emptyList())
        }.flowOn(Dispatchers.Default).catch { emit(ManagedLibrary(loadError = true)) }
    }

    fun <T> organize(
        flow: Flow<List<T>>, room: LibraryRoom, item: (T) -> OrganizationItem,
        viewKey: String? = null, chronological: Boolean = false,
    ): Flow<List<T>> = combine(flow, state) { rows, current ->
        val pairs = rows.map { it to current.identify(item(it)) }
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
