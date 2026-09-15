package com.streammate.tv.feature.home

import com.sohva.tv.addons.AddonWatchProgress
import com.sohva.tv.trakt.TraktItem
import com.streammate.tv.trakt.TraktAddonIdentity
import com.streammate.tv.iptv.repository.ContinueWatchingItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

/**
 * One part-watched title on Home, whichever ledger holds it: the VOD library
 * (local or via Trakt) or Discover's own history.
 */
sealed interface HomeResumeEntry {
    val key: String
    val title: String
    val subtitle: String?
    val posterUrl: String?
    val backdropUrl: String?
    val fraction: Float
    val remainingMillis: Long?
    val updatedAtMillis: Long
    /** One card per series: the newest episode stands for the rest. */
    val groupKey: String

    data class Vod(val item: ContinueWatchingItem) : HomeResumeEntry {
        override val key get() = "vod:" + item.contentKey
        override val title get() = item.title
        override val subtitle get() = item.subtitle
        override val posterUrl get() = item.posterUrl
        override val backdropUrl get() = item.posterUrl
        override val fraction get() = item.progress.fraction
        override val remainingMillis get() = (item.progress.durationMillis - item.progress.positionMillis).takeIf { item.progress.durationMillis > 0L && it > 0L }
        override val updatedAtMillis get() = item.progress.lastWatchedEpochMillis
        override val groupKey get() = "vod:" + item.groupKey
    }

    data class Discover(val progress: AddonWatchProgress) : HomeResumeEntry {
        override val key get() = "discover:" + progress.identity.metadataInstallationId + ":" + progress.identity.media.id + ":" + progress.identity.video.id
        override val title get() = progress.artwork?.name ?: progress.title
        override val subtitle get() = progress.title.takeIf { progress.artwork != null && it != progress.artwork?.name }
        override val posterUrl get() = progress.artwork?.poster
        override val backdropUrl get() = progress.artwork?.background ?: progress.artwork?.poster
        override val fraction get() = if (progress.durationMillis > 0L) (progress.positionMillis.toFloat() / progress.durationMillis).coerceIn(0f, 1f) else 0f
        override val remainingMillis get() = (progress.durationMillis - progress.positionMillis).takeIf { progress.durationMillis > 0L && it > 0L }
        override val updatedAtMillis get() = progress.updatedAtMillis
        override val groupKey get() = "discover:" + progress.identity.metadataInstallationId + ":" + progress.identity.media.type + ":" + progress.identity.media.id
    }
}

internal data class HomeResumeProfile(val id: String, val discoverAllowed: Boolean)

enum class HomeResumeStatus { LOADING, EMPTY, READY, FAILED }

data class HomeResumeSnapshot(
    val profileId: String,
    val entries: List<HomeResumeEntry> = emptyList(),
    val status: HomeResumeStatus = HomeResumeStatus.LOADING,
    val discoverAllowed: Boolean = false,
) {
    val settled: Boolean get() = status != HomeResumeStatus.LOADING
}

/** A retained projection of existing progress, never a second history store. */
internal class HomeResumeStore(
    scope: CoroutineScope,
    profiles: Flow<HomeResumeProfile>,
    private val vod: (String) -> Flow<List<ContinueWatchingItem>>,
    private val discover: (String) -> Flow<List<AddonWatchProgress>>,
    private val onInitialRead: (Long) -> Unit = {},
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val refresh = MutableStateFlow(0)
    private val mutableState = MutableStateFlow(HomeResumeSnapshot(""))
    val state: StateFlow<HomeResumeSnapshot> = mutableState.asStateFlow()

    init {
        scope.launch {
            combine(profiles.distinctUntilChanged(), refresh) { profile, _ -> profile }.collectLatest { profile ->
                mutableState.value = HomeResumeSnapshot(profile.id, discoverAllowed = profile.discoverAllowed)
                val started = clock()
                var initial = true
                combine(
                    observeSource { vod(profile.id) },
                    observeSource { if (profile.discoverAllowed) discover(profile.id) else flowOf(emptyList()) },
                ) { local, addon ->
                    val entries = mergeHomeResume(local.getOrDefault(emptyList()), addon.getOrDefault(emptyList()))
                    HomeResumeSnapshot(profile.id, entries, when {
                        local.isFailure || addon.isFailure -> HomeResumeStatus.FAILED
                        entries.isEmpty() -> HomeResumeStatus.EMPTY
                        else -> HomeResumeStatus.READY
                    }, profile.discoverAllowed)
                }.collect { snapshot ->
                    mutableState.value = snapshot
                    if (initial) { onInitialRead(clock() - started); initial = false }
                }
            }
        }
    }

    fun retry() { refresh.update { it + 1 } }
    suspend fun awaitInitial() = state.first { it.settled }
    suspend fun awaitInitial(profileId: String) = state.first { it.profileId == profileId && it.settled }

    // A stuck local read gets an explicit unavailable state. If it recovers,
    // the same subscription still supplies the result; no polling or network dependency.
    private fun <T> observeSource(read: () -> Flow<List<T>>): Flow<Result<List<T>>> = channelFlow {
        val timeout = launch { delay(5_000); send(Result.failure(IllegalStateException("Home history unavailable"))) }
        try {
            read().collect { timeout.cancel(); send(Result.success(it)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            timeout.cancel()
            send(Result.failure(IllegalStateException("Home history unavailable")))
        }
    }
}

internal fun mergeHomeResume(vod: List<ContinueWatchingItem>, discover: List<AddonWatchProgress>): List<HomeResumeEntry> {
    val entries = vod.map { HomeResumeEntry.Vod(it) } +
        discover.filter { !it.completed && it.resumePositionMillis > 0L }.map { HomeResumeEntry.Discover(it) }
    val groups = mutableListOf<ResumeGroup>()
    for (entry in entries) {
        val aliases = entry.movieAliases() + entry.groupKey
        val matches = groups.filter { group -> group.aliases.any { it in aliases } }
        if (matches.isEmpty()) groups += ResumeGroup(aliases.toMutableSet(), mutableListOf(entry))
        else {
            // Trakt can bridge a Discover IMDb ID and a VOD TMDB ID. Merge all
            // matching groups, including when that bridge arrives after both cards.
            val target = matches.first()
            target.aliases += aliases
            target.entries += entry
            matches.drop(1).forEach { other ->
                target.aliases += other.aliases
                target.entries += other.entries
                groups.remove(other)
            }
        }
    }
    return groups.map { group ->
        // A newly imported provider copy is a fallback, not a new viewing event.
        // Keep the last source actually watched, even if Trakt's mirror is newer.
        val watched = group.entries.filter { it.isMovie() && it.localWatchedAtMillis() != null }
        if (watched.isNotEmpty()) watched.sortedWith(
            compareByDescending<HomeResumeEntry> { it.localWatchedAtMillis() }.thenBy { it.key },
        ).first()
        else group.entries.sortedWith(compareByDescending<HomeResumeEntry> { it.updatedAtMillis }.thenBy { it.key }).first()
    }.sortedByDescending { it.updatedAtMillis }.take(12)
}

private data class ResumeGroup(val aliases: MutableSet<String>, val entries: MutableList<HomeResumeEntry>)

private fun HomeResumeEntry.isMovie(): Boolean = when (this) {
    is HomeResumeEntry.Vod -> item.contentKey.startsWith("vod:movie:")
    is HomeResumeEntry.Discover -> progress.identity.media.type == "movie"
}

private fun HomeResumeEntry.localWatchedAtMillis(): Long? = when (this) {
    is HomeResumeEntry.Vod -> item.localWatchedAtMillis
    is HomeResumeEntry.Discover -> progress.updatedAtMillis
}

private fun HomeResumeEntry.movieAliases(): Set<String> {
    if (!isMovie()) return emptySet()
    val (tmdb, imdb) = when (this) {
        is HomeResumeEntry.Vod -> item.tmdbId to item.imdbId
        is HomeResumeEntry.Discover -> {
            val identity = TraktAddonIdentity.resolve(progress.identity) as? TraktItem.Movie
            identity?.ids?.tmdb to identity?.ids?.imdb
        }
    }
    return buildSet {
        tmdb?.takeIf { it > 0 }?.let { add("movie:tmdb:$it") }
        imdb?.takeIf { it.matches(Regex("tt\\d{5,10}")) }?.let { add("movie:imdb:$it") }
    }
}

/** Keep structure above the viewer stable; refresh values for cards already present. */
internal fun <T, K> retainHomeOrder(previous: List<T>, latest: List<T>, locked: Boolean, key: (T) -> K): List<T> {
    if (!locked) return latest
    val updates = latest.associateBy(key)
    return previous.map { updates[key(it)] ?: it }
}
