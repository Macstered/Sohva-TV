package com.sohva.tv.feature.discover

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.discover.cache.ResponseCache
import com.sohva.tv.feature.discover.data.AddonBrowser
import com.sohva.tv.feature.discover.data.AddonManager
import com.sohva.tv.feature.discover.data.AddonSourcesResolver
import com.sohva.tv.feature.discover.play.AddonPlayback
import com.sohva.tv.feature.discover.net.AddonClient
import com.sohva.tv.feature.discover.store.CatalogPrefs
import com.sohva.tv.feature.discover.store.DiscoverAccess
import com.sohva.tv.feature.discover.store.DiscoverCipher
import com.sohva.tv.feature.discover.store.DiscoverDatabase
import com.sohva.tv.feature.discover.store.InstallationStore
import com.sohva.tv.feature.discover.store.LibraryStore
import com.sohva.tv.feature.discover.store.ProgressStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

/** The dispatchers Discover uses: [io] for storage and network, [ui] for list shaping, never the main thread. */
class DiscoverDispatchers(val io: CoroutineDispatcher, val ui: CoroutineDispatcher)

/**
 * Discover's pieces for the process (spec 50 §6 "In memory"). The app builds one only when the
 * build allows addons, and every part below is built on first use: nothing opens, connects or
 * schedules until the viewer opens Discover, except [progress], which Home reads (§9 "Start-up").
 */
class DiscoverHost(
    context: Context,
    private val cipher: DiscoverCipher,
    private val http: () -> OkHttpClient,
    val clock: Clock,
    val dispatchers: DiscoverDispatchers,
    val access: DiscoverAccess,
    val log: DiagnosticsLog,
    /** An app-lifetime scope: progress writes outlive the screens that start them (FR-106). */
    val appScope: CoroutineScope,
    /** The viewer's playback settings: VOD languages and subtitle look apply to addon playback too (FR-85, -104). */
    val playback: suspend () -> PlaybackSettings,
) {
    private val app = context.applicationContext

    /** Device tests' local addon servers speak plain HTTP; no screen sets this (decision "Spec 50 open questions" Q1). */
    @Volatile var testAllowHttp: Boolean = false

    private val database: DiscoverDatabase by lazy { DiscoverDatabase.open(app) }

    val installations: InstallationStore by lazy { InstallationStore(database.installations(), cipher, clock) }
    val catalogs: CatalogPrefs by lazy { CatalogPrefs(database.catalogs()) }
    val progress: ProgressStore by lazy { ProgressStore(database.progress(), cipher, clock) }
    val library: LibraryStore by lazy { LibraryStore(database.library(), cipher, clock) }
    val client: AddonClient by lazy { AddonClient(http()) }

    /** Subtitle files (FR-101): their own client, built on first choice. */
    val subtitleFiles: com.sohva.tv.feature.discover.net.SubtitleDownloader by lazy { com.sohva.tv.feature.discover.net.SubtitleDownloader(http()) }
    val cache: ResponseCache by lazy { ResponseCache(File(app.noBackupFilesDir, ResponseCache.DIR), cipher, clock) }
    val manager: AddonManager by lazy { AddonManager(access, installations, client, dispatchers.io) { testAllowHttp } }
    val browser: AddonBrowser by lazy { AddonBrowser(access, installations, client, cache, clock, dispatchers.io) }
    val settings: DiscoverSettings by lazy { DiscoverSettings(app) }
    val sources: AddonSourcesResolver by lazy { AddonSourcesResolver(access, installations, client, dispatchers.io) }

    private val playbacks = object : LinkedHashMap<String, AddonPlayback>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AddonPlayback>?): Boolean = size > MAX_PLAYBACKS
    }
    private val sessions = java.util.concurrent.atomic.AtomicLong(clock.wallMillis())

    /** A new progress session number, always larger than the last (FR-106). */
    fun nextSession(): Long = sessions.incrementAndGet()

    /** Keeps [playback] for the engine to resolve by its token; only the last few are kept. */
    fun keepPlayback(playback: AddonPlayback) = synchronized(playbacks) { playbacks[playback.token] = playback }

    fun playback(token: String): AddonPlayback? = synchronized(playbacks) { playbacks[token] }

    /** Profile ids whose installations, order or visibility changed; screens re-read on these (FR-57). */
    private val _setupChanged = MutableStateFlow(0L)
    val setupChanged: StateFlow<Long> = _setupChanged.asStateFlow()

    fun noteSetupChanged() {
        _setupChanged.value = _setupChanged.value + 1
    }

    /**
     * Title pages open from the catalog's preview (FR-74), but routes carry ids only: the last few
     * requests wait here for their page (bounded; a missing one just loads details).
     */
    private val titles = object : LinkedHashMap<String, com.sohva.tv.feature.discover.ui.TitleRequest>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.sohva.tv.feature.discover.ui.TitleRequest>?): Boolean = size > MAX_TITLES
    }

    fun keepTitle(request: com.sohva.tv.feature.discover.ui.TitleRequest) = synchronized(titles) {
        titles["${request.owner}|${request.preview.type}|${request.preview.id}"] = request
    }

    fun title(owner: String?, type: String, id: String): com.sohva.tv.feature.discover.ui.TitleRequest? = synchronized(titles) { titles["$owner|$type|$id"] }

    /** Removing a profile removes its Discover data (plan/04 §15.8). */
    suspend fun forgetProfile(profile: String) {
        installations.forget(profile)
        catalogs.forget(profile)
        progress.forgetProfile(profile)
        library.forgetProfile(profile)
        noteSetupChanged()
    }

    private companion object {
        const val MAX_TITLES = 16
        const val MAX_PLAYBACKS = 4
    }

    /** Tests start from nothing: every profile's rows, the cache and the preference. Not on the main thread. */
    suspend fun resetForTests() {
        database.clearAllTables()
        synchronized(titles) { titles.clear() }
        cache.clear()
        installations.invalidate()
        settings.setAllLanguages(false)
        testAllowHttp = false
        noteSetupChanged()
    }
}

/**
 * Discover's one global preference (spec 50 §6 "UI preferences"): "Show all languages" for
 * subtitle results, written synchronously. Catalog order and visibility live in `discover.db`.
 */
class DiscoverSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _allLanguages = MutableStateFlow(prefs.getBoolean(ALL_LANGUAGES, false))
    val allLanguages: StateFlow<Boolean> = _allLanguages.asStateFlow()

    // Written synchronously as spec 50 §6 asks; callers are on the io dispatcher, never the main thread.
    @SuppressLint("ApplySharedPref")
    fun setAllLanguages(on: Boolean) {
        prefs.edit(commit = true) { putBoolean(ALL_LANGUAGES, on) }
        _allLanguages.value = on
    }

    private companion object {
        const val FILE = "sohva_discover_ui"
        const val ALL_LANGUAGES = "all_subtitle_languages"
    }
}
