package com.sohva.tv.app.diagnostics

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.BuildConfig
import com.sohva.tv.core.data.metadata.MetadataConfig
import com.sohva.tv.core.model.diagnostics.DeviceFacts
import com.sohva.tv.core.model.diagnostics.DiagnosticsReport
import com.sohva.tv.core.model.diagnostics.RefreshFact
import com.sohva.tv.core.model.diagnostics.SettingsFacts
import com.sohva.tv.core.model.diagnostics.SourceFact
import com.sohva.tv.ui.design.components.errorText
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Save diagnostics (spec 72 §4.10, §7.6): the facts collected, rendered and redacted off the main
 * thread and written to the document the viewer chose. The app never sends the file anywhere
 * (ABOUT-FR-31). [context] is the activity's, so display facts and error texts are the window's
 * and in the interface language.
 */
class DiagnosticsFile(private val graph: AppGraph, private val context: Context) {
    /** Writes the file; the number of lines written. Throws [IOException] when the place cannot be written. */
    suspend fun write(target: Uri): Int = withContext(graph.dispatchers.io) {
        val lines = collect()
        val out = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("no stream")
        out.bufferedWriter(Charsets.UTF_8).use { writer -> lines.forEach { writer.write(it); writer.write("\n") } }
        graph.diagnostics.info("diagnostics", "saved ${lines.size} lines")
        lines.size
    }

    private suspend fun collect(): List<String> {
        val data = graph.data
        val zone = ZoneId.systemDefault()
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone)
        fun at(millis: Long?) = millis?.let { time.format(Instant.ofEpochMilli(it)) } ?: "never"
        val device = DeviceFacts(
            generated = time.format(Instant.ofEpochMilli(graph.clock.wallMillis())),
            zone = zone.id,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            packageName = context.packageName,
            device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            locale = Locale.getDefault().toLanguageTag(),
            display = display(),
            sqlite = data.diagnostics.sqliteVersion(),
        )
        val prefs = data.preferences
        val playback = prefs.playbackSettings.first()
        val settings = SettingsFacts(
            timeZone = prefs.timeZone.first(),
            interfaceSize = prefs.scale.first().name.lowercase(),
            startup = prefs.startupScreen.first().name.lowercase(),
            refreshHours = prefs.refreshInterval.first().hours,
            buffer = playback.buffer.name.lowercase(),
            recovery = playback.reconnect.name.lowercase(),
            metadataLanguage = MetadataConfig.language(prefs.metadataLanguage(), Locale.getDefault().language),
            matchDisplay = playback.matchFrameRate,
            nextEpisode = prefs.autoPlayNext.first(),
        )
        val sources = data.sources.all()
        val names = sources.associate { it.id to it.name }
        val sourceFacts = sources.map { SourceFact(it.name, it.type.name.lowercase(), it.enabled, it.importScope.name.lowercase(), it.priority) }
        val refresh = data.diagnostics.refreshRecords().map { r ->
            val h = r.health
            RefreshFact(
                source = names[h.sourceId] ?: h.sourceId,
                kind = h.kind.id,
                status = h.state.name.lowercase(),
                items = h.itemCount,
                failuresInRow = h.consecutiveFailures,
                attempted = at(r.attemptedAt),
                succeeded = at(r.succeededAt),
                failed = at(r.failedAt),
                lastError = h.error?.let { errorText(context.resources, it) },
            )
        }
        return DiagnosticsReport.render(device, settings, sourceFacts, refresh, graph.diagnostics.snapshot())
    }

    /**
     * The panel's mode beside what the app is told (§7.6 "Display line"): a mode larger than the
     * reported size is normal; a window smaller than the reported size means something insets it.
     */
    private fun display(): String {
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        val app = "app ${metrics.widthPixels}x${metrics.heightPixels} px, ${config.screenWidthDp}x${config.screenHeightDp} dp at ${metrics.densityDpi} dpi"
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return app
        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(real)
        val mode = display.mode
        return "panel ${mode.physicalWidth}x${mode.physicalHeight} at ${"%.1f".format(Locale.ROOT, mode.refreshRate)} Hz, " +
            "reported ${real.widthPixels}x${real.heightPixels} px; $app"
    }
}
