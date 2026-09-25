package com.sohva.tv.core.model.vod

import com.sohva.tv.core.model.text.Initials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 40 VOD-FR-11, -12, -21, -38, -62, -85, -90; cases carried over from beta 23's tests. */
class VodRulesTest {
    private val min = 60_000L

    @Test
    fun watchedAtNinetyPercent() {
        assertTrue(WatchedRule.isWatched(90_000, 100_000))
        assertFalse(WatchedRule.isWatched(89_999, 100_000))
    }

    @Test
    fun watchedWithThreeMinutesLeftOnTenMinutesOrMore() {
        assertTrue(WatchedRule.isWatched(17 * min, 20 * min))
        assertFalse(WatchedRule.isWatched(16 * min, 20 * min))
        // A five-minute short needs 90 %.
        assertFalse(WatchedRule.isWatched(2 * min, 5 * min))
        assertTrue(WatchedRule.isWatched(270_000, 5 * min))
    }

    @Test
    fun neverWatchedWithoutADuration() {
        assertFalse(WatchedRule.isWatched(5 * min, 0))
        assertFalse(WatchedRule.isWatched(5 * min, -1))
        // A position past the end is clamped.
        assertTrue(WatchedRule.isWatched(30 * min, 20 * min))
    }

    @Test
    fun yearFromTheProviderElseFromBrackets() {
        assertEquals(2001, VodText.year(2001, "Film (1999)"))
        assertEquals(1999, VodText.year(null, "Film (1999)"))
        assertEquals(2010, VodText.year(null, "Film [2010] 4K"))
        assertNull(VodText.year(null, "Film 1999"))
        assertNull(VodText.year(null, "Film (1899)"))
    }

    @Test
    fun breadcrumbStripsDecorationWithoutEmptyingALabel() {
        assertEquals("Movies", VodText.breadcrumbGroup(" Movies [Multi-Sub] (4K) "))
        assertEquals("[4K]", VodText.breadcrumbGroup("[4K]"))
        assertEquals("Action Films", VodText.breadcrumbGroup("Action  (HD)  Films"))
    }

    @Test
    fun episodeTitlesDropTheirMarker() {
        assertEquals("Pilot", VodText.episodeTitle("Show S01E01 - Pilot", 1, 1))
        assertEquals("The End", VodText.episodeTitle("Show S2 E10: The End.", 2, 10))
        assertNull(VodText.episodeTitle("Show S01E01", 1, 1))
        assertEquals("Show S01E011 x", VodText.episodeTitle("Show S01E011 x", 1, 1))
        assertEquals("Plain title", VodText.episodeTitle("  Plain title ", 3, 4))
        assertNull(VodText.episodeTitle("  ", 1, 1))
        assertNull(VodText.episodeTitle(null, 1, 1))
    }

    @Test
    fun ratingsReadTheirLeadingNumber() {
        assertEquals(7.5, VodText.ratingNumber("7.5")!!, 0.0)
        assertEquals(7.5, VodText.ratingNumber("7,5")!!, 0.0)
        assertEquals(8.0, VodText.ratingNumber("8/10")!!, 0.0)
        assertEquals(1.0, VodText.ratingNumber("1e5")!!, 0.0)
        assertNull(VodText.ratingNumber("N/A"))
        assertNull(VodText.ratingNumber(".5"))
        assertNull(VodText.ratingNumber(null))
        assertEquals(75, VodText.ratingTenths("7,5"))
        assertEquals(80, VodText.ratingTenths("8/10"))
    }

    @Test
    fun initialsFollowTheSharedRule() {
        assertEquals("AA", Initials.of("Ad Astra"))
        assertEquals("QO", Initials.of("Quantum of Solace"))
        assertEquals("AL", Initials.of("Aladdin"))
        assertEquals("GU", Initials.of("2 Guns"))
        assertEquals("30", Initials.of("300"))
        assertEquals("19", Initials.of("1917"))
        assertEquals("", Initials.of("   "))
    }

    @Test
    fun genresRoundTripAndIgnoreUnknownValues() {
        assertEquals(22, Genre.entries.size)
        Genre.entries.forEach { assertEquals(it, Genre.ofWire(it.wire)) }
        assertNull(Genre.ofWire("kids"))
        assertEquals(Genre.SCIENCE_FICTION, Genre.ofWire("science_fiction"))
    }

    private fun group(
        name: String = "Lasten elokuvat",
        genres: Set<Genre> = emptySet(),
        fromYear: Int? = null,
        toYear: Int? = null,
        minRating: Double? = null,
    ) = CustomGroup("id", name, genres, fromYear, toYear, minRating)

    @Test
    fun customGroupsNeedEveryConditionTheySet() {
        val any = group(genres = setOf(Genre.FAMILY, Genre.ANIMATION))
        assertTrue(any.matches(Genre.ANIMATION, 1985, 8.0))
        assertFalse(any.matches(Genre.HORROR, 1985, 8.0))
        val eighties = group(genres = setOf(Genre.ACTION), fromYear = 1980, toYear = 1989)
        assertTrue(eighties.matches(Genre.ACTION, 1985, null))
        assertFalse(eighties.matches(Genre.ACTION, 1995, null))
        assertFalse(eighties.matches(Genre.COMEDY, 1985, null))
        assertTrue(group(fromYear = 1980, toYear = 1989).matches(null, 1980, null))
        assertTrue(group(fromYear = 1980, toYear = 1989).matches(null, 1989, null))
        assertFalse(group(fromYear = 1980, toYear = 1989).matches(null, 1990, null))
    }

    @Test
    fun unknownFactsNeverSlipIntoANarrowedGroup() {
        assertFalse(group(fromYear = 1980).matches(Genre.ACTION, null, 8.0))
        assertFalse(group(minRating = 7.0).matches(Genre.ACTION, 1985, null))
        assertFalse(group(genres = setOf(Genre.DRAMA)).matches(null, 1985, 8.0))
        assertTrue(group(minRating = 7.5).matches(null, null, 7.5))
        assertFalse(group(minRating = 7.5).matches(null, null, 7.4))
    }

    @Test
    fun onlyNamedGroupsWithAConditionAreUsable() {
        assertFalse(group().isUsable)
        assertFalse(group().matches(Genre.DRAMA, 1999, 9.0))
        assertFalse(group(name = "  ", genres = setOf(Genre.DRAMA)).isUsable)
        assertTrue(group(fromYear = 1980, toYear = 1989).isUsable)
    }
}
