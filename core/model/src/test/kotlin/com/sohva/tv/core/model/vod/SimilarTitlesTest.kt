package com.sohva.tv.core.model.vod

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 40 VOD-FR-71: which library films stand for TMDB's similar titles. */
class SimilarTitlesTest {
    private fun ref(id: String, title: String, year: Int?, vararg alt: String) = SimilarReference(id, title, alt.toList(), year, null)

    private fun film(key: String, source: String, year: Int?, similar: String?, replacement: String? = null) =
        SimilarCandidate(key, source, year, similar, replacement)

    @Test
    fun keysAreCleanedNormalisedAndAtLeastTwoCharacters() {
        // NFKD leaves the accent as a separate mark, which becomes a space (META-FR-21).
        assertEquals(setOf("heat", "la fie vre"), SimilarTitles.keys(ref("1", "Heat", 1995, "La Fièvre", "X")))
    }

    @Test
    fun theSameSourceWinsThenTheSameYear() {
        val picked = SimilarTitles.pick(
            "me", "a",
            listOf(ref("1", "Heat", 1995)),
            listOf(film("b-1995", "b", 1995, "heat"), film("a-none", "a", null, "heat"), film("a-1995", "a", 1995, "heat")),
        )
        assertEquals(listOf("a-1995"), picked.map { it.second.key })
    }

    @Test
    fun aDifferentYearDoesNotMatchButAMissingOneDoes() {
        val picked = SimilarTitles.pick(
            "me", "a",
            listOf(ref("1", "Heat", 1995), ref("2", "Ronin", 1998)),
            listOf(film("heat-1986", "a", 1986, "heat"), film("ronin", "a", null, null, "ronin")),
        )
        assertEquals(listOf("ronin"), picked.map { it.second.key })
    }

    @Test
    fun theCurrentFilmAndUsedFilmsAreSkippedAndTwelveIsTheLimit() {
        val refs = (1..20).map { ref("$it", "Title $it", null) } + ref("dup", "Title 1", null)
        val films = (1..20).map { film("f$it", "a", null, "title $it") } + film("me", "a", null, "title 1")
        val picked = SimilarTitles.pick("me", "a", refs, films)
        assertEquals((1..12).map { "f$it" }, picked.map { it.second.key })
        val again = SimilarTitles.pick("me", "a", listOf(ref("1", "Title 1", null), ref("dup", "Title 1", null)), films)
        assertEquals(listOf("f1"), again.map { it.second.key })
    }
}
