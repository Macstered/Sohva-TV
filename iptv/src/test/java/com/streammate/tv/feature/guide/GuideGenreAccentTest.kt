package com.streammate.tv.feature.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The slim bar down the edge of a programme block.
 *
 * It is only worth drawing if it means the same thing every time, so the
 * mapping is closed: a category the guide does not recognise gets nothing
 * rather than an arbitrary colour, and a feed writing its categories in Finnish
 * lights the same colour as one writing them in English.
 */
class GuideGenreAccentTest {

    @Test
    fun aRecognisedCategoryAlwaysGivesTheSameAccent() {
        assertEquals(genreAccent(listOf("Sport")), genreAccent(listOf("sport")))
        assertEquals(genreAccent(listOf("Sport")), genreAccent(listOf("Urheilu")))
        assertEquals(genreAccent(listOf("News")), genreAccent(listOf("Uutiset")))
        assertEquals(genreAccent(listOf("Movie")), genreAccent(listOf("Elokuva")))
        assertEquals(genreAccent(listOf("Children")), genreAccent(listOf("Lastenohjelma")))
    }

    @Test
    fun differentGenresDoNotShareAnAccent() {
        val accents = listOf("Sport", "News", "Movie", "Children").map(::listOf).map(::genreAccent)
        assertEquals(accents.size, accents.distinct().size)
    }

    @Test
    fun anUnknownCategoryGetsNoAccent() {
        assertNull(genreAccent(listOf("Ostoskanava")))
        assertNull(genreAccent(emptyList()))
        assertNull(genreAccent(listOf("")))
    }

    @Test
    fun theFirstRecognisedCategoryWins() {
        // A feed listing several categories should not have the colour depend
        // on map iteration order.
        assertEquals(genreAccent(listOf("Sport")), genreAccent(listOf("Sport", "News")))
        assertEquals(genreAccent(listOf("News")), genreAccent(listOf("News", "Sport")))
    }
}
