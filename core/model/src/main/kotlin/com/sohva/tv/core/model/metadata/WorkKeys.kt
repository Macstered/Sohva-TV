package com.sohva.tv.core.model.metadata

import java.util.Locale

/**
 * A film's identity across playlists (spec 41 §4.12, META-FR-66): `tmdb:<id>` once matched, else
 * `name:<identity>:<year>`. Copies such as "FIN | The Matrix (1999) 4K" and "The Matrix 1999
 * [MULTI-SUBS] 1080p" get one key. Plain titles take a fast path of one pass; the rest go through
 * [TitleCleaner] and a hand-written strip of technical tokens (no regex, spec 41 §9.1).
 */
object WorkKeys {
    fun of(title: String, year: Int?, externalId: String? = null): String {
        externalId?.trim()?.takeIf { it.isNotEmpty() }?.let { return "tmdb:$it" }
        simpleIdentity(title)?.let { return "name:$it:${year ?: ""}" }
        val bare = TitleCleaner.searchTitle(title).ifBlank { title }
        val plain = withoutMarks(TitleCleaner.nfkd(bare))
        val normalized = TitleCleaner.normalizeTitle(plain)
        val stripped = collapseRepeatedSpaces(stripTechnical(normalized).trim())
        val identity = stripped.ifBlank { normalized }.ifBlank { title.trim().lowercase(Locale.ROOT) }
        return "name:$identity:${year ?: TitleCleaner.yearFromTitle(title) ?: ""}"
    }

    /**
     * Plain words straight to an identity: letters, digits and whitespace only, no provider prefix
     * in front of other words, no technical token.
     */
    private fun simpleIdentity(title: String): String? {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { !it.isLetterOrDigit() && !it.isWhitespace() }) return null
        val tokens = splitOnSpaces(trimmed).filter { it.isNotBlank() }
        if (tokens.size > 1 && tokens.first().lowercase(Locale.ROOT) in PREFIX_TOKENS) return null
        if (tokens.any(::needsFullCleaning)) return null
        return collapseRepeatedSpaces(withoutMarks(TitleCleaner.nfkd(trimmed)).lowercase(Locale.ROOT).trim()).takeIf { it.isNotBlank() }
    }

    private fun needsFullCleaning(token: String): Boolean {
        val t = token.lowercase(Locale.ROOT)
        if (t in TECHNICAL_TOKENS || isMarkerToken(t)) return true
        // Unicode digits here, as beta 23's token check used Char.isDigit.
        if (t.length in 4..5 && t.endsWith('p') && t.dropLast(1).all(Char::isDigit)) return true
        return t.length == 4 && t.all(Char::isDigit) && (t.toIntOrNull() ?: 0) in 1900..2099
    }

    /** `s\d{1,2}e\d{1,3}`, `k\d{1,2}` or `j\d{1,3}`, the whole token. */
    private fun isMarkerToken(t: String): Boolean {
        if (t.isEmpty()) return false
        fun digits(from: Int, to: Int) = from < to && (from until to).all { t[it] in '0'..'9' }
        return when (t[0]) {
            'k' -> t.length in 2..3 && digits(1, t.length)
            'j' -> t.length in 2..4 && digits(1, t.length)
            's' -> {
                val e = t.indexOf('e')
                e in 2..3 && digits(1, e) && t.length - e - 1 in 1..3 && digits(e + 1, t.length)
            }
            else -> false
        }
    }

    /**
     * `(?<![a-z0-9])(?:(?:19|20)\d{2}|\d{3,4}p|4k|uhd|hdr10\+?|hdr|dolby ?vision|dovi|x26[45]|hevc|
     * h ?26[45]|bluray|bdrip|webrip|web ?dl|hdtv|remux)(?![a-z0-9])` → space, on a normalised title.
     */
    internal fun stripTechnical(s: String): String {
        var out: StringBuilder? = null
        var copied = 0
        var i = 0
        while (i < s.length) {
            if (i == 0 || !isAsciiAlnum(s[i - 1])) {
                val end = technicalEnd(s, i)
                if (end > 0) {
                    val b = out ?: StringBuilder(s.length).also { out = it }
                    b.append(s, copied, i).append(' ')
                    i = end
                    copied = end
                    continue
                }
            }
            i++
        }
        val b = out ?: return s
        return b.append(s, copied, s.length).toString()
    }

    /** The end of the first alternative at [i] followed by a non-alphanumeric, or -1. */
    private fun technicalEnd(s: String, i: Int): Int {
        fun ok(end: Int) = end <= s.length && (end == s.length || !isAsciiAlnum(s[end]))
        fun lit(at: Int, w: String) = s.startsWith(w, at)
        // (19|20)\d{2}
        if ((lit(i, "19") || lit(i, "20")) && i + 4 <= s.length && s[i + 2] in '0'..'9' && s[i + 3] in '0'..'9' && ok(i + 4)) return i + 4
        // \d{3,4}p, four digits first
        for (n in intArrayOf(4, 3)) {
            if (i + n < s.length && (i until i + n).all { s[it] in '0'..'9' } && s[i + n] == 'p' && ok(i + n + 1)) return i + n + 1
        }
        for (w in listOf("4k", "uhd", "hdr10+", "hdr10", "hdr", "dolby vision", "dolbyvision", "dovi", "x264", "x265", "hevc", "h 264", "h 265", "h264", "h265", "bluray", "bdrip", "webrip", "web dl", "webdl", "hdtv", "remux")) {
            if (lit(i, w) && ok(i + w.length)) return i + w.length
        }
        return -1
    }

    private fun isAsciiAlnum(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9'

    private fun withoutMarks(s: String): String {
        if (s.none { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }) return s
        val b = StringBuilder(s.length)
        for (c in s) if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) b.append(c)
        return b.toString()
    }

    /** Runs of two or more `\s` become one space. */
    private fun collapseRepeatedSpaces(s: String): String {
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (isSpace(c) && i + 1 < s.length && isSpace(s[i + 1])) {
                while (i < s.length && isSpace(s[i])) i++
                b.append(' ')
                continue
            }
            b.append(c)
            i++
        }
        return b.toString()
    }

    private fun splitOnSpaces(s: String): List<String> {
        val out = ArrayList<String>()
        var start = -1
        for (i in s.indices) {
            if (isSpace(s[i])) {
                if (start >= 0) out += s.substring(start, i)
                start = -1
            } else if (start < 0) {
                start = i
            }
        }
        if (start >= 0) out += s.substring(start)
        return out
    }

    private fun isSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

    private val TECHNICAL_TOKENS = setOf(
        "4k", "uhd", "fhd", "hd", "hdr", "hdr10", "hdr10+", "dolby", "vision", "dv", "dovi", "hevc", "x264", "x265", "h264", "h265",
        "bluray", "bdrip", "webrip", "hdtv", "remux", "multi", "subs", "subtitles", "audio",
    )
    private val PREFIX_TOKENS = setOf("fi", "fin", "en", "eng", "sv", "swe", "da", "dan", "no", "nor", "de", "ger", "fr", "fre", "es", "spa", "nc", "nordic", "vip", "vod") + TECHNICAL_TOKENS
}
