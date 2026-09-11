package com.sohva.tv.addons

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class AddonEndpointTest {
    @Test fun installationBaseOptInPreservesOpaquePathAndQueryButRejectsPages() {
        val base = "stremio://example.invalid/config/AbC%2Fopaque?key=Private"
        assertEquals("https://example.invalid/config/AbC%2Fopaque/manifest.json?key=Private",
            AddonEndpoint.parse(base, allowBaseUrl = true).exportConfiguredUrl())
        for (tail in listOf("configure", "install", "index.html")) {
            expectFailure(AddonFailure.INVALID_URL) { AddonEndpoint.parse("https://example.invalid/$tail", allowBaseUrl = true) }
        }
    }
    @Test fun aiometadataUuidBaseWithoutTrailingSlashMatchesItsExplicitManifest() {
        val base = "https://example.invalid/stremio/12345678-1234-1234-1234-123456789abc"
        assertEquals(AddonEndpoint.parse("$base/manifest.json?key=private").fingerprint,
            AddonEndpoint.parse("$base?key=private").fingerprint)
        expectFailure(AddonFailure.INVALID_URL) { AddonEndpoint.parse("$base/configure") }
        expectFailure(AddonFailure.INVALID_URL) { AddonEndpoint.parse("https://example.invalid/other/12345678-1234-1234-1234-123456789abc") }
    }
    @Test fun configuredPathAndQueryArePreserved() {
        val endpoint = AddonEndpoint.parse("stremio://EXAMPLE.invalid/User/Secret%2FCase/manifest.json?token=AbC%2Bxy&x=1")
        assertEquals("https://example.invalid/User/Secret%2FCase/manifest.json?token=AbC%2Bxy&x=1", endpoint.exportConfiguredUrl())
        val resource = endpoint.resourceUrl("meta", "anime.series", "kitsu:12:1:2").toHttpUrl()
        assertEquals("/User/Secret%2FCase/meta/anime.series/kitsu:12:1:2.json", resource.encodedPath)
        assertEquals("token=AbC%2Bxy&x=1", resource.encodedQuery)
        assertFalse(endpoint.toString().contains("Secret"))
    }
    @Test fun requestExtrasCannotBecomeNewPathsOrQueryParameters() {
        val endpoint = AddonEndpoint.parse("https://example.invalid/manifest.json")
        val url = endpoint.resourceUrl("catalog", "Trakt", "my/list", mapOf("search" to "A/B & x=y+z?#", "skip" to "20")).toHttpUrl()
        assertEquals("my/list", url.pathSegments[2])
        assertEquals("search=A%2FB%20%26%20x%3Dy%2Bz%3F%23&skip=20.json", url.encodedPathSegments.last())
        assertNull(url.query)
        assertEquals(4, url.pathSegments.size)
    }
    @Test fun profilesCanCompareConfigurationWithoutLoggingIt() {
        val first = AddonEndpoint.parse("https://example.invalid/A/manifest.json")
        val duplicate = AddonEndpoint.parse("stremio://example.invalid/A/manifest.json")
        val other = AddonEndpoint.parse("https://example.invalid/a/manifest.json")
        assertEquals(first.fingerprint, duplicate.fingerprint)
        assertNotEquals(first.fingerprint, other.fingerprint)
        assertEquals(64, first.fingerprint.length)
    }
    @Test fun onlyExplicitDirectoriesAreExpanded() {
        assertEquals("https://example.invalid/config/manifest.json", AddonEndpoint.parse("https://example.invalid/config/").exportConfiguredUrl())
        assertEquals("https://example.invalid/manifest.json", AddonEndpoint.parse("https://example.invalid").exportConfiguredUrl())
        expectFailure(AddonFailure.INVALID_URL) { AddonEndpoint.parse("https://example.invalid/config") }
    }
    @Test fun cleartextRequiresExplicitConsent() {
        expectFailure(AddonFailure.INSECURE_URL) { AddonEndpoint.parse("http://example.invalid/manifest.json") }
        assertEquals("http", AddonEndpoint.parse("http://example.invalid/manifest.json", true).url.scheme)
    }
    @Test fun rejectsAmbiguousOrUnsupportedEndpointsWithoutEchoingThem() {
        listOf("file:///secret/manifest.json", "https://user:password@example.invalid/manifest.json",
            "https://example.invalid/manifest.json#secret", "https://example.invalid/secret\n/manifest.json",
            "https://example.invalid\\secret/manifest.json").forEach {
            val error = expectFailure(AddonFailure.INVALID_URL) { AddonEndpoint.parse(it) }
            assertFalse(error.toString().contains("secret"))
            assertFalse(error.toString().contains("password"))
        }
    }
    @Test fun rejectsTraversalResourceRequests() {
        val endpoint = AddonEndpoint.parse("https://example.invalid/manifest.json")
        expectFailure(AddonFailure.INVALID_REQUEST) { endpoint.resourceUrl("meta", "..", "tt1") }
        expectFailure(AddonFailure.INVALID_REQUEST) { endpoint.resourceUrl("meta", "movie", "..") }
        expectFailure(AddonFailure.INVALID_REQUEST) { endpoint.resourceUrl("configure", "movie", "tt1") }
    }
}
