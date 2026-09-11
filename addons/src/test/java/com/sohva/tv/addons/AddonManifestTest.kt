package com.sohva.tv.addons

import org.junit.Assert.*
import org.junit.Test

class AddonManifestTest {
    @Test fun preservesCustomTypesAndVariablePageSizes() {
        val manifest = AddonManifestParser.parse(aioManifest())
        assertEquals(listOf("movie", "anime.series", "Trakt"), manifest.catalogs.map { it.type })
        assertEquals(listOf(20, 25, null), manifest.catalogs.map { it.pageSize })
        assertFalse(manifest.catalogs.last().showInHome)
        assertTrue(manifest.configurable)
    }
    @Test fun catalogMatchingDoesNotUseMediaIdPrefixes() {
        val manifest = AddonManifestParser.parse(aioManifest())
        assertTrue(manifest.supports("catalog", "Trakt", "mine"))
        assertFalse(manifest.supports("catalog", "movie", "mine"))
        assertTrue(manifest.supports("meta", "anime.series", "kitsu:123"))
        assertFalse(manifest.supports("meta", "movie", "other123"))
    }
    @Test fun objectResourceWithoutPrefixesDoesNotInheritGlobalPrefixes() {
        val manifest = AddonManifestParser.parse(aioManifest())
        assertTrue(manifest.supports("stream", "movie", "other123"))
        assertFalse(manifest.supports("stream", "series", "tt123"))
        assertFalse(manifest.supports("subtitles", "movie", "tt123"))
    }
    @Test fun missingRequiredOrUnknownExtrasAreRejected() {
        val catalog = AddonManifestParser.parse(aioManifest()).catalogs.last()
        expectFailure(AddonFailure.INVALID_REQUEST) { catalog.validateExtras(emptyMap()) }
        expectFailure(AddonFailure.INVALID_REQUEST) { catalog.validateExtras(mapOf("genre" to "Drama", "unknown" to "a")) }
        expectFailure(AddonFailure.INVALID_REQUEST) { catalog.validateExtras(mapOf("genre" to "Drama", "skip" to "-1")) }
        catalog.validateExtras(mapOf("genre" to "Drama", "skip" to "20"))
    }
    @Test fun pagingUsesReturnedCountAndExplicitSizeNotOneHundred() {
        val catalogs = AddonManifestParser.parse(aioManifest()).catalogs
        assertEquals(20, catalogs[0].nextSkip(0, 20))
        assertEquals(50, catalogs[1].nextSkip(25, 25))
        assertNull(catalogs[0].nextSkip(20, 10))
        assertEquals(10, catalogs[2].nextSkip(0, 10)) // Unknown page size: don't truncate a short page.
        assertNull(catalogs[2].nextSkip(10, 0))
        assertNull(catalogs[2].nextSkip(Int.MAX_VALUE, 1))
    }
    @Test fun legacyCatalogExtrasRemainUsable() {
        val text = """{"id":"legacy","name":"Legacy","version":"1","types":["movie"],"resources":["catalog"],"catalogs":[{"id":"s","type":"movie","extraSupported":["search","skip"],"extraRequired":["search"]}]}"""
        val catalog = AddonManifestParser.parse(text).catalogs.single()
        assertEquals(listOf("search", "skip"), catalog.extras.map { it.name })
        expectFailure(AddonFailure.INVALID_REQUEST) { catalog.validateExtras(emptyMap()) }
        catalog.validateExtras(mapOf("search" to "test"))
    }
    @Test fun malformedResponsesFailSafely() {
        listOf("null", "[]", "<html>secret</html>", "{}", aioManifest().replace("\"pageSize\":20", "\"pageSize\":0"),
            aioManifest().replace("\"resources\":[", "\"resources\": [null,")).forEach {
            val error = expectFailure(AddonFailure.INVALID_MANIFEST) { AddonManifestParser.parse(it) }
            assertFalse(error.toString().contains("secret"))
        }
    }
    @Test fun sizeAndNestingAreBoundedBeforeParsing() {
        expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { AddonManifestParser.parse(" ".repeat(AddonManifestParser.MAX_BYTES + 1)) }
        expectFailure(AddonFailure.INVALID_MANIFEST) { AddonManifestParser.parse("[".repeat(65) + "0" + "]".repeat(65)) }
    }
    @Test fun optionalFutureFieldsDoNotBreakParsing() {
        assertEquals("Metadata fixture", AddonManifestParser.parse(aioManifest()).name)
        assertTrue(AddonManifestParser.parse(aioManifest(required = true)).configurationRequired)
    }
}
