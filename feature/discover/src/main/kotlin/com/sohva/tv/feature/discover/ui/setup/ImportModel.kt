package com.sohva.tv.feature.discover.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.data.ImportList
import com.sohva.tv.feature.discover.data.NuvioExport
import com.sohva.tv.feature.discover.protocol.AddonEndpoint
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.AddonManifest
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.net.phone.LocalAddress
import com.sohva.tv.core.net.phone.PhoneMode
import com.sohva.tv.core.net.phone.PhoneServer
import com.sohva.tv.core.net.phone.PhoneState
import com.sohva.tv.core.net.phone.PhoneSubmission
import com.sohva.tv.core.net.phone.QrCodes
import com.sohva.tv.feature.discover.net.StremioException
import com.sohva.tv.feature.discover.net.StremioProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A preview row's state (spec 50 FR-40 table). */
enum class ImportStatus { CHECKING, READY, ALREADY_INSTALLED, DUPLICATE_INPUT, NEEDS_CONFIGURATION, BAD_URL, REDIRECT, UNAVAILABLE, INSTALLED }

/** "Addon N" and its status; the URL and manifest stay out of the state the screen reads. */
data class ImportRow(val number: Int, val status: ImportStatus, val selected: Boolean = false)

/** Where the import page is: the methods, or the phone or Stremio sub-page (§5.8). */
enum class ImportPage { METHODS, PHONE, STREMIO }

/** The screen's one-line messages (FR-35, -36, -39, -41). */
sealed interface ImportNote {
    data object ListFull : ImportNote

    data object Invalid : ImportNote

    data object NoPicker : ImportNote

    data object Failed : ImportNote

    data class Copied(val count: Int) : ImportNote

    data object NoAddons : ImportNote

    data object Complete : ImportNote
}

/** The phone sub-page (§4.7.1). */
sealed interface PhoneUi {
    data object Off : PhoneUi

    data object NoNetwork : PhoneUi

    /** [url] carries the token in its fragment: shown on the TV only. */
    class Open(val url: String, val qr: QrMatrix?) : PhoneUi {
        override fun toString(): String = "Open"
    }

    data object Ended : PhoneUi
}

/** The Stremio sub-page (§4.7.2). */
sealed interface StremioUi {
    data object Idle : StremioUi

    data object Starting : StremioUi

    class Waiting(val link: String, val qr: QrMatrix?) : StremioUi {
        override fun toString(): String = "Waiting"
    }

    data class Failed(val problem: StremioProblem) : StremioUi

    data object Background : StremioUi
}

data class ImportState(
    val page: ImportPage = ImportPage.METHODS,
    /** How many lines are pending (the lines themselves stay out of the screen's state, FR-42). */
    val pending: Int = 0,
    val rows: List<ImportRow>? = null,
    val previewing: Boolean = false,
    val committing: Boolean = false,
    val committed: Boolean = false,
    val note: ImportNote? = null,
    val phone: PhoneUi = PhoneUi.Off,
    val stremio: StremioUi = StremioUi.Idle,
) {
    val selectedCount: Int get() = rows.orEmpty().count { it.status == ImportStatus.READY && it.selected }
}

/**
 * Add / import (spec 50 §4.7): one pending list fed by every method, a preview that checks each
 * line in order (one at a time, a failure never stopping the rest), and a commit that installs the
 * selected entries with the manifests fetched at preview. Nothing typed or received is kept in
 * saved state, preferences or logs; leaving the page drops it all (FR-42).
 */
class ImportModel(private val host: DiscoverHost, private val profile: String) : ViewModel() {
    private companion object {
        const val STREMIO_MS = 10 * 60 * 1_000L
        const val STREMIO_POLL_MS = 3_000L
    }

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** The pending lines; a null entry is a Nuvio placeholder that fails in preview (FR-38). */
    private var pending: List<String?> = emptyList()

    /** The preview's endpoints and manifests, in memory only until the commit (FR-41). */
    private var fetched: List<Pair<AddonEndpoint, AddonManifest>?> = emptyList()
    private var work: Job? = null
    private var stremioJob: Job? = null
    private var phoneJob: Job? = null

    /** The one-use phone server (FR-43..46), built when the phone page first opens. */
    private val phoneServer: PhoneServer by lazy {
        PhoneServer(AddonPhoneAnswers, { submission ->
            val list = (submission as? PhoneSubmission.AddonList)?.bytes ?: return@PhoneServer false
            viewModelScope.launch(host.dispatchers.ui) { received(list) }
            true
        })
    }

    fun go(page: ImportPage) {
        if (page != ImportPage.PHONE) stopPhone()
        if (page != ImportPage.STREMIO) stopStremio(background = false)
        _state.update { it.copy(page = page) }
    }

    // ---- Phone (FR-43..46) ----

    fun startPhone() {
        phoneJob?.cancel()
        phoneJob = viewModelScope.launch {
            withContext(host.dispatchers.io) { phoneServer.start(LocalAddress.current(), PhoneMode.Addons { ImportList.parse(it) != null }) }
            var opened = false
            phoneServer.state.collect { st ->
                when (st) {
                    PhoneState.NoNetwork -> _state.update { it.copy(phone = PhoneUi.NoNetwork) }
                    is PhoneState.Running -> {
                        opened = true
                        val qr = withContext(host.dispatchers.io) { QrCodes.of(st.url) }
                        _state.update { it.copy(phone = PhoneUi.Open(st.url, qr)) }
                    }
                    PhoneState.Stopped -> if (opened) _state.update { if (it.phone is PhoneUi.Open) it.copy(phone = PhoneUi.Ended) else it }
                }
            }
        }
    }

    /** Leaving the page or the app going to the background closes the server (FR-45). */
    fun stopPhone() {
        phoneJob?.cancel()
        phoneJob = null
        if (_state.value.phone !is PhoneUi.Off) viewModelScope.launch(host.dispatchers.io) { phoneServer.stop() }
        _state.update { if (it.phone is PhoneUi.Open) it.copy(phone = PhoneUi.Ended) else it }
    }

    private fun received(bytes: ByteArray) {
        stopPhone()
        useList(bytes)
        _state.update { it.copy(page = ImportPage.METHODS, phone = PhoneUi.Off) }
    }

    // ---- Stremio (FR-47..50) ----

    fun startStremio() {
        if (_state.value.stremio is StremioUi.Starting || _state.value.stremio is StremioUi.Waiting) return
        _state.update { it.copy(stremio = StremioUi.Starting) }
        stremioJob = viewModelScope.launch {
            val result = try {
                withTimeout(STREMIO_MS) {
                    if (!host.access.allowed(profile)) throw StremioException(StremioProblem.ACCESS_DENIED)
                    val pending = host.stremio.create()
                    val qr = withContext(host.dispatchers.io) { QrCodes.of(pending.link) }
                    _state.update { it.copy(stremio = StremioUi.Waiting(pending.link, qr)) }
                    var token: String? = null
                    while (token == null) {
                        delay(STREMIO_POLL_MS)
                        token = host.stremio.read(pending.code)
                    }
                    val urls = host.stremio.addons(token)
                    if (!host.access.allowed(profile)) throw StremioException(StremioProblem.ACCESS_DENIED)
                    urls
                }
            } catch (e: TimeoutCancellationException) {
                _state.update { it.copy(stremio = StremioUi.Failed(StremioProblem.TIMEOUT)) }
                return@launch
            } catch (e: StremioException) {
                _state.update { it.copy(stremio = StremioUi.Failed(e.problem)) }
                return@launch
            }
            _state.update { it.copy(stremio = StremioUi.Idle) }
            useStremio(result)
        }
    }

    /** Back, a page change or ON_STOP end the attempt; a fresh one always makes a new link (FR-48). */
    fun stopStremio(background: Boolean) {
        val running = stremioJob?.isActive == true
        stremioJob?.cancel()
        stremioJob = null
        _state.update { it.copy(stremio = if (background && running) StremioUi.Background else if (running) StremioUi.Idle else it.stremio) }
    }

    /** FR-35: the trimmed field joins the list when the whole list still fits. False: nothing changed. */
    fun addManual(text: String): Boolean {
        val line = text.trim()
        if (line.isEmpty()) return false
        if (!ImportList.fits(pending, line)) {
            _state.update { it.copy(note = ImportNote.ListFull) }
            return false
        }
        replaceWith(pending + line, null)
        return true
    }

    /** FR-36, -37: a text list from a file or the phone replaces the pending list. */
    fun useList(bytes: ByteArray?) {
        val lines = bytes?.let(ImportList::parse)
        if (lines == null) _state.update { it.copy(note = ImportNote.Invalid) } else replaceWith(lines, null)
    }

    /** FR-38: a Nuvio export's URLs, placeholders kept in place. */
    fun useNuvio(bytes: ByteArray?) {
        val urls = bytes?.let(NuvioExport::urls)
        if (urls == null) _state.update { it.copy(note = ImportNote.Invalid) } else replaceWith(urls, null)
    }

    /** FR-39: a Stremio account's addons replace the pending list. */
    fun useStremio(urls: List<String>) {
        if (urls.isEmpty()) {
            _state.update { it.copy(page = ImportPage.METHODS, note = ImportNote.NoAddons) }
            return
        }
        replaceWith(urls, ImportNote.Copied(urls.size))
        _state.update { it.copy(page = ImportPage.METHODS) }
    }

    fun noPicker() = _state.update { it.copy(note = ImportNote.NoPicker) }

    private fun replaceWith(lines: List<String?>, note: ImportNote?) {
        work?.cancel()
        pending = lines
        fetched = emptyList()
        _state.update { it.copy(pending = lines.size, rows = null, committed = false, note = note) }
    }

    /** "Clear list" and "Start another list" (FR-41). */
    fun clear() {
        work?.cancel()
        pending = emptyList()
        fetched = emptyList()
        _state.update { ImportState(page = it.page) }
    }

    /** FR-40: each line in order, one at a time. */
    fun preview() {
        if (pending.isEmpty() || _state.value.previewing) return
        val lines = pending
        pending = emptyList()
        val results = arrayOfNulls<Pair<AddonEndpoint, AddonManifest>>(lines.size)
        _state.update { s -> s.copy(pending = 0, previewing = true, note = null, rows = lines.indices.map { ImportRow(it + 1, ImportStatus.CHECKING) }) }
        work = viewModelScope.launch {
            try {
                val installed = host.manager.list(profile).map { it.endpoint.fingerprint }.toSet()
                val seen = HashSet<String>()
                for ((i, line) in lines.withIndex()) {
                    val status = check(line, seen, installed) { endpoint, manifest -> results[i] = endpoint to manifest }
                    _state.update { s -> s.copy(rows = s.rows?.toMutableList()?.also { it[i] = ImportRow(i + 1, status, status == ImportStatus.READY) }) }
                }
                fetched = results.toList()
                _state.update { it.copy(previewing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fetched = emptyList()
                _state.update { it.copy(previewing = false, rows = null, note = ImportNote.Failed) }
            }
        }
    }

    private suspend fun check(line: String?, seen: MutableSet<String>, installed: Set<String>, ready: (AddonEndpoint, AddonManifest) -> Unit): ImportStatus {
        val endpoint = try {
            host.manager.endpoint(line ?: return ImportStatus.BAD_URL, baseAllowed = true)
        } catch (e: AddonException) {
            return ImportStatus.BAD_URL
        }
        if (!seen.add(endpoint.fingerprint)) return ImportStatus.DUPLICATE_INPUT
        if (endpoint.fingerprint in installed) return ImportStatus.ALREADY_INSTALLED
        return try {
            ready(endpoint, host.manager.manifest(endpoint))
            ImportStatus.READY
        } catch (e: AddonException) {
            when (e.failure) {
                AddonFailure.CONFIGURATION_REQUIRED -> ImportStatus.NEEDS_CONFIGURATION
                AddonFailure.REDIRECT -> ImportStatus.REDIRECT
                AddonFailure.INVALID_URL, AddonFailure.INSECURE_URL -> ImportStatus.BAD_URL
                else -> ImportStatus.UNAVAILABLE
            }
        }
    }

    /** OK on a READY row (FR-40). */
    fun toggle(index: Int) = _state.update { s ->
        val rows = s.rows ?: return@update s
        val row = rows.getOrNull(index)?.takeIf { it.status == ImportStatus.READY } ?: return@update s
        s.copy(rows = rows.toMutableList().also { it[index] = row.copy(selected = !row.selected) })
    }

    /** FR-41: the selected READY entries in list order, with the manifests from the preview. */
    fun commit() {
        val s = _state.value
        if (s.committing || s.selectedCount == 0) return
        _state.update { it.copy(committing = true) }
        work = viewModelScope.launch {
            try {
                val rows = s.rows.orEmpty().toMutableList()
                for ((i, row) in rows.withIndex()) {
                    if (row.status != ImportStatus.READY || !row.selected) continue
                    val (endpoint, manifest) = fetched.getOrNull(i) ?: continue
                    val before = host.manager.list(profile).any { it.endpoint.fingerprint == endpoint.fingerprint }
                    if (!before) host.manager.installFetched(profile, endpoint, manifest)
                    rows[i] = row.copy(status = if (before) ImportStatus.ALREADY_INSTALLED else ImportStatus.INSTALLED)
                    _state.update { it.copy(rows = rows.toList()) }
                }
                fetched = emptyList()
                host.noteSetupChanged()
                _state.update { it.copy(committing = false, committed = true, note = ImportNote.Complete) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fetched = emptyList()
                _state.update { it.copy(committing = false, note = ImportNote.Failed) }
            }
        }
    }

    /** A file read off the main thread (FR-36): no copy, no name, one read. */
    suspend fun readDocument(open: () -> java.io.InputStream?): ByteArray? = withContext(host.dispatchers.io) {
        runCatching { open()?.use(ImportList::read) }.getOrNull()
    }

    override fun onCleared() {
        work?.cancel()
        stremioJob?.cancel()
        phoneJob?.cancel()
        if (_state.value.phone !is PhoneUi.Off) phoneServer.stop()
        pending = emptyList()
        fetched = emptyList()
    }
}
