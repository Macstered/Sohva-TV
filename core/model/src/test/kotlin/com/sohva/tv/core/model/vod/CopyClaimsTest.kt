package com.sohva.tv.core.model.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 40 VOD-FR-28…30; cases carried over from beta 23's claims, preference and presentation tests. */
class CopyClaimsTest {
    private fun claims(name: String) = CopyClaimReader.read(name)

    @Test
    fun aPrefixedLanguageAndATrailingQualityAreBothRead() {
        val c = claims("FIN | The Matrix (1999) 4K")
        assertEquals(listOf(CopyLanguage.FINNISH), c.languages)
        assertEquals(listOf("4K UHD"), c.picture)
    }

    @Test
    fun aBracketedTagAndAResolutionAreBothRead() {
        val c = claims("The Matrix 1999 [MULTI-SUBS] 1080p")
        assertEquals(listOf(CopyLanguage.SUBTITLED), c.languages)
        assertEquals(listOf("1080p"), c.picture)
    }

    @Test
    fun aNordicCopyReadsAsNordic() {
        val c = claims("NORDIC - The Matrix - HDR10")
        assertEquals(listOf(CopyLanguage.NORDIC), c.languages)
        assertEquals(listOf("HDR10"), c.picture)
    }

    @Test
    fun bracketedCodesAndSeveralAudioTracks() {
        assertEquals(listOf(CopyLanguage.FINNISH), claims("[FI] The Matrix").languages)
        val multi = claims("The Matrix [MULTI-AUDIO] 2160p")
        assertEquals(listOf(CopyLanguage.MULTIPLE_AUDIO), multi.languages)
        assertEquals(listOf("2160p"), multi.picture)
    }

    @Test
    fun aLanguageWordInsideATitleIsPartOfTheTitle() {
        listOf("Fin del mundo", "Nordic Noir", "The English Patient", "Deutschland 83").forEach {
            assertTrue(it, claims(it).languages.isEmpty())
        }
    }

    @Test
    fun aNameWithNothingToSaySaysNothing() {
        val c = claims("Quiet Harbour")
        assertEquals(0, c.languageMask)
        assertEquals(emptyList<String>(), c.picture)
    }

    @Test
    fun aCopyCanClaimMoreThanOneThing() {
        val c = claims("FIN | ENG | The Matrix - 4K HDR10")
        assertEquals(listOf(CopyLanguage.FINNISH, CopyLanguage.ENGLISH), c.languages)
        assertEquals(listOf("4K UHD", "HDR10"), c.picture)
    }

    @Test
    fun onlyARealResolutionReadsAsOne() {
        assertEquals(emptyList<String>(), claims("The Matrix 1999").picture)
        assertEquals(emptyList<String>(), claims("Apollo 13pm").picture)
        assertEquals(emptyList<String>(), claims("The Matrix FHD").picture)
    }

    @Test
    fun qualityChipsOnlyReflectExplicitTags() {
        assertEquals(listOf("4K UHD", "HDR10"), QualityChips.labels(QualityChips.mask("Example Series [4K] [HDR10]")))
        assertEquals(listOf("4K UHD", "Dolby Vision"), QualityChips.labels(QualityChips.mask("Example Series UHD DOVI")))
        assertEquals(listOf("HDR10+"), QualityChips.labels(QualityChips.mask("Film HDR10+")))
        assertEquals(listOf("HDR10"), QualityChips.labels(QualityChips.mask("Film HDR")))
        assertEquals(emptyList<String>(), QualityChips.labels(QualityChips.mask("Example Series 2026")))
        assertEquals(emptyList<String>(), QualityChips.labels(QualityChips.mask("Talk4Kids")))
    }

    private fun score(name: String, preferred: PreferredCopy): Int = claims(name).let { preferred.score(it.languageMask, it.pictureRank) }

    @Test
    fun finnishAudioAndSubtitlesPreferTheirCopy() {
        assertTrue(score("FIN | The Matrix (1999) 4K", PreferredCopy.FINNISH_AUDIO) > score("The Matrix 1999 [MULTI-SUBS] 1080p", PreferredCopy.FINNISH_AUDIO))
        assertTrue(score("The Matrix 1999 [MULTI-SUBS] 1080p", PreferredCopy.FINNISH_SUBTITLES) > score("FIN | The Matrix (1999) 4K", PreferredCopy.FINNISH_SUBTITLES))
        assertTrue(score("NORDIC - The Matrix", PreferredCopy.FINNISH_AUDIO) > score("The Matrix", PreferredCopy.FINNISH_AUDIO))
    }

    @Test
    fun theBiggestPictureWinsAndRangeOnlySeparatesASize() {
        val ranked = listOf("The Matrix 720p", "FIN | The Matrix 4K", "The Matrix 1080p").sortedByDescending { score(it, PreferredCopy.LARGEST_PICTURE) }
        assertEquals(listOf("FIN | The Matrix 4K", "The Matrix 1080p", "The Matrix 720p"), ranked)
        val plain = score("The Matrix 1080p", PreferredCopy.LARGEST_PICTURE)
        val brighter = score("The Matrix 1080p HDR10", PreferredCopy.LARGEST_PICTURE)
        val bigger = score("The Matrix 2160p", PreferredCopy.LARGEST_PICTURE)
        assertTrue(brighter > plain)
        assertTrue(bigger > brighter)
    }

    @Test
    fun silentCopiesAndNoPreferenceTie() {
        PreferredCopy.entries.forEach { assertEquals(it.name, 0, score("Quiet Harbour", it)) }
        listOf("FIN | The Matrix 4K", "The Matrix [MULTI-SUBS] 1080p").forEach { assertEquals(0, score(it, PreferredCopy.NONE)) }
        assertEquals(score("FIN | The Matrix 1080p", PreferredCopy.FINNISH_AUDIO), score("[FI] The Matrix 720p", PreferredCopy.FINNISH_AUDIO))
    }

    @Test
    fun anUnknownStoredPreferenceReadsAsNone() {
        assertEquals(PreferredCopy.NONE, PreferredCopy.of("BEST"))
        assertEquals(PreferredCopy.NONE, PreferredCopy.of(null))
        assertEquals(PreferredCopy.LARGEST_PICTURE, PreferredCopy.of("LARGEST_PICTURE"))
    }
}
