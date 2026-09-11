package com.streammate.tv.addons

import com.sohva.tv.addons.AddonSubtitle
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class AddonSubtitleTimingTest {
    @Test fun timingHasExplicitSignAndStablePrecision() {
        assertEquals("+0.000 s", AddonSubtitleTiming.label(0))
        assertEquals("+0.100 s", AddonSubtitleTiming.label(100))
        assertEquals("-1.250 s", AddonSubtitleTiming.label(-1250))
    }
    @Test fun fineCoarseAndBoundsArePredictable() {
        assertEquals(100L, AddonSubtitleTiming.step(0, 1, false))
        assertEquals(-1000L, AddonSubtitleTiming.step(0, -1, true))
        assertEquals(60000L, AddonSubtitleTiming.step(60000, 1, true))
        assertEquals(-60000L, AddonSubtitleTiming.bound(Long.MIN_VALUE))
    }
    @Test fun choicesDistinguishSameLanguageAndNeverExposeUrls() {
        val a = AddonSubtitle("same", "eng", "https://example.invalid/a?fixture=one")
        val b = AddonSubtitle("same", "eng", "https://example.invalid/a?fixture=two")
        assertNotEquals(addonSubtitleChoiceKey(a, "provider"), addonSubtitleChoiceKey(b, "provider"))
        assertNotEquals(addonSubtitleChoiceKey(a, "one"), addonSubtitleChoiceKey(a, "two"))
        assertTrue(addonSubtitleChoiceKey(a, null).matches(Regex("external-[a-f0-9]{64}")))
    }
    @Test fun languageNamesUseNormalizedCodes() {
        assertEquals("Finnish", addonSubtitleLanguageName("fin", Locale.ENGLISH))
        assertEquals("English", addonSubtitleLanguageName("en-US", Locale.ENGLISH))
        assertEquals("Unknown language", addonSubtitleLanguageName("und", Locale.ENGLISH))
    }
}
