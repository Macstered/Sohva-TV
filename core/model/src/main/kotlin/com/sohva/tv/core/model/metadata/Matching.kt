package com.sohva.tv.core.model.metadata

import com.sohva.tv.core.model.vod.Genre
import kotlin.math.abs
import kotlin.math.max

/** What a lookup asks for (spec 41 META-FR-15). */
enum class MediaType(val wire: String) {
    MOVIE("movie"), SERIES("series"), EPISODE("episode"), PROGRAMME("programme")
}

/** A lookup after sanitising (META-FR-16): the cleaned title and the numbers in range. */
data class Lookup(
    val type: MediaType,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/** One provider result the matcher weighs (META-FR-24…27). */
data class Candidate(
    val externalId: String,
    val type: MediaType,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val popularity: Double? = null,
)

/** A chosen candidate and its score. */
data class Match(val candidate: Candidate, val confidence: Double)

/**
 * Conservative automatic matching (spec 41 §4.5): 0.88 title + 0.08 year + 0.04 episode, a
 * 0.92 floor and a 0.08 lead over the runner-up, which popularity may break only for an exact
 * title. A wrong poster is worse than none, so recall is not "improved" (lesson 9).
 */
object Matcher {
    const val MIN_CONFIDENCE: Double = 0.92
    const val MIN_LEAD: Double = 0.08
    private const val EPSILON = 0.000_001

    fun choose(lookup: Lookup, candidates: List<Candidate>): Match? {
        val source = TitleCleaner.normalizeTitle(lookup.title)
        val ranked = candidates.withIndex()
            .filter { (_, c) -> typeFits(lookup.type, c.type) && episodeFits(lookup, c) }
            .map { (i, c) -> Triple(Match(c, score(lookup, source, c)), c.popularity ?: Double.NEGATIVE_INFINITY, i) }
            .sortedWith(compareByDescending<Triple<Match, Double, Int>> { it.first.confidence }.thenByDescending { it.second }.thenBy { it.third })
        val best = ranked.firstOrNull()?.first ?: return null
        if (best.confidence + EPSILON < MIN_CONFIDENCE) return null
        val runnerUp = ranked.getOrNull(1)?.first
        if (runnerUp != null && best.confidence - runnerUp.confidence + EPSILON < MIN_LEAD && !popularityBreaksTie(source, best, runnerUp)) return null
        return best
    }

    private fun score(lookup: Lookup, source: String, c: Candidate): Double {
        val title = (listOf(c.title) + c.alternativeTitles).maxOf { similarity(source, TitleCleaner.normalizeTitle(it)) }
        val year = when {
            lookup.year == null -> 1.0
            c.year == null -> 0.6
            lookup.year == c.year -> 1.0
            abs(lookup.year - c.year) == 1 -> 0.45
            else -> 0.0
        }
        val episode = if (lookup.type != MediaType.EPISODE || (lookup.season == c.season && lookup.episode == c.episode)) 1.0 else 0.0
        return (0.88 * title + 0.08 * year + 0.04 * episode).coerceIn(0.0, 1.0)
    }

    /** The larger of the token Dice coefficient and the normalised edit similarity (META-FR-25). */
    internal fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val left = a.split(' ').filter { it.isNotBlank() }.toSet()
        val right = b.split(' ').filter { it.isNotBlank() }.toSet()
        val dice = if (left.isEmpty() || right.isEmpty()) 0.0 else 2.0 * left.intersect(right).size / (left.size + right.size)
        val edit = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
        return max(dice, edit.coerceAtLeast(0.0))
    }

    private fun popularityBreaksTie(source: String, best: Match, runnerUp: Match): Boolean {
        val exact = (listOf(best.candidate.title) + best.candidate.alternativeTitles).any { TitleCleaner.normalizeTitle(it) == source }
        if (!exact) return false
        val b = best.candidate.popularity?.takeIf { it >= 0.0 } ?: return false
        val r = runnerUp.candidate.popularity?.takeIf { it >= 0.0 } ?: return false
        if (b <= r) return false
        return b - r >= 10.0 || (r > 0.0 && b / r >= 1.5)
    }

    private fun typeFits(asked: MediaType, found: MediaType) = asked == found || (asked == MediaType.PROGRAMME && (found == MediaType.MOVIE || found == MediaType.SERIES))

    private fun episodeFits(lookup: Lookup, c: Candidate) = lookup.type != MediaType.EPISODE || (lookup.season == c.season && lookup.episode == c.episode)

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            }
            val t = previous
            previous = current
            current = t
        }
        return previous[b.length]
    }
}

/**
 * TMDB genre ids → the one primary genre of a title (spec 41 §4.10): the first id in the
 * response's order that the table knows. Kids is Family; "TV Movie" (10770) is not a genre.
 */
object TmdbGenres {
    private val FILM = mapOf(
        28 to Genre.ACTION, 12 to Genre.ADVENTURE, 16 to Genre.ANIMATION, 35 to Genre.COMEDY, 80 to Genre.CRIME, 99 to Genre.DOCUMENTARY,
        18 to Genre.DRAMA, 10751 to Genre.FAMILY, 14 to Genre.FANTASY, 36 to Genre.HISTORY, 27 to Genre.HORROR, 10402 to Genre.MUSIC,
        9648 to Genre.MYSTERY, 10749 to Genre.ROMANCE, 878 to Genre.SCIENCE_FICTION, 53 to Genre.THRILLER, 10752 to Genre.WAR, 37 to Genre.WESTERN,
    )
    private val TELEVISION = mapOf(
        10759 to Genre.ACTION, 16 to Genre.ANIMATION, 35 to Genre.COMEDY, 80 to Genre.CRIME, 99 to Genre.DOCUMENTARY, 18 to Genre.DRAMA,
        10751 to Genre.FAMILY, 10762 to Genre.FAMILY, 9648 to Genre.MYSTERY, 10763 to Genre.NEWS, 10764 to Genre.REALITY,
        10765 to Genre.SCIENCE_FICTION, 10766 to Genre.SOAP, 10767 to Genre.TALK, 10768 to Genre.WAR, 37 to Genre.WESTERN,
    )

    fun primary(type: MediaType, ids: List<Int>): Genre? = ids.firstNotNullOfOrNull { id ->
        when (type) {
            MediaType.MOVIE -> FILM[id]
            MediaType.SERIES, MediaType.EPISODE -> TELEVISION[id]
            MediaType.PROGRAMME -> FILM[id] ?: TELEVISION[id]
        }
    }
}
