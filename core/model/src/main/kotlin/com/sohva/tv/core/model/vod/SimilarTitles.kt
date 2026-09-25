package com.sohva.tv.core.model.vod

import com.sohva.tv.core.model.metadata.TitleCleaner

/** A film TMDB calls similar (spec 41 META-FR-37): its id, titles, year and poster path. */
data class SimilarReference(
    val externalId: String,
    val title: String,
    val alternativeTitles: List<String>,
    val year: Int?,
    val poster: String?,
)

/** A library film that may stand for a reference: its comparison keys, year and source. */
data class SimilarCandidate(
    val key: String,
    val sourceId: String,
    val year: Int?,
    val similarKey: String?,
    val replacementKey: String?,
)

/**
 * Which library films stand for TMDB's similar titles (spec 40 VOD-FR-71). Pure, so the rule is
 * tested on the JVM; the candidates come from one indexed read of the comparison keys.
 */
object SimilarTitles {
    const val REFERENCES: Int = 20
    const val LIMIT: Int = 12

    /** The comparison keys of a reference: its title and alternatives cleaned and normalised, 2 characters or more. */
    fun keys(reference: SimilarReference): Set<String> =
        (listOf(reference.title) + reference.alternativeTitles).map(TitleCleaner::normalizeTitle).filter { it.length >= 2 }.toSet()

    /**
     * For each of the first 20 references in order: the candidates whose provider or replacement
     * key is one of its keys and whose year agrees (or either is missing); the same source as the
     * current film first, then the same year; the first not used yet (the current film counts as
     * used). At most 12 pairs.
     */
    fun pick(
        currentKey: String,
        currentSource: String,
        references: List<SimilarReference>,
        candidates: List<SimilarCandidate>,
    ): List<Pair<SimilarReference, SimilarCandidate>> {
        val used = hashSetOf(currentKey)
        val picked = ArrayList<Pair<SimilarReference, SimilarCandidate>>()
        for (reference in references.take(REFERENCES)) {
            if (picked.size == LIMIT) break
            val keys = keys(reference)
            if (keys.isEmpty()) continue
            val best = candidates.asSequence()
                .filter { it.key !in used && (it.similarKey in keys || it.replacementKey in keys) }
                .filter { it.year == null || reference.year == null || it.year == reference.year }
                .sortedWith(
                    compareBy<SimilarCandidate> { if (it.sourceId == currentSource) 0 else 1 }
                        .thenBy { if (it.year != null && it.year == reference.year) 0 else 1 },
                )
                .firstOrNull() ?: continue
            used += best.key
            picked += reference to best
        }
        return picked
    }
}
