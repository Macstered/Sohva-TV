package com.streammate.tv.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueCustomGroupTest {

    @Test
    fun aGroupOfGenresTakesAnythingInAnyOfThem() {
        val group = group(genres = setOf(CatalogueGenre.FAMILY, CatalogueGenre.ANIMATION))

        assertTrue(matches(group, genres = setOf(CatalogueGenre.ANIMATION)))
        assertTrue(matches(group, genres = setOf(CatalogueGenre.FAMILY, CatalogueGenre.COMEDY)))
        assertFalse(matches(group, genres = setOf(CatalogueGenre.HORROR)))
    }

    /** A group of children's films from the eighties is both, not either. */
    @Test
    fun everyConditionSetHasToHold() {
        val group = group(
            genres = setOf(CatalogueGenre.ACTION),
            fromYear = 1980,
            toYear = 1989,
        )

        assertTrue(matches(group, genres = setOf(CatalogueGenre.ACTION), year = 1985))
        assertFalse(matches(group, genres = setOf(CatalogueGenre.ACTION), year = 1995))
        assertFalse(matches(group, genres = setOf(CatalogueGenre.COMEDY), year = 1985))
    }

    @Test
    fun anUnsetConditionIsNotACondition() {
        val group = group(genres = setOf(CatalogueGenre.DRAMA))

        assertTrue(matches(group, genres = setOf(CatalogueGenre.DRAMA), year = null, rating = null))
    }

    @Test
    fun theYearRangeIncludesItsEnds() {
        val group = group(fromYear = 1980, toYear = 1989)

        assertTrue(matches(group, year = 1980))
        assertTrue(matches(group, year = 1989))
        assertFalse(matches(group, year = 1979))
        assertFalse(matches(group, year = 1990))
    }

    /**
     * A title the metadata pass has not reached has no year and no rating.
     * Letting it through would put unknown titles in every group that happens
     * to have a loose enough range.
     */
    @Test
    fun aTitleNothingIsKnownAboutDoesNotSlipIntoANarrowedGroup() {
        assertFalse(matches(group(fromYear = 1980), year = null))
        assertFalse(matches(group(minRating = 7.0), rating = null))
    }

    @Test
    fun aTitleWithNoGenresNeverMatchesAGroupThatNamesOne() {
        assertFalse(matches(group(genres = setOf(CatalogueGenre.DRAMA)), genres = emptySet()))
    }

    @Test
    fun theRatingFloorIsAFloorRatherThanAThreshold() {
        val group = group(minRating = 7.5)

        assertTrue(matches(group, rating = 7.5))
        assertTrue(matches(group, rating = 9.1))
        assertFalse(matches(group, rating = 7.4))
    }

    /** A name over nothing would quietly collect the entire library. */
    @Test
    fun aGroupWithNoConditionsIsNotUsable() {
        assertFalse(group().isUsable)
        assertFalse(matches(group(), genres = setOf(CatalogueGenre.DRAMA), year = 1999, rating = 9.0))
    }

    @Test
    fun aGroupWithoutANameIsNotUsable() {
        assertFalse(group(name = "  ", genres = setOf(CatalogueGenre.DRAMA)).isUsable)
    }

    @Test
    fun aGroupNarrowedOnlyByYearIsUsable() {
        assertTrue(group(fromYear = 1980, toYear = 1989).isUsable)
    }

    private fun group(
        name: String = "Lasten elokuvat",
        genres: Set<CatalogueGenre> = emptySet(),
        fromYear: Int? = null,
        toYear: Int? = null,
        minRating: Double? = null,
    ) = CatalogueCustomGroup("id", name, genres, fromYear, toYear, minRating)

    private fun matches(
        group: CatalogueCustomGroup,
        genres: Set<CatalogueGenre> = setOf(CatalogueGenre.ACTION),
        year: Int? = 1985,
        rating: Double? = 8.0,
    ) = catalogueGroupMatches(group, genres, year, rating)
}
