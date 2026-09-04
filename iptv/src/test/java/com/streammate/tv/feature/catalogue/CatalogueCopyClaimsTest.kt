package com.streammate.tv.feature.catalogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names here are the ones from the plan, plus the ones that would make a
 * careless reading say something untrue about a film.
 */
class CatalogueCopyClaimsTest {

    @Test
    fun aPrefixedLanguageAndATrailingQualityAreBothRead() {
        val claims = catalogueCopyClaims("FIN | The Matrix (1999) 4K")

        assertEquals(listOf(CatalogueCopyLanguage.FINNISH), claims.languages)
        assertEquals(listOf("4K UHD"), claims.picture)
    }

    @Test
    fun aBracketedTagAndAResolutionAreBothRead() {
        val claims = catalogueCopyClaims("The Matrix 1999 [MULTI-SUBS] 1080p")

        assertEquals(listOf(CatalogueCopyLanguage.SUBTITLED), claims.languages)
        assertEquals(listOf("1080p"), claims.picture)
    }

    @Test
    fun aNordicCopyReadsAsNordic() {
        val claims = catalogueCopyClaims("NORDIC - The Matrix - HDR10")

        assertEquals(listOf(CatalogueCopyLanguage.NORDIC), claims.languages)
        assertEquals(listOf("HDR10"), claims.picture)
    }

    @Test
    fun aBracketedLanguageCodeIsRead() {
        val claims = catalogueCopyClaims("[FI] The Matrix")

        assertEquals(listOf(CatalogueCopyLanguage.FINNISH), claims.languages)
    }

    @Test
    fun severalAudioTracksReadAsSeveral() {
        val claims = catalogueCopyClaims("The Matrix [MULTI-AUDIO] 2160p")

        assertEquals(listOf(CatalogueCopyLanguage.MULTIPLE_AUDIO), claims.languages)
        assertEquals(listOf("2160p"), claims.picture)
    }

    /**
     * The reason languages are read out of the decoration and nowhere else.
     * Every one of these words is a language marker somewhere; in none of these
     * names is it one.
     */
    @Test
    fun aLanguageWordInsideATitleIsPartOfTheTitle() {
        listOf("Fin del mundo", "Nordic Noir", "The English Patient", "Deutschland 83").forEach { title ->
            assertTrue(
                "$title is a name, not a claim: ${catalogueCopyClaims(title).languages}",
                catalogueCopyClaims(title).languages.isEmpty(),
            )
        }
    }

    /** A copy that says nothing about itself says nothing, rather than guessing. */
    @Test
    fun aNameWithNothingToSaySaysNothing() {
        val claims = catalogueCopyClaims("Quiet Harbour")

        assertEquals(emptyList<CatalogueCopyLanguage>(), claims.languages)
        assertEquals(emptyList<String>(), claims.picture)
    }

    /** Two claims in one name are two claims. */
    @Test
    fun aCopyCanClaimMoreThanOneThing() {
        val claims = catalogueCopyClaims("FIN | ENG | The Matrix - 4K HDR10")

        assertEquals(
            listOf(CatalogueCopyLanguage.FINNISH, CatalogueCopyLanguage.ENGLISH),
            claims.languages,
        )
        assertEquals(listOf("4K UHD", "HDR10"), claims.picture)
    }

    /**
     * A year is not a resolution, and neither is a number that happens to end
     * in a letter inside a word.
     */
    @Test
    fun onlyARealResolutionReadsAsOne() {
        assertEquals(emptyList<String>(), catalogueCopyClaims("The Matrix 1999").picture)
        assertEquals(emptyList<String>(), catalogueCopyClaims("Apollo 13pm").picture)
    }
}
