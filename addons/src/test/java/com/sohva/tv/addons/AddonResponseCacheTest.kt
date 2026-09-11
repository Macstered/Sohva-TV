package com.sohva.tv.addons

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddonResponseCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun encryptedCacheReopensWithoutPlaintextAndKeysDoNotCollide(): Unit = runBlocking {
        val directory = temporary.newFolder()
        val cipher = TestAddonCipher()
        val key = addonCacheKey(listOf("adult", "private/config", "id"))
        EncryptedFileAddonCache(directory, cipher).put(key, CachedAddonResponse("PrivateArtworkURL", 100, 200))
        assertFalse(directory.listFiles()!!.single().readText().contains("PrivateArtworkURL"))
        val restored = EncryptedFileAddonCache(directory, cipher).get(key)!!
        assertEquals("PrivateArtworkURL", restored.body)
        assertTrue(restored.fresh(150)); assertFalse(restored.fresh(99)); assertFalse(restored.fresh(200))
        assertNotEquals(addonCacheKey(listOf("a:b", "c")), addonCacheKey(listOf("a", "b:c")))
    }
    @Test fun corruptOrSwappedFilesAreCacheMisses(): Unit = runBlocking {
        val directory = temporary.newFolder()
        val cache = EncryptedFileAddonCache(directory, TestAddonCipher())
        val first = addonCacheKey(listOf("one")); val second = addonCacheKey(listOf("two"))
        cache.put(first, CachedAddonResponse("secret", 1, 2))
        File(directory, "$first.cache").copyTo(File(directory, "$second.cache"))
        assertNull(cache.get(second))
        File(directory, "$first.cache").writeText("corrupt")
        assertNull(cache.get(first))
    }
    @Test fun cacheIsBoundedAndRemovalWorks(): Unit = runBlocking {
        val directory = temporary.newFolder()
        val cache = EncryptedFileAddonCache(directory, TestAddonCipher(), maxBytes = 2048, maxEntries = 2)
        repeat(6) { cache.put(addonCacheKey(listOf("$it")), CachedAddonResponse("small", 1, 2)) }
        assertTrue(directory.listFiles()!!.size <= 2)
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 2048)
        val key = addonCacheKey(listOf("large"))
        cache.put(key, CachedAddonResponse("x".repeat(4096), 1, 2))
        assertNull(cache.get(key))
        val existing = directory.listFiles()!!.first().nameWithoutExtension
        cache.remove(existing)
        assertNull(cache.get(existing))
    }
}
