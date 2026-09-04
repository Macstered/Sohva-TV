package com.streammate.tv.iptv.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The names here are the shapes two playlists actually produce for one film.
 * If these do not collapse, nothing downstream of them works.
 */
class CatalogueWorkKeyTest {

    @Test
    fun copiesOfOneFilmCollapseHoweverTheyAreDecorated() {
        val keys = listOf(
            catalogueWorkKey("FIN | The Matrix (1999) 4K", year = 1999),
            catalogueWorkKey("The Matrix 1999 [MULTI-SUBS] 1080p", year = 1999),
            catalogueWorkKey("NORDIC - The Matrix - HDR10", year = 1999),
            catalogueWorkKey("The Matrix 1999 [MULTI-SUBS] HDR10 1080p", year = 1999),
            catalogueWorkKey("[FI] The Matrix", year = 1999),
        )

        assertEquals("these are all one film: $keys", 1, keys.distinct().size)
    }

    @Test
    fun delimiterlessProviderPrefixStillUsesFullNormalization() {
        assertEquals(
            catalogueWorkKey("The Matrix", year = 1999),
            catalogueWorkKey("FIN The Matrix", year = 1999),
        )
    }

    @Test
    fun differentFilmsStayApart() {
        assertNotEquals(
            catalogueWorkKey("The Matrix", year = 1999),
            catalogueWorkKey("The Matrix Reloaded", year = 2003),
        )
    }

    /** A remake is not the film it remade, however alike the names are. */
    @Test
    fun theSameNameInADifferentYearIsADifferentFilm() {
        assertNotEquals(
            catalogueWorkKey("The Thing", year = 1982),
            catalogueWorkKey("The Thing", year = 2011),
        )
    }

    @Test
    fun aYearInTheNameCountsAsMuchAsOneInItsOwnField() {
        assertEquals(
            catalogueWorkKey("The Matrix", year = 1999),
            catalogueWorkKey("The Matrix (1999)", year = null),
        )
    }

    /**
     * The case step two exists for: until TMDB has settled what these are, two
     * copies disagreeing about the year are two different films by name alone.
     */
    @Test
    fun copiesThatDisagreeAboutTheYearDoNotCollapseByNameAlone() {
        assertNotEquals(
            catalogueWorkKey("The Matrix", year = 1999),
            catalogueWorkKey("The Matrix", year = 2000),
        )
    }

    @Test
    fun theRecordTmdbSettledOnWinsOverTheName() {
        assertEquals(
            catalogueWorkKey("FIN | The Matrix 4K", year = 1999, externalId = "603"),
            catalogueWorkKey("The Matrix", year = 2000, externalId = "603"),
        )
    }

    @Test
    fun differentRecordsStayApartHoweverAlikeTheNamesAre() {
        assertNotEquals(
            catalogueWorkKey("The Matrix", year = 1999, externalId = "603"),
            catalogueWorkKey("The Matrix", year = 1999, externalId = "604"),
        )
    }

    @Test
    fun anEmptyExternalIdFallsBackToTheName() {
        assertEquals(
            catalogueWorkKey("The Matrix", year = 1999),
            catalogueWorkKey("The Matrix", year = 1999, externalId = "   "),
        )
    }

    /** Case and accents are the provider's business, not a difference. */
    @Test
    fun spellingDifferencesThatAreNotDifferencesCollapse() {
        assertEquals(
            catalogueWorkKey("Amelie", year = 2001),
            catalogueWorkKey("AMÉLIE", year = 2001),
        )
    }

    /**
     * A name that is nothing but decoration must not reduce to an empty key, or
     * every such title in the library would be treated as the same film.
     */
    @Test
    fun aNameThatIsAllDecorationKeepsSomethingOfItself() {
        val first = catalogueWorkKey("[MULTI-SUBS]", year = null)
        val second = catalogueWorkKey("[4K]", year = null)

        assertNotEquals(first, second)
    }
}
