package com.sohva.tv.feature.discover

import com.sohva.tv.feature.discover.net.RedirectRule
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decision "Addon redirects": which targets an addon JSON request may move to. */
class RedirectRuleTest {
    private val addon = "https://addon.example/cfg/subtitles/movie/tt0000001.json".toHttpUrl()

    @Test
    fun httpsTargetsElsewhereAreFollowed() {
        // OpenSubtitles Pro's shape: a cross-origin 302 to another HTTPS host.
        assertEquals("https://cdn.example/a.json".toHttpUrl(), RedirectRule.next(addon, "https://cdn.example/a.json"))
        assertEquals("https://addon.example/other.json".toHttpUrl(), RedirectRule.next(addon, "/other.json"))
    }

    @Test
    fun downgradesLocalAddressesAndMissingLocationsAreRefused() {
        assertNull(RedirectRule.next(addon, "http://cdn.example/a.json"))
        // Private ranges are refused by the same check (the public-source audit keeps household-looking addresses out of the repo).
        assertNull(RedirectRule.next(addon, "https://100.64.0.1/a.json"))
        assertNull(RedirectRule.next(addon, "https://[fd12::1]/a.json"))
        assertNull(RedirectRule.next(addon, "https://127.0.0.1/a.json"))
        assertNull(RedirectRule.next(addon, "https://localhost/a.json"))
        assertNull(RedirectRule.next(addon, "https://[::1]/a.json"))
        assertNull(RedirectRule.next(addon, "https://169.254.1.1/a.json"))
        assertNull(RedirectRule.next(addon, null))
    }
}
