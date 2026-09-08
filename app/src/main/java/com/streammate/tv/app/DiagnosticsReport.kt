package com.streammate.tv.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import com.streammate.tv.core.diagnostics.DiagnosticsLog
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.security.SecretRedactor
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.repository.SourceRefreshHealth
import com.streammate.tv.core.error.StoredFailureMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * What "Save diagnostics" writes: the facts a tester cannot easily read off
 * the screen, redacted, as one text file. Nothing here leaves the device
 * unless the tester shares the file.
 */
data class DiagnosticsInfo(
    val generatedAtEpochMillis: Long,
    val appVersion: String,
    val packageName: String,
    val device: String,
    val android: String,
    val locale: String,
    val deviceTimeZone: String,
    /** What the television is showing and what the app is laid out against. */
    val display: String,
    val sqliteVersion: String,
    val preferences: AppPreferences,
    val sources: List<IptvSourceConfiguration>,
    val health: List<SourceRefreshHealth>,
    val log: List<String>,
)

/** The file's text for [info]; a pure function so a test can read it. */
fun renderDiagnostics(info: DiagnosticsInfo): String {
    val zone = ZoneId.systemDefault()
    val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun time(millis: Long?): String = millis?.let { stamp.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "never"
    val names = info.sources.associate { it.id to it.name }
    val text = buildString {
        appendLine("Sohva TV diagnostics")
        appendLine("Generated: ${time(info.generatedAtEpochMillis)} (${zone.id})")
        appendLine("App: ${info.appVersion} (${info.packageName})")
        appendLine("Device: ${info.device}")
        appendLine("Android: ${info.android}")
        appendLine("Locale: ${info.locale}; TV time zone: ${info.deviceTimeZone}")
        appendLine("Display: ${info.display}")
        appendLine("SQLite: ${info.sqliteVersion}")
        appendLine()
        appendLine("Settings")
        val p = info.preferences
        appendLine("  time zone: ${if (p.timeZoneFollowsDevice) "TV's own" else p.timeZoneId}")
        appendLine("  interface size: ${p.interfaceScale.name.lowercase()}; startup: ${p.startupScreen.name.lowercase()}")
        appendLine("  refresh interval: ${p.playlistEpgRefreshInterval.hours} h; buffer: ${p.playbackBufferProfile.name.lowercase()}; recovery: ${p.playbackReconnectPolicy.name.lowercase()}")
        appendLine("  metadata language: ${p.metadataLanguage}; match display: ${p.autoFrameRateEnabled}; next episode: ${p.autoPlayNextEpisodeEnabled}")
        appendLine()
        appendLine("Sources (${info.sources.size})")
        if (info.sources.isEmpty()) appendLine("  none")
        info.sources.forEach { source ->
            appendLine("  ${source.name}: ${source.type.name.lowercase()}, ${if (source.enabled) "in use" else "off"}, imports ${source.importScope.name.lowercase()}, priority ${source.priority}")
        }
        appendLine()
        appendLine("Refresh states (${info.health.size})")
        if (info.health.isEmpty()) appendLine("  none")
        info.health.sortedWith(compareBy({ names[it.sourceId] ?: it.sourceId }, { it.kind })).forEach { h ->
            appendLine("  ${names[h.sourceId] ?: h.sourceId} / ${h.kind}: ${h.status}, items ${h.itemCount}, failures in a row ${h.consecutiveFailures}")
            appendLine("    attempted ${time(h.lastAttemptAtEpochMillis)}, succeeded ${time(h.lastSuccessAtEpochMillis)}, failed ${time(h.lastFailureAtEpochMillis)}")
            h.lastError?.takeIf(String::isNotBlank)?.let { appendLine("    last error: $it") }
        }
        appendLine()
        appendLine("Recent events (${info.log.size})")
        if (info.log.isEmpty()) appendLine("  none")
        info.log.forEach { appendLine("  $it") }
    }
    // Everything above was built from redacted parts; one more pass costs
    // nothing and catches a field that was not.
    return text.lines().joinToString("\n") { SecretRedactor.redact(it) ?: "" }
}

/** Gathers [DiagnosticsInfo] from the running app and writes it where the tester chose. */
class DiagnosticsReport(
    private val context: Context,
    private val preferencesRepository: AppPreferencesRepository,
    private val secretSettingsStore: SecretSettingsStore,
    private val guideRepository: com.streammate.tv.iptv.repository.GuideRepository,
    private val sqliteVersion: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun collect(): DiagnosticsInfo = withContext(Dispatchers.IO) {
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionCode = packageInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        }
        DiagnosticsInfo(
            generatedAtEpochMillis = clock(),
            appVersion = "${packageInfo?.versionName ?: "?"} (build ${versionCode ?: "?"})",
            packageName = context.packageName,
            device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            locale = Locale.getDefault().toLanguageTag(),
            deviceTimeZone = TimeZone.getDefault().id,
            display = displaySummary(context),
            sqliteVersion = runCatching(sqliteVersion).getOrDefault("?"),
            preferences = preferencesRepository.preferences.first(),
            sources = secretSettingsStore.loadSources(),
            // A failure is stored by its resource and arguments; the file should
            // carry the words, in the language the television is set to.
            health = guideRepository.observeSourceRefreshHealth().first().map { state ->
                state.copy(lastError = StoredFailureMessage.resolve(context.resources, state.lastError) ?: state.lastError)
            },
            log = DiagnosticsLog.snapshot(),
        )
    }

    suspend fun writeTo(uri: Uri) {
        val text = renderDiagnostics(collect())
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("The chosen location could not be opened")
            stream.bufferedWriter().use { it.write(text) }
        }
        DiagnosticsLog.i("diagnostics", "saved ${text.lines().size} lines")
    }
}


/**
 * The screen, for the reports that say the interface does not fill the
 * television while playback does.
 *
 * Three sizes, because two of them differ perfectly normally: a Shield drives
 * a 3840x2160 panel while reporting 1920x1080 to everything running on it, so
 * the panel's mode disagreeing with the reported size means nothing on its
 * own. The one that tells is the app's own window against the reported size.
 * Smaller means something outside the app has inset it, which is where
 * overscan compensation shows up - and video on its own plane can escape the
 * same scaling, which is why playback can still fill the screen.
 */
internal fun displaySummary(context: Context): String {
    val metrics = context.resources.displayMetrics
    val configuration = context.resources.configuration
    val app = "app ${metrics.widthPixels}x${metrics.heightPixels} px, " +
        "${configuration.screenWidthDp}x${configuration.screenHeightDp} dp at ${metrics.densityDpi} dpi"
    val panel = runCatching {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return@runCatching null
        val mode = display.mode ?: return@runCatching null
        val real = DisplayMetrics().also { @Suppress("DEPRECATION") display.getRealMetrics(it) }
        val hertz = String.format(Locale.ROOT, "%.1f", mode.refreshRate)
        "panel ${mode.physicalWidth}x${mode.physicalHeight} at $hertz Hz, " +
            "reported ${real.widthPixels}x${real.heightPixels} px"
    }.getOrNull()
    return if (panel == null) app else "$panel; $app"
}
