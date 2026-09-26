package com.sohva.tv.app.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.BuildConfig
import com.sohva.tv.app.BuildInfo
import com.sohva.tv.app.diagnostics.DiagnosticsFile
import com.sohva.tv.core.model.BuildKind
import com.sohva.tv.core.model.diagnostics.Redactor
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.update.UpdateState
import com.sohva.tv.core.net.phone.QrCodes
import com.sohva.tv.feature.settings.AboutSettingsServices
import com.sohva.tv.feature.settings.DiagnosticsOutcome
import java.io.IOException
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Settings › About and the legal screen from the app graph (spec 72). The activity is held weakly
 * (these services live in a ViewModel, which outlives a recreated activity): links start from it,
 * and the diagnostics file reads its window and language; without it, the application.
 */
class AppAboutSettings(private val graph: AppGraph, activity: Context?, private val openLegalScreen: () -> Unit) : AboutSettingsServices {
    private val activity = WeakReference(activity)
    private val context: Context get() = activity.get() ?: graph.app

    override val installedVersion: String = BuildConfig.VERSION_NAME

    override val labNotice: Boolean = BuildInfo.KIND == BuildKind.LAB

    override val updates: StateFlow<UpdateState> get() = graph.updater.state

    override fun check() = graph.updater.check()

    override fun download() = graph.updater.download()

    override fun install() = graph.updater.install()

    /**
     * Started from the activity, so Back from Android's page returns to About; from the application
     * it would open in its own task and Back would leave for the launcher.
     */
    override fun openPermission(): Boolean {
        val a = activity.get() as? Activity ?: return graph.updater.openPermission()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${a.packageName}".toUri()))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun openLegal() = openLegalScreen()

    override suspend fun paletteLicences(): String? = withContext(graph.dispatchers.io) {
        runCatching { context.assets.open("theme-licenses.txt").use { it.readBytes().toString(Charsets.UTF_8).trim() } }.getOrNull()
    }

    override fun openLink(url: String): Boolean = try {
        val c = context
        val view = Intent(Intent.ACTION_VIEW, url.toUri())
        if (c !is Activity) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(view)
        true
    } catch (_: Exception) {
        false
    }

    override suspend fun qrCode(url: String): QrMatrix? = withContext(graph.dispatchers.io) { QrCodes.of(url) }

    override suspend fun saveDiagnostics(target: Uri): DiagnosticsOutcome = try {
        DiagnosticsFile(graph, context).write(target)
        DiagnosticsOutcome.Saved
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        DiagnosticsOutcome.OpenFailed
    } catch (_: SecurityException) {
        DiagnosticsOutcome.OpenFailed
    } catch (e: Exception) {
        graph.diagnostics.error("diagnostics", "save failed", e)
        DiagnosticsOutcome.Failed(Redactor.redact(e.message))
    }
}
