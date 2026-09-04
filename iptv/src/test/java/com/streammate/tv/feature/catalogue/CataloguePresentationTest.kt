package com.streammate.tv.feature.catalogue

import org.junit.Assert.assertEquals
import org.junit.Test

class CataloguePresentationTest {
    @Test
    fun detailBreadcrumbStripsDecorationWithoutChangingAnEmptyLabel() {
        assertEquals("Movies", catalogueDisplayCategory(" Movies [Multi-Sub] (4K) "))
        assertEquals("[4K]", catalogueDisplayCategory("[4K]"))
    }

    @Test
    fun posterInitialsSkipWordsThatAreNotWords() {
        assertEquals("AA", catalogueInitials("Ad Astra"))
        assertEquals("QO", catalogueInitials("Quantum of Solace"))
        // One word gives up two of its own letters; one alone looks lost on a
        // poster-shaped tile.
        assertEquals("AL", catalogueInitials("Aladdin"))
        // A leading number is not an initial: "2 Guns" is G-U, the same way
        // "Ben 10" is B-E rather than B-1.
        assertEquals("GU", catalogueInitials("2 Guns"))
        // A title made only of digits has no initials to give, so it keeps its
        // own first two characters - an empty tile says even less.
        assertEquals("30", catalogueInitials("300"))
        assertEquals("19", catalogueInitials("1917"))
        assertEquals("", catalogueInitials("   "))
    }

    @Test
    fun seriesQualityPillsOnlyReflectExplicitProviderTags() {
        assertEquals(
            listOf("4K UHD", "HDR10"),
            catalogueQualityTags("Example Series [4K] [HDR10]"),
        )
        assertEquals(
            listOf("4K UHD", "Dolby Vision"),
            catalogueQualityTags("Example Series UHD DOVI"),
        )
        assertEquals(emptyList<String>(), catalogueQualityTags("Example Series 2026"))
    }
}
