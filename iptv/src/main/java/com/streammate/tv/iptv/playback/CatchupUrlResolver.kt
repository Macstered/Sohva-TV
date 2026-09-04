package com.streammate.tv.iptv.playback

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class CatchupUrlRequest(
    val liveUrl: String,
    val catchupType: String,
    val catchupSource: String? = null,
    val xtreamStreamId: String? = null,
    val timeZoneId: String? = null,
    val programmeStartEpochMillis: Long,
    val programmeStopEpochMillis: Long,
    val nowEpochMillis: Long,
) {
    override fun toString(): String =
        "CatchupUrlRequest(catchupType=$catchupType, liveUrl=<redacted>, catchupSource=<redacted>, " +
            "programmeStartEpochMillis=$programmeStartEpochMillis, " +
            "programmeStopEpochMillis=$programmeStopEpochMillis)"
}

class CatchupUrlResolver {
    fun resolve(request: CatchupUrlRequest): String? {
        if (
            request.programmeStartEpochMillis >= request.programmeStopEpochMillis ||
            request.programmeStartEpochMillis > request.nowEpochMillis
        ) {
            return null
        }
        val mode = request.catchupType.trim().lowercase(Locale.ROOT)
        val template = when (mode) {
            "default", "vod" -> request.catchupSource
            "append" -> request.catchupSource?.let { request.liveUrl + it }
            "shift", "timeshift" -> request.liveUrl + shiftQuerySeparator(request.liveUrl) + SHIFT_QUERY
            "xtream", "xc" -> return resolveXtream(request)
            else -> null
        } ?: return null
        val rendered = renderTemplate(template, request) ?: return null
        return rendered.toSafeHttpUrl()?.toString()
    }

    private fun resolveXtream(request: CatchupUrlRequest): String? {
        val liveUrl = request.liveUrl.toSafeHttpUrl() ?: return null
        val parts = xtreamParts(liveUrl, request.xtreamStreamId) ?: return null
        val durationMinutes = ceil(
            (request.programmeStopEpochMillis - request.programmeStartEpochMillis) / MILLIS_PER_MINUTE,
        ).toLong().coerceAtLeast(1)
        val zone = safeZone(request.timeZoneId)
        val start = XTREAM_DATE_TIME_FORMAT.format(
            Instant.ofEpochMilli(request.programmeStartEpochMillis).atZone(zone),
        )
        return liveUrl.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .apply { parts.prefixSegments.forEach(::addPathSegment) }
            .addPathSegment("timeshift")
            .addPathSegment(parts.username)
            .addPathSegment(parts.password)
            .addPathSegment(durationMinutes.toString())
            .addPathSegment(start)
            .addPathSegment("${parts.streamId}.ts")
            .build()
            .toString()
    }

    private fun xtreamParts(url: HttpUrl, explicitStreamId: String?): XtreamParts? {
        val segments = url.pathSegments.filter(String::isNotBlank)
        val liveIndex = segments.indexOfLast { it.equals("live", ignoreCase = true) }
        val credentialStart = if (liveIndex >= 0) liveIndex + 1 else segments.size - 3
        if (credentialStart < 0 || segments.size < credentialStart + 3) return null
        val inferredId = segments[credentialStart + 2].substringBefore('.')
        val streamId = explicitStreamId?.takeIf { it.matches(XTREAM_ID_PATTERN) }
            ?: inferredId.takeIf { it.matches(XTREAM_ID_PATTERN) }
            ?: return null
        val username = segments[credentialStart].takeIf(String::isNotBlank) ?: return null
        val password = segments[credentialStart + 1].takeIf(String::isNotBlank) ?: return null
        return XtreamParts(
            prefixSegments = segments.take(if (liveIndex >= 0) liveIndex else credentialStart),
            username = username,
            password = password,
            streamId = streamId,
        )
    }

    private fun renderTemplate(template: String, request: CatchupUrlRequest): String? {
        if (template.contains("{catchup-id}", ignoreCase = true)) return null
        val startSeconds = Math.floorDiv(request.programmeStartEpochMillis, MILLIS_PER_SECOND)
        val stopSeconds = Math.floorDiv(request.programmeStopEpochMillis, MILLIS_PER_SECOND)
        val nowSeconds = Math.floorDiv(request.nowEpochMillis, MILLIS_PER_SECOND)
        val durationSeconds = (stopSeconds - startSeconds).coerceAtLeast(1)
        val zone = safeZone(request.timeZoneId)
        val start = Instant.ofEpochSecond(startSeconds).atZone(zone)
        val stop = Instant.ofEpochSecond(stopSeconds).atZone(zone)
        val now = Instant.ofEpochSecond(nowSeconds).atZone(zone)

        var rendered = template
        var invalidTemplate = false
        rendered = TIMESTAMP_PATTERN.replace(rendered) { match ->
            val token = match.groupValues[1].lowercase(Locale.ROOT)
            val customFormat = match.groupValues[2].takeIf(String::isNotEmpty)
            val value = when (token) {
                "utc", "start" -> start
                "utcend", "end" -> stop
                "lutc", "now", "timestamp" -> now
                else -> return@replace match.value
            }
            if (customFormat == null) {
                value.toEpochSecond().toString()
            } else {
                formatTimestamp(value, customFormat) ?: run {
                    invalidTemplate = true
                    match.value
                }
            }
        }
        rendered = DURATION_PATTERN.replace(rendered) { match ->
            val divisor = match.groupValues[1].takeIf(String::isNotEmpty)?.toLongOrNull() ?: 1L
            if (divisor <= 0) {
                invalidTemplate = true
                match.value
            } else {
                (durationSeconds / divisor).toString()
            }
        }
        rendered = OFFSET_PATTERN.replace(rendered) { match ->
            val divisor = match.groupValues[1].toLongOrNull()
            if (divisor == null || divisor <= 0) {
                invalidTemplate = true
                match.value
            } else {
                ((nowSeconds - startSeconds).coerceAtLeast(0) / divisor).toString()
            }
        }
        val startValues = mapOf(
            "Y" to "%04d".format(Locale.ROOT, start.year),
            "m" to "%02d".format(Locale.ROOT, start.monthValue),
            "d" to "%02d".format(Locale.ROOT, start.dayOfMonth),
            "H" to "%02d".format(Locale.ROOT, start.hour),
            "M" to "%02d".format(Locale.ROOT, start.minute),
            "S" to "%02d".format(Locale.ROOT, start.second),
        )
        startValues.forEach { (token, value) -> rendered = rendered.replace("{$token}", value) }
        return rendered.takeUnless { invalidTemplate || UNRESOLVED_TOKEN_PATTERN.containsMatchIn(it) }
    }

    private fun formatTimestamp(
        value: java.time.ZonedDateTime,
        format: String,
    ): String? {
        if (format.isBlank() || format.any { it !in TIMESTAMP_FORMAT_CHARACTERS }) return null
        val values = mapOf(
            'Y' to "%04d".format(Locale.ROOT, value.year),
            'm' to "%02d".format(Locale.ROOT, value.monthValue),
            'd' to "%02d".format(Locale.ROOT, value.dayOfMonth),
            'H' to "%02d".format(Locale.ROOT, value.hour),
            'M' to "%02d".format(Locale.ROOT, value.minute),
            'S' to "%02d".format(Locale.ROOT, value.second),
        )
        return buildString { format.forEach { append(values[it] ?: it) } }
    }

    private fun String.toSafeHttpUrl(): HttpUrl? = toHttpUrlOrNull()
        ?.takeIf { it.scheme == "http" || it.scheme == "https" }

    private fun safeZone(timeZoneId: String?): ZoneId = timeZoneId
        ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        ?: ZoneId.systemDefault()

    private data class XtreamParts(
        val prefixSegments: List<String>,
        val username: String,
        val password: String,
        val streamId: String,
    )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000.0
        const val SHIFT_QUERY = "utc={utc}&lutc={lutc}"
        val XTREAM_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
        val XTREAM_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
        val TIMESTAMP_PATTERN = Regex(
            """\$?\{(utc|lutc|utcend|start|now|timestamp|end)(?::([^{}]+))?\}""",
            RegexOption.IGNORE_CASE,
        )
        val DURATION_PATTERN = Regex("""\$?\{duration(?::([^{}]+))?\}""", RegexOption.IGNORE_CASE)
        val OFFSET_PATTERN = Regex("""\$?\{offset:([^{}]+)\}""", RegexOption.IGNORE_CASE)
        val UNRESOLVED_TOKEN_PATTERN = Regex("""\$?\{[^{}]+\}""")
        val TIMESTAMP_FORMAT_CHARACTERS = "YmdHMS-_:/. "

        fun shiftQuerySeparator(url: String): String = if ('?' in url) "&" else "?"
    }
}
