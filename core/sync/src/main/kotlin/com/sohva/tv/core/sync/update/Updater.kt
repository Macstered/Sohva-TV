package com.sohva.tv.core.sync.update

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.update.OfferedUpdate
import com.sohva.tv.core.model.update.ReleaseNotes
import com.sohva.tv.core.model.update.Releases
import com.sohva.tv.core.model.update.UpdateFailure
import com.sohva.tv.core.model.update.UpdatePhase
import com.sohva.tv.core.model.update.UpdateState
import com.sohva.tv.core.net.update.UpdateHttp
import com.sohva.tv.core.model.update.ReleaseAsset
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The installed build and where it looks for updates. [enabled] only in the release package (ABOUT-FR-01). */
data class UpdaterConfig(val enabled: Boolean, val versionName: String, val versionCode: Int, val sdk: Int, val feed: HttpUrl)

/**
 * A verified download kept across processes: Android kills the app when the viewer allows it to
 * install apps, and the update must still be there to install when the app comes back.
 */
data class DownloadedUpdate(
    val version: String,
    val build: Int,
    val notes: String?,
    val apkName: String,
    val apkDigest: String,
    val profileName: String?,
    val profileDigest: String?,
)

/**
 * What the updater keeps between processes (spec 72 §6): the last successful check, each build's
 * own notes, and a verified download not yet installed.
 */
interface UpdateMemory {
    suspend fun lastCheck(): Long?

    suspend fun setLastCheck(millis: Long)

    suspend fun notes(versionCode: Int): String?

    suspend fun setNotes(versionCode: Int, notes: String)

    suspend fun downloaded(): DownloadedUpdate?

    suspend fun setDownloaded(update: DownloadedUpdate?)
}

/** The platform side of installing (ABOUT-FR-12…16); the app implements it. */
interface UpdateInstaller {
    /** Android 8+: whether this app may ask to install packages. */
    fun mayInstall(): Boolean

    /** One PackageInstaller session with `base.apk` and `base.dm` (ABOUT-FR-14). */
    suspend fun installWithProfile(apk: File, profile: File): SessionOutcome

    /** The system installer screen (ABOUT-FR-15); false when it could not be opened. */
    fun installPlain(apk: File): Boolean

    /** The "install unknown apps" page for this app (ABOUT-FR-16); false when the TV has none. */
    fun openPermission(): Boolean
}

enum class SessionOutcome { SUCCESS, CANCELLED, FAILED, UNAVAILABLE }

/**
 * The public updater (spec 72 §4.2–§4.4). Every step runs in the app's scope, off the main thread,
 * with each state change made in one place, so leaving Settings or a recreated activity never
 * leaves a phase stuck (§8). The state is collected only by About (ABOUT-NFR-03).
 */
class Updater(
    private val config: UpdaterConfig,
    private val http: UpdateHttp,
    private val memory: UpdateMemory,
    private val folder: File,
    private val installer: UpdateInstaller,
    private val log: DiagnosticsLog,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val language: () -> String,
) {
    private val _state = MutableStateFlow(UpdateState(if (config.enabled) UpdatePhase.IDLE else UpdatePhase.DISABLED))
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The verified files of the DOWNLOADED phase; the profile is dropped after a failed session (ABOUT-FR-14). */
    @Volatile private var apk: File? = null

    @Volatile private var profile: File? = null

    /**
     * After the first frame (ABOUT-FR-04, ABOUT-NFR-01): the installed build's notes; a kept download
     * that still matches its digest comes back as Downloaded, anything else in the folder goes; then
     * a check when the last successful one is a day old, in the future, or missing.
     */
    fun start(automatic: Boolean) {
        if (!config.enabled) return
        scope.launch {
            memory.notes(config.versionCode)?.let { notes -> _state.update { if (it.update == null) it.copy(notesVersion = config.versionName, notes = notes) else it } }
            if (restore()) return@launch
            folder.listFiles()?.forEach { it.delete() }
            val last = memory.lastCheck()
            val age = last?.let { clock.wallMillis() - it }
            if (automatic && (age == null || age < 0 || age >= DAY)) runCheck()
        }
    }

    private suspend fun restore(): Boolean {
        val kept = memory.downloaded() ?: return false
        val file = File(folder, kept.apkName)
        val dm = kept.profileName?.let { File(folder, it) }
        val valid = kept.build > config.versionCode && file.isFile && sha256(file) == kept.apkDigest
        if (!valid) {
            memory.setDownloaded(null)
            return false
        }
        apk = file
        profile = dm?.takeIf { it.isFile && sha256(it) == kept.profileDigest }
        val update = OfferedUpdate(kept.version, kept.build, "", ReleaseAsset(kept.apkName, "", file.length()), null, null)
        _state.value = UpdateState(UpdatePhase.DOWNLOADED, update, notesVersion = kept.version, notes = kept.notes)
        log.info("update", "download kept: ${kept.version}")
        return true
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** ABOUT-FR-05: at once; ignored while a check or a download runs. */
    fun check() {
        if (!config.enabled || _state.value.phase in BUSY) return
        scope.launch { runCheck() }
    }

    fun download() {
        val update = _state.value.update ?: return
        if (_state.value.phase != UpdatePhase.AVAILABLE && _state.value.phase != UpdatePhase.FAILED) return
        scope.launch { runDownload(update) }
    }

    fun install() {
        val phase = _state.value.phase
        if (phase != UpdatePhase.DOWNLOADED && phase != UpdatePhase.NEEDS_PERMISSION) return
        scope.launch { runInstall() }
    }

    /** ABOUT-FR-16; false when the TV has no such page, so About can say where it is. */
    fun openPermission(): Boolean = installer.openPermission()

    private suspend fun runCheck() {
        if (!begin(UpdatePhase.CHECKING)) return
        try {
            val releases = http.releases(config.feed)
            val update = Releases.select(releases, config.versionCode, config.sdk)
            // Converted once here, off the main thread (ABOUT-NFR-03).
            val tag = language()
            Releases.own(releases, config.versionCode)?.let { own -> ReleaseNotes.of(own.body, tag)?.let { memory.setNotes(config.versionCode, it) } }
            memory.setLastCheck(clock.wallMillis())
            val installedNotes = memory.notes(config.versionCode)
            if (update == null) {
                log.info("update", "up to date")
                _state.value = UpdateState(UpdatePhase.UP_TO_DATE, notesVersion = config.versionName.takeIf { installedNotes != null }, notes = installedNotes)
            } else {
                log.info("update", "available: ${update.version}")
                _state.value = UpdateState(UpdatePhase.AVAILABLE, update, notesVersion = update.version, notes = ReleaseNotes.of(update.body, tag))
            }
        } catch (e: CancellationException) {
            _state.update { it.copy(phase = UpdatePhase.IDLE) }
            throw e
        } catch (e: Exception) {
            // The time is stored only on success: the next start tries again (ABOUT-FR-06, rebuild).
            log.error("update", "check failed", e)
            _state.update { it.copy(phase = UpdatePhase.FAILED, failure = UpdateFailure.NETWORK, update = null) }
        }
    }

    private suspend fun runDownload(update: OfferedUpdate) {
        val checksums = update.checksums
        if (checksums == null) {
            _state.update { it.copy(phase = UpdatePhase.FAILED, failure = UpdateFailure.NO_CHECKSUMS) }
            return
        }
        if (!begin(UpdatePhase.DOWNLOADING)) return
        val target = File(folder, update.apk.name)
        try {
            memory.setDownloaded(null)
            val sums = http.text(checksums.url.toHttpUrl())
            val published = Releases.digest(sums, update.apk.name)
            if (published == null) {
                fail(UpdateFailure.CHECKSUM_MISMATCH)
                return
            }
            folder.mkdirs()
            folder.listFiles()?.forEach { it.delete() }
            val digest = http.download(update.apk.url.toHttpUrl(), target, update.apk.size) { percent -> _state.update { it.copy(percent = percent) } }
            if (digest != published) {
                target.delete()
                fail(UpdateFailure.CHECKSUM_MISMATCH)
                return
            }
            apk = target
            profile = fetchProfile(update, sums)
            val dm = profile
            memory.setDownloaded(
                DownloadedUpdate(
                    update.version, update.build, _state.value.notes, target.name, digest,
                    dm?.name, dm?.let { Releases.digest(sums, it.name) },
                ),
            )
            _state.update { it.copy(phase = UpdatePhase.DOWNLOADED, failure = null) }
        } catch (e: CancellationException) {
            target.delete()
            _state.update { it.copy(phase = UpdatePhase.AVAILABLE) }
            throw e
        } catch (e: Exception) {
            target.delete()
            log.error("update", "download failed", e)
            fail(UpdateFailure.DOWNLOAD_FAILED)
        }
    }

    /** ABOUT-FR-10: only a profile the checksum file names, ≤ 1 MiB, matching its digest; else none. */
    private suspend fun fetchProfile(update: OfferedUpdate, sums: String): File? {
        val asset = update.profile ?: return null
        val published = Releases.digest(sums, asset.name)
        if (published == null) {
            log.info("update", "install profile not used")
            return null
        }
        return try {
            val bytes = http.bytes(asset.url.toHttpUrl(), UpdateHttp.MAX_PROFILE)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (digest != published) {
                log.info("update", "install profile not used")
                null
            } else {
                File(folder, asset.name).also { it.writeBytes(bytes) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.info("update", "install profile not used")
            null
        }
    }

    private suspend fun runInstall() {
        val file = apk
        if (file == null || !file.isFile) {
            // The cache was cleared: offer Download again (ABOUT-FR-12, rebuild).
            apk = null
            profile = null
            memory.setDownloaded(null)
            _state.update { it.copy(phase = UpdatePhase.AVAILABLE, failure = null) }
            return
        }
        if (config.sdk >= 26 && !installer.mayInstall()) {
            _state.update { it.copy(phase = UpdatePhase.NEEDS_PERMISSION) }
            return
        }
        _state.update { it.copy(phase = UpdatePhase.DOWNLOADED) }
        val dm = profile?.takeIf { it.isFile && config.sdk >= 28 }
        if (dm != null) {
            log.info("update", "installing with profile")
            val outcome = installer.installWithProfile(file, dm)
            log.info("update", "install with profile: ${outcome.name.lowercase()}")
            when (outcome) {
                SessionOutcome.SUCCESS, SessionOutcome.CANCELLED -> return
                // The next Install uses the system screen (ABOUT-FR-14).
                SessionOutcome.FAILED -> {
                    profile = null
                    return
                }
                SessionOutcome.UNAVAILABLE -> log.info("update", "install session unavailable")
            }
        }
        if (!installer.installPlain(file)) fail(UpdateFailure.INSTALL_BLOCKED)
    }

    /** Moves to [phase] unless a check or download already runs; one place decides, so two presses never race. */
    private fun begin(phase: UpdatePhase): Boolean {
        var started = false
        _state.update {
            if (it.phase in BUSY) {
                it
            } else {
                started = true
                it.copy(phase = phase, percent = 0, failure = null)
            }
        }
        return started
    }

    private fun fail(failure: UpdateFailure) = _state.update { it.copy(phase = UpdatePhase.FAILED, failure = failure) }

    private companion object {
        const val DAY = 86_400_000L
        val BUSY = setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING)
    }
}
