package com.streammate.tv.app

import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.model.CatalogueGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * How custom groups sit in the preference file.
 *
 * An instrumentation test because `org.json` is an Android class with no
 * implementation on a plain JVM. The encoding is the app's own, so it is worth
 * pinning: a preference that cannot be read would take every other setting down
 * with it.
 */
@RunWith(AndroidJUnit4::class)
class CustomGroupEncodingTest {

    @Test
    fun aGroupSurvivesTheRoundTrip() {
        val groups = listOf(
            CatalogueCustomGroup(
                id = "children",
                name = "Lasten elokuvat",
                genres = setOf(CatalogueGenre.FAMILY, CatalogueGenre.ANIMATION),
            ),
            CatalogueCustomGroup(
                id = "eighties",
                name = "80-luvun toiminta",
                genres = setOf(CatalogueGenre.ACTION),
                fromYear = 1980,
                toYear = 1989,
                minRating = 6.5,
            ),
        )

        assertEquals(groups, decodeCustomGroups(encodeCustomGroups(groups)))
    }

    @Test
    fun theUnsetFieldsStayUnset() {
        val group = CatalogueCustomGroup(
            id = "drama",
            name = "Draama",
            genres = setOf(CatalogueGenre.DRAMA),
        )

        val restored = decodeCustomGroups(encodeCustomGroups(listOf(group))).single()

        assertEquals(null, restored.fromYear)
        assertEquals(null, restored.toYear)
        assertEquals(null, restored.minRating)
    }

    /** A year of zero is a year, and must not read back as "no year set". */
    @Test
    fun aBoundaryYearIsNotMistakenForAbsence() {
        val group = CatalogueCustomGroup(id = "old", name = "Vanhat", fromYear = 0)

        assertEquals(0, decodeCustomGroups(encodeCustomGroups(listOf(group))).single().fromYear)
    }

    @Test
    fun anUnreadablePreferenceCostsTheGroupsAndNothingElse() {
        assertEquals(emptyList<CatalogueCustomGroup>(), decodeCustomGroups("not json at all"))
        assertEquals(emptyList<CatalogueCustomGroup>(), decodeCustomGroups(""))
        assertEquals(emptyList<CatalogueCustomGroup>(), decodeCustomGroups(null))
    }

    /**
     * A genre added in a later version, read back by an older one. The group
     * keeps the genres this version knows rather than disappearing.
     */
    @Test
    fun aGenreThisVersionDoesNotKnowIsDropped() {
        val stored = """
            [{"id":"a","name":"Seka","genres":["drama","a_genre_from_the_future"]}]
        """.trimIndent()

        val restored = decodeCustomGroups(stored).single()

        assertEquals(setOf(CatalogueGenre.DRAMA), restored.genres)
    }

    @Test
    fun aGroupWithNothingLeftInItIsNotRestored() {
        val stored = """[{"id":"a","name":"Tyhjä","genres":["only_unknown_genres"]}]"""

        assertTrue(decodeCustomGroups(stored).isEmpty())
    }

    @Test
    fun aGroupWithoutAnIdOrANameIsSkippedRatherThanFatal() {
        val stored = """
            [
              {"name":"Nimetön","genres":["drama"]},
              {"id":"b","genres":["drama"]},
              {"id":"c","name":"Kelpaa","genres":["drama"]}
            ]
        """.trimIndent()

        assertEquals(listOf("c"), decodeCustomGroups(stored).map { it.id })
    }
}
