package com.sohva.tv.feature.settings

import android.net.Uri
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Settings › About does to the world (spec 72 §4, §5.1); the app implements it. */
interface AboutSettingsServices {
    /** "0.2.0-beta.1" (ABOUT-01). */
    val installedVersion: String

    /** The Lab build shows its safety notice in place of release notes (ABOUT-FR-03). */
    val labNotice: Boolean

    /** The Google Play build: Play updates the app, so the section says so (decision "Play build"). */
    val updatesFromStore: Boolean get() = false

    /** The updater's state; collected only while About is on screen (ABOUT-NFR-03). */
    val updates: StateFlow<UpdateState>

    fun check()

    fun download()

    fun install()

    /** False when the TV has no "install unknown apps" page for the app (ABOUT-FR-16). */
    fun openPermission(): Boolean

    /** Opens the legal screen (ABOUT-13). */
    fun openLegal()

    /** The palettes' MIT notices shipped in the APK (ABOUT-24), read off the main thread. */
    suspend fun paletteLicences(): String?

    /** Opens [url] in the TV's browser or mail app; false when nothing can (ABOUT-FR-23). */
    fun openLink(url: String): Boolean

    suspend fun qrCode(url: String): QrMatrix?

    /** Writes the diagnostics file into [target] (ABOUT-FR-30). */
    suspend fun saveDiagnostics(target: Uri): DiagnosticsOutcome

    /** False when the TV has no "create document" picker; the launcher's own failure also says so. */
    fun filePickerAvailable(): Boolean = true

    /** A TV without a picker: the file goes straight to the Download folder as [name] (rebuild). */
    suspend fun saveDiagnosticsToDownloads(name: String): DiagnosticsOutcome = DiagnosticsOutcome.NoPicker

    /** The phone page that hands the diagnostics file to a phone (rebuild); closed when not open. */
    val diagnosticsPhone: Flow<DiagnosticsPhone> get() = flowOf(DiagnosticsPhone.Closed)

    /** Renders the file now and serves it as [name] on the phone page. */
    fun openDiagnosticsPhone(name: String) = Unit

    fun closeDiagnosticsPhone() = Unit
}

/** The diagnostics phone page as the TV shows it. */
sealed interface DiagnosticsPhone {
    data object Closed : DiagnosticsPhone

    data object NoNetwork : DiagnosticsPhone

    /** [sent]: a phone has fetched the file at least once. */
    data class Open(val url: String, val qr: QrMatrix?, val sent: Boolean) : DiagnosticsPhone
}

sealed interface DiagnosticsOutcome {
    data object Saved : DiagnosticsOutcome

    data object OpenFailed : DiagnosticsOutcome

    data object NoPicker : DiagnosticsOutcome

    /** No picker, so the file was written to the Download folder as [name]. */
    data class SavedToDownloads(val name: String) : DiagnosticsOutcome

    /** [text] is already redacted, or null for "Unknown error". */
    data class Failed(val text: String?) : DiagnosticsOutcome
}

/** An address the TV could not open, shown as text and a code (ABOUT-FR-23, rebuild). */
data class LinkShown(val url: String, val qr: QrMatrix?)

/**
 * Settings › About for the life of the Settings screen; the legal screen reaches links through
 * the same holder. Nothing here runs until About or the legal screen is shown.
 */
class AboutSettings internal constructor(private val services: AboutSettingsServices, private val scope: CoroutineScope) {
    val installedVersion: String get() = services.installedVersion
    val labNotice: Boolean get() = services.labNotice
    val updatesFromStore: Boolean get() = services.updatesFromStore

    val updates: StateFlow<UpdateState> get() = services.updates

    private val _diagnostics = MutableStateFlow<DiagnosticsOutcome?>(null)
    val diagnostics: StateFlow<DiagnosticsOutcome?> = _diagnostics.asStateFlow()

    private val _link = MutableStateFlow<LinkShown?>(null)
    val link: StateFlow<LinkShown?> = _link.asStateFlow()

    /** Set when "Allow installs" found no page: About then says where it is. */
    private val _permissionHelp = MutableStateFlow(false)
    val permissionHelp: StateFlow<Boolean> = _permissionHelp.asStateFlow()

    fun check() = services.check()

    fun download() = services.download()

    fun install() = services.install()

    fun openPermission() {
        _permissionHelp.value = !services.openPermission()
    }

    /** Set while the legal screen is open: Back lands on the licences button, not on Playlists (§10). */
    private var legalReturn = false

    fun openLegal() {
        legalReturn = true
        services.openLegal()
    }

    /** True once after coming back from the legal screen. */
    fun takeLegalReturn(): Boolean = legalReturn.also { legalReturn = false }

    fun openLink(url: String) {
        if (services.openLink(url)) return
        scope.launch { _link.value = LinkShown(url, services.qrCode(url)) }
    }

    fun closeLink() = _link.update { null }

    fun saveDiagnostics(target: Uri?) {
        if (target == null) return
        scope.launch { _diagnostics.value = services.saveDiagnostics(target) }
    }

    fun filePickerAvailable(): Boolean = services.filePickerAvailable()

    /** No picker on this TV: save to the Download folder instead and say where. */
    fun noPicker(name: String) {
        scope.launch { _diagnostics.value = services.saveDiagnosticsToDownloads(name) }
    }

    private val _phoneOpen = MutableStateFlow(false)

    /** The "Send to phone" dialog is up. */
    val phoneOpen: StateFlow<Boolean> = _phoneOpen.asStateFlow()

    val diagnosticsPhone: Flow<DiagnosticsPhone> get() = services.diagnosticsPhone

    fun openPhone(name: String) {
        _phoneOpen.value = true
        services.openDiagnosticsPhone(name)
    }

    fun closePhone() {
        services.closeDiagnosticsPhone()
        _phoneOpen.value = false
    }
}
