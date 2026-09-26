package com.sohva.tv.feature.discover.ui.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.data.EpisodeOrder
import com.sohva.tv.feature.discover.data.ProviderSources
import com.sohva.tv.feature.discover.data.ProviderState
import com.sohva.tv.feature.discover.play.AddonPlayback
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.AddonStream
import com.sohva.tv.feature.discover.protocol.AddonVideo
import com.sohva.tv.feature.discover.protocol.MetaDetails
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.protocol.StreamKind
import com.sohva.tv.feature.discover.store.Artwork
import com.sohva.tv.feature.discover.store.Installation
import com.sohva.tv.feature.discover.store.LibraryTitle
import com.sohva.tv.feature.discover.store.WatchEntry
import com.sohva.tv.feature.discover.store.WatchIdentity
import com.sohva.tv.feature.discover.ui.TitleRequest
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which page a title shows (FR-75, -77). */
sealed interface TitlePage {
    data object Movie : TitlePage

    data object Series : TitlePage

    data class Episode(val video: AddonVideo) : TitlePage
}

/** What the primary action asked for, carried out once every provider has answered (FR-82). */
enum class PendingStart { CONTINUE, BEGINNING, AUTOPLAY }

data class TitleState(
    val preview: MetaPreview,
    val details: MetaDetails? = null,
    val loading: Boolean = true,
    val failure: AddonFailure? = null,
    /** Access or the addon went away: the page is usable only for Back (FR-74). */
    val revoked: Boolean = false,
    val page: TitlePage = TitlePage.Movie,
    val season: Int? = null,
    val sources: List<ProviderSources>? = null,
    val noProviders: Boolean = false,
    val scraper: String? = null,
    val progress: WatchEntry? = null,
    val inLibrary: Boolean? = null,
    val libraryFailed: Boolean = false,
    val libraryFull: Boolean = false,
    val pending: PendingStart? = null,
    val noPlayable: Boolean = false,
) {
    val sourcesLoading: Boolean get() = sources?.any { it.state is ProviderState.Loading } == true

    val shown: List<ProviderSources> get() = sources.orEmpty().filter { scraper == null || it.installation.id == scraper }

    /** Seasons ascending, numbered first, then no-season entries, Specials (0) last (FR-76). */
    val seasons: List<Int?>
        get() {
            val all = details?.videos.orEmpty().map { it.season }.distinct()
            return all.filterNotNull().filter { it > 0 }.sorted() + (if (null in all) listOf<Int?>(null) else emptyList()) + (if (0 in all) listOf<Int?>(0) else emptyList())
        }

    val episodes: List<AddonVideo> get() = details?.videos.orEmpty().filter { it.season == season }
}

/**
 * A Discover title (spec 50 §4.10, §4.11): the catalog's preview first, details through the route
 * on entry, then the movie, series or episode page; sources only on playable pages, only once
 * details have settled, and again every time the page is shown after playback.
 */
class TitleModel(private val host: DiscoverHost, private val profile: String, private val request: TitleRequest, autoplay: Boolean) : ViewModel() {
    private val _state = MutableStateFlow(TitleState(request.preview))
    val state: StateFlow<TitleState> = _state.asStateFlow()
    private var sourcesJob: Job? = null

    /** Set when a page was opened from the player's end: its first playable source starts from 0 (FR-93). */
    private var autoplay = autoplay

    init {
        loadDetails(refresh = false)
    }

    /** "Retry details" / "Retry episode details" (FR-74): past the fresh cache. */
    fun retry() = loadDetails(refresh = true)

    private fun loadDetails(refresh: Boolean) {
        _state.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            val details = try {
                host.browser.details(profile, request.owner, request.preview.type, request.preview.id, refresh).value
            } catch (e: AddonException) {
                _state.update { it.copy(loading = false, failure = e.failure, revoked = e.failure.revocation) }
                if (e.failure.revocation) return@launch
                null
            }
            _state.update { s ->
                val preview = details?.preview?.let { d -> d.copy(id = request.preview.id) } ?: s.preview
                val single = preview.singleVideo
                // Opened on a saved episode: that episode's page directly (FR-75).
                val saved = request.videoId?.takeIf { !single }?.let { id -> details?.videos?.firstOrNull { it.id == id } ?: AddonVideo(id, preview.name, null, null, null, null) }
                val page = when {
                    single -> TitlePage.Movie
                    saved != null -> TitlePage.Episode(saved)
                    else -> TitlePage.Series
                }
                val season = saved?.season ?: s.season ?: TitleState(preview, details).seasons.firstOrNull()
                s.copy(preview = preview, details = details ?: s.details, loading = false, page = page, season = season)
            }
            afterPage()
        }
        loadLibrary()
    }

    /** The video key for sources (FR-78): from the loaded details; progress keeps the catalog's key. */
    private fun videoId(): String? = when (val page = _state.value.page) {
        TitlePage.Movie -> _state.value.preview.videoId ?: request.preview.id.takeIf { request.preview.type == "movie" }
        TitlePage.Series -> null
        is TitlePage.Episode -> page.video.id
    }

    private fun identity(video: String): WatchIdentity = WatchIdentity(request.owner.orEmpty(), request.preview.type, request.preview.id, request.preview.type, video)

    private fun afterPage() {
        val video = videoId() ?: return
        viewModelScope.launch {
            val saved = runCatching { withContext(host.dispatchers.io) { host.progress.get(profile, identity(video)) } }.getOrNull()
            _state.update { it.copy(progress = saved?.takeIf { e -> e.positionMs > 0 && !e.completed }) }
        }
        resolveSources()
        if (autoplay) {
            autoplay = false
            _state.update { it.copy(pending = PendingStart.AUTOPLAY) }
        }
    }

    /** The page is shown again after playback: signed URLs are never reused (FR-79). */
    fun shownAgain() {
        if (_state.value.loading || videoId() == null) return
        afterPage()
    }

    /** "Refresh" in the sources column: ignored while providers are still answering (FR-81). */
    fun refreshSources() {
        if (!_state.value.sourcesLoading) resolveSources()
    }

    private fun resolveSources() {
        val video = videoId() ?: return
        sourcesJob?.cancel()
        _state.update { it.copy(sources = null, noProviders = false, noPlayable = false) }
        sourcesJob = viewModelScope.launch {
            try {
                host.sources.streams(profile, request.preview.type, video).collect { list ->
                    _state.update { it.copy(sources = list, noProviders = list.isEmpty()) }
                    if (list.none { it.state is ProviderState.Loading }) startPending()
                }
            } catch (e: AddonException) {
                _state.update { it.copy(sources = emptyList(), noProviders = true, revoked = e.failure == AddonFailure.ACCESS_DENIED) }
            }
        }
    }

    fun chooseScraper(provider: String?) = _state.update { it.copy(scraper = provider) }

    // ---- Series and episodes (FR-76, -77) ----

    fun chooseSeason(season: Int?) = _state.update { it.copy(season = season) }

    fun openEpisode(video: AddonVideo) {
        _state.update { it.copy(page = TitlePage.Episode(video), season = video.season, sources = null, progress = null, pending = null, noPlayable = false) }
        afterPage()
    }

    /** Back on an episode page opened from the series page: the series page, that episode's season. False when Back should leave. */
    fun backToSeries(): Boolean {
        val page = _state.value.page as? TitlePage.Episode ?: return false
        if (request.videoId != null && request.videoId == page.video.id && _state.value.details == null) return false
        sourcesJob?.cancel()
        _state.update { it.copy(page = TitlePage.Series, season = page.video.season, sources = null, pending = null) }
        return true
    }

    // ---- Starting playback (FR-82, -83) ----

    /** Continue watching / Start from beginning: after every provider answered, the first playable source in priority order. */
    fun start(fromBeginning: Boolean) {
        _state.update { it.copy(pending = if (fromBeginning) PendingStart.BEGINNING else PendingStart.CONTINUE, noPlayable = false) }
        if (_state.value.sources != null && !_state.value.sourcesLoading) startPending()
    }

    private fun startPending() {
        val s = _state.value
        val pending = s.pending ?: return
        val first = s.shown.firstNotNullOfOrNull { p -> (p.state as? ProviderState.Ready)?.streams?.firstOrNull { it.kind == StreamKind.HTTP }?.let { p.installation to it } }
        // A request is carried out once; returning from playback does not start again.
        _state.update { it.copy(pending = null, noPlayable = first == null) }
        first ?: return
        play(first.first, first.second, resume = pending == PendingStart.CONTINUE)
    }

    private val _started = MutableStateFlow<String?>(null)

    /** The token of a playback to open (the screen hands it to the app, then [consumed]). */
    val started: StateFlow<String?> = _started.asStateFlow()

    fun consumed() {
        _started.value = null
    }

    /** A source card (FR-83): with resume (the local position); Start from beginning plays from 0. */
    fun play(source: Installation, stream: AddonStream, resume: Boolean = true) {
        if (stream.kind != StreamKind.HTTP || stream.url == null) return
        val video = videoId() ?: return
        val s = _state.value
        val page = s.page
        val preview = s.preview
        val title = when (page) {
            is TitlePage.Episode -> listOfNotNull(
                preview.name,
                listOfNotNull(page.video.season?.let { "S$it" }, page.video.episode?.let { "E$it" }).joinToString(" · ").ifEmpty { null },
                page.video.title,
            ).joinToString(" · ")
            else -> preview.name
        }
        val next = (page as? TitlePage.Episode)?.let { ep -> s.details?.videos?.let { EpisodeOrder.next(it, ep.video.id) } }
            ?.let { TitleRequest(request.owner, request.preview, it.id) }
        val playback = AddonPlayback(
            token = UUID.randomUUID().toString(), profile = profile, source = source, stream = stream, identity = identity(video),
            videoType = request.preview.type, title = title,
            savedTitle = (page as? TitlePage.Episode)?.video?.title?.takeIf { it.isNotBlank() } ?: preview.name,
            artwork = Artwork(preview.name, preview.poster, preview.background), logo = preview.logo,
            startMs = if (resume) s.progress?.resumeMs ?: 0 else 0, traktFraction = null, next = next, session = host.nextSession(),
        )
        host.keepPlayback(playback)
        _started.value = playback.token
    }

    // ---- Library (FR-110) ----

    private fun loadLibrary() {
        val type = request.preview.type
        if (type != "movie" && type != "series") return
        viewModelScope.launch {
            try {
                val saved = withContext(host.dispatchers.io) { host.library.contains(profile, request.owner.orEmpty(), type, request.preview.id) }
                _state.update { it.copy(inLibrary = saved, libraryFailed = false) }
            } catch (e: Exception) {
                _state.update { it.copy(libraryFailed = true) }
            }
        }
    }

    fun toggleLibrary() {
        val s = _state.value
        val saved = s.inLibrary ?: return loadLibrary()
        viewModelScope.launch {
            val owner = request.owner.orEmpty()
            val type = request.preview.type
            try {
                val full = withContext(host.dispatchers.io) {
                    if (saved) {
                        host.library.remove(profile, owner, type, request.preview.id)
                        false
                    } else {
                        val p = s.preview
                        !host.library.add(profile, LibraryTitle(owner, type, request.preview.id, p.name, p.poster, p.background, p.releaseInfo, 0))
                    }
                }
                _state.update { it.copy(inLibrary = if (full) false else !saved, libraryFull = full) }
            } catch (e: Exception) {
                _state.update { it.copy(libraryFailed = true) }
            }
        }
    }
}
