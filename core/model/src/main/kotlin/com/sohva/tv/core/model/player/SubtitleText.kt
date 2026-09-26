package com.sohva.tv.core.model.player

import java.util.Locale

/** The side-loaded subtitle formats (spec 50 ADDON-FR-101), with their Media3 MIME types. */
enum class SubtitleFormat(val mimeType: String) {
    WEBVTT("text/vtt"),
    SSA("text/x-ssa"),
    SUBRIP("application/x-subrip"),
}

/**
 * Downloaded subtitle text (ADDON-FR-101, -103): decoded as UTF-8 without its byte-order mark,
 * recognised by content (never by name or type), and shifted by the viewer's timing before the
 * player parses it, so every cue — and seeking — uses the shifted times. Pure, so it is tested on
 * the JVM; it runs off the main thread (performance rule 1: regex work).
 */
object SubtitleText {
    const val MAX_BYTES: Int = 4 * 1024 * 1024

    /** ±60 s (FR-102). */
    const val MAX_OFFSET_MS: Long = 60_000

    private val srtTiming = Regex("""(?m)^\d{2,}:\d{2}:\d{2}[,.]\d{3}\s*-->""")
    private val clock = Regex("""(?:(\d{1,}):)?(\d{2}):(\d{2})([,.])(\d{3})""")
    private val assTime = Regex("""(\d+):(\d{2}):(\d{2})\.(\d{2})""")

    fun decode(bytes: ByteArray): String? {
        if (bytes.size > MAX_BYTES) return null
        val text = String(bytes, Charsets.UTF_8)
        return text.removePrefix("﻿")
    }

    fun detect(text: String): SubtitleFormat? {
        val head = text.trimStart()
        return when {
            head.startsWith("WEBVTT") -> SubtitleFormat.WEBVTT
            head.startsWith("[Script Info]") && text.contains("[Events]") -> SubtitleFormat.SSA
            srtTiming.containsMatchIn(text) -> SubtitleFormat.SUBRIP
            else -> null
        }
    }

    /** [offsetMs] later when positive; times before zero become zero. Only timing lines change. */
    fun shift(text: String, format: SubtitleFormat, offsetMs: Long): String {
        if (offsetMs == 0L) return text
        val out = StringBuilder(text.length + 64)
        for (line in text.lineSequence()) {
            val shifted = when (format) {
                SubtitleFormat.SUBRIP, SubtitleFormat.WEBVTT -> if (line.contains("-->")) shiftClock(line, format, offsetMs) else line
                SubtitleFormat.SSA -> if (line.startsWith("Dialogue:")) shiftAss(line, offsetMs) else line
            }
            out.append(shifted).append('\n')
        }
        return out.toString()
    }

    /** The label under the sync control (FR-102): `%+.3f s`. */
    fun label(offsetMs: Long): String = String.format(Locale.ROOT, "%+.3f s", offsetMs / 1000.0)

    private fun shiftClock(line: String, format: SubtitleFormat, offsetMs: Long): String {
        // The start and end times; cue settings after them (WebVTT) are kept as they are.
        val timing = clock.findAll(line).take(2).toList()
        if (timing.size < 2) return line
        val sb = StringBuilder(line)
        for (m in timing.asReversed()) {
            val (h, mi, s, _, ms) = m.destructured
            val total = ((h.ifEmpty { "0" }.toLong() * 60 + mi.toLong()) * 60 + s.toLong()) * 1000 + ms.toLong() + offsetMs
            sb.replace(m.range.first, m.range.last + 1, clockText(total.coerceAtLeast(0), if (format == SubtitleFormat.SUBRIP) ',' else '.'))
        }
        return sb.toString()
    }

    private fun clockText(ms: Long, separator: Char): String =
        String.format(Locale.ROOT, "%02d:%02d:%02d%c%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, separator, ms % 1000)

    /** `Dialogue: layer,Start,End,…`: only the second and third fields. */
    private fun shiftAss(line: String, offsetMs: Long): String {
        val fields = line.split(',', limit = 4)
        if (fields.size < 4) return line
        fun move(value: String): String {
            val m = assTime.matchEntire(value.trim()) ?: return value
            val (h, mi, s, cs) = m.destructured
            val total = (((h.toLong() * 60 + mi.toLong()) * 60 + s.toLong()) * 100 + cs.toLong()) * 10 + offsetMs
            val t = total.coerceAtLeast(0) / 10
            return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", t / 360_000, t / 6_000 % 60, t / 100 % 60, t % 100)
        }
        return listOf(fields[0], move(fields[1]), move(fields[2]), fields[3]).joinToString(",")
    }
}
