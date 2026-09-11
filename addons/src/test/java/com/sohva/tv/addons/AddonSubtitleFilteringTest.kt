package com.sohva.tv.addons

import org.junit.Assert.*
import org.junit.Test

class AddonSubtitleFilteringTest {
    @Test fun primaryThenSecondaryOnlyAndExplicitAllLanguageOverride() {
        val items = listOf("ger", "eng", "fin", "und").map { AddonSubtitle(it, it, "https://example.invalid/$it") }
        val preferred = AddonSubtitlePolicy.preferred("fi", "en")
        assertEquals(listOf("fin", "eng"), AddonSubtitlePolicy.visible(items, preferred, false).map { it.language })
        assertEquals(listOf("fin", "eng", "ger", "und"), AddonSubtitlePolicy.visible(items, preferred, true).map { it.language })
        assertTrue(AddonSubtitlePolicy.visible(items, emptyList(), false).isEmpty())
        assertEquals(4, AddonSubtitlePolicy.visible(items, emptyList(), true).size)
    }
}
