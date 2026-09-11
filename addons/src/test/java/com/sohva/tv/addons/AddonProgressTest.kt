package com.sohva.tv.addons

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AddonProgressTest {
    private val persistence = MemoryProgress()
    private val cipher = TestAddonCipher()
    private val access = AddonManagementAccess { it == "adult" }
    private val identity = AddonWatchIdentity("metadata-install", AddonMediaKey("series", "tmdb:original"), AddonMediaKey("series", "opaque:episode"))
    private fun repository() = AddonProgressRepository(persistence, cipher, access)

    @Test fun encryptedHistoryReopensWithStableIdentityAndNoSourceUrls(): Unit = runBlocking {
        val repository = repository()
        val session = repository.begin("adult", identity, "Private fixture title", AddonWatchArtwork("Fixture series", "https://example.invalid/poster.jpg", "https://example.invalid/backdrop.jpg"))
        repository.save(session, 1, 12_000, 120_000)
        assertFalse(persistence.rows.values.single().encryptedPayload.contains("Private fixture"))
        val saved = repository().get("adult", identity)!!
        assertEquals(12_000, saved.resumePositionMillis)
        assertEquals("opaque:episode", saved.identity.video.id)
        assertEquals("tmdb:original", saved.identity.media.id)
        assertEquals("https://example.invalid/poster.jpg", saved.artwork?.poster)
        assertEquals("Fixture series", saved.artwork?.name)
        assertFalse(persistence.rows.values.single().encryptedPayload.contains("poster.jpg"))
        assertFalse(saved.toString().contains("Private"))
        assertNull(repository.get("adult", AddonWatchIdentity("other-config", identity.media, identity.video)))
    }
    @Test fun staleSnapshotsCannotOverwriteNewerSessionOrSequence(): Unit = runBlocking {
        val repository = repository()
        val old = repository.begin("adult", identity, "Fixture")
        repository.save(old, 2, 20_000, 120_000)
        repository.save(old, 1, 10_000, 120_000)
        assertEquals(20_000, repository.get("adult", identity)!!.positionMillis)
        val current = repository.begin("adult", identity, "Fixture")
        repository.save(current, 1, 30_000, 120_000)
        repository.save(old, 3, 40_000, 120_000)
        assertEquals(30_000, repository.get("adult", identity)!!.positionMillis)
    }
    @Test fun unknownDurationPreservesKnownValueAndCompletionStartsFromBeginning(): Unit = runBlocking {
        val repository = repository()
        val session = repository.begin("adult", identity, "Fixture")
        repository.save(session, 1, 50_000, 120_000)
        repository.save(session, 2, 60_000, -1)
        assertEquals(120_000, repository.get("adult", identity)!!.durationMillis)
        repository.save(session, 3, 119_000, 120_000)
        assertEquals(0, repository.get("adult", identity)!!.resumePositionMillis)
        repository.save(session, 4, 20_000, 120_000)
        assertFalse(repository.get("adult", identity)!!.completed)
    }
    @Test fun profileRestrictionsAndRemovalRejectLateWrites(): Unit = runBlocking {
        val repository = repository()
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.recent("child") }
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.begin("child", identity, "Fixture") }
        val session = repository.begin("adult", identity, "Fixture")
        repository.save(session, 1, 1000, 120_000)
        repository.remove("adult", identity)
        repository.save(session, 2, 2000, 120_000)
        assertNull(repository.get("adult", identity))
    }
    @Test fun artworkRepairPreservesLegacyProgressOrderingAndActiveSessionAndCannotResurrectRemoval(): Unit = runBlocking {
        var now = 100L
        val repository = AddonProgressRepository(persistence, cipher, access) { now }
        val session = repository.begin("adult", identity, "Legacy title")
        repository.save(session, 1, 25_000, 120_000)
        assertNull(repository().get("adult", identity)!!.artwork)
        val artwork = AddonWatchArtwork("Series name", "https://example.invalid/poster.jpg")
        now = 200
        repository.updateArtwork("adult", identity, artwork)
        val repaired = repository().get("adult", identity)!!
        assertEquals(100L, repaired.updatedAtMillis)
        assertEquals(25_000L, repaired.positionMillis)
        assertEquals(120_000L, repaired.durationMillis)
        assertFalse(repaired.completed)
        repository.save(session, 2, 30_000, 120_000)
        assertEquals(artwork.poster, repository.get("adult", identity)!!.artwork?.poster)
        expectFailure(AddonFailure.ACCESS_DENIED) { repository.updateArtwork("child", identity, artwork) }
        expectFailure(AddonFailure.INVALID_REQUEST) { repository.updateArtwork("adult", identity, AddonWatchArtwork("Invalid", "file:///private")) }
        repository.remove("adult", identity)
        repository.updateArtwork("adult", identity, artwork)
        assertNull(repository.get("adult", identity))
    }
}

internal class MemoryProgress : AddonProgressPersistence {
    val rows = linkedMapOf<String, AddonProgressRow>()
    override suspend fun get(profileId: String, key: String) = rows[key]?.takeIf { it.profileId == profileId }
    override suspend fun recent(profileId: String) = rows.values.filter { it.profileId == profileId }.sortedByDescending { it.updatedAtMillis }
    override suspend fun put(row: AddonProgressRow) { rows[row.key] = row }
    override suspend fun remove(profileId: String, key: String) { if (rows[key]?.profileId == profileId) rows.remove(key) }
}
