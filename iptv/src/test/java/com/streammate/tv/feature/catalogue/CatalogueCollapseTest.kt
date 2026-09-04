package com.streammate.tv.feature.catalogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names here are the shapes two playlists give one film, and the question
 * each test asks is what the wall ends up holding.
 */
class CatalogueCollapseTest {

    @Test
    fun twoCopiesOfOneFilmBecomeOneCard() {
        val films = collapse(
            copy("a", "FIN | The Matrix (1999) 4K", 1999),
            copy("b", "The Matrix 1999 [MULTI-SUBS] 1080p", 1999),
            copy("c", "Quiet Harbour", 2021),
        )

        assertEquals(listOf("a", "c"), films.films.map { it.contentKey })
    }

    /**
     * A film is stood for by one of its own copies, never by an invented key.
     * Everything already recorded against a content key - a chosen poster, a
     * saved position, a metadata attempt - keeps working only because of that.
     */
    @Test
    fun theCardKeepsTheKeyOfARealCopy() {
        val films = collapse(
            copy("a", "FIN | The Matrix (1999) 4K", 1999),
            copy("b", "The Matrix 1999 [MULTI-SUBS] 1080p", 1999),
        )

        assertEquals("a", films.keyOf("b"))
        assertEquals("a", films.keyOf("a"))
    }

    /** A key nobody folded is its own film, and answers for itself. */
    @Test
    fun aKeyThatWasNeverFoldedAnswersForItself() {
        val films = collapse(copy("a", "The Matrix", 1999), copy("b", "Quiet Harbour", 2021))

        assertEquals("b", films.keyOf("b"))
    }

    /**
     * A library with nothing duplicated in it is handed back untouched, so a
     * wall that has nothing to fold pays for the grouping pass and no more.
     */
    @Test
    fun aLibraryWithNoDuplicatesIsLeftAloneEntirely() {
        val copies = listOf(copy("a", "The Matrix", 1999), copy("b", "Quiet Harbour", 2021))

        val films = catalogueFilms(copies, emptyMap()) { primary, _ -> primary }

        assertSame(copies, films.films)
        assertTrue(films.filmKeyByCopy.isEmpty())
    }

    /** The case phase one's external id exists for. */
    @Test
    fun copiesThatDisagreeAboutTheYearFoldOnceBothHaveMatched() {
        val copies = listOf(copy("a", "The Matrix", 1999), copy("b", "The Matrix", 2000))

        val byName = catalogueFilms(copies, emptyMap()) { primary, _ -> primary }
        val byRecord = catalogueFilms(copies, mapOf("a" to "603", "b" to "603")) { primary, _ -> primary }

        assertEquals(2, byName.films.size)
        assertEquals(1, byRecord.films.size)
    }

    /**
     * What makes collapsing an improvement rather than only a shortening: a
     * poster missing from one playlist arrives from the other.
     */
    @Test
    fun theCardTakesFromItsCopiesWhateverItLacks() {
        val films = catalogueFilms(
            listOf(
                copy("a", "FIN | The Matrix (1999) 4K", 1999, poster = null),
                copy("b", "The Matrix 1999 [MULTI-SUBS] 1080p", 1999, poster = "poster.jpg"),
            ),
            emptyMap(),
        ) { primary, group ->
            primary.copy(poster = primary.poster ?: group.firstNotNullOfOrNull { it.poster })
        }

        assertEquals("poster.jpg", films.films.single().poster)
    }

    /** A copy that was never folded is not handed to the merge at all. */
    @Test
    fun aFilmWithOneCopyIsNotMerged() {
        var merged = 0
        catalogueFilms(
            listOf(copy("a", "The Matrix", 1999), copy("b", "The Matrix", 1999), copy("c", "Quiet Harbour", 2021)),
            emptyMap(),
        ) { primary, _ -> merged += 1; primary }

        assertEquals(1, merged)
    }

    /**
     * Ranking parses provider language and quality claims. It has no effect on
     * a film with one copy, so large libraries with few duplicates must not pay
     * that regex cost for every title.
     */
    @Test
    fun onlyDuplicateGroupsAreRanked() {
        var ranked = 0
        catalogueFilms(
            copies = listOf(
                copy("a", "The Matrix", 1999),
                copy("b", "The Matrix", 1999),
                copy("c", "Quiet Harbour", 2021),
            ),
            externalIds = emptyMap(),
            workKey = { it.title },
            rank = { ranked += 1; 0 },
        ) { primary, _ -> primary }

        assertEquals(2, ranked)
    }

    /**
     * Genres and positions are recorded against whichever copy was enriched or
     * played, so a film would lose what its siblings knew without this.
     */
    @Test
    fun whatWasRecordedAgainstACopyIsFoundAgainstItsFilm() {
        val films = collapse(
            copy("a", "FIN | The Matrix (1999) 4K", 1999),
            copy("b", "The Matrix 1999 [MULTI-SUBS] 1080p", 1999),
        )

        val genres = mapOf("b" to setOf("action")).byFilm(films) { first, second -> first + second }

        assertEquals(mapOf("a" to setOf("action")), genres)
    }

    /** Where both copies know something, the merge settles what the film gets. */
    @Test
    fun theMergeSettlesWhatAFilmGetsWhenBothCopiesKnowSomething() {
        val films = collapse(
            copy("a", "FIN | The Matrix (1999) 4K", 1999),
            copy("b", "The Matrix 1999 [MULTI-SUBS] 1080p", 1999),
        )

        val positions = mapOf("a" to 10L, "b" to 40L).byFilm(films) { first, second -> maxOf(first, second) }

        assertEquals(mapOf("a" to 40L), positions)
    }

    @Test
    fun somethingTrueOfACopyIsTrueOfItsFilm() {
        val films = collapse(
            copy("a", "FIN | The Matrix (1999) 4K", 1999),
            copy("b", "The Matrix 1999 [MULTI-SUBS] 1080p", 1999),
            copy("c", "Quiet Harbour", 2021),
        )

        assertEquals(setOf("a", "c"), setOf("b", "c").byFilm(films))
    }

    private fun collapse(vararg copies: Copy) =
        catalogueFilms(copies.toList(), emptyMap()) { primary, _ -> primary }

    private fun copy(key: String, title: String, year: Int?, poster: String? = null) =
        Copy(contentKey = key, title = title, year = year, poster = poster)

    private data class Copy(
        override val contentKey: String,
        override val title: String,
        override val year: Int?,
        val poster: String?,
    ) : CatalogueCopy
}
