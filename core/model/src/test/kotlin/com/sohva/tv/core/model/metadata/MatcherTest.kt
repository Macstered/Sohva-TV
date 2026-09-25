package com.sohva.tv.core.model.metadata

import com.sohva.tv.core.model.vod.Genre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 41 §11 "Matcher" and "Genres": the cases of META-FR-28 (carried over from beta 23's tests) and the id table. */
class MatcherTest {
    private fun c(id: String, title: String, type: MediaType = MediaType.MOVIE, year: Int? = null, season: Int? = null, episode: Int? = null, popularity: Double? = null) =
        Candidate(id, type, title, year = year, season = season, episode = episode, popularity = popularity)

    @Test
    fun anExactTitleAndYearWinsWithAClearLead() {
        val m = Matcher.choose(Lookup(MediaType.MOVIE, "Dune (2021)", 2021), listOf(c("1", "Dune", year = 2021), c("2", "Dune Warriors", year = 1991)))
        assertEquals("1", m?.candidate?.externalId)
        assertTrue(m!!.confidence >= Matcher.MIN_CONFIDENCE)
    }

    @Test
    fun identicalResultsAreAmbiguous() {
        assertNull(Matcher.choose(Lookup(MediaType.SERIES, "Top Gear"), listOf(c("1", "Top Gear", MediaType.SERIES), c("2", "Top Gear", MediaType.SERIES))))
    }

    @Test
    fun popularityResolvesAnExactTitleTieButNotANearOne() {
        val avatar = Matcher.choose(Lookup(MediaType.MOVIE, "Avatar"), listOf(c("old", "Avatar", year = 1916, popularity = 2.0), c("new", "Avatar", year = 2009, popularity = 84.0)))
        assertEquals("new", avatar?.candidate?.externalId)
        assertNull(Matcher.choose(Lookup(MediaType.SERIES, "Foundation"), listOf(c("1", "Foundation", MediaType.SERIES, popularity = 20.0), c("2", "Foundation", MediaType.SERIES, popularity = 18.0))))
        // The ratio rule: 3 against 2 is a 1.5 ratio with a gap under 10.
        assertEquals("a", Matcher.choose(Lookup(MediaType.SERIES, "Foundation"), listOf(c("a", "Foundation", MediaType.SERIES, popularity = 3.0), c("b", "Foundation", MediaType.SERIES, popularity = 2.0)))?.candidate?.externalId)
    }

    @Test
    fun weakTitlesAndWrongEpisodesAreRejected() {
        assertNull(Matcher.choose(Lookup(MediaType.MOVIE, "The Matrix"), listOf(c("1", "Matrix Revolutions"))))
        assertNull(Matcher.choose(Lookup(MediaType.EPISODE, "The Bear", season = 2, episode = 6), listOf(c("1", "The Bear", MediaType.EPISODE, season = 2, episode = 7))))
    }

    @Test
    fun anExactTitleTwoYearsOffScoresExactlyTheFloorAndIsAccepted() {
        val m = Matcher.choose(Lookup(MediaType.MOVIE, "Heat", 1997), listOf(c("1", "Heat", year = 1995)))
        assertEquals(0.92, m!!.confidence, 1e-9)
        assertNull(Matcher.choose(Lookup(MediaType.MOVIE, "Heat", 1997), listOf(c("1", "Heat 2", year = 1995))))
    }

    @Test
    fun programmesAcceptFilmsAndSeries() {
        assertEquals("tv", Matcher.choose(Lookup(MediaType.PROGRAMME, "Northern Line"), listOf(c("tv", "Northern Line", MediaType.SERIES)))?.candidate?.externalId)
    }

    @Test
    fun genresTakeTheFirstKnownId() {
        assertEquals(Genre.DRAMA, TmdbGenres.primary(MediaType.MOVIE, listOf(10770, 18, 28)))
        assertEquals(Genre.FAMILY, TmdbGenres.primary(MediaType.SERIES, listOf(10762)))
        assertEquals(Genre.ACTION, TmdbGenres.primary(MediaType.SERIES, listOf(10759)))
        assertEquals(Genre.WAR, TmdbGenres.primary(MediaType.SERIES, listOf(10768)))
        assertEquals(Genre.WAR, TmdbGenres.primary(MediaType.MOVIE, listOf(10752)))
        assertNull(TmdbGenres.primary(MediaType.MOVIE, listOf(10770, 99999)))
        assertEquals(Genre.NEWS, TmdbGenres.primary(MediaType.PROGRAMME, listOf(10763)))
    }
}
