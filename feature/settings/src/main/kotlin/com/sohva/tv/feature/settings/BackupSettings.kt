package com.sohva.tv.feature.settings

import android.net.Uri
import com.sohva.tv.core.model.backup.BackupCipher
import com.sohva.tv.core.model.backup.BackupProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Settings › Backup & tools does to the world (spec 71, spec 70 SET-FR-96); the app implements it. */
interface BackupSettingsServices {
    /** Encrypts a backup into the document at [target] (BACKUP-FR-07). */
    suspend fun save(target: Uri, password: CharArray): BackupOutcome

    /** Reads and checks the backup at [target] without changing anything (BACKUP-FR-18 steps 1–3). */
    suspend fun open(target: Uri, password: CharArray): BackupOutcome

    /** Applies what [open] read; it runs to its end even when Settings closes (§9). */
    suspend fun restore(): BackupOutcome

    /** Forgets what [open] read. */
    fun discard()

    /** Whether the last restore stopped half-way (spec 71 §8). */
    suspend fun unfinished(): Boolean

    /** Removes every stored programme (SET-FR-96). */
    suspend fun clearGuide()
}

sealed interface BackupOutcome {
    data object Saved : BackupOutcome

    /** Read and checked; [removes] names the sources a restore takes off this TV. */
    data class Opened(val removes: List<String>) : BackupOutcome

    data object Restored : BackupOutcome

    /** [problem] null: an error with no sentence of its own ("Unknown error"). */
    data class Failed(val problem: BackupProblem?, val field: String? = null, val fileKept: Boolean = false) : BackupOutcome
}

enum class BackupBusy { SAVING, RESTORING }

/** The group's status line (spec 71 §5 "Status (flaw)"): shown in this section, not the Playlists one. */
sealed interface BackupStatus {
    data object Unfinished : BackupStatus

    data object NoPicker : BackupStatus

    data class Done(val outcome: BackupOutcome) : BackupStatus
}

data class BackupState(
    val password: String = "",
    val busy: BackupBusy? = null,
    val status: BackupStatus? = null,
    /** Sources a read backup would remove, while the viewer is asked (§8). */
    val removes: List<String>? = null,
    val clearingGuide: Boolean = false,
    val guideCleared: Boolean = false,
) {
    /** BACKUP-FR-02: at least 8 characters and nothing running. */
    val canAct: Boolean get() = password.length >= BackupCipher.MIN_PASSWORD && busy == null && removes == null
}

/**
 * Settings › Backup & tools for the life of the Settings screen. The password lives only here,
 * as the field's text, and goes to the services as a character array wiped after use (BACKUP-FR-05).
 */
class BackupSettings internal constructor(private val services: BackupSettingsServices, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    init {
        scope.launch { if (services.unfinished()) _state.update { if (it.status == null) it.copy(status = BackupStatus.Unfinished) else it } }
    }

    /** BACKUP-FR-01: at most 128 characters; a longer entry is refused whole, never cut to a password the viewer did not type. */
    fun type(value: String) {
        if (value.length <= BackupCipher.MAX_PASSWORD) _state.update { it.copy(password = value) }
    }

    fun noPicker() = _state.update { it.copy(status = BackupStatus.NoPicker) }

    /** The picker's answer; null (cancelled) keeps everything as it was (BACKUP-FR-06). */
    fun save(target: Uri?) {
        if (target == null) return
        run(BackupBusy.SAVING) { services.save(target, it) }
    }

    /** The picker's answer: reads the file, then restores at once or first asks about removed sources. */
    fun open(target: Uri?) {
        if (target == null) return
        run(BackupBusy.RESTORING) { password ->
            val opened = services.open(target, password)
            if (opened is BackupOutcome.Opened && opened.removes.isEmpty()) services.restore() else opened
        }
    }

    fun confirmRestore() {
        _state.update { it.copy(removes = null, busy = BackupBusy.RESTORING, status = null) }
        scope.launch { finish(services.restore()) }
    }

    fun cancelRestore() {
        services.discard()
        _state.update { it.copy(removes = null, busy = null) }
    }

    fun clearGuide() {
        if (_state.value.clearingGuide) return
        _state.update { it.copy(clearingGuide = true, guideCleared = false) }
        scope.launch {
            services.clearGuide()
            _state.update { it.copy(clearingGuide = false, guideCleared = true) }
        }
    }

    private fun run(busy: BackupBusy, work: suspend (CharArray) -> BackupOutcome) {
        val typed = _state.value.password
        _state.update { it.copy(busy = busy, status = null) }
        scope.launch {
            val password = typed.toCharArray()
            val outcome = try {
                work(password)
            } finally {
                password.fill('\u0000')
            }
            // BACKUP-FR-04: the field empties once the chosen file has been handled.
            _state.update { it.copy(password = "") }
            if (outcome is BackupOutcome.Opened) {
                _state.update { it.copy(removes = outcome.removes) }
            } else {
                finish(outcome)
            }
        }
    }

    private fun finish(outcome: BackupOutcome) = _state.update { it.copy(busy = null, status = BackupStatus.Done(outcome)) }
}
