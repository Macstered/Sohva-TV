package com.sohva.tv.feature.settings

import com.sohva.tv.core.model.settings.ArtworkCacheLimit
import androidx.compose.runtime.Immutable
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.vod.CustomGroup
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The stored metadata settings as Settings shows them (spec 41 §4.1). */
@Immutable
data class MetadataSettingsView(
    val tmdbSwitch: Boolean,
    val credential: String,
    val tvmaze: Boolean,
    val language: String,
    /** The worker stopped after TMDB refused the key (spec 41 Q8). */
    val keyRefused: Boolean,
)

/** What Settings › Library does to the world; the app implements it. */
interface LibrarySettingsServices {
    fun metadata(): Flow<MetadataSettingsView>

    /** The TMDB switch, saved at once with the typed key and the TVmaze switch (META-FR-02). */
    suspend fun setTmdb(on: Boolean, typed: String): Outcome<Unit>

    suspend fun setTvmaze(on: Boolean): Outcome<Unit>

    /** "Save key" (META-FR-04, -05). */
    suspend fun saveKey(typed: String): Outcome<Unit>

    /** "Test TMDB" with the typed key, never the saved one (META-FR-06). */
    suspend fun testTmdb(typed: String): Outcome<Unit>

    /** A new language resets the library's titles and restarts the worker (META-FR-79). */
    suspend fun setLanguage(tag: String)

    /** "Clear metadata cache" (META-FR-80). */
    suspend fun clearMetadata()

    fun preferredCopy(): Flow<PreferredCopy>

    /** "When a film has more than one version" (spec 40 VOD-FR-32); the standing copies follow in the background. */
    suspend fun setPreferredCopy(copy: PreferredCopy)

    /** The TMDB and TVmaze buttons (META-FR-11); a TV without a browser does nothing. */
    fun openWeb(url: String)

    /** "Manage groups & content": the library manager's Live room (spec 42 §3). */
    fun openManager()

    /** Groups of your own (spec 42 ORG-FR-60), in their saved order. */
    fun customGroups(): Flow<List<CustomGroup>>

    suspend fun saveCustomGroup(group: CustomGroup)

    suspend fun deleteCustomGroup(id: String)

    /** The genres the library's films and series have, in vocabulary order (ORG-FR-63). */
    suspend fun libraryGenres(): List<Genre>

    /** The image cache limit, applied from the next start (spec 70 SET-FR-80). */
    suspend fun artworkLimit(): ArtworkCacheLimit

    suspend fun setArtworkLimit(limit: ArtworkCacheLimit)

    /** The disk cache's own tracked size in bytes, off the main thread (SET-FR-81). */
    suspend fun artworkUsage(): Long

    /** Disk and memory image caches (SET-FR-82). */
    suspend fun clearArtwork()
}

/** Settings › Library's state (spec 41 §5.1): the typed key is the viewer's until they save it. */
@Immutable
data class LibrarySettingsState(
    val stored: MetadataSettingsView? = null,
    val typed: String = "",
    val preferredCopy: PreferredCopy = PreferredCopy.NONE,
    /** An action runs: every control of the group is disabled (META-FR-08). */
    val busy: Boolean = false,
    val status: SettingsMessage? = null,
    val statusIsError: Boolean = false,
    val artworkLimit: ArtworkCacheLimit = ArtworkCacheLimit.MEDIUM,
    /** Null until read; nothing is shown before (SET-FR-81). */
    val artworkUsage: Long? = null,
    val clearingArtwork: Boolean = false,
    /** Under the image cache group, next to its button (spec 70 SET-FR-31). */
    val artworkStatus: SettingsMessage? = null,
)

/**
 * Settings › Library for the life of the Settings screen, owned by [SettingsModel]: one action at
 * a time, each ending in the status line under the first group (META-FR-08).
 */
class LibrarySettings internal constructor(private val services: LibrarySettingsServices, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(LibrarySettingsState())
    val state: StateFlow<LibrarySettingsState> = _state.asStateFlow()

    private val _customGroups = MutableStateFlow<List<CustomGroup>>(emptyList())

    /** Groups of your own (spec 42 ORG-FR-61), in their saved order. */
    val customGroups: StateFlow<List<CustomGroup>> = _customGroups.asStateFlow()

    init {
        scope.launch {
            services.metadata().collect { view ->
                // The field starts from the saved key and follows a save; typing is kept until then.
                _state.update { s -> s.copy(stored = view, typed = if (s.stored == null || s.stored.credential != view.credential) view.credential else s.typed) }
            }
        }
        scope.launch { services.preferredCopy().collect { copy -> _state.update { it.copy(preferredCopy = copy) } } }
        scope.launch { services.customGroups().collect { _customGroups.value = it } }
        scope.launch {
            val limit = services.artworkLimit()
            val usage = services.artworkUsage()
            _state.update { it.copy(artworkLimit = limit, artworkUsage = usage) }
        }
    }

    /** Written at once, used from the next start (SET-FR-80). */
    fun setArtworkLimit(limit: ArtworkCacheLimit) {
        _state.update { it.copy(artworkLimit = limit) }
        scope.launch { services.setArtworkLimit(limit) }
    }

    /** Clears disk and memory, re-reads the usage, says so (SET-FR-82). */
    fun clearArtwork() {
        val s = _state.value
        if (s.clearingArtwork || (s.artworkUsage ?: 0L) <= 0L) return
        _state.update { it.copy(clearingArtwork = true) }
        scope.launch {
            try {
                services.clearArtwork()
                val usage = services.artworkUsage()
                _state.update { it.copy(artworkUsage = usage, artworkStatus = SettingsMessage.Text(R.string.artwork_cache_cleared)) }
            } finally {
                _state.update { it.copy(clearingArtwork = false) }
            }
        }
    }

    fun saveCustomGroup(group: CustomGroup) {
        scope.launch { services.saveCustomGroup(group) }
    }

    fun deleteCustomGroup(id: String) {
        scope.launch { services.deleteCustomGroup(id) }
    }

    /** The genres the editor offers: those present in the library (ORG-FR-63). */
    suspend fun libraryGenres(): List<Genre> = runCatching { services.libraryGenres() }.getOrDefault(emptyList())

    fun type(value: String) = _state.update { it.copy(typed = value.take(KEY_MAX)) }

    /** Turning TMDB on with an empty field only says what is missing (META-FR-02). */
    fun toggleTmdb() {
        val s = _state.value
        val on = !(s.stored?.tmdbSwitch ?: false)
        if (on && s.typed.isBlank()) return report(SettingsMessage.Text(R.string.metadata_key_required), error = true)
        act { services.setTmdb(on, s.typed).failureOr(null) }
    }

    fun toggleTvmaze() = act { services.setTvmaze(!(_state.value.stored?.tvmaze ?: false)).failureOr(null) }

    fun saveKey() = act { services.saveKey(_state.value.typed).failureOr(SettingsMessage.Text(R.string.metadata_saved)) }

    fun testTmdb() = act { services.testTmdb(_state.value.typed).failureOr(SettingsMessage.Text(R.string.metadata_tmdb_test_ok)) }

    fun setLanguage(tag: String) = act {
        services.setLanguage(tag)
        null
    }

    fun clearCache() = act {
        services.clearMetadata()
        SettingsMessage.Text(R.string.metadata_cache_cleared)
    }

    fun setPreferredCopy(copy: PreferredCopy) {
        scope.launch { services.setPreferredCopy(copy) }
    }

    fun openWeb(url: String) = services.openWeb(url)

    private var managerOpened = false

    fun openManager() {
        managerOpened = true
        services.openManager()
    }

    /** Whether the pane is shown again on the way back from the manager: its row takes focus (spec 42 §3). */
    internal fun takeManagerReturn(): Boolean = managerOpened.also { managerOpened = false }

    private fun act(block: suspend () -> Any?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        scope.launch {
            try {
                when (val result = block()) {
                    is AppError -> report(SettingsMessage.Failure(result), error = true)
                    is SettingsMessage -> report(result, error = false)
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun report(message: SettingsMessage, error: Boolean) = _state.update { it.copy(status = message, statusIsError = error) }

    /** The failure to show, else [ok] (null shows nothing new). */
    private fun Outcome<Unit>.failureOr(ok: SettingsMessage?): Any? = when (this) {
        is Outcome.Failed -> error
        is Outcome.Ok -> ok
    }

    private companion object {
        const val KEY_MAX = 2_048
    }
}
