package com.streammate.tv.feature.catalogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class CatalogueCopyDerivationCacheTest {
    @Test
    fun repeatedGroupQueriesReuseTitleDerivations() {
        val cache = CatalogueCopyDerivationCache()
        val firstObject = copy("a", "FIN | The Matrix (1999) 4K", 1999)
        val nextQueryObject = firstObject.copy()

        val firstKey = cache.workKey(firstObject, null)
        val firstClaims = cache.claims(firstObject)

        assertSame(firstKey, cache.workKey(nextQueryObject, null))
        assertSame(firstClaims, cache.claims(nextQueryObject))
    }

    @Test
    fun changedInputsReplaceTheCachedIdentity() {
        val cache = CatalogueCopyDerivationCache()
        val original = copy("a", "The Matrix", 1999)
        val renamed = copy("a", "Quiet Harbour", 2021)

        val originalKey = cache.workKey(original, null)
        val originalClaims = cache.claims(original)

        assertNotEquals(originalKey, cache.workKey(renamed, null))
        assertNotSame(originalClaims, cache.claims(renamed))
    }

    @Test
    fun acceptedExternalIdInvalidatesANameBasedKey() {
        val cache = CatalogueCopyDerivationCache()
        val item = copy("a", "The Matrix", 1999)

        assertNotEquals(cache.workKey(item, null), cache.workKey(item, "603"))
        assertEquals("tmdb:603", cache.workKey(item, "603"))
    }

    @Test
    fun cacheRemainsBounded() {
        val cache = CatalogueCopyDerivationCache(maxEntries = 2)

        cache.workKey(copy("a", "A", 2001), null)
        cache.workKey(copy("b", "B", 2002), null)
        cache.workKey(copy("c", "C", 2003), null)

        assertEquals(2, cache.entryCount())
    }

    private fun copy(key: String, title: String, year: Int?) =
        Copy(contentKey = key, title = title, year = year)

    private data class Copy(
        override val contentKey: String,
        override val title: String,
        override val year: Int?,
    ) : CatalogueCopy
}
