package com.streammate.tv.iptv.metadata

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

internal data class MetadataMatch(
    val candidate: MetadataCandidate,
    val confidence: Double,
)

private data class RankedMetadataMatch(
    val match: MetadataMatch,
    val providerOrder: Int,
)

internal object MetadataMatcher {
    const val MIN_CONFIDENCE = 0.92
    const val MIN_RUNNER_UP_MARGIN = 0.08

    fun choose(lookup: MetadataLookup, candidates: List<MetadataCandidate>): MetadataMatch? {
        val ranked = candidates
            .withIndex()
            .asSequence()
            .filter { (_, candidate) ->
                candidateMatchesType(lookup.mediaType, candidate.mediaType) &&
                    candidateMatchesEpisode(lookup, candidate)
            }
            .map { (index, candidate) ->
                RankedMetadataMatch(MetadataMatch(candidate, score(lookup, candidate)), index)
            }
            .sortedWith(
                compareByDescending<RankedMetadataMatch> { it.match.confidence }
                    .thenByDescending { it.match.candidate.providerPopularity ?: Double.NEGATIVE_INFINITY }
                    .thenBy { it.providerOrder },
            )
            .toList()
        val best = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)
        if (best.match.confidence + SCORE_EPSILON < MIN_CONFIDENCE) return null
        if (
            runnerUp != null &&
            best.match.confidence - runnerUp.match.confidence + SCORE_EPSILON < MIN_RUNNER_UP_MARGIN &&
            !providerPopularityResolvesTie(lookup, best.match, runnerUp.match)
        ) {
            return null
        }
        return best.match
    }

    fun normalizeTitle(value: String): String = Normalizer.normalize(
        removeDecorationGroups(value),
        Normalizer.Form.NFKD,
    )
        .lowercase()
        .replace(PROVIDER_LANGUAGE_PREFIX, " ")
        .replace(PROVIDER_DECORATION_PREFIX, " ")
        .replace(SEASON_EPISODE_SUFFIX, " ")
        .replace(TECHNICAL_TAG, " ")
        .replace(YEAR_SUFFIX, " ")
        .replace(TRAILING_TECHNICAL_SUFFIX, " ")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(MULTIPLE_SPACES, " ")

    fun searchTitle(value: String): String = removeDecorationGroups(value)
        .replace(PROVIDER_LANGUAGE_PREFIX, " ")
        .replace(PROVIDER_DECORATION_PREFIX, " ")
        .replace(SEASON_EPISODE_SUFFIX, " ")
        .replace(TECHNICAL_TAG, " ")
        .replace(YEAR_SUFFIX, " ")
        .replace(TRAILING_TECHNICAL_SUFFIX, " ")
        .trim(' ', '-', '–', '—', ':')
        .replace(MULTIPLE_SPACES, " ")

    fun yearFromTitle(value: String): Int? = YEAR_SUFFIX.find(value)
        ?.value
        ?.filter(Char::isDigit)
        ?.toIntOrNull()

    private fun removeDecorationGroups(value: String): String {
        val stripped = value.replace(DECORATION_GROUP, " ")
        if (stripped.any(Char::isLetterOrDigit)) return stripped
        return value.replace(DECORATION_DELIMITER, " ")
    }

    private fun score(lookup: MetadataLookup, candidate: MetadataCandidate): Double {
        val sourceTitle = normalizeTitle(lookup.title)
        val titleScore = (listOf(candidate.matchingTitle) + candidate.alternativeTitles)
            .maxOfOrNull { titleSimilarity(sourceTitle, normalizeTitle(it)) }
            ?: 0.0
        val yearScore = when {
            lookup.year == null -> 1.0
            candidate.year == null -> 0.6
            lookup.year == candidate.year -> 1.0
            abs(lookup.year - candidate.year) == 1 -> 0.45
            else -> 0.0
        }
        val episodeScore = when {
            lookup.mediaType != MetadataMediaType.EPISODE -> 1.0
            lookup.seasonNumber == candidate.seasonNumber &&
                lookup.episodeNumber == candidate.episodeNumber -> 1.0
            else -> 0.0
        }
        return (TITLE_WEIGHT * titleScore + YEAR_WEIGHT * yearScore + EPISODE_WEIGHT * episodeScore)
            .coerceIn(0.0, 1.0)
    }

    private fun titleSimilarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        val leftTokens = left.split(' ').filter(String::isNotBlank).toSet()
        val rightTokens = right.split(' ').filter(String::isNotBlank).toSet()
        val dice = if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            0.0
        } else {
            2.0 * leftTokens.intersect(rightTokens).size / (leftTokens.size + rightTokens.size)
        }
        val edit = 1.0 - levenshtein(left, right).toDouble() / max(left.length, right.length)
        return max(dice, edit.coerceAtLeast(0.0))
    }

    private fun providerPopularityResolvesTie(
        lookup: MetadataLookup,
        best: MetadataMatch,
        runnerUp: MetadataMatch,
    ): Boolean {
        val lookupTitle = normalizeTitle(lookup.title)
        val bestHasExactTitle = (listOf(best.candidate.matchingTitle) + best.candidate.alternativeTitles)
            .any { normalizeTitle(it) == lookupTitle }
        if (!bestHasExactTitle) return false
        val bestPopularity = best.candidate.providerPopularity?.takeIf { it >= 0.0 } ?: return false
        val runnerUpPopularity = runnerUp.candidate.providerPopularity?.takeIf { it >= 0.0 } ?: return false
        if (bestPopularity <= runnerUpPopularity) return false
        return bestPopularity - runnerUpPopularity >= MIN_POPULARITY_GAP ||
            (runnerUpPopularity > 0.0 && bestPopularity / runnerUpPopularity >= MIN_POPULARITY_RATIO)
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun candidateMatchesType(requested: MetadataMediaType, candidate: MetadataMediaType): Boolean =
        requested == candidate || requested == MetadataMediaType.PROGRAMME

    private fun candidateMatchesEpisode(
        lookup: MetadataLookup,
        candidate: MetadataCandidate,
    ): Boolean = lookup.mediaType != MetadataMediaType.EPISODE ||
        (lookup.seasonNumber == candidate.seasonNumber && lookup.episodeNumber == candidate.episodeNumber)

    private const val TITLE_WEIGHT = 0.88
    private const val YEAR_WEIGHT = 0.08
    private const val EPISODE_WEIGHT = 0.04
    private const val SCORE_EPSILON = 0.000_001
    private const val MIN_POPULARITY_GAP = 10.0
    private const val MIN_POPULARITY_RATIO = 1.5
    private val DECORATION_GROUP = Regex(
        "\\[[^\\[\\]\\r\\n]*\\]|\\([^()\\r\\n]*\\)|\\{[^{}\\r\\n]*\\}",
    )
    private val DECORATION_DELIMITER = Regex("[\\[\\](){}]")
    private val PROVIDER_LANGUAGE_PREFIX = Regex(
        "(?i)^\\s*(?:[\\[(](?:fi|fin|en|eng|sv|swe|da|dan|no|nor|de|ger|fr|fre|es|spa)[\\])]" +
            "|(?:fin|eng|swe|dan|nor|ger|fre|spa)\\s*[|•·-])\\s*",
    )
    private val PROVIDER_DECORATION_PREFIX = Regex(
        "(?i)^\\s*(?:(?:4k|uhd|fhd|hd|hdr(?:10\\+?)?|dolby\\s*vision|dv|x26[45]|hevc|" +
            "nc|nordic|multi(?:[- ]?(?:subs?|subtitles?|audio))?|vip|vod|" +
            "fi|fin|en|eng|sv|swe|da|dan|no|nor|de|ger|fr|fre|es|spa)" +
            "(?=\\s|[|•·:–—-]|$)" +
            "\\s*(?:[|•·:–—-]+\\s*)?)+(?=\\S)",
    )
    private val SEASON_EPISODE_SUFFIX = Regex("(?i)\\b(?:s\\d{1,2}e\\d{1,3}|k\\d{1,2}\\s*j\\d{1,3})\\b")
    private val YEAR_SUFFIX = Regex("(?:[\\[(](?:19|20)\\d{2}[\\])]|\\s+(?:19|20)\\d{2})\\s*$")
    private val TECHNICAL_TAG = Regex(
        "(?i)\\[(?:multi[- ]?(?:subs?|subtitles?|audio)(?:\\s*&\\s*(?:subs?|subtitles?|audio))?" +
            "|imdb(?:\\s*(?:top\\s*\\d+|\\d+(?:\\.\\d+)?))?" +
            "|4k|uhd|fhd|hd|hdr(?:10\\+?)?|dolby\\s*vision|only\\s+on[^]]+devices?|x26[45]|hevc)\\]",
    )
    private val TRAILING_TECHNICAL_SUFFIX = Regex(
        "(?i)(?:\\s*(?:[|•·]|[-–—])\\s*)?" +
            "(?:4k|uhd|fhd|hd|hdr(?:10\\+?)?|dolby\\s*vision|x26[45]|hevc|" +
            "multi(?:[- ]?(?:subs?|subtitles?|audio))?(?:\\s*&\\s*(?:subs?|subtitles?|audio))?)" +
            "(?:\\s+(?:4k|uhd|fhd|hd|hdr(?:10\\+?)?|x26[45]|hevc))*\\s*$",
    )
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val MULTIPLE_SPACES = Regex("\\s+")
}
