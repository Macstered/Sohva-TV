package com.streammate.tv.app

import com.streammate.tv.core.diagnostics.DiagnosticsLog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Why an update step did not complete; the settings screen puts words to these. */
enum class AppUpdateFailure { NETWORK, NO_CHECKSUMS, CHECKSUM_MISMATCH, INSTALL_BLOCKED }

sealed interface AppUpdateState {
    data object Disabled : AppUpdateState
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val update: AvailableUpdate) : AppUpdateState
    data class Downloading(val update: AvailableUpdate, val percent: Int) : AppUpdateState
    data class Downloaded(val update: AvailableUpdate, val file: File, val profile: File? = null) : AppUpdateState
    data class NeedsInstallPermission(val update: AvailableUpdate, val file: File, val profile: File? = null) : AppUpdateState
    data class Failed(val reason: AppUpdateFailure, val update: AvailableUpdate?) : AppUpdateState
}

/**
 * Finds, fetches, verifies and hands over a newer beta from the public
 * release list, so a tester never has to find the APK on GitHub again.
 *
 * Nothing is downloaded without a press. The APK goes to the app's own cache,
 * is hashed, and is only offered to the system installer if its digest is the
 * one the release published; a mismatch deletes it. The install itself is
 * Android's: the same package-installer screen a sideload shows, with its
 * one-time "allow installs from this app" prompt.
 *
 * A release may also carry the APK's install-time profile. It is fetched and
 * verified the same way and handed over with the APK, so that the system
 * compiles the update as it installs it (see [UpdateSessionInstaller]).
 * Nothing depends on it: without one, or if anything about it fails, the
 * update installs as it always has.
 */
class AppUpdateChecker(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val updatesAllowed = AppRuntimePolicy.forPackage(context.packageName).publicUpdatesAllowed
    private val sessionInstaller = UpdateSessionInstaller(context)
    private val preferences = context.getSharedPreferences("streammate_updates", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow<AppUpdateState>(if (updatesAllowed) AppUpdateState.Idle else AppUpdateState.Disabled)
    val state: StateFlow<AppUpdateState> = mutableState

    val installedVersionCode: Int = context.packageManager.getPackageInfo(context.packageName, 0).let { info ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }

    val installedVersionName: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    private val mutableInstalledNotes =
        MutableStateFlow(
            if (AppRuntimePolicy.forPackage(context.packageName).isLab) {
                context.getString(com.streammate.tv.R.string.lab_safety_notice)
            } else {
                preferences.getString(KEY_INSTALLED_NOTES_PREFIX + installedVersionCode, null)
            },
        )

    /**
     * What changed in the build that is installed, from its own published
     * release; kept once seen, so About can say so without a network.
     */
    val installedNotes: StateFlow<String?> = mutableInstalledNotes

    /** A daily look, from app start; the settings row can always ask again. */
    suspend fun checkIfDue() {
        if (!updatesAllowed) return
        val last = preferences.getLong(KEY_LAST_CHECK, 0L)
        if (clock() - last < CHECK_INTERVAL_MILLIS) return
        check()
    }

    suspend fun check() {
        if (!updatesAllowed) return
        val current = mutableState.value
        if (current is AppUpdateState.Downloading) return
        mutableState.value = AppUpdateState.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val releases = AppUpdates.parseReleases(fetchText(AppUpdates.RELEASES_URL))
                val installed = AppUpdates.installedRelease(releases, installedVersionCode)
                    ?.let { AppUpdates.releaseNotes(it.body) }
                AppUpdates.selectUpdate(releases, installedVersionCode, Build.VERSION.SDK_INT) to installed
            }
        }
        preferences.edit().putLong(KEY_LAST_CHECK, clock()).apply()
        mutableState.value = result.fold(
            onSuccess = { (update, installed) ->
                if (installed != null) {
                    preferences.edit().putString(KEY_INSTALLED_NOTES_PREFIX + installedVersionCode, installed).apply()
                    mutableInstalledNotes.value = installed
                }
                DiagnosticsLog.i("update", update?.let { "available: ${it.versionName}" } ?: "up to date")
                update?.let(AppUpdateState::Available) ?: AppUpdateState.UpToDate
            },
            onFailure = { error ->
                DiagnosticsLog.w("update", "check failed", error)
                AppUpdateState.Failed(AppUpdateFailure.NETWORK, null)
            },
        )
    }

    suspend fun download(update: AvailableUpdate) {
        if (!updatesAllowed) return
        val checksums = update.checksums
        if (checksums == null) {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.NO_CHECKSUMS, update)
            return
        }
        mutableState.value = AppUpdateState.Downloading(update, 0)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val published = fetchText(checksums.downloadUrl)
                val expected = AppUpdates.expectedChecksum(published, update.apk.name)
                    ?: throw ChecksumException()
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                directory.listFiles()?.forEach { it.delete() }
                val target = File(directory, update.apk.name)
                val digest = MessageDigest.getInstance("SHA-256")
                val request = Request.Builder().url(update.apk.downloadUrl).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val total = response.body.contentLength().takeIf { it > 0 } ?: update.apk.sizeBytes
                    var received = 0L
                    var lastPercent = -1
                    response.body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                received += read
                                val percent = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    mutableState.value = AppUpdateState.Downloading(update, percent)
                                }
                            }
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { byte -> HEX[(byte.toInt() shr 4) and 0x0f].toString() + HEX[byte.toInt() and 0x0f] }
                if (actual != expected) {
                    target.delete()
                    throw ChecksumException()
                }
                target to update.profile?.let { fetchProfile(it, published, directory) }
            }
        }
        mutableState.value = result.fold(
            onSuccess = { (file, profile) -> AppUpdateState.Downloaded(update, file, profile) },
            onFailure = { error ->
                AppUpdateState.Failed(
                    if (error is ChecksumException) AppUpdateFailure.CHECKSUM_MISMATCH else AppUpdateFailure.NETWORK,
                    update,
                )
            },
        )
    }

    /**
     * The release's install-time profile, verified like the APK, or null: a
     * few kilobytes that make the first start faster and that the update does
     * not need.
     */
    private fun fetchProfile(asset: ReleaseAsset, published: String, directory: File): File? = runCatching {
        val expected = AppUpdates.expectedChecksum(published, asset.name) ?: return null
        val request = Request.Builder().url(asset.downloadUrl).get().build()
        val bytes = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            if (response.body.contentLength() > MAX_PROFILE_BYTES) throw IOException("profile too large")
            response.body.bytes()
        }
        if (bytes.size > MAX_PROFILE_BYTES) throw IOException("profile too large")
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> HEX[(byte.toInt() shr 4) and 0x0f].toString() + HEX[byte.toInt() and 0x0f] }
        if (actual != expected) throw ChecksumException()
        File(directory, asset.name).also { it.writeBytes(bytes) }
    }.onFailure { DiagnosticsLog.w("update", "install profile not used", it) }.getOrNull()

    /** Hands the verified file to the system installer, or asks for the permission it needs first. */
    fun install(update: AvailableUpdate, file: File, profile: File? = null) {
        if (!updatesAllowed) return
        if (!file.isFile) {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.NETWORK, update)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            mutableState.value = AppUpdateState.NeedsInstallPermission(update, file, profile)
            return
        }
        if (profile != null && profile.isFile) {
            val handedOver = sessionInstaller.install(file, profile) { outcome ->
                DiagnosticsLog.i("update", "install with profile: $outcome")
                mutableState.value = when (outcome) {
                    UpdateSessionInstaller.Outcome.CANCELLED -> AppUpdateState.Downloaded(update, file, profile)
                    // Once more the old way, which the press that follows takes.
                    UpdateSessionInstaller.Outcome.FAILED -> AppUpdateState.Downloaded(update, file, null)
                }
            }
            DiagnosticsLog.i("update", if (handedOver) "installing with profile" else "install session unavailable")
            if (handedOver) return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { mutableState.value = AppUpdateState.Failed(AppUpdateFailure.INSTALL_BLOCKED, update) }
    }

    /** Opens Android's "install unknown apps" page for this app; [install] can be tried again after. */
    fun openInstallPermissionSettings() {
        if (!updatesAllowed) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Back to the verified file after the permission was granted. */
    fun retryInstall() {
        val current = mutableState.value
        if (current is AppUpdateState.NeedsInstallPermission) install(current.update, current.file, current.profile)
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").get().build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
    }

    private class ChecksumException : IOException("checksum mismatch")

    private companion object {
        const val KEY_LAST_CHECK = "last_check_epoch_millis"
        const val KEY_INSTALLED_NOTES_PREFIX = "installed_notes_"
        const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1_000
        const val HEX = "0123456789abcdef"
        /** A profile is about fifteen kilobytes; anything far larger is not one. */
        const val MAX_PROFILE_BYTES = 1L * 1024 * 1024
    }
}
