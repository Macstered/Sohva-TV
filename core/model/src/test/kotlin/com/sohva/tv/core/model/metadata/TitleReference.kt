package com.sohva.tv.core.model.metadata

import java.text.Normalizer

/**
 * Beta 23's title cleaning as regular expressions (spec 41 §4.4): the definition the hand-written
 * [TitleCleaner] and [WorkKeys] must equal. Test code only (§9.1). One adjustment: beta 23 ran on
 * Android, whose regex engine (ICU) counts every letter as a word character for `\b`; the JVM's
 * `\b` is ASCII-only since JDK 19, so the word boundary is written out explicitly here.
 */
object TitleReference {
    fun normalizeTitle(value: String): String = Normalizer.normalize(removeDecorationGroups(value), Normalizer.Form.NFKD)
        .lowercase()
        .replace(LANGUAGE_PREFIX, " ")
        .replace(DECORATION_PREFIX, " ")
        .replace(SEASON_EPISODE, " ")
        .replace(TECHNICAL_TAG, " ")
        .replace(YEAR_SUFFIX, " ")
        .replace(TRAILING_TECHNICAL, " ")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(SPACES, " ")

    fun searchTitle(value: String): String = removeDecorationGroups(value)
        .replace(LANGUAGE_PREFIX, " ")
        .replace(DECORATION_PREFIX, " ")
        .replace(SEASON_EPISODE, " ")
        .replace(TECHNICAL_TAG, " ")
        .replace(YEAR_SUFFIX, " ")
        .replace(TRAILING_TECHNICAL, " ")
        .trim(' ', '-', '–', '—', ':')
        .replace(SPACES, " ")

    fun yearFromTitle(value: String): Int? = YEAR_SUFFIX.find(value)?.value?.filter(Char::isDigit)?.toIntOrNull()

    fun workKey(title: String, year: Int?, externalId: String? = null): String {
        externalId?.trim()?.takeIf(String::isNotBlank)?.let { return "tmdb:$it" }
        simpleIdentity(title)?.let { return "name:$it:${year ?: ""}" }
        val bare = searchTitle(title).ifBlank { title }
        val plain = Normalizer.normalize(bare, Normalizer.Form.NFKD).replace(MARKS, "")
        val normalized = normalizeTitle(plain)
        val stripped = normalized.replace(TECHNICAL_TOKEN, " ").trim().replace(REPEATED_SPACE, " ")
        val identity = stripped.ifBlank { normalized }.ifBlank { title.trim().lowercase() }
        return "name:$identity:${year ?: yearFromTitle(title) ?: ""}"
    }

    private fun simpleIdentity(title: String): String? {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { !it.isLetterOrDigit() && !it.isWhitespace() }) return null
        val tokens = trimmed.split(SPACES).filter(String::isNotBlank)
        if ((tokens.size > 1 && tokens.first().lowercase() in PREFIX_TOKENS) || tokens.any(::requiresFull)) return null
        return Normalizer.normalize(trimmed, Normalizer.Form.NFKD).replace(MARKS, "").lowercase().trim().replace(REPEATED_SPACE, " ")
            .takeIf(String::isNotBlank)
    }

    private fun requiresFull(token: String): Boolean {
        val t = token.lowercase()
        return t in TECHNICAL_TOKENS || t.matches(MARKER_TOKEN) ||
            t.removeSuffix("p").let { d -> t.endsWith('p') && d.length in 3..4 && d.all(Char::isDigit) } ||
            (t.length == 4 && t.all(Char::isDigit) && t.toInt() in 1900..2099)
    }

    private fun removeDecorationGroups(value: String): String {
        val stripped = value.replace(DECORATION_GROUP, " ")
        if (stripped.any(Char::isLetterOrDigit)) return stripped
        return value.replace(DECORATION_DELIMITER, " ")
    }

    private const val WORD = "[\\p{L}\\p{Nd}\\p{Mn}_]"
    private val DECORATION_GROUP = Regex("\\[[^\\[\\]\\r\\n]*\\]|\\([^()\\r\\n]*\\)|\\{[^{}\\r\\n]*\\}")
    private val DECORATION_DELIMITER = Regex("[\\[\\](){}]")
    private val LANGUAGE_PREFIX = Regex(
        "(?i)^\\s*(?:[\\[(](?:fi|fin|en|eng|sv|swe|da|dan|no|nor|de|ger|fr|fre|es|spa)[\\])]" +
            "|(?:fin|eng|swe|dan|nor|ger|fre|spa)\\s*[|•·-])\\s*",
    )
    private val DECORATION_PREFIX = Regex(
        "(?i)^\\s*(?:(?:4k|uhd|fhd|hd|hdr(?:10\\+?)?|dolby\\s*vision|dv|x26[45]|hevc|" +
            "nc|nordic|multi(?:[- ]?(?:subs?|subtitles?|audio))?|vip|vod|" +
            "fi|fin|en|eng|sv|swe|da|dan|no|nor|de|ger|fr|fre|es|spa)" +
            "(?=\\s|[|•·:–—-]|$)" +
            "\\s*(?:[|•·:–—-]+\\s*)?)+(?=\\S)",
    )
    private val SEASON_EPISODE = Regex("(?i)(?<!$WORD)(?:s\\d{1,2}e\\d{1,3}|k\\d{1,2}\\s*j\\d{1,3})(?!$WORD)")
    private val YEAR_SUFFIX = Regex("(?:[\\[(](?:19|20)\\d{2}[\\])]|\\s+(?:19|20)\\d{2})\\s*$")
    private val TECHNICAL_TAG = Regex(
        "(?i)\\[(?:multi[- ]?(?:subs?|subtitles?|audio)(?:\\s*&\\s*(?:subs?|subtitles?|audio))?" +
            "|imdb(?:\\s*(?:top\\s*\\d+|\\d+(?:\\.\\d+)?))?" +
            "|4k|uhd|fhd|hd|hdr(?:10\\+?)?|dolby\\s*vision|only\\s+on[^]]+devices?|x26[45]|hevc)\\]",
    )
    private val TRAILING_TECHNICAL = Regex(
        "(?i)(?:\\s*(?:[|•·]|[-–—])\\s*)?" +
            "(?:4k|uhd|fhd|hd|hdr(?:10\\+?)?|dolby\\s*vision|x26[45]|hevc|" +
            "multi(?:[- ]?(?:subs?|subtitles?|audio))?(?:\\s*&\\s*(?:subs?|subtitles?|audio))?)" +
            "(?:\\s+(?:4k|uhd|fhd|hd|hdr(?:10\\+?)?|x26[45]|hevc))*\\s*$",
    )
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val SPACES = Regex("\\s+")
    private val MARKS = Regex("\\p{Mn}+")
    private val REPEATED_SPACE = Regex("\\s{2,}")
    private val TECHNICAL_TOKEN = Regex(
        "(?<![a-z0-9])(?:(?:19|20)\\d{2}|\\d{3,4}p|4k|uhd|hdr10\\+?|hdr|dolby ?vision|dovi|x26[45]|hevc|h ?26[45]" +
            "|bluray|bdrip|webrip|web ?dl|hdtv|remux)(?![a-z0-9])",
    )
    private val MARKER_TOKEN = Regex("(?:s\\d{1,2}e\\d{1,3}|k\\d{1,2}|j\\d{1,3})")
    private val TECHNICAL_TOKENS = setOf(
        "4k", "uhd", "fhd", "hd", "hdr", "hdr10", "hdr10+", "dolby", "vision", "dv", "dovi", "hevc", "x264", "x265", "h264", "h265",
        "bluray", "bdrip", "webrip", "hdtv", "remux", "multi", "subs", "subtitles", "audio",
    )
    private val PREFIX_TOKENS = setOf("fi", "fin", "en", "eng", "sv", "swe", "da", "dan", "no", "nor", "de", "ger", "fr", "fre", "es", "spa", "nc", "nordic", "vip", "vod") +
        TECHNICAL_TOKENS
}
