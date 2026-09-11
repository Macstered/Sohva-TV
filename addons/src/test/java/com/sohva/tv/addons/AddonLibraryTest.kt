package com.sohva.tv.addons

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AddonLibraryTest {
    private val storage = MemoryLibrary()
    private val cipher = TestAddonCipher()
    private val access = AddonManagementAccess { it in setOf("adult", "other") }
    private val art = AddonWatchArtwork("Private title", "https://example.invalid/poster.jpg?token=synthetic")
    private fun identity(type: String = "movie", owner: String = "one", id: String = "opaque") = AddonLibraryIdentity(owner, AddonMediaKey(type, id))
    private fun repository() = AddonLibraryRepository(storage, cipher, access) { 123L }

    @Test fun savedArtworkAndOriginalIdentityAreEncryptedDurableAndIdempotent(): Unit = runBlocking {
        val first = repository(); val key = identity()
        first.add("adult", key, art, "2026"); first.add("adult", key, art, "2026")
        val saved = repository().list("adult").single()
        assertEquals("opaque", saved.preview().key.id); assertEquals("2026", saved.preview().releaseInfo)
        assertEquals(art.poster, saved.preview().poster); assertEquals(123L, saved.addedAtMillis)
        assertFalse(storage.rows.values.single().encryptedPayload.contains("Private title"))
        assertFalse(storage.rows.values.single().encryptedPayload.contains("synthetic"))
        assertFalse(saved.toString().contains("Private")); assertFalse(key.toString().contains("opaque"))
    }
    @Test fun movieSeriesProvidersAndProfilesNeverCollide(): Unit = runBlocking {
        val repo = repository()
        repo.add("adult", identity(), art); repo.add("adult", identity("series"), art)
        repo.add("adult", identity(owner = "two"), art); repo.add("other", identity(), art)
        assertEquals(3, repo.list("adult").size); assertEquals(1, repo.list("other").size)
        repo.remove("adult", identity()); repo.remove("adult", identity())
        assertNull(repo.get("adult", identity())); assertEquals(2, repo.list("adult").size)
        assertNotNull(repo.get("other", identity()))
    }
    @Test fun invalidIdentityArtworkAndRestrictedAccessCannotWrite(): Unit = runBlocking {
        val repo = repository()
        expectFailure(AddonFailure.ACCESS_DENIED) { repo.add("child", identity(), art) }
        expectFailure(AddonFailure.ACCESS_DENIED) { repo.list("child") }
        expectFailure(AddonFailure.ACCESS_DENIED) { repo.remove("child", identity()) }
        expectFailure(AddonFailure.INVALID_REQUEST) { repo.add("adult", identity("episode"), art) }
        expectFailure(AddonFailure.INVALID_REQUEST) { repo.add("adult", identity(id = ""), art) }
        expectFailure(AddonFailure.INVALID_REQUEST) { repo.add("adult", identity(), AddonWatchArtwork("Bad", "file:///private")) }
        expectFailure(AddonFailure.INVALID_REQUEST) { repo.add("adult", identity(), art, "x".repeat(257)) }
        assertTrue(storage.rows.isEmpty())
    }
    @Test fun capacityNeverEvictsOldTitlesAndExistingEntryCanBeUpdated(): Unit = runBlocking {
        val repo = repository()
        repeat(AddonLibraryRepository.MAX_TITLES) { repo.add("adult", identity(id = "$it"), art) }
        expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { repo.add("adult", identity(id = "overflow"), art) }
        assertEquals(1000, repo.list("adult").size); assertNotNull(repo.get("adult", identity(id = "0")))
        repo.add("adult", identity(id = "0"), art, "2025")
        repo.remove("adult", identity(id = "0")); repo.add("adult", identity(id = "overflow"), art)
        assertEquals(1000, repo.list("adult").size)
    }
    @Test fun concurrentDuplicateAddsOnlyCreateOneBookmark(): Unit = runBlocking {
        val repo = repository()
        coroutineScope { repeat(20) { launch { repo.add("adult", identity(), art) } } }
        assertEquals(1, repo.list("adult").size)
    }
    @Test fun corruptPayloadAndForeignRowFailWithoutLeakingData(): Unit = runBlocking {
        val repo = repository(); repo.add("adult", identity(), art)
        val original = storage.rows.values.single()
        storage.rows[original.key] = AddonLibraryRow(original.key, "adult", "Secret corruption", 123)
        expectFailure(AddonFailure.STORAGE) { repo.list("adult") }
        storage.rows[original.key] = AddonLibraryRow(original.key, "adult", original.encryptedPayload, 456)
        expectFailure(AddonFailure.STORAGE) { repo.list("adult") }
    }
    @Test fun accessRevocationDuringReadDoesNotReturnSavedTitles(): Unit = runBlocking {
        repository().add("adult", identity(), art)
        var allowed = true
        val wrapped = object : AddonLibraryPersistence by storage {
            override suspend fun list(profile: String): List<AddonLibraryRow> { allowed = false; return storage.list(profile) }
        }
        expectFailure(AddonFailure.ACCESS_DENIED) {
            AddonLibraryRepository(wrapped, cipher, AddonManagementAccess { allowed }).list("adult")
        }
    }
}

private class MemoryLibrary : AddonLibraryPersistence {
    val rows = linkedMapOf<String, AddonLibraryRow>()
    override suspend fun get(profile: String, key: String) = rows[key]?.takeIf { it.profileId == profile }
    override suspend fun list(profile: String) = rows.values.filter { it.profileId == profile }.sortedByDescending { it.addedAtMillis }
    override suspend fun put(row: AddonLibraryRow): Boolean {
        if (row.key !in rows && rows.values.count { it.profileId == row.profileId } >= AddonLibraryRepository.MAX_TITLES) return false
        rows[row.key] = row; return true
    }
    override suspend fun remove(profile: String, key: String) { if (rows[key]?.profileId == profile) rows.remove(key) }
}
