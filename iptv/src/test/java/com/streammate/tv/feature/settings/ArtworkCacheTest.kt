package com.streammate.tv.feature.settings

import com.streammate.tv.app.ArtworkCacheLimit
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkCacheTest {

    @Test
    fun `sizes read as sizes rather than as byte counts`() {
        assertEquals("512 B", ArtworkCache.formatBytes(512))
        assertEquals("2 kB", ArtworkCache.formatBytes(2 * 1024))
        assertEquals("250 MB", ArtworkCache.formatBytes(250L * 1024 * 1024))
    }

    @Test
    fun `an empty cache reports nothing rather than a blank`() {
        assertEquals("0 B", ArtworkCache.formatBytes(0))
    }

    @Test
    fun `every offered ceiling is the size it claims to be`() {
        // The label is generated from megabytes and the cache is built from
        // bytes, so the two have to agree or the setting lies about itself.
        ArtworkCacheLimit.entries.forEach { limit ->
            assertEquals(limit.name, limit.megabytes * 1024L * 1024L, limit.bytes)
        }
    }

    @Test
    fun `an unknown stored value falls back rather than losing the cache`() {
        // A downgrade, or a hand-edited preference, must not leave the loader
        // with no ceiling at all.
        assertEquals(ArtworkCacheLimit.DEFAULT, ArtworkCacheLimit.fromStoredValue(null))
        assertEquals(ArtworkCacheLimit.DEFAULT, ArtworkCacheLimit.fromStoredValue("HUGE"))
        assertEquals(ArtworkCacheLimit.SMALL, ArtworkCacheLimit.fromStoredValue("SMALL"))
    }

    @Test
    fun `the default is a fraction of what was hardcoded before`() {
        // It used to be a flat gigabyte, chosen by nobody. Anything close to
        // that again would defeat the point of asking.
        assertEquals(250, ArtworkCacheLimit.DEFAULT.megabytes)
    }
}
