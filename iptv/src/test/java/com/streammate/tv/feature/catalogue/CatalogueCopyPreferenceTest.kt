package com.streammate.tv.feature.catalogue

import com.streammate.tv.app.CataloguePreferredCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names here are the shapes two playlists give one film, and the question
 * each test asks is which of them the Watch button should play.
 */
class CatalogueCopyPreferenceTest {

    @Test
    fun aCopySoldAsFinnishWinsWhereFinnishAudioMatters() {
        assertTrue(
            score("FIN | The Matrix (1999) 4K", CataloguePreferredCopy.FINNISH_AUDIO) >
                score("The Matrix 1999 [MULTI-SUBS] 1080p", CataloguePreferredCopy.FINNISH_AUDIO),
        )
    }

    @Test
    fun aSubtitledCopyWinsWhereSubtitlesMatter() {
        assertTrue(
            score("The Matrix 1999 [MULTI-SUBS] 1080p", CataloguePreferredCopy.FINNISH_SUBTITLES) >
                score("FIN | The Matrix (1999) 4K", CataloguePreferredCopy.FINNISH_SUBTITLES),
        )
    }

    /** A weaker version of the same claim still beats nothing. */
    @Test
    fun aNordicCopyBeatsOneThatSaysNothing() {
        assertTrue(
            score("NORDIC - The Matrix", CataloguePreferredCopy.FINNISH_AUDIO) >
                score("The Matrix", CataloguePreferredCopy.FINNISH_AUDIO),
        )
    }

    @Test
    fun theBiggestPictureWinsWhereThePictureMatters() {
        val ranked = listOf(
            "The Matrix 720p",
            "FIN | The Matrix 4K",
            "The Matrix 1080p",
        ).sortedByDescending { score(it, CataloguePreferredCopy.LARGEST_PICTURE) }

        assertEquals(listOf("FIN | The Matrix 4K", "The Matrix 1080p", "The Matrix 720p"), ranked)
    }

    /** Dynamic range separates two copies of a size, and never outranks a size. */
    @Test
    fun dynamicRangeOnlySeparatesCopiesOfTheSameSize() {
        val plain = score("The Matrix 1080p", CataloguePreferredCopy.LARGEST_PICTURE)
        val brighter = score("The Matrix 1080p HDR10", CataloguePreferredCopy.LARGEST_PICTURE)
        val bigger = score("The Matrix 2160p", CataloguePreferredCopy.LARGEST_PICTURE)

        assertTrue(brighter > plain)
        assertTrue(bigger > brighter)
    }

    /**
     * A copy that claims nothing is not guessed at. It scores level with every
     * other silent copy, which leaves the choice between them to library order.
     */
    @Test
    fun aCopyThatClaimsNothingIsNotGuessedAt() {
        CataloguePreferredCopy.entries.forEach { preferred ->
            assertEquals(
                "$preferred should have nothing to say about a name that says nothing",
                0,
                score("Quiet Harbour", preferred),
            )
        }
    }

    /** Two copies a preference cannot separate are a tie, not a coin toss. */
    @Test
    fun copiesAPreferenceCannotSeparateTie() {
        assertEquals(
            score("FIN | The Matrix 1080p", CataloguePreferredCopy.FINNISH_AUDIO),
            score("[FI] The Matrix 720p", CataloguePreferredCopy.FINNISH_AUDIO),
        )
    }

    /** With no preference expressed, nothing is ranked above anything. */
    @Test
    fun noPreferenceRanksNothing() {
        listOf("FIN | The Matrix 4K", "The Matrix [MULTI-SUBS] 1080p", "Quiet Harbour").forEach {
            assertEquals(0, score(it, CataloguePreferredCopy.NONE))
        }
    }

    /**
     * The whole point of the setting: the copy the wall stands on follows it,
     * and a tie leaves the library in the order its playlists arrived.
     */
    @Test
    fun theWallStandsOnTheCopyThePreferenceChose() {
        val copies = listOf(
            copy("a", "FIN | The Matrix (1999) 4K"),
            copy("b", "The Matrix 1999 [MULTI-SUBS] 1080p"),
        )

        assertEquals("a", standsOn(copies, CataloguePreferredCopy.NONE))
        assertEquals("a", standsOn(copies, CataloguePreferredCopy.FINNISH_AUDIO))
        assertEquals("b", standsOn(copies, CataloguePreferredCopy.FINNISH_SUBTITLES))
        assertEquals("a", standsOn(copies, CataloguePreferredCopy.LARGEST_PICTURE))
    }

    /** A preference nothing satisfies changes nothing. */
    @Test
    fun aPreferenceNothingSatisfiesLeavesTheLibraryAsItWas() {
        val copies = listOf(copy("a", "The Matrix (1999)"), copy("b", "The Matrix 1999"))

        assertEquals("a", standsOn(copies, CataloguePreferredCopy.FINNISH_AUDIO))
    }

    private fun standsOn(copies: List<Copy>, preferred: CataloguePreferredCopy): String =
        catalogueFilms(
            copies = copies,
            externalIds = emptyMap(),
            rank = { catalogueCopyScore(catalogueCopyClaims(it.title), preferred) },
        ) { primary, _ -> primary }
            .films
            .single()
            .contentKey

    private fun score(title: String, preferred: CataloguePreferredCopy) =
        catalogueCopyScore(catalogueCopyClaims(title), preferred)

    private fun copy(key: String, title: String) = Copy(key, title, year = 1999)

    private data class Copy(
        override val contentKey: String,
        override val title: String,
        override val year: Int?,
    ) : CatalogueCopy
}
