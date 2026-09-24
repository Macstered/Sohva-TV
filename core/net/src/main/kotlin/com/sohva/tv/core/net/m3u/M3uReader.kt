package com.sohva.tv.core.net.m3u

import com.sohva.tv.core.model.diagnostics.Redactor
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.text.NameNormalizer
import com.sohva.tv.core.model.text.StableIds
import java.util.Locale
import okio.BufferedSource

/**
 * Reads an M3U playlist one entry at a time from a byte stream (spec 10 §4.10): the caller pulls
 * with [next], so nothing but the current line and the pending directives is held (SRC-L-03).
 *
 * Rebuild rules beyond beta 23: a line over [MAX_LINE_BYTES] is skipped without being decoded;
 * the header's `url-tvg`/`x-tvg-url` is offered as [headerGuideUrl]; `#EXTGRP` supplies a missing
 * group (plan/09 M1); stored names are cut to [MAX_NAME_CHARS].
 */
class M3uReader(private val source: BufferedSource, private val ids: StableIds = StableIds()) {
    /** The guide address the `#EXTM3U` header names, once the header has been read. */
    var headerGuideUrl: String? = null
        private set

    /** First 60 characters of the first line, redacted, when the document was refused. */
    var refusedOpening: String? = null
        private set

    private var opened = false
    private var entries = 0
    private var pending = Pending()
    private var skippedOverlong = false

    /** The next entry, or null at the end. Throws [AppException] with `playlist_not_m3u`. */
    fun next(): M3uEntry? {
        while (true) {
            val line = readLine() ?: return null
            if (skippedOverlong) {
                skippedOverlong = false
                if (!opened) refuse("")
                continue
            }
            val text = line.removePrefix("﻿").trim()
            if (text.isEmpty()) continue
            if (!opened) {
                if (!opensPlaylist(text)) refuse(text)
                opened = true
            }
            if (text[0] == '#') directive(text) else return entry(text)
        }
    }

    private fun refuse(text: String): Nothing {
        refusedOpening = Redactor.redact(text.take(60)).orEmpty()
        throw AppException(AppError.PlaylistNotM3u)
    }

    private fun directive(line: String) {
        when {
            line.startsWith("#EXTINF:", ignoreCase = true) -> pending = Pending().also { it.readExtInf(line.substring(8)) }
            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> pending.readVlcOption(line.substring(11))
            line.startsWith("#KODIPROP:", ignoreCase = true) -> pending.readKodiProperty(line.substring(10))
            line.startsWith("#EXTHTTP:", ignoreCase = true) -> pending.readJsonHeaders(line.substring(9))
            line.startsWith("#EXTGRP:", ignoreCase = true) -> pending.extGroup = line.substring(8).trim().ifEmpty { null }
            line.startsWith("#EXTM3U", ignoreCase = true) && line.length > 7 && line[7].isWhitespace() ->
                if (headerGuideUrl == null) headerGuideUrl = headerGuide(line.substring(8))
        }
    }

    private fun entry(rawAddress: String): M3uEntry {
        val p = pending
        pending = Pending()
        entries++
        val bar = rawAddress.indexOf('|')
        val address = if (bar < 0) rawAddress else rawAddress.substring(0, bar)
        val headers = if (bar < 0) emptyMap() else M3uText.headerPairs(rawAddress.substring(bar + 1))
        val a = p.attributes
        val name = p.displayName ?: a["tvg-name"]?.takeIf { it.isNotBlank() }
        // The id keeps beta 23's Finnish fallback so ids do not depend on the language (plan/04 §6).
        val normalized = NameNormalizer.normalize(name ?: "Kanava $entries")
        val tvgId = a["tvg-id"]?.takeIf { it.isNotBlank() }
        val group = a["group-title"]?.takeIf { it.isNotBlank() } ?: p.extGroup
        return M3uEntry(
            index = entries - 1,
            id = ids.m3uEntryId(tvgId, normalized, address),
            name = name?.take(MAX_NAME_CHARS),
            normalizedName = normalized,
            tvgId = tvgId,
            logoUrl = a["tvg-logo"]?.takeIf { it.isNotBlank() },
            group = group,
            channelNumber = (a["tvg-chno"] ?: a["channel-number"])?.trim()?.toIntOrNull()?.takeIf { it > 0 },
            catchupType = (a["catchup"] ?: a["catchup-type"] ?: a["timeshift"]?.let { "timeshift" })
                ?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() },
            catchupDays = (a["catchup-days"] ?: a["timeshift"])?.trim()?.toIntOrNull()?.coerceIn(1, MAX_CATCHUP_DAYS),
            catchupSource = a["catchup-source"]?.takeIf { it.isNotBlank() },
            durationSeconds = p.duration,
            kind = M3uText.kindOf(p.typeHints(), group, address, p.duration),
            streamUrl = address,
            userAgent = headers["user-agent"] ?: p.userAgent,
            referrer = headers["referer"] ?: headers["referrer"] ?: p.referrer,
        )
    }

    /**
     * The next line without its terminator, or null at the end. A line over the cap is skipped
     * unread: it reads as empty and sets [skippedOverlong].
     */
    private fun readLine(): String? {
        if (source.exhausted()) return null
        val newline = source.indexOf(NEWLINE, 0, MAX_LINE_BYTES + 1)
        if (newline >= 0) {
            val line = source.readUtf8(newline)
            source.skip(1)
            return line
        }
        if (!source.request(MAX_LINE_BYTES + 1)) return source.readUtf8()
        while (true) {
            val end = source.indexOf(NEWLINE, 0, SKIP_CHUNK)
            if (end >= 0) {
                source.skip(end + 1)
                break
            }
            if (!source.request(1)) break
            // Only the searched bytes: the buffer may already hold the lines after the newline.
            source.skip(minOf(source.buffer.size, SKIP_CHUNK))
        }
        skippedOverlong = true
        return ""
    }

    private fun opensPlaylist(line: String): Boolean =
        line[0] == '#' || (line[0] != '<' && line[0] != '{' && line[0] != '[' && line.contains("://"))

    private fun headerGuide(attributes: String): String? {
        val a = M3uText.attributes(attributes)
        val value = a["url-tvg"]?.takeIf { it.isNotBlank() } ?: a["x-tvg-url"]?.takeIf { it.isNotBlank() } ?: return null
        // Some playlists list several guides separated by commas; the first one is used.
        return value.substringBefore(',').trim().ifEmpty { null }
    }

    /** Directives seen since the last `#EXTINF` (or the last entry). */
    private class Pending {
        var attributes: Map<String, String> = emptyMap()
        var displayName: String? = null
        var duration: Int? = null
        var userAgent: String? = null
        var referrer: String? = null
        var extGroup: String? = null

        fun readExtInf(body: String) {
            val comma = M3uText.unquotedComma(body)
            val head = if (comma < 0) body else body.substring(0, comma)
            displayName = if (comma < 0) null else body.substring(comma + 1).trim().ifEmpty { null }
            attributes = M3uText.attributes(head)
            duration = body.substringBefore(' ').substringBefore(',').trim().toIntOrNull()
        }

        fun readVlcOption(option: String) {
            val key = option.substringBefore('=').trim().lowercase(Locale.ROOT)
            val value = option.substringAfter('=', "").trim()
            when (key) {
                "http-user-agent" -> userAgent = value
                "http-referrer", "http-referer" -> referrer = value
            }
        }

        fun readKodiProperty(property: String) {
            if (!property.substringBefore('=').trim().endsWith("stream_headers", ignoreCase = true)) return
            apply(M3uText.headerPairs(property.substringAfter('=', "")))
        }

        fun readJsonHeaders(json: String) = apply(M3uText.jsonHeaders(json))

        private fun apply(headers: Map<String, String>) {
            headers["user-agent"]?.let { userAgent = it }
            (headers["referer"] ?: headers["referrer"])?.let { referrer = it }
        }

        fun typeHints(): String = listOfNotNull(attributes["type"], attributes["media-type"], attributes["content-type"], attributes["tvg-type"])
            .joinToString(" ")
            .lowercase(Locale.ROOT)
    }

    companion object {
        const val MAX_LINE_BYTES: Long = 64L * 1024
        const val MAX_NAME_CHARS: Int = 512
        private const val MAX_CATCHUP_DAYS = 365
        private const val SKIP_CHUNK: Long = 8L * 1024
        private const val NEWLINE: Byte = '\n'.code.toByte()
    }
}
