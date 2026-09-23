package com.streammate.tv.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import com.streammate.tv.core.diagnostics.DiagnosticsLog
import java.io.File

/**
 * Installs an APK together with its install-time profile.
 *
 * A sideloaded update runs interpreted until Android's idle job compiles it,
 * about a day later: on the Shield that is 922 ms for Home's first composition
 * against 218 ms compiled, and frozen frames on the first visit to every
 * screen. The APK's own profile cannot help, since ProfileInstaller only
 * copies it on first launch and nothing compiles it then. A profile handed
 * over with the APK is different: the system compiles with it while
 * installing ("reason=install-dm"), so the first start is already the fast
 * one. Handing the file to the package-installer screen, as the app did,
 * cannot carry it; an install session can, as a second file named after the
 * first.
 *
 * The confirmation is still Android's own screen. Anything that goes wrong
 * before that screen is reported to the caller, which falls back to the way
 * the app has always installed.
 */
class UpdateSessionInstaller(private val context: Context) {
    /** How an install that got as far as the system ended; success replaces the process and is never seen. */
    enum class Outcome { CANCELLED, FAILED }

    private var receiver: BroadcastReceiver? = null

    /**
     * Returns false when the session could not be set up, with nothing left
     * behind. True means the system has the files and will ask the viewer.
     */
    fun install(apk: File, profile: File, targetPackage: String = context.packageName, onOutcome: (Outcome) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(targetPackage)
            params.setSize(apk.length() + profile.length())
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                // The profile is matched to the APK by name: the same, with ".dm".
                session.write(BASE_NAME + ".apk", apk)
                session.write(BASE_NAME + ".dm", profile)
                listenFor(sessionId, onOutcome)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val status = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(ACTION_STATUS).setPackage(context.packageName),
                    flags,
                )
                session.commit(status.intentSender)
            }
            true
        } catch (error: Exception) {
            DiagnosticsLog.w("update", "install session failed", error)
            stopListening()
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            false
        }
    }

    private fun PackageInstaller.Session.write(name: String, file: File) {
        openWrite(name, 0, file.length()).use { output ->
            file.inputStream().use { it.copyTo(output, 64 * 1024) }
            fsync(output)
        }
    }

    private fun listenFor(sessionId: Int, onOutcome: (Outcome) -> Unit) {
        stopListening()
        val listening = object : BroadcastReceiver() {
            override fun onReceive(received: Context, intent: Intent) {
                if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != sessionId) return
                when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        val shown = confirm != null && runCatching {
                            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.isSuccess
                        if (!shown) {
                            runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
                            stopListening()
                            onOutcome(Outcome.FAILED)
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> stopListening()
                    else -> {
                        DiagnosticsLog.w("update", "install session ended with status $status")
                        stopListening()
                        onOutcome(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) Outcome.CANCELLED else Outcome.FAILED)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, listening, IntentFilter(ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = listening
    }

    private fun stopListening() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private companion object {
        const val ACTION_STATUS = "com.streammate.tv.app.UPDATE_INSTALL_STATUS"
        const val BASE_NAME = "base"
    }
}
