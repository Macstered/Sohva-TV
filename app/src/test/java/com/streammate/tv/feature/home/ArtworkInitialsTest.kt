package com.streammate.tv.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a continue-watching card falls back to when the library has no poster.
 *
 * Scraped libraries are missing artwork often enough that this is the ordinary
 * case rather than the exception, so an empty rectangle would be what most
 * cards looked like.
 */
class ArtworkInitialsTest {

    @Test
    fun `takes the leading letter of the first two words`() {
        assertEquals("BR", "Blade Runner".artworkInitials())
        assertEquals("TK", "The Krakken".artworkInitials())
    }

    @Test
    fun `one word gives up two letters, because one looks lost on a tile`() {
        assertEquals("AN", "Anaconda".artworkInitials())
    }

    @Test
    fun `numbers in a title never become an initial`() {
        // "Ben 10" reduced to "B1" reads as a fault rather than a placeholder.
        assertEquals("BE", "Ben 10".artworkInitials())
        assertEquals("SW", "Star.Wars.1977".artworkInitials())
        assertEquals("XF", "X-Files".artworkInitials())
        assertEquals("FF", "2 Fast 2 Furious".artworkInitials())
    }

    @Test
    fun `a title that is only punctuation or blank yields nothing rather than crashing`() {
        assertEquals("", "".artworkInitials())
        assertEquals("", "   ".artworkInitials())
        assertEquals("", "---".artworkInitials())
    }

    @Test
    fun `initials are upper case whatever the title does`() {
        assertEquals("HP", "harry potter".artworkInitials())
    }
}
