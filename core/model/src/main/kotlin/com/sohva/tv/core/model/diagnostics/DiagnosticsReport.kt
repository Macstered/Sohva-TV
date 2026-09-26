package com.sohva.tv.core.model.diagnostics

/** The facts of the diagnostics file's header (spec 72 §7.6), already in words. */
data class DeviceFacts(
    val generated: String,
    val zone: String,
    val versionName: String,
    val versionCode: Int,
    val packageName: String,
    val device: String,
    val android: String,
    val locale: String,
    val display: String,
    val sqlite: String,
)

/** The key settings (§7.6 "Settings"), as the file names them. */
data class SettingsFacts(
    val timeZone: String?,
    val interfaceSize: String,
    val startup: String,
    val refreshHours: Int,
    val buffer: String,
    val recovery: String,
    val metadataLanguage: String,
    val matchDisplay: Boolean,
    val nextEpisode: Boolean,
)

/** A source by name only: never its addresses or credentials (§7.6). */
data class SourceFact(val name: String, val type: String, val enabled: Boolean, val scope: String, val priority: Int)

data class RefreshFact(
    val source: String,
    val kind: String,
    val status: String,
    val items: Int,
    val failuresInRow: Int,
    val attempted: String,
    val succeeded: String,
    val failed: String,
    /** In the TV's current language, or null. */
    val lastError: String?,
)

/**
 * The diagnostics file (spec 72 §7.6): plain lines; every line passes through the redactor again
 * after rendering (ABOUT-FR-28). Pure: the caller collects the facts and runs this off the main
 * thread (ABOUT-NFR-05).
 */
object DiagnosticsReport {
    fun render(
        device: DeviceFacts,
        settings: SettingsFacts,
        sources: List<SourceFact>,
        refresh: List<RefreshFact>,
        events: List<String>,
    ): List<String> {
        val out = ArrayList<String>(32 + sources.size + refresh.size * 3 + events.size)
        out += "Sohva TV diagnostics"
        out += "Generated: ${device.generated} (${device.zone})"
        out += "App: ${device.versionName} (build ${device.versionCode}) (${device.packageName})"
        out += "Device: ${device.device}"
        out += "Android: ${device.android}"
        out += "Locale: ${device.locale}; TV time zone: ${device.zone}"
        out += "Display: ${device.display}"
        out += "SQLite: ${device.sqlite}"
        out += ""
        out += "Settings"
        out += "  time zone: ${settings.timeZone ?: "TV's own"}"
        out += "  interface size: ${settings.interfaceSize}; startup: ${settings.startup}"
        out += "  refresh interval: ${settings.refreshHours} h; buffer: ${settings.buffer}; recovery: ${settings.recovery}"
        out += "  metadata language: ${settings.metadataLanguage}; match display: ${settings.matchDisplay}; next episode: ${settings.nextEpisode}"
        out += ""
        out += "Sources (${sources.size})"
        if (sources.isEmpty()) out += "  none"
        for (s in sources) out += "  ${s.name}: ${s.type}, ${if (s.enabled) "in use" else "off"}, imports ${s.scope}, priority ${s.priority}"
        out += ""
        out += "Refresh states (${refresh.size})"
        if (refresh.isEmpty()) out += "  none"
        for (r in refresh.sortedWith(compareBy({ it.source.lowercase() }, { KINDS.indexOf(it.kind) }))) {
            out += "  ${r.source} / ${r.kind}: ${r.status}, items ${r.items}, failures in a row ${r.failuresInRow}"
            out += "    attempted ${r.attempted}, succeeded ${r.succeeded}, failed ${r.failed}"
            if (r.lastError != null) out += "    last error: ${r.lastError}"
        }
        out += ""
        out += "Recent events (${events.size})"
        if (events.isEmpty()) out += "  none"
        for (e in events) out += "  $e"
        return out.map { Redactor.redact(it) ?: "" }
    }

    private val KINDS = listOf("playlist", "epg", "catalogue")
}
