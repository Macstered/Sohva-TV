package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.model.CatalogueGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbGenresTest {

    @Test
    fun theFirstRecognisedFilmGenreIsThePrimaryGenre() {
        assertEquals(
            listOf(CatalogueGenre.ACTION),
            TmdbGenres.of(MetadataMediaType.MOVIE, listOf(28, 878)),
        )
    }

    /**
     * The reason this mapping exists. Television keeps these as one genre each,
     * and a library grouped by the provider's own ids would show "Action" from
     * the films and "Action & Adventure" from the series as two rows.
     */
    @Test
    fun combinedTelevisionGenresUseTheirFirstViewerFacingGenre() {
        assertEquals(
            listOf(CatalogueGenre.ACTION),
            TmdbGenres.of(MetadataMediaType.SERIES, listOf(10759)),
        )
        assertEquals(
            listOf(CatalogueGenre.SCIENCE_FICTION),
            TmdbGenres.of(MetadataMediaType.SERIES, listOf(10765)),
        )
    }

    @Test
    fun aSeriesAndAFilmAboutAWarLandInTheSameRow() {
        assertEquals(
            TmdbGenres.of(MetadataMediaType.MOVIE, listOf(10752)),
            TmdbGenres.of(MetadataMediaType.SERIES, listOf(10768)),
        )
    }

    @Test
    fun kidsAndFamilyAreOneRow() {
        assertEquals(
            listOf(CatalogueGenre.FAMILY),
            TmdbGenres.of(MetadataMediaType.SERIES, listOf(10762, 10751)),
        )
    }

    /** Where something was first shown is not what it is. */
    @Test
    fun theTelevisionMovieTagIsNotAGenre() {
        assertEquals(emptyList<CatalogueGenre>(), TmdbGenres.of(MetadataMediaType.MOVIE, listOf(10770)))
        assertEquals(
            listOf(CatalogueGenre.DRAMA),
            TmdbGenres.of(MetadataMediaType.MOVIE, listOf(10770, 18)),
        )
    }

    @Test
    fun anIdThisVersionDoesNotKnowIsIgnoredRatherThanFatal() {
        assertEquals(
            listOf(CatalogueGenre.COMEDY),
            TmdbGenres.of(MetadataMediaType.MOVIE, listOf(999_999, 35)),
        )
    }

    @Test
    fun aMultiSearchStillChoosesOnlyOnePrimaryGenre() {
        assertEquals(
            listOf(CatalogueGenre.ACTION),
            TmdbGenres.of(MetadataMediaType.PROGRAMME, listOf(10759, 12)),
        )
    }

    @Test
    fun aMultiSearchLooksInBothTablesAndKeepsReportedOrder() {
        assertEquals(
            listOf(CatalogueGenre.REALITY),
            TmdbGenres.of(MetadataMediaType.PROGRAMME, listOf(10764, 878)),
        )
    }

    @Test
    fun anUnknownLeadingIdDoesNotHideTheFirstRecognisedGenre() {
        assertEquals(
            listOf(CatalogueGenre.DRAMA),
            TmdbGenres.of(MetadataMediaType.MOVIE, listOf(999_999, 18, 28)),
        )
    }

    @Test
    fun everyStoredGenreCanBeReadBackFromItsWireValue() {
        CatalogueGenre.entries.forEach { genre ->
            assertEquals(genre, CatalogueGenre.fromWireValue(genre.wireValue))
        }
        assertEquals(null, CatalogueGenre.fromWireValue("a genre from a later version"))
    }
}
