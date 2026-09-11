package com.sohva.tv.addons

import org.junit.Assert.*
import org.junit.Test

class AddonSubtitlePolicyTest {
    @Test fun normalizesAndDeduplicatesRegionalAndProviderCodes() {
        assertEquals(listOf("fi", "en"), AddonSubtitlePolicy.preferred("fin", "en-US"))
        assertEquals(listOf("no"), AddonSubtitlePolicy.preferred("nob", "nn-NO"))
        assertEquals(emptyList<String>(), AddonSubtitlePolicy.preferred(null, "und"))
        assertTrue(AddonSubtitlePolicy.matches("eng", listOf("fi", "en")))
        assertFalse(AddonSubtitlePolicy.matches("deu", listOf("fi", "en")))
    }
    @Test fun primaryAudioRuleMatchesExistingUniversalVodBehavior() {
        assertTrue(AddonSubtitlePolicy.suppressForAudio("fi", listOf("fin", "eng")))
        assertFalse(AddonSubtitlePolicy.suppressForAudio("fi", listOf("eng")))
        assertFalse(AddonSubtitlePolicy.suppressForAudio(null, listOf("eng")))
    }
}
