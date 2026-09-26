package com.sohva.tv.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.source.ImportRoute
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.RefreshState
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.core.sync.SourceChecks
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One source on the Playlists list (spec 10 SRC-FR-08). */
@Immutable
data class SourceRow(val id: String, val name: String, val type: SourceType, val enabled: Boolean, val failure: AppError?)

/** Where focus goes next; [serial] makes a repeated request a new one. */
@Immutable
data class FocusCommand(val target: FocusTarget, val serial: Int)

sealed interface FocusTarget {
    /** The selected section's first control (spec 70 SET-FR-14). */
    data object SectionStart : FocusTarget

    /** A source's row on the list: after its page closes (SRC-FR-06). */
    data class SourceRowOf(val sourceId: String) : FocusTarget
}

@Immutable
data class SettingsState(
    val section: SettingsSection = SettingsSection.SOURCES,
    val accounts: Boolean = false,
    /** Null until the list has loaded once, so the first status line is not "Add your first source" by mistake. */
    val sources: List<SourceRow>? = null,
    val health: List<SourceHealth> = emptyList(),
    /** The open source page, or null on the list. */
    val page: SourceDraft? = null,
    val confirmingDelete: Boolean = false,
    /** The screen's own action is running: its buttons are disabled (SRC-FR-19). */
    val busy: Boolean = false,
    val messages: Map<SettingsSection, SettingsMessage> = emptyMap(),
    val refreshInterval: RefreshInterval = RefreshInterval.TWENTY_FOUR_HOURS,
    val remote: RemoteMapping = RemoteMapping.DEFAULTS,
    /** "Reminders can open Sohva TV": null when the build has no reminders (REM-FR-34). */
    val remindersCanOpen: Boolean? = null,
    val phone: PhoneSetupState = PhoneSetupState.Closed,
    /** The phone page's code, once built for its current address. */
    val qr: QrMatrix? = null,
    val focus: FocusCommand = FocusCommand(FocusTarget.SectionStart, 0),
)

/**
 * Settings' screen model (plan/03 §4.6): one immutable state, events in, work on the services'
 * own threads. M1 builds the frame, Playlists and General's refresh interval; the other sections
 * arrive with their milestones.
 */
class SettingsModel(private val services: SettingsServices, accounts: Boolean) : ViewModel() {
    private val state = MutableStateFlow(SettingsState(accounts = accounts))
    val ui: StateFlow<SettingsState> = state.asStateFlow()

    /** Settings › Library: its own state and status line (spec 41 §5.1). */
    val library: LibrarySettings = LibrarySettings(services.library, viewModelScope)

    /** Settings › General › Profiles and Parental controls (spec 04 §5.3). */
    val profiles: ProfileSettings = ProfileSettings(services.profiles, viewModelScope)

    /** Settings › General's own rows (spec 70 §4.5). */
    val general: GeneralSettings = GeneralSettings(services.general, viewModelScope)

    /** Settings › Playback (spec 70 §4.7). */
    val playback: PlaybackSettingsHolder = PlaybackSettingsHolder(services.playback, viewModelScope)

    /** The page as last loaded or saved, to tell an edit that needs a sync from a re-save. */
    private var lastSaved: SourceDraft? = null

    init {
        viewModelScope.launch {
            combine(services.sources(), services.health()) { sources, health ->
                val rows = sources.map { s ->
                    val failure = KIND_ORDER.firstNotNullOfOrNull { kind ->
                        health.firstOrNull { it.sourceId == s.id && it.kind == kind && it.state == RefreshState.FAILED }
                    }
                    SourceRow(s.id, s.name, s.type, s.enabled, failure?.let { it.error ?: AppError.Unknown })
                }
                rows to health
            }.collect { (rows, health) ->
                state.update { s ->
                    // The list draws nothing until it has loaded, so the first focus waits for it (SET-FR-02).
                    val firstLoad = s.sources == null && s.section == SettingsSection.SOURCES && s.page == null
                    s.copy(sources = rows, health = health, focus = if (firstLoad) s.focus.next(FocusTarget.SectionStart) else s.focus)
                }
            }
        }
        viewModelScope.launch { services.refreshInterval().collect { value -> state.update { it.copy(refreshInterval = value) } } }
        viewModelScope.launch { services.remoteMapping().collect { value -> state.update { it.copy(remote = value) } } }
        viewModelScope.launch { services.phoneSetup().collect(::onPhoneSetup) }
        viewModelScope.launch {
            // One plain sentence in Playlists when beta 23's sources could not be read.
            val problem = services.importProblem() ?: return@launch
            state.update { s -> if (SettingsSection.SOURCES in s.messages) s else s.withMessage(SettingsMessage.Failure(problem), SettingsSection.SOURCES) }
        }
    }

    fun openPhoneSetup() = services.openPhoneSetup()

    /** Read again whenever the screen resumes, so coming back from the TV settings updates it (REM-FR-34). */
    fun refreshReminderAccess() = state.update { it.copy(remindersCanOpen = services.remindersCanOpen()) }

    /** A TV without the screen says so in General's status line (spec 70 SET-FR-57, Q-08). */
    fun openOverlaySettings() {
        if (!services.openOverlaySettings()) {
            state.update { it.withMessage(SettingsMessage.Text(R.string.reminders_open_unavailable), SettingsSection.GENERAL) }
        }
    }

    fun setRemoteAction(button: RemoteButton, gesture: Gesture, action: RemoteAction) {
        viewModelScope.launch { services.setRemoteAction(button, gesture, action) }
    }

    fun resetRemoteMapping() {
        viewModelScope.launch { services.resetRemoteMapping() }
    }

    /** Back or "Close the phone page": the page stops and the dialog closes (PHONE-FR-02). */
    fun closePhoneSetup() = services.closePhoneSetup()

    private var qrFor: String? = null

    private fun onPhoneSetup(phone: PhoneSetupState) {
        val before = state.value.phone
        state.update { it.copy(phone = phone) }
        if (phone !is PhoneSetupState.Open) {
            qrFor = null
            state.update { it.copy(qr = null) }
            return
        }
        if (qrFor != phone.url) {
            qrFor = phone.url
            state.update { it.copy(qr = null) }
            viewModelScope.launch {
                val qr = services.qrCode(phone.url)
                if (qrFor == phone.url) state.update { it.copy(qr = qr) }
            }
        }
        // A receipt: Playlists' status line says what came (PHONE-FR-34).
        val count = (before as? PhoneSetupState.Open)?.received ?: 0
        if (phone.received > count) {
            val message = if (phone.lastWasKeys) {
                SettingsMessage.Text(R.string.phone_setup_received_keys)
            } else {
                SettingsMessage.Text(R.string.phone_setup_received, listOf(phone.lastSource.orEmpty()))
            }
            state.update { it.withMessage(message, SettingsSection.SOURCES) }
        }
    }

    /** The page never outlives Settings (PHONE-FR-03). */
    override fun onCleared() = services.closePhoneSetup()

    /** OK on a rail row: closes the source page and moves focus into the section (SET-FR-12). */
    fun select(section: SettingsSection) = state.update {
        it.copy(section = section, page = null, confirmingDelete = false, focus = it.focus.next(FocusTarget.SectionStart))
    }

    fun openSource(sourceId: String) = act { current ->
        when (val loaded = services.load(sourceId)) {
            is Outcome.Failed -> message(loaded.error)
            is Outcome.Ok -> {
                val config = loaded.value ?: return@act
                val draft = SourceDraft.of(config)
                lastSaved = draft
                state.update {
                    // "All playlists" takes focus on a page that opens (SRC-FR-13).
                    it.copy(page = draft, confirmingDelete = false, messages = it.messages - current, focus = it.focus.next(FocusTarget.SectionStart))
                }
            }
        }
    }

    fun addSource(type: SourceType) = state.update { s ->
        val saved = s.sources.orEmpty().count { it.type == type }
        val status = if (type == SourceType.M3U) R.string.source_new_m3u else R.string.source_new_xtream
        s.copy(page = SourceDraft.new(type, saved), confirmingDelete = false, focus = s.focus.next(FocusTarget.SectionStart))
            .withMessage(SettingsMessage.Text(status))
    }

    /** "All playlists" and Back: the list, with focus on this source's row (SRC-FR-06). */
    fun closePage() = state.update { s ->
        val id = s.page?.id ?: return@update s
        val target = if (s.sources.orEmpty().any { it.id == id }) FocusTarget.SourceRowOf(id) else FocusTarget.SectionStart
        s.copy(page = null, confirmingDelete = false, focus = s.focus.next(target))
    }

    fun edit(change: (SourceDraft) -> SourceDraft) = state.update { s -> s.page?.let { s.copy(page = change(it)) } ?: s }

    /** Save securely (SRC-FR-28 with plan/09's "an edited source syncs on save"). */
    fun save() = act {
        val before = state.value.page ?: return@act
        val previous = lastSaved
        persist() ?: return@act
        val saved = state.value.page ?: return@act
        // Syncs a new source, and one whose import inputs changed; a rename alone does not.
        if (before.isNew || previous == null || previous.importInputs() != saved.importInputs()) {
            services.syncNow(saved.id)
            message(SettingsMessage.Text(R.string.source_saved_syncing))
        } else {
            message(SettingsMessage.Text(R.string.source_saved))
        }
    }

    fun syncEverything() = act {
        val saved = persist() ?: return@act
        services.syncNow(saved.source.id)
        message(SettingsMessage.Text(R.string.source_sync_started))
    }

    fun refresh(kind: RefreshKind) = act {
        val saved = persist() ?: return@act
        val id = saved.source.id
        message(SettingsMessage.Text(PROGRESS.getValue(kind)))
        // The result comes from the database, not from the observed list, which may not have caught up yet.
        val health = services.refresh(id, kind)
        if (health?.state == RefreshState.FAILED) {
            message(health.error ?: AppError.Unknown)
            return@act
        }
        message(
            when (kind) {
                RefreshKind.PLAYLIST -> SettingsMessage.Count(R.plurals.source_imported_channels, health?.itemCount ?: 0)
                RefreshKind.EPG -> SettingsMessage.Count(R.plurals.source_imported_programmes, health?.itemCount ?: 0)
                RefreshKind.CATALOGUE -> services.catalogueCounts(id).let { (films, series) -> SettingsMessage.Catalogue(films, series) }
            },
        )
    }

    /** Test address / Test connection: validates, never saves (SRC-FR-24…26). */
    fun test() = act {
        val page = state.value.page ?: return@act
        val config = when (val result = SourceRules.validate(page.toConfig())) {
            is SourceRules.Result.Invalid -> return@act message(result.error)
            is SourceRules.Result.Valid -> result.config
        }
        val result = when (page.type) {
            SourceType.M3U -> {
                message(SettingsMessage.Text(R.string.source_testing_m3u))
                services.testPlaylist(config.secrets.m3uUrl.orEmpty())
            }
            SourceType.XTREAM -> {
                message(SettingsMessage.Text(R.string.source_testing_xtream))
                services.testXtream((ImportRoute.of(config) as ImportRoute.Xtream).account)
            }
        }
        message(
            when (result) {
                is SourceChecks.Result.Playlist -> when {
                    result.entries == 0 -> SettingsMessage.Text(R.string.source_test_m3u_empty)
                    result.more -> SettingsMessage.Text(R.string.source_test_m3u_ok_more, listOf(result.entries))
                    else -> SettingsMessage.Count(R.plurals.source_test_m3u_ok, result.entries)
                }
                is SourceChecks.Result.Account -> SettingsMessage.ConnectionOk(result.serverLimit)
                is SourceChecks.Result.Failed -> SettingsMessage.Failure(result.error)
            },
        )
    }

    /** Remove source asks first (plan/09 M1: confirm destructive actions). */
    fun askDelete() = state.update { it.copy(confirmingDelete = true) }

    fun cancelDelete() = state.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() = act {
        val id = state.value.page?.id ?: return@act
        when (val removed = services.remove(id)) {
            is Outcome.Failed -> message(removed.error)
            is Outcome.Ok -> state.update { s ->
                s.copy(page = null, confirmingDelete = false, focus = s.focus.next(FocusTarget.SectionStart))
                    .withMessage(SettingsMessage.Text(R.string.source_deleted))
            }
        }
    }

    fun setRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch {
            services.setRefreshInterval(interval)
            state.update { it.withMessage(SettingsMessage.IntervalSaved(interval), SettingsSection.GENERAL) }
        }
    }

    private suspend fun persist(): SourceConfig? {
        val page = state.value.page ?: return null
        return when (val saved = services.save(page.toConfig())) {
            is Outcome.Failed -> {
                message(saved.error)
                null
            }
            is Outcome.Ok -> {
                val draft = SourceDraft.of(saved.value)
                lastSaved = draft
                state.update { it.copy(page = draft) }
                saved.value
            }
        }
    }

    /** Runs one of the page's own actions: the buttons are disabled meanwhile (SRC-FR-19). */
    private fun act(block: suspend (SettingsSection) -> Unit) {
        if (state.value.busy) return
        val section = state.value.section
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block(section)
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    private fun message(error: AppError) = message(SettingsMessage.Failure(error))

    private fun message(message: SettingsMessage) = state.update { it.withMessage(message) }

    private fun SettingsState.withMessage(message: SettingsMessage, section: SettingsSection = this.section): SettingsState =
        copy(messages = messages + (section to message))

    private fun FocusCommand.next(target: FocusTarget) = FocusCommand(target, serial + 1)

    companion object {
        /** A source's first failed kind names its row's failure (SRC-FR-08). */
        private val KIND_ORDER = listOf(RefreshKind.PLAYLIST, RefreshKind.EPG, RefreshKind.CATALOGUE)
        private val PROGRESS = mapOf(
            RefreshKind.PLAYLIST to R.string.source_refreshing_playlist,
            RefreshKind.EPG to R.string.source_refreshing_epg,
            RefreshKind.CATALOGUE to R.string.source_refreshing_catalogue,
        )
    }
}
