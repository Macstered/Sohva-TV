package com.sohva.tv.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.sync.update.SessionOutcome
import com.sohva.tv.core.sync.update.UpdateInstaller
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Installing a verified update (spec 72 §4.4). With a profile: one PackageInstaller session holding
 * `base.apk` and `base.dm`, so Android compiles the update while it installs (ABOUT-FR-14, lesson
 * 3.6); otherwise the system installer screen through the updates FileProvider (ABOUT-FR-15).
 * Android's own confirmation is always shown (ABOUT-FR-18).
 */
class AndroidUpdateInstaller(private val context: Context, private val log: DiagnosticsLog, private val io: CoroutineDispatcher) : UpdateInstaller {
    private val app = context.applicationContext

    override fun mayInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    override suspend fun installWithProfile(apk: File, profile: File): SessionOutcome {
        val installer = app.packageManager.packageInstaller
        var sessionId = -1
        val result = CompletableDeferred<SessionOutcome>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != sessionId) return
                outcome(intent, installer, sessionId)?.let(result::complete)
            }
        }
        try {
            withContext(io) {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(apk.length() + profile.length())
                }
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    // The profile is matched to the APK by its name beside it.
                    write(session, "base.apk", apk)
                    write(session, "base.dm", profile)
                    ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val status = PendingIntent.getBroadcast(app, sessionId, Intent(ACTION_STATUS).setPackage(app.packageName), flags)
                    session.commit(status.intentSender)
                }
            }
        } catch (e: CancellationException) {
            abandon(installer, sessionId)
            unregister(receiver)
            throw e
        } catch (e: Exception) {
            log.error("update", "install session failed", e)
            abandon(installer, sessionId)
            unregister(receiver)
            return SessionOutcome.UNAVAILABLE
        }
        return try {
            result.await()
        } finally {
            unregister(receiver)
        }
    }

    /** Null while Android waits for the viewer's answer: the session reports again after it. */
    private fun outcome(intent: Intent, installer: PackageInstaller, sessionId: Int): SessionOutcome? =
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                try {
                    app.startActivity(confirm!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    null
                } catch (e: Exception) {
                    log.error("update", "install confirmation could not open", e)
                    abandon(installer, sessionId)
                    SessionOutcome.FAILED
                }
            }
            PackageInstaller.STATUS_SUCCESS -> SessionOutcome.SUCCESS
            PackageInstaller.STATUS_FAILURE_ABORTED -> SessionOutcome.CANCELLED
            else -> {
                log.info("update", "install session ended with status $status")
                SessionOutcome.FAILED
            }
        }

    override fun installPlain(apk: File): Boolean = try {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(view)
        true
    } catch (e: Exception) {
        log.error("update", "installer could not open", e)
        false
    }

    override fun openPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${app.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun write(session: PackageInstaller.Session, name: String, file: File) {
        file.inputStream().use { input ->
            session.openWrite(name, 0, file.length()).use { out ->
                input.copyTo(out, 64 * 1024)
                session.fsync(out)
            }
        }
    }

    private fun abandon(installer: PackageInstaller, sessionId: Int) {
        if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
    }

    private fun unregister(receiver: BroadcastReceiver) {
        runCatching { app.unregisterReceiver(receiver) }
    }

    private companion object {
        const val ACTION_STATUS = "com.streammate.tv.app.UPDATE_INSTALL_STATUS"
    }
}
