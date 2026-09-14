package com.streammate.tv.trakt

import android.content.Context
import com.sohva.tv.trakt.*
import com.streammate.tv.BuildConfig
import com.streammate.tv.core.database.TraktStateDao
import com.streammate.tv.core.database.TraktStateEntity
import com.streammate.tv.core.diagnostics.DiagnosticsLog
import com.streammate.tv.core.security.SecretCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Trakt for one Sohva installation: sign in per profile, keep tokens fresh,
 * and scrobble what the players report. Trakt itself is the viewing ledger;
 * local VOD and Discover progress stay exactly as they are.
 */
/** [offline] serves what is cached and never calls Trakt: the screenshot build's fictional account. */
class TraktService(context: Context, cipher: SecretCipher, private val state: TraktStateDao, private val offline: Boolean = false) {
    /** False when the build carries no Trakt application credentials. */
    val configured: Boolean = BuildConfig.TRAKT_CLIENT_ID.isNotBlank() && BuildConfig.TRAKT_CLIENT_SECRET.isNotBlank()

    private val credentials by lazy { TraktAppCredentials(BuildConfig.TRAKT_CLIENT_ID, BuildConfig.TRAKT_CLIENT_SECRET, REDIRECT_URI) }
    private val auth by lazy { TraktAuthClient(credentials) }
    private val identities by lazy { TraktIdentityClient(credentials.clientId) }
    private val api by lazy { TraktApiClient(credentials.clientId) }
    private val store = TraktAccountStore(context, cipher)
    private val accounts = MutableStateFlow(store.profiles().associateWith { store.account(it) })
    private val refreshes = Mutex()
    private val deliveries = Mutex()
    private val syncs = Mutex()
    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val homeTitles = MutableStateFlow<Map<String, List<TraktHomeTitle>>>(emptyMap())

    /** Trakt's picks for the profile, as last fetched; empty until the first fetch. */
    fun recommendations(profile: String): Flow<List<TraktHomeTitle>> = homeRows(profile, TraktAccountStore.RECOMMENDATIONS)
    /** The next unwatched episode of each show the profile has been watching, newest first. */
    fun nextUp(profile: String): Flow<List<TraktHomeTitle>> = homeRows(profile, TraktAccountStore.NEXT_UP)

    private fun homeRows(profile: String, name: String): Flow<List<TraktHomeTitle>> {
        val key = "$name:$profile"
        if (homeTitles.value[key] == null) {
            val stored = store.titles(profile, name)?.items.orEmpty()
            homeTitles.value = homeTitles.value + (key to stored)
        }
        return homeTitles.map { it[key].orEmpty() }
    }

    private fun publishRows(profile: String, name: String, items: List<TraktHomeTitle>) {
        store.saveTitles(profile, name, TraktAccountStore.Titles(System.currentTimeMillis(), items))
        homeTitles.value = homeTitles.value + ("$name:$profile" to items)
    }

    fun account(profile: String): Flow<TraktAccount?> = accounts.map { it[profile] }
    fun currentAccount(profile: String): TraktAccount? = accounts.value[profile]

    /** Device pairing: shows the code through [onPrompt], then waits for approval. */
    suspend fun connect(profile: String, onPrompt: suspend (TraktPairingPrompt) -> Unit): TraktAccount {
        if (!configured) throw TraktException(TraktFailure.CONFIGURATION)
        val authorization = TraktDeviceAuthorizer(auth, identities).authorize(onPrompt)
        withContext(Dispatchers.IO) { store.save(profile, authorization.tokens, authorization.identity.username) }
        return publish(profile)!!
    }

    /** Removes the local tokens and the cached Trakt state. The account's Trakt history is untouched. */
    suspend fun disconnect(profile: String) {
        withContext(Dispatchers.IO) { store.remove(profile); state.deleteProfile(profile) }
        homeTitles.value = homeTitles.value - "${TraktAccountStore.RECOMMENDATIONS}:$profile" - "${TraktAccountStore.NEXT_UP}:$profile"
        publish(profile)
    }

    /** Sends a scrobble now, or keeps it for the next attempt when Trakt cannot be reached. */
    suspend fun scrobble(profile: String, item: TraktItem, action: TraktScrobbleAction, progress: Double) {
        if (!configured || offline || currentAccount(profile) == null) return
        // Remembered outside the queue: a start that never gets its stop, because the
        // app was killed mid-playback, would otherwise leave Trakt showing "watching".
        withContext(Dispatchers.IO) {
            store.saveActive(profile, if (action == TraktScrobbleAction.START) TraktPendingScrobble(item, action, progress) else null)
        }
        deliveries.withLock {
            val pending = withContext(Dispatchers.IO) { store.pending(profile) } + TraktPendingScrobble(item, action, progress.coerceIn(0.0, 100.0))
            val remaining = deliver(profile, pending)
            withContext(Dispatchers.IO) { store.savePending(profile, remaining) }
        }
    }

    /** The position moved while a start is in flight; kept so a kill still pauses near the right place. */
    suspend fun noteProgress(profile: String, item: TraktItem, progress: Double) {
        withContext(Dispatchers.IO) {
            val active = store.active(profile) ?: return@withContext
            if (active.item.toString() == item.toString()) store.saveActive(profile, TraktPendingScrobble(item, TraktScrobbleAction.START, progress))
        }
    }

    /** After a restart, close a start that never got its stop with a pause at the last known position. */
    private suspend fun closeInterrupted(profile: String) {
        val active = withContext(Dispatchers.IO) { store.active(profile) } ?: return
        DiagnosticsLog.i(TAG, "closing an interrupted playback with a pause at ${"%.1f".format(active.progress)}%")
        scrobble(profile, active.item, TraktScrobbleAction.PAUSE, active.progress)
    }

    /** Retries anything left over from earlier, without adding a new scrobble. */
    suspend fun flushPending(profile: String) {
        if (!configured || currentAccount(profile) == null) return
        deliveries.withLock {
            val pending = withContext(Dispatchers.IO) { store.pending(profile) }
            if (pending.isEmpty()) return
            val remaining = deliver(profile, pending)
            withContext(Dispatchers.IO) { store.savePending(profile, remaining) }
        }
    }

    /** Current tokens for API reads, refreshed when close to expiry; null when signed out or re-authorization is needed. */
    suspend fun tokens(profile: String): TraktTokens? = refreshes.withLock {
        if (!configured || offline) return null
        val current = withContext(Dispatchers.IO) { store.tokens(profile) } ?: return null
        if (current.expiresAtMillis - System.currentTimeMillis() > REFRESH_MARGIN_MILLIS) return current
        try {
            val refreshed = auth.refresh(current.refreshToken)
            val username = currentAccount(profile)?.username ?: ""
            withContext(Dispatchers.IO) { store.save(profile, refreshed, username) }
            refreshed
        } catch (error: TraktException) {
            if (error.failure != TraktFailure.REAUTHORIZE) throw error
            withContext(Dispatchers.IO) { store.markReauthorization(profile) }
            publish(profile)
            null
        }
    }

    internal fun api(): TraktApiClient = api

    /**
     * Keeps the cached Trakt state fresh while a profile is active: once at
     * start, shortly after each stop we report, and every quarter hour.
     */
    suspend fun runSync(profile: String): Nothing {
        if (offline) kotlinx.coroutines.awaitCancellation()
        runCatching { closeInterrupted(profile) }.onFailure { if (it is CancellationException) throw it }
        while (true) {
            // A first run has nothing cached for the rows, so it syncs even when Trakt reports nothing new.
            val firstRun = withContext(Dispatchers.IO) { store.titles(profile, TraktAccountStore.NEXT_UP) == null }
            runCatching { sync(profile, force = firstRun) }.onFailure { if (it is CancellationException) throw it }
            runCatching { refreshRecommendations(profile) }.onFailure { if (it is CancellationException) throw it }
            val requested = withTimeoutOrNull(SYNC_INTERVAL_MILLIS) { syncRequests.first() } != null
            if (requested) delay(SYNC_SETTLE_MILLIS)
        }
    }

    /** Refreshes the cached playback and watched lists when Trakt reports new activity. */
    suspend fun sync(profile: String, force: Boolean = false): Boolean = syncs.withLock {
        if (!configured || currentAccount(profile) == null) return false
        val tokens = tokens(profile) ?: return false
        val activities = api.lastActivities(tokens)
        val stamp = withContext(Dispatchers.IO) { store.activityStamp(profile) }
        if (!force && stamp != 0L && activities.latest <= stamp) return false
        val rows = linkedMapOf<String, TraktStateEntity>()
        fun key(kind: String, ids: TraktIds, season: Int? = null, number: Int? = null): String? {
            val id = ids.tmdb?.let { "tmdb:$it" } ?: ids.imdb?.let { "imdb:$it" } ?: return null
            return if (kind == "movie") "movie:$id" else "episode:$id:$season:$number"
        }
        for (movie in api.watchedMovies(tokens)) {
            val key = key("movie", movie.ids) ?: continue
            rows[key] = TraktStateEntity(profile, key, "movie", movie.ids.tmdb, movie.ids.imdb, null, null, 0.0, true, movie.plays, movie.lastWatchedAtMillis)
        }
        val shows = api.watchedShows(tokens)
        for (show in shows) for (episode in show.episodes) {
            val key = key("episode", show.ids, episode.season, episode.number) ?: continue
            rows[key] = TraktStateEntity(profile, key, "episode", show.ids.tmdb, show.ids.imdb, episode.season, episode.number, 0.0, true, episode.plays, episode.lastWatchedAtMillis)
        }
        for (paused in api.playback(tokens)) {
            val item = paused.item
            val key = when (item) {
                is TraktItem.Movie -> key("movie", item.ids)
                is TraktItem.Episode -> key("episode", item.ids, item.season, item.number)
            } ?: continue
            val season = (item as? TraktItem.Episode)?.season
            val number = (item as? TraktItem.Episode)?.number
            val existing = rows[key]
            rows[key] = TraktStateEntity(profile, key, if (item is TraktItem.Movie) "movie" else "episode", item.ids.tmdb, item.ids.imdb, season, number,
                paused.progress, existing?.watched ?: false, existing?.plays ?: 0, maxOf(paused.pausedAtMillis, existing?.updatedAtMillis ?: 0L))
        }
        withContext(Dispatchers.IO) { state.replace(profile, rows.values.toList()); store.saveActivityStamp(profile, activities.latest) }
        DiagnosticsLog.i(TAG, "synced ${rows.size} Trakt titles")
        runCatching { refreshNextUp(profile, tokens, shows) }.onFailure { if (it is CancellationException) throw it }
        return true
    }

    /**
     * The next episode for the shows watched most recently. One progress call
     * per show, and a summary call the first time a show is seen, for its
     * pictures and synopsis; shows the account has finished drop out.
     */
    private suspend fun refreshNextUp(profile: String, tokens: TraktTokens, shows: List<TraktWatchedShow>) {
        val known = withContext(Dispatchers.IO) { store.titles(profile, TraktAccountStore.NEXT_UP)?.items.orEmpty() }.associateBy { it.ids.trakt }
        val items = mutableListOf<TraktHomeTitle>()
        for (show in shows.sortedByDescending { it.lastWatchedAtMillis }.take(NEXT_UP_SHOWS)) {
            val id = show.ids.trakt?.toString() ?: continue
            val progress = api.showProgress(tokens, id) ?: continue
            val season = progress.nextSeason ?: continue
            val number = progress.nextNumber ?: continue
            val summary = known[show.ids.trakt]?.takeIf { it.poster != null }
                ?: api.showSummary(tokens, id)?.let { TraktHomeTitle(it.kind, it.ids, it.title, it.year, it.overview, it.poster, it.fanart) }
            items += TraktHomeTitle(
                kind = "show", ids = show.ids, title = summary?.title ?: show.title ?: continue, year = summary?.year ?: show.year,
                overview = summary?.overview, poster = summary?.poster, fanart = summary?.fanart,
                season = season, number = number, episodeTitle = progress.nextTitle,
                updatedAtMillis = maxOf(progress.lastWatchedAtMillis, show.lastWatchedAtMillis),
            )
        }
        withContext(Dispatchers.IO) { publishRows(profile, TraktAccountStore.NEXT_UP, items.sortedByDescending { it.updatedAtMillis }) }
        DiagnosticsLog.i(TAG, "next up: ${items.size} shows")
    }

    /** Trakt's recommendations, refreshed twice a day; movies and shows interleaved. */
    private suspend fun refreshRecommendations(profile: String) {
        if (!configured || currentAccount(profile) == null) return
        val cached = withContext(Dispatchers.IO) { store.titles(profile, TraktAccountStore.RECOMMENDATIONS) }
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMillis < RECOMMENDATIONS_TTL_MILLIS) return
        val tokens = tokens(profile) ?: return
        val movies = api.recommendations(tokens, "movies", RECOMMENDATIONS_PER_KIND)
        val shows = api.recommendations(tokens, "shows", RECOMMENDATIONS_PER_KIND)
        val items = mutableListOf<TraktHomeTitle>()
        for (index in 0 until maxOf(movies.size, shows.size)) {
            movies.getOrNull(index)?.let { items += TraktHomeTitle(it.kind, it.ids, it.title, it.year, it.overview, it.poster, it.fanart) }
            shows.getOrNull(index)?.let { items += TraktHomeTitle(it.kind, it.ids, it.title, it.year, it.overview, it.poster, it.fanart) }
        }
        withContext(Dispatchers.IO) { publishRows(profile, TraktAccountStore.RECOMMENDATIONS, items) }
        DiagnosticsLog.i(TAG, "recommendations: ${items.size} titles")
    }

    /**
     * Trakt state keyed the way Discover addresses titles: `movie:<media id>` and
     * `series:<media id>:<season>:<episode>`, where the media id is the addon's
     * `tt…` or `tmdb:…` key. Both id forms are present when Trakt gave both.
     */
    /** The Discover key for an item we address Trakt by, matching [observeDiscoverState]. */
    fun discoverKey(item: TraktItem): String? {
        val id = item.ids.imdb ?: item.ids.tmdb?.let { "tmdb:$it" } ?: return null
        return when (item) {
            is TraktItem.Movie -> "movie:$id"
            is TraktItem.Episode -> "series:$id:${item.season}:${item.number}"
        }
    }

    suspend fun discoverState(profile: String, key: String): TraktTitleState? = observeDiscoverState(profile).first()[key]

    fun observeDiscoverState(profile: String): Flow<Map<String, TraktTitleState>> = state.observe(profile).map { rows ->
        val map = HashMap<String, TraktTitleState>(rows.size * 2)
        for (row in rows) {
            val inProgress = row.progress > 0.0 && row.progress < 100.0
            val value = TraktTitleState(if (inProgress) (row.progress / 100.0).toFloat() else null, row.watched && !inProgress, row.updatedAtMillis)
            for (id in listOfNotNull(row.imdb, row.tmdb?.let { "tmdb:$it" })) {
                map[if (row.kind == "movie") "movie:$id" else "series:$id:${row.season}:${row.number}"] = value
            }
        }
        map
    }

    /** Delivers in order; stops at the first outage and returns what is still owed. */
    private suspend fun deliver(profile: String, pending: List<TraktPendingScrobble>): List<TraktPendingScrobble> {
        // A newer report for the same title supersedes an older one; only the last position matters.
        val collapsed = pending.reversed().distinctBy { it.item.toString() }.reversed()
        val tokens = try { tokens(profile) } catch (_: TraktException) { return collapsed } ?: return emptyList()
        collapsed.forEachIndexed { index, entry ->
            try {
                val result = api.scrobble(tokens, entry.item, entry.action, entry.progress)
                DiagnosticsLog.i(TAG, "${entry.action.name.lowercase()} ${entry.item} at ${"%.1f".format(entry.progress)}%: ${result.action} (${result.recorded ?: "no title echoed"})")
                if (entry.action == TraktScrobbleAction.STOP) syncRequests.tryEmit(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: TraktException) {
                DiagnosticsLog.w(TAG, "${entry.action.name.lowercase()} ${entry.item} failed: ${error.failure}")
                return when (error.failure) {
                    TraktFailure.NETWORK, TraktFailure.SERVICE, TraktFailure.RATE_LIMITED -> collapsed.drop(index)
                    TraktFailure.REAUTHORIZE -> {
                        withContext(Dispatchers.IO) { store.markReauthorization(profile) }
                        publish(profile)
                        collapsed.drop(index)
                    }
                    // Trakt rejected this item for good (unknown ids, bad request): drop it, keep the rest.
                    else -> collapsed.drop(index + 1)
                }
            }
        }
        return emptyList()
    }

    private suspend fun publish(profile: String): TraktAccount? {
        val account = withContext(Dispatchers.IO) { store.account(profile) }
        accounts.value = accounts.value.toMutableMap().apply { if (account == null) remove(profile) else put(profile, account) }
        return account
    }

    private companion object {
        const val TAG = "Trakt"
        const val REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
        const val REFRESH_MARGIN_MILLIS = 24 * 60 * 60 * 1000L
        const val SYNC_INTERVAL_MILLIS = 15 * 60 * 1000L
        const val SYNC_SETTLE_MILLIS = 3_000L
        const val NEXT_UP_SHOWS = 8
        const val RECOMMENDATIONS_PER_KIND = 10
        const val RECOMMENDATIONS_TTL_MILLIS = 12 * 60 * 60 * 1000L
    }
}
