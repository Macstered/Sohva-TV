package com.sohva.tv.feature.trakt.shelf

import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.TraktKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * A Trakt list Home can show as an added row (spec 02 HOME-FR-94, -99): its layout id, the API path,
 * the kind of its titles (a public list mixes both and says per item), whether it needs the
 * profile's account, how long a fetched copy lasts, and a short name for the store and the log.
 */
class TraktRowSource private constructor(
    val id: String,
    val path: String,
    val kind: TraktKind,
    val account: Boolean,
    val maxAgeMs: Long,
    val name: String,
    /** A public list by number (HOME-FR-99): its row is titled with the list's own name. */
    val listId: Long? = null,
    /** A smart list (HOME-FR-101): [listId] is a smart list's number. */
    val smart: Boolean = false,
) {
    /**
     * The store's record name: a chart or public list is the same for everyone, so profiles share
     * one copy; a watchlist is the profile's own and goes with its account (TraktAccountStore.STATE_KEYS).
     */
    fun key(profile: String): String = if (account) "$name:$profile" else "chart:$name"

    override fun toString(): String = name

    companion object {
        val WATCHLIST_MOVIES = TraktRowSource(HomeLayout.TRAKT_WATCHLIST_MOVIES, "sync/watchlist/movies/rank", TraktKind.MOVIE, true, ACCOUNT_AGE, "watchlist_movies")
        val WATCHLIST_SHOWS = TraktRowSource(HomeLayout.TRAKT_WATCHLIST_SHOWS, "sync/watchlist/shows/rank", TraktKind.SHOW, true, ACCOUNT_AGE, "watchlist_shows")
        val TRENDING_MOVIES = TraktRowSource(HomeLayout.TRAKT_TRENDING_MOVIES, "movies/trending", TraktKind.MOVIE, false, CHART_AGE, "trending_movies")
        val TRENDING_SHOWS = TraktRowSource(HomeLayout.TRAKT_TRENDING_SHOWS, "shows/trending", TraktKind.SHOW, false, CHART_AGE, "trending_shows")
        val POPULAR_MOVIES = TraktRowSource(HomeLayout.TRAKT_POPULAR_MOVIES, "movies/popular", TraktKind.MOVIE, false, CHART_AGE, "popular_movies")
        val POPULAR_SHOWS = TraktRowSource(HomeLayout.TRAKT_POPULAR_SHOWS, "shows/popular", TraktKind.SHOW, false, CHART_AGE, "popular_shows")
        val ANTICIPATED_MOVIES = TraktRowSource(HomeLayout.TRAKT_ANTICIPATED_MOVIES, "movies/anticipated", TraktKind.MOVIE, false, CHART_AGE, "anticipated_movies")
        val ANTICIPATED_SHOWS = TraktRowSource(HomeLayout.TRAKT_ANTICIPATED_SHOWS, "shows/anticipated", TraktKind.SHOW, false, CHART_AGE, "anticipated_shows")
        val BOX_OFFICE = TraktRowSource(HomeLayout.TRAKT_BOX_OFFICE, "movies/boxoffice", TraktKind.MOVIE, false, CHART_AGE, "box_office")

        /** The fixed catalogue, in Settings › Home's order. */
        val entries: List<TraktRowSource> = listOf(
            WATCHLIST_MOVIES, WATCHLIST_SHOWS, TRENDING_MOVIES, TRENDING_SHOWS, POPULAR_MOVIES, POPULAR_SHOWS,
            ANTICIPATED_MOVIES, ANTICIPATED_SHOWS, BOX_OFFICE,
        )

        /** A public list's row (HOME-FR-99): its films and series in the list's own order. */
        fun list(id: Long): TraktRowSource =
            TraktRowSource(HomeLayout.traktList(id), "lists/$id/items/movie,show", TraktKind.MOVIE, false, CHART_AGE, "list_$id", listId = id)

        /** A public smart list's row (HOME-FR-101): what its filters resolve to now, in Trakt's order. */
        fun smart(id: Long): TraktRowSource =
            TraktRowSource(HomeLayout.traktSmartList(id), "smart-lists/$id/items", TraktKind.MOVIE, false, CHART_AGE, "smart_$id", listId = id, smart = true)

        fun of(id: String): TraktRowSource? = entries.firstOrNull { it.id == id }
            ?: HomeLayout.traktListId(id)?.let(::list)
            ?: HomeLayout.traktSmartListId(id)?.let(::smart)

        /** Titles per row: a row is a glance, not a catalogue (rule 4.2). */
        const val TITLES: Int = 30
    }
}

/** A watchlist is refetched after an hour, or at once when a row is added or a sync is asked for. */
private const val ACCOUNT_AGE: Long = 60L * 60 * 1000

/** Charts and public lists move slowly: six hours. */
private const val CHART_AGE: Long = 6L * 60 * 60 * 1000

/**
 * The stored lists of Home's added Trakt rows, read from the encrypted store once and then served
 * from memory, like [TraktShelves]. Memory is bounded by the catalogue: at most one list per chart,
 * plus two watchlists per profile read this session, each of at most [TraktRowSource.TITLES] cards.
 */
class TraktRowLists(private val host: TraktHost) {
    private val _lists = MutableStateFlow<Map<String, TraktShelf?>>(emptyMap())

    /** Every list read so far, by store key; Home maps it to its added rows. */
    val lists: StateFlow<Map<String, TraktShelf?>> = _lists.asStateFlow()

    suspend fun read(profile: String, source: TraktRowSource): TraktShelf? {
        val key = source.key(profile)
        _lists.value[key]?.let { return it }
        if (key in _lists.value) return null
        val read = withContext(host.dispatchers.io) { host.store.secret(key)?.let(TraktShelves::decode) }
        _lists.update { it + (key to read) }
        return read
    }

    suspend fun save(profile: String, source: TraktRowSource, shelf: TraktShelf) {
        val key = source.key(profile)
        withContext(host.dispatchers.io) { host.store.putSecret(key, TraktShelves.encode(shelf)) }
        _lists.update { it + (key to shelf) }
    }

    /** A public list's name before its titles arrive (HOME-FR-99): kept with any titles already stored. */
    suspend fun saveTitle(profile: String, source: TraktRowSource, title: String) {
        val stored = read(profile, source)
        save(profile, source, (stored ?: TraktShelf(0, emptyList())).copy(title = title))
    }

    /** A profile's watchlists gone from memory (disconnect, another account); the store drops its records itself. */
    fun forget(profile: String) = _lists.update { map -> map.filterKeys { !it.endsWith(":$profile") || it.startsWith("chart:") } }

    /** Device tests. */
    fun clear() = _lists.update { emptyMap() }
}
