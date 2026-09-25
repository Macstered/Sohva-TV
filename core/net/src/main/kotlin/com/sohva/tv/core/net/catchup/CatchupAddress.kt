package com.sohva.tv.core.net.catchup

import com.sohva.tv.core.model.guide.CatchupRules
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What the archive address is built from. [liveUrl] and [template] are secrets of the viewer's
 * source: they are never stored with the result and never logged ([toString] leaves them out,
 * spec 22 §4.3).
 */
class CatchupRequest(
    val liveUrl: String,
    val type: String?,
    val template: String?,
    val xtreamStreamId: String?,
    /** The channel's catch-up zone (an Xtream panel's server zone); invalid or null = [fallbackZone]. */
    val zone: String?,
    val fallbackZone: ZoneId,
    val start: Long,
    val stop: Long,
    val now: Long,
) {
    override fun toString(): String = "CatchupRequest(type=$type, start=$start, stop=$stop, now=$now)"
}

/**
 * Builds the provider's archive address for a programme (spec 22 §4.3–4.4): the M3U schemes from
 * the channel's template, and the Xtream `timeshift` path from the live address. Pure string work,
 * run once per playback start off the main thread; the patterns are compiled once (CATCH-NFR-01).
 * Anything unsafe or unusable gives null, never a guess.
 */
object CatchupAddress {
    private val TIME_TOKEN = Regex("""\$?\{(utc|lutc|utcend|start|now|timestamp|end)(?::([^{}]+))?\}""", RegexOption.IGNORE_CASE)
    private val DURATION_TOKEN = Regex("""\$?\{duration(?::([^{}]+))?\}""", RegexOption.IGNORE_CASE)
    private val OFFSET_TOKEN = Regex("""\$?\{offset:([^{}]+)\}""", RegexOption.IGNORE_CASE)
    private val FIELD_TOKEN = Regex("""\{([YmdHMS])\}""")
    private val LEFTOVER = Regex("""\$?\{[^{}]*\}""")
    private val STREAM_ID = Regex("[A-Za-z0-9._-]{1,128}")
    private const val FORMAT_CHARACTERS = "YmdHMS-_:/. "

    fun build(request: CatchupRequest): String? {
        if (request.start >= request.stop || request.start > request.now) return null
        val zone = zoneOf(request.zone) ?: request.fallbackZone
        val template = request.template?.takeIf { it.isNotBlank() }
        val live = request.liveUrl
        return when (CatchupRules.normalType(request.type)) {
            "default", "vod" -> template?.let { render(it, request, zone) }
            "append" -> template?.let { render(live + it, request, zone) }
            "shift", "timeshift" -> render(live + (if ('?' in live) "&" else "?") + "utc={utc}&lutc={lutc}", request, zone)
            "xtream", "xc" -> xtream(request, zone)
            else -> null
        }
    }

    private fun zoneOf(id: String?): ZoneId? = id?.takeIf { it.isNotBlank() }?.let {
        try {
            ZoneId.of(it.trim())
        } catch (_: DateTimeException) {
            null
        }
    }

    /** CATCH-FR-22 and -23. */
    private fun render(template: String, request: CatchupRequest, zone: ZoneId): String? {
        if (template.contains("{catchup-id}", ignoreCase = true)) return null
        val start = request.start / 1000
        val stop = request.stop / 1000
        val now = request.now / 1000
        var failed = false
        var text = TIME_TOKEN.replace(template) { match ->
            val seconds = when (match.groupValues[1].lowercase()) {
                "utc", "start" -> start
                "utcend", "end" -> stop
                else -> now
            }
            val format = match.groups[2]?.value
            if (format == null) seconds.toString() else formatted(seconds, format, zone) ?: "".also { failed = true }
        }
        if (failed) return null
        val duration = maxOf(1L, stop - start)
        text = DURATION_TOKEN.replace(text) { match ->
            val divisor = match.groups[1]?.value?.trim()?.toLongOrNull() ?: 1L
            if (divisor <= 0) "".also { failed = true } else (duration / divisor).toString()
        }
        if (failed) return null
        text = OFFSET_TOKEN.replace(text) { match ->
            val divisor = match.groupValues[1].trim().toLongOrNull()
            if (divisor == null || divisor <= 0) "".also { failed = true } else (maxOf(0L, now - start) / divisor).toString()
        }
        if (failed) return null
        text = FIELD_TOKEN.replace(text) { match -> formatted(start, match.groupValues[1], zone).orEmpty() }
        if (LEFTOVER.containsMatchIn(text)) return null
        // HttpUrl accepts http and https only, and gives the normalised form.
        return text.toHttpUrlOrNull()?.toString()
    }

    /** A token's `:format`: only the letters and separators of CATCH-FR-22; anything else refuses. */
    private fun formatted(epochSeconds: Long, format: String, zone: ZoneId): String? {
        if (format.isBlank() || format.any { it !in FORMAT_CHARACTERS }) return null
        val t = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), zone)
        return buildString {
            for (c in format) {
                when (c) {
                    'Y' -> append(t.year.toString().padStart(4, '0'))
                    'm' -> append(two(t.monthValue))
                    'd' -> append(two(t.dayOfMonth))
                    'H' -> append(two(t.hour))
                    'M' -> append(two(t.minute))
                    'S' -> append(two(t.second))
                    else -> append(c)
                }
            }
        }
    }

    private fun two(value: Int): String = value.toString().padStart(2, '0')

    /** CATCH-FR-30…33. */
    private fun xtream(request: CatchupRequest, zone: ZoneId): String? {
        val url = request.liveUrl.toHttpUrlOrNull() ?: return null
        val segments = url.pathSegments.filter { it.isNotBlank() }
        val live = segments.indexOfLast { it.equals("live", ignoreCase = true) }
        val credentials = if (live >= 0) live + 1 else segments.size - 3
        if (credentials < 0 || credentials + 3 > segments.size) return null
        val user = segments[credentials]
        val password = segments[credentials + 1]
        if (user.isBlank() || password.isBlank()) return null
        val streamId = request.xtreamStreamId?.takeIf { STREAM_ID.matches(it) }
            ?: segments[credentials + 2].substringBefore('.').takeIf { STREAM_ID.matches(it) }
            ?: return null
        val minutes = maxOf(1L, (request.stop - request.start + 59_999) / 60_000)
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(request.start), zone)
        val start = "${t.year.toString().padStart(4, '0')}-${two(t.monthValue)}-${two(t.dayOfMonth)}:${two(t.hour)}-${two(t.minute)}"
        val prefix = segments.subList(0, if (live >= 0) live else credentials)
        val builder = HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port)
        for (segment in prefix + listOf("timeshift", user, password, minutes.toString(), start, "$streamId.ts")) {
            builder.addPathSegment(segment)
        }
        return builder.build().toString()
    }
}
