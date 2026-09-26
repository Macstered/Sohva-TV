package com.sohva.tv.core.model.settings

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** One zone of the time-zone dialog (spec 70 SET-FR-60): its id, region, city and offset now. */
data class ZoneRow(val id: String, val region: String, val city: String, val offset: String) {
    /** "Helsinki · UTC+3" (SET-FR-54). */
    val label: String get() = "$city · $offset"
}

/** The time-zone dialog's list and labels (spec 70 §4.6), pure so they are tested on the JVM. */
object TimeZones {
    /** The six former fixed choices under Recent (SET-FR-62). */
    val RECENT: List<String> = listOf("Europe/Helsinki", "Europe/Stockholm", "Europe/Berlin", "Europe/London", "America/New_York", "UTC")

    /** `UTC` and every id with `/` that is not `Etc/` or `SystemV/`, by region then city (SET-FR-60). */
    fun rows(ids: Collection<String>, at: Instant): List<ZoneRow> = ids
        .filter { it == "UTC" || ('/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/")) }
        .mapNotNull { id -> runCatching { row(id, at) }.getOrNull() }
        .sortedWith(compareBy({ it.region }, { it.city }))

    fun row(id: String, at: Instant): ZoneRow {
        val parts = id.split('/')
        val region = if (id == "UTC") "UTC" else parts.first()
        val last = parts.last().replace('_', ' ')
        val middle = parts.drop(1).dropLast(1).map { it.replace('_', ' ') }
        val city = when {
            id == "UTC" -> "UTC"
            parts.size > 2 -> "$last, ${middle.joinToString(", ")}"
            else -> last
        }
        return ZoneRow(id, region, city, offset(ZoneId.of(id), at))
    }

    /** "UTC" for zero, else "UTC+3", "UTC−5:30" (U+2212 for minus) (SET-FR-60). */
    fun offset(zone: ZoneId, at: Instant): String {
        val seconds = zone.rules.getOffset(at).totalSeconds
        if (seconds == 0) return "UTC"
        val sign = if (seconds > 0) "+" else "−"
        val total = Math.abs(seconds) / 60
        val hours = total / 60
        val minutes = total % 60
        return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:" + String.format(Locale.ROOT, "%02d", minutes)
    }

    /** Rows whose city, region or id contains the trimmed [query], ignoring case (SET-FR-63). */
    fun filter(rows: List<ZoneRow>, query: String): List<ZoneRow> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return rows
        return rows.filter { q in it.city.lowercase(Locale.ROOT) || q in it.region.lowercase(Locale.ROOT) || q in it.id.lowercase(Locale.ROOT) }
    }
}
