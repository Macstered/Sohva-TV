package com.streammate.tv.iptv.metadata

import java.text.Normalizer

/**
 * Which film a copy is of.
 *
 * Two playlists carrying the same catalogue give two of everything, under names
 * that agree about the film and disagree about everything else:
 * `FIN | The Matrix (1999) 4K` and `The Matrix 1999 [MULTI-SUBS] 1080p`. This
 * reduces both to the same answer.
 *
 * It starts from the reduction the metadata matcher already performs to decide
 * what to ask TMDB about - proven against this library, and it means grouping
 * agrees with the artwork already on screen rather than forming a second
 * opinion about what things are called - and then goes further, because the two
 * jobs want different things. A search title is handed to a provider, so
 * removing a word that might be part of the real name costs a match. A grouping
 * key is only ever compared against another one, so anything that is plainly
 * not part of a title can go.
 *
 * [externalId] wins where it is known. A provider's year is a guess often
 * enough that two copies of one film can disagree about it, and the record TMDB
 * settled on is a stronger statement than any normalised string.
 *
 * Scoped to films. Series duplicates need matching at the episode level and are
 * a harder problem left alone for now.
 */
fun catalogueWorkKey(
    title: String,
    year: Int?,
    externalId: String? = null,
): String {
    externalId?.trim()?.takeIf(String::isNotBlank)?.let { return "tmdb:$it" }
    // Most provider rows are already ordinary titles. Running the complete
    // metadata matcher over several thousand of those made first-open copy
    // folding take seconds on Shield. This fast path is deliberately narrow:
    // punctuation, years and every technical token fall through to the proven
    // cleanup below, while clean words normalize to the exact same identity.
    simpleTitleIdentity(title)?.let { identity ->
        return "name:$identity:${year ?: ""}"
    }
    val bare = MetadataMatcher.searchTitle(title).ifBlank { title }
    // Accents are decomposed by the matcher's own normalisation and then the
    // combining marks become spaces, so AMELIE and AMÉLIE part company as
    // "amelie" and "ame lie". Taking the marks off first keeps them together.
    val plain = Normalizer.normalize(bare, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "")
    val normalized = MetadataMatcher.normalizeTitle(plain)
    // What survives that but is still nobody's title: a year sitting in the
    // middle of a name, a resolution, a codec. The matcher only takes these off
    // the end, which is enough to search with and not enough to group by.
    val stripped = normalized
        .replace(TECHNICAL_TOKEN, " ")
        .trim()
        .replace(REPEATED_SPACE, " ")
    // Unless that leaves nothing at all. A name that is only decoration is a
    // poor key, but an empty one would make every such title the same film.
    val identity = stripped.ifBlank { normalized }.ifBlank { title.trim().lowercase() }
    val resolvedYear = year ?: MetadataMatcher.yearFromTitle(title)
    return "name:$identity:${resolvedYear ?: ""}"
}

private fun simpleTitleIdentity(title: String): String? {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.any { !it.isLetterOrDigit() && !it.isWhitespace() }) return null
    val tokens = trimmed.split(SIMPLE_WHITESPACE).filter(String::isNotBlank)
    if (
        (tokens.size > 1 && tokens.first().lowercase() in PROVIDER_PREFIX_TOKENS) ||
        tokens.any(::requiresFullNormalization)
    ) {
        return null
    }
    return Normalizer.normalize(trimmed, Normalizer.Form.NFKD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .trim()
        .replace(REPEATED_SPACE, " ")
        .takeIf(String::isNotBlank)
}

private fun requiresFullNormalization(token: String): Boolean {
    val normalized = token.lowercase()
    return normalized in TECHNICAL_TOKENS ||
        normalized.matches(SEASON_EPISODE_TOKEN) ||
        normalized.removeSuffix("p").let { digits ->
            normalized.endsWith('p') && digits.length in 3..4 && digits.all(Char::isDigit)
        } ||
        normalized.let { value ->
            value.length == 4 && value.all(Char::isDigit) &&
                value.toInt() in MIN_TECHNICAL_YEAR..MAX_TECHNICAL_YEAR
        }
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * Only tokens that cannot be part of a film's name. Language words are
 * deliberately absent: stripping "fin" would take the first word off
 * *Fin del mundo*, and the matcher has already removed those where they appear
 * as a prefix, which is where providers put them.
 */
private val TECHNICAL_TOKEN = Regex(
    "(?<![a-z0-9])(?:" +
        "(?:19|20)\\d{2}" +
        "|\\d{3,4}p" +
        "|4k|uhd|hdr10\\+?|hdr|dolby ?vision|dovi" +
        "|x26[45]|hevc|h ?26[45]" +
        "|bluray|bdrip|webrip|web ?dl|hdtv|remux" +
        ")(?![a-z0-9])",
)

private val REPEATED_SPACE = Regex("\\s{2,}")
private val SIMPLE_WHITESPACE = Regex("\\s+")
private val TECHNICAL_TOKENS = setOf(
    "4k",
    "uhd",
    "fhd",
    "hd",
    "hdr",
    "hdr10",
    "hdr10+",
    "dolby",
    "vision",
    "dv",
    "dovi",
    "hevc",
    "x264",
    "x265",
    "h264",
    "h265",
    "bluray",
    "bdrip",
    "webrip",
    "hdtv",
    "remux",
    "multi",
    "subs",
    "subtitles",
    "audio",
)
private val PROVIDER_PREFIX_TOKENS = setOf(
    "fi",
    "fin",
    "en",
    "eng",
    "sv",
    "swe",
    "da",
    "dan",
    "no",
    "nor",
    "de",
    "ger",
    "fr",
    "fre",
    "es",
    "spa",
    "nc",
    "nordic",
    "vip",
    "vod",
) + TECHNICAL_TOKENS
private val SEASON_EPISODE_TOKEN = Regex("(?:s\\d{1,2}e\\d{1,3}|k\\d{1,2}|j\\d{1,3})")
private const val MIN_TECHNICAL_YEAR = 1900
private const val MAX_TECHNICAL_YEAR = 2099
