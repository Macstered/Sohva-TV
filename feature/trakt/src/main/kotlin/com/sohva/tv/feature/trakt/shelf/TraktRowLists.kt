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
 * A Trakt list Home can show as an added row (spec 02 HOME-FR-94): its layout id, the API path, the
 * kind of its titles, whether it needs the profile's account, and how long a fetched copy lasts.
 */
enum class TraktRowSource(val id: String, val path: String, val kind: TraktKind, val account: Boolean, val maxAgeMs: Long) {
    WATCHLIST_MOVIES(HomeLayout.TRAKT_WATCHLIST_MOVIES, "sync/watchlist/movies/rank", TraktKind.MOVIE, true, ACCOUNT_AGE),
    WATCHLIST_SHOWS(HomeLayout.TRAKT_WATCHLIST_SHOWS, "sync/watchlist/shows/rank", TraktKind.SHOW, true, ACCOUNT_AGE),
    TRENDING_MOVIES(HomeLayout.TRAKT_TRENDING_MOVIES, "movies/trending", TraktKind.MOVIE, false, CHART_AGE),
    TRENDING_SHOWS(HomeLayout.TRAKT_TRENDING_SHOWS, "shows/trending", TraktKind.SHOW, false, CHART_AGE),
    POPULAR_MOVIES(HomeLayout.TRAKT_POPULAR_MOVIES, "movies/popular", TraktKind.MOVIE, false, CHART_AGE),
    POPULAR_SHOWS(HomeLayout.TRAKT_POPULAR_SHOWS, "shows/popular", TraktKind.SHOW, false, CHART_AGE),
    ANTICIPATED_MOVIES(HomeLayout.TRAKT_ANTICIPATED_MOVIES, "movies/anticipated", TraktKind.MOVIE, false, CHART_AGE),
    ANTICIPATED_SHOWS(HomeLayout.TRAKT_ANTICIPATED_SHOWS, "shows/anticipated", TraktKind.SHOW, false, CHART_AGE),
    BOX_OFFICE(HomeLayout.TRAKT_BOX_OFFICE, "movies/boxoffice", TraktKind.MOVIE, false, CHART_AGE),
    ;

    /**
     * The store's record name: a chart is the same for everyone, so profiles share one copy; a
     * watchlist is the profile's own and goes with its account (TraktAccountStore.STATE_KEYS).
     */
    fun key(profile: String): String = if (account) "${name.lowercase()}:$profile" else "chart:${name.lowercase()}"

    companion object {
        fun of(id: String): TraktRowSource? = entries.firstOrNull { it.id == id }

        /** Titles per row: a row is a glance, not a catalogue (rule 4.2). */
        const val TITLES: Int = 30
    }
}

/** A watchlist is refetched after an hour, or at once when a row is added or a sync is asked for. */
private const val ACCOUNT_AGE: Long = 60L * 60 * 1000

/** Charts move slowly: six hours. */
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

    /** A profile's watchlists gone from memory (disconnect, another account); the store drops its records itself. */
    fun forget(profile: String) = _lists.update { map -> map.filterKeys { !it.endsWith(":$profile") || it.startsWith("chart:") } }

    /** Device tests. */
    fun clear() = _lists.update { emptyMap() }
}
