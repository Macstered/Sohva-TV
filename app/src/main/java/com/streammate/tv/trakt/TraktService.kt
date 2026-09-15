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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.streammate.tv.core.concurrent.parallelResults
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
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
    private val store by lazy { TraktAccountStore(context.applicationContext, cipher) }
    private val accounts = MutableStateFlow<Map<String, TraktAccount?>>(emptyMap())
    private val initialization = Mutex()
    @Volatile private var initialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!initialized) initialization.withLock {
            if (!initialized) {
                accounts.value = store.profiles().associateWith { store.account(it) }
                initialized = true
            }
        }
    }
    private val accountWrites = Mutex()
    private val reads = Semaphore(3)
    private suspend fun <T> readApi(fetch: suspend () -> T): T = reads.withPermit { fetch() }
    private val refreshes = Mutex()
    private val deliveries = Mutex()
    private val syncs = Mutex()
    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val homeTitles = MutableStateFlow<Map<String, List<TraktHomeTitle>>>(emptyMap())
    private val historyRevision = MutableStateFlow(0L)

    /** Distinguishes a newly connected account from a genuinely empty cached history. */
    fun initialHistoryPending(profile: String): Flow<Boolean> =
        kotlinx.coroutines.flow.combine(accounts, historyRevision) { connected, _ ->
            connected[profile] != null && withContext(Dispatchers.IO) { store.activityStamp(profile) == 0L }
        }

    /** Trakt's picks for the profile, as last fetched; empty until the first fetch. */
    fun recommendations(profile: String): Flow<List<TraktHomeTitle>> = homeRows(profile, TraktAccountStore.RECOMMENDATIONS)
    /** The next unwatched episode of each show the profile has been watching, newest first. */
    fun nextUp(profile: String): Flow<List<TraktHomeTitle>> = homeRows(profile, TraktAccountStore.NEXT_UP)

    private val titleReads = Mutex()

    private fun homeRows(profile: String, name: String): Flow<List<TraktHomeTitle>> = flow {
        initialize()
        val key = "$name:$profile"
        titleReads.withLock {
            if (homeTitles.value[key] == null) {
                val stored = store.titles(profile, name)?.items.orEmpty()
                homeTitles.update { it + (key to stored) }
            }
        }
        emitAll(homeTitles.map { it[key].orEmpty() })
    }.flowOn(Dispatchers.IO)

    private suspend fun publishRows(profile: String, name: String, items: List<TraktHomeTitle>, account: TraktAccount) = accountWrites.withLock {
        if (currentAccount(profile) !== account) return@withLock
        titleReads.withLock {
            store.saveTitles(profile, name, TraktAccountStore.Titles(System.currentTimeMillis(), items))
            homeTitles.update { it + ("$name:$profile" to items) }
        }
    }

    fun account(profile: String): Flow<TraktAccount?> = flow {
        initialize()
        emitAll(accounts.map { it[profile] })
    }
    fun currentAccount(profile: String): TraktAccount? = accounts.value[profile]

    /** Device pairing: shows the code through [onPrompt], then waits for approval. */
    suspend fun connect(profile: String, onPrompt: suspend (TraktPairingPrompt) -> Unit): TraktAccount {
        initialize()
        if (!configured) throw TraktException(TraktFailure.CONFIGURATION)
        val authorization = TraktDeviceAuthorizer(auth, identities).authorize(onPrompt)
        return withContext(Dispatchers.IO) { accountWrites.withLock {
            store.save(profile, authorization.tokens, authorization.identity.username)
            publish(profile)!!
        } }
    }

    /** Removes the local tokens and the cached Trakt state. The account's Trakt history is untouched. */
    suspend fun disconnect(profile: String) {
        initialize()
        withContext(Dispatchers.IO) { accountWrites.withLock {
            titleReads.withLock {
                store.remove(profile)
                state.deleteProfile(profile)
                homeTitles.update { it - "${TraktAccountStore.RECOMMENDATIONS}:$profile" - "${TraktAccountStore.NEXT_UP}:$profile" }
            }
            publish(profile)
        } }
    }

    /** Sends a scrobble now, or keeps it for the next attempt when Trakt cannot be reached. */
    suspend fun scrobble(profile: String, item: TraktItem, action: TraktScrobbleAction, progress: Double) {
        initialize()
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
        initialize()
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
        initialize()
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
    suspend fun runSync(profile: String): Unit = withContext(Dispatchers.Default) {
        initialize()
        if (offline) kotlinx.coroutines.awaitCancellation()
        runCatching { closeInterrupted(profile) }.onFailure { if (it is CancellationException) throw it }
        while (true) {
            // A first run has nothing cached for the rows, so it syncs even when Trakt reports nothing new.
            val firstRun = withContext(Dispatchers.IO) { store.titles(profile, TraktAccountStore.NEXT_UP) == null }
            supervisorScope {
                launch { runCatching { refreshRecommendations(profile) }.onFailure { if (it is CancellationException) throw it } }
                runCatching { sync(profile, force = firstRun) }.onFailure { if (it is CancellationException) throw it }
            }
            val requested = withTimeoutOrNull(SYNC_INTERVAL_MILLIS) { syncRequests.first() } != null
            if (requested) delay(SYNC_SETTLE_MILLIS)
        }
    }

    /** Refreshes the cached playback and watched lists when Trakt reports new activity. */
    suspend fun sync(profile: String, force: Boolean = false): Boolean = withContext(Dispatchers.Default) { syncs.withLock {
        initialize()
        if (!configured) return@withLock false
        val account = currentAccount(profile) ?: return@withLock false
        val tokens = tokens(profile) ?: return@withLock false
        val activities = readApi { api.lastActivities(tokens) }
        val stamp = withContext(Dispatchers.IO) { store.activityStamp(profile) }
        if (!force && stamp != 0L && activities.latest <= stamp) return@withLock false
        val (movies, shows, playback) = supervisorScope {
            val movies = async { readApi { api.watchedMovies(tokens) } }
            val shows = async { readApi { api.watchedShows(tokens) } }
            val playback = readApi { api.playback(tokens) }
            withContext(Dispatchers.IO) { accountWrites.withLock {
                if (currentAccount(profile) === account) state.replacePlayback(profile, playback.mapNotNull { pausedRow(profile, it) })
            } }
            Triple(movies.await(), shows.await(), playback)
        }
        val rows = linkedMapOf<String, TraktStateEntity>()
        fun key(kind: String, ids: TraktIds, season: Int? = null, number: Int? = null): String? {
            val id = ids.tmdb?.let { "tmdb:$it" } ?: ids.imdb?.let { "imdb:$it" } ?: return null
            return if (kind == "movie") "movie:$id" else "episode:$id:$season:$number"
        }
        for (movie in movies) {
            val key = key("movie", movie.ids) ?: continue
            rows[key] = TraktStateEntity(profile, key, "movie", movie.ids.tmdb, movie.ids.imdb, null, null, 0.0, true, movie.plays, movie.lastWatchedAtMillis)
        }
        for (show in shows) for (episode in show.episodes) {
            val key = key("episode", show.ids, episode.season, episode.number) ?: continue
            rows[key] = TraktStateEntity(profile, key, "episode", show.ids.tmdb, show.ids.imdb, episode.season, episode.number, 0.0, true, episode.plays, episode.lastWatchedAtMillis)
        }
        for (paused in playback) {
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
        val saved = withContext(Dispatchers.IO) { accountWrites.withLock {
            if (currentAccount(profile) !== account) false else {
                state.replace(profile, rows.values.toList())
                store.saveActivityStamp(profile, activities.latest)
                true
            }
        } }
        if (!saved) return@withLock false
        historyRevision.update { it + 1 }
        DiagnosticsLog.i(TAG, "synced ${rows.size} Trakt titles")
        runCatching { refreshNextUp(profile, account, tokens, shows) }.onFailure { if (it is CancellationException) throw it }
        true
    } }

    /**
     * The next episode for the shows watched most recently. One progress call
     * per show, and a summary call the first time a show is seen, for its
     * pictures and synopsis; shows the account has finished drop out.
     */
    private suspend fun refreshNextUp(profile: String, account: TraktAccount, tokens: TraktTokens, shows: List<TraktWatchedShow>) {
        val known = withContext(Dispatchers.IO) { store.titles(profile, TraktAccountStore.NEXT_UP)?.items.orEmpty() }.associateBy { it.ids.trakt }
        val selected = shows.sortedByDescending { it.lastWatchedAtMillis }.filter { it.ids.trakt != null }.take(NEXT_UP_SHOWS)
        val items = selected.mapNotNull { show -> known[show.ids.trakt]?.let { show.ids.trakt to it } }.toMap().toMutableMap()
        if (selected.isEmpty()) withContext(Dispatchers.IO) { publishRows(profile, TraktAccountStore.NEXT_UP, emptyList(), account) }
        selected.parallelResults { show ->
            val id = show.ids.trakt!!.toString()
            val progress = readApi { api.showProgress(tokens, id) } ?: return@parallelResults null
            val season = progress.nextSeason ?: return@parallelResults null
            val number = progress.nextNumber ?: return@parallelResults null
            val summary = known[show.ids.trakt]?.takeIf { it.poster != null }
                ?: readApi { api.showSummary(tokens, id) }?.let { TraktHomeTitle(it.kind, it.ids, it.title, it.year, it.overview, it.poster, it.fanart) }
            TraktHomeTitle(
                kind = "show", ids = show.ids, title = summary?.title ?: show.title ?: return@parallelResults null, year = summary?.year ?: show.year,
                overview = summary?.overview, poster = summary?.poster, fanart = summary?.fanart,
                season = season, number = number, episodeTitle = progress.nextTitle,
                updatedAtMillis = maxOf(progress.lastWatchedAtMillis, show.lastWatchedAtMillis),
            )
        }.collect { (show, result) ->
            if (result.isSuccess) {
                val title = result.getOrNull()
                if (title == null) items.remove(show.ids.trakt) else items[show.ids.trakt] = title
                withContext(Dispatchers.IO) { publishRows(profile, TraktAccountStore.NEXT_UP, items.values.sortedByDescending { it.updatedAtMillis }, account) }
            }
        }
        DiagnosticsLog.i(TAG, "next up: ${items.size} shows")
    }

    private fun pausedRow(profile: String, paused: TraktPlaybackItem): TraktStateEntity? {
        val item = paused.item
        val id = item.ids.tmdb?.let { "tmdb:$it" } ?: item.ids.imdb?.let { "imdb:$it" } ?: return null
        val episode = item as? TraktItem.Episode
        val kind = if (episode == null) "movie" else "episode"
        val key = if (episode == null) "movie:$id" else "episode:$id:${episode.season}:${episode.number}"
        return TraktStateEntity(profile, key, kind, item.ids.tmdb, item.ids.imdb, episode?.season, episode?.number,
            paused.progress, false, 0, paused.pausedAtMillis)
    }

    /** Trakt's recommendations, refreshed twice a day; movies and shows interleaved. */
    private suspend fun refreshRecommendations(profile: String) {
        if (!configured) return
        val account = currentAccount(profile) ?: return
        val cached = withContext(Dispatchers.IO) { store.titles(profile, TraktAccountStore.RECOMMENDATIONS) }
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMillis < RECOMMENDATIONS_TTL_MILLIS) return
        val tokens = tokens(profile) ?: return
        val (movies, shows) = supervisorScope {
            val movies = async { readApi { api.recommendations(tokens, "movies", RECOMMENDATIONS_PER_KIND) } }
            val shows = async { readApi { api.recommendations(tokens, "shows", RECOMMENDATIONS_PER_KIND) } }
            movies.await() to shows.await()
        }
        val items = mutableListOf<TraktHomeTitle>()
        for (index in 0 until maxOf(movies.size, shows.size)) {
            movies.getOrNull(index)?.let { items += TraktHomeTitle(it.kind, it.ids, it.title, it.year, it.overview, it.poster, it.fanart) }
            shows.getOrNull(index)?.let { items += TraktHomeTitle(it.kind, it.ids, it.title, it.year, it.overview, it.poster, it.fanart) }
        }
        withContext(Dispatchers.IO) { publishRows(profile, TraktAccountStore.RECOMMENDATIONS, items, account) }
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
    }.flowOn(Dispatchers.Default)

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
        accounts.update { it.toMutableMap().apply { if (account == null) remove(profile) else put(profile, account) } }
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
