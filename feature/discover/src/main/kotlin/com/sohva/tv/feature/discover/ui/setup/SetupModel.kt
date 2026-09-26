package com.sohva.tv.feature.discover.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.model.player.VodLanguages
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.store.CatalogEntry
import com.sohva.tv.feature.discover.store.Installation
import com.sohva.tv.feature.discover.store.WatchEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Addons & setup's pages (§5.7, §5.8); the left column's sections and the nested pages. */
sealed interface SetupPage {
    data object Installed : SetupPage

    data object Add : SetupPage

    data object Subtitles : SetupPage

    data object Catalogs : SetupPage

    data object History : SetupPage

    data object Import : SetupPage

    /** One addon's catalogs, hidden and required-choice ones included (FR-33). */
    data class AddonCatalogs(val installation: String) : SetupPage
}

data class SetupState(
    val installed: List<Installation>? = null,
    val busy: Boolean = false,
    val failure: AddonFailure? = null,
    val history: List<WatchEntry>? = null,
    val languages: VodLanguages = VodLanguages(),
)

/**
 * Addons & setup (spec 50 §4.6, §4.8, §4.14, §5.7): one management action at a time (a second
 * press while busy is ignored), then the list reloads and the landing is told (FR-33, -57).
 */
class SetupModel(private val host: DiscoverHost, private val profile: String) : ViewModel() {
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()
    // The preference file is first opened on io, never by the screen (AGENTS.md §4 rule 1).
    private val _allLanguages = MutableStateFlow(false)
    val allLanguages: StateFlow<Boolean> = _allLanguages.asStateFlow()

    private val _page = MutableStateFlow<SetupPage>(SetupPage.Installed)
    val page: StateFlow<SetupPage> = _page.asStateFlow()

    /** The section the nested page came from, focused again on Back (§3 table). */
    var returnTo: SetupPage = SetupPage.Installed

    init {
        reload()
        viewModelScope.launch {
            val languages = withContext(host.dispatchers.io) { host.playback().vodLanguages }
            _state.update { it.copy(languages = languages) }
        }
        viewModelScope.launch {
            withContext(host.dispatchers.io) { host.settings }.allLanguages.collect { _allLanguages.value = it }
        }
    }

    fun show(page: SetupPage) {
        if (page is SetupPage.Catalogs || page is SetupPage.History || page is SetupPage.Import || page is SetupPage.AddonCatalogs) {
            if (_page.value !is SetupPage.AddonCatalogs) returnTo = _page.value.takeIf { it is SetupPage.Installed || it is SetupPage.Add || it is SetupPage.Subtitles } ?: returnTo
        }
        _page.value = page
        if (page == SetupPage.History) loadHistory()
    }

    /** Back inside Addons & setup: nested pages return to the section; false when Back should leave. */
    fun back(): Boolean {
        val page = _page.value
        return when (page) {
            is SetupPage.AddonCatalogs -> {
                _page.value = SetupPage.Installed
                true
            }
            SetupPage.Catalogs, SetupPage.History, SetupPage.Import -> {
                _page.value = returnTo
                true
            }
            else -> false
        }
    }

    /** "Reload saved addons" (FR-33): the stored list again, no network. */
    fun reload() {
        viewModelScope.launch {
            try {
                val list = withContext(host.dispatchers.io) { host.manager.list(profile) }
                _state.update { it.copy(installed = list) }
            } catch (e: AddonException) {
                _state.update { it.copy(failure = e.failure, installed = emptyList()) }
            }
        }
    }

    fun install(url: String) = act { host.manager.install(profile, url) }

    fun refresh(id: String) = act { host.manager.refresh(profile, id) }

    fun setEnabled(id: String, enabled: Boolean) = act { host.manager.setEnabled(profile, id, enabled) }

    fun raise(id: String) = act { host.manager.raise(profile, id) }

    fun remove(id: String) = act { host.manager.remove(profile, id) }

    fun setAllLanguages(on: Boolean) {
        viewModelScope.launch { withContext(host.dispatchers.io) { host.settings.setAllLanguages(on) } }
    }

    private fun act(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            val failure = try {
                withContext(host.dispatchers.io) { action() }
                null
            } catch (e: AddonException) {
                e.failure
            }
            val list = runCatching { withContext(host.dispatchers.io) { host.manager.list(profile) } }.getOrNull()
            _state.update { it.copy(busy = false, failure = failure, installed = list ?: it.installed) }
            host.noteSetupChanged()
        }
    }

    // ---- Watch history (FR-108) ----

    fun loadHistory() {
        viewModelScope.launch {
            val entries = runCatching {
                withContext(host.dispatchers.io) {
                    val enabled = host.installations.list(profile).filter { it.enabled }.map { it.id }.toSet()
                    host.progress.recent(profile).filter { it.identity.installation in enabled }
                }
            }.getOrDefault(emptyList())
            _state.update { it.copy(history = entries) }
        }
    }

    fun forget(entry: WatchEntry) {
        viewModelScope.launch {
            withContext(host.dispatchers.io) { host.progress.forget(profile, entry.identity) }
            loadHistory()
        }
    }

    // ---- One addon's catalogs (FR-33) ----

    suspend fun catalogsOf(installation: String): List<CatalogEntry> = withContext(host.dispatchers.io) {
        host.catalogs.ordered(profile, host.manager.list(profile)).filter { it.installation.id == installation }
    }
}
