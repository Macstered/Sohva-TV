package com.sohva.tv.addons

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddonHeroSynopsisTest {
    private fun media(id: String = "original", description: String? = "English catalog", type: String = "movie") =
        AddonMedia(AddonMediaKey(type, id), "Catalog name", "poster", "poster", "background", description,
            "2026", emptyList(), "original-video", "logo", listOf("Drama"), "90m", "7.0")

    @Test fun freshCachedSynopsisIsFirstEmissionAndKeepsCatalogIdentityArtworkAndHints() = runTest {
        var requests = 0
        val preview = media()
        val resolver = AddonHeroSynopsis({ _, _, _ -> media("canonical", "Suomenkielinen kuvaus") },
            { _, _, _ -> requests++; error("Must not request metadata") })
        val result = resolver.resolve("profile", "owner", preview).toList().single()
        assertEquals("Suomenkielinen kuvaus", result.description)
        assertSame(preview.key, result.key)
        assertEquals(preview.name, result.name); assertEquals(preview.logo, result.logo)
        assertEquals(preview.poster, result.poster); assertEquals(preview.background, result.background)
        assertEquals(preview.defaultVideoId, result.defaultVideoId); assertSame(preview.videos, result.videos)
        assertEquals(0, requests)
    }

    @Test fun networkWaitsForSettledFocusAndReceivesOriginalOwnerAndKey() = runTest {
        var requests = 0
        val preview = media()
        val resolver = AddonHeroSynopsis({ _, _, _ -> null }, { profile, owner, key ->
            assertEquals("profile", profile); assertEquals("owner", owner); assertEquals(preview.key, key)
            requests++; media("canonical", "Suomeksi")
        })
        val values = mutableListOf<AddonMedia>()
        val job = launch { resolver.resolve("profile", "owner", preview).toList(values) }
        advanceTimeBy(120); runCurrent()
        val pending = values.single()
        assertNull(pending.description)
        assertSame(preview.key, pending.key); assertEquals(preview.name, pending.name)
        assertEquals(preview.poster, pending.poster); assertEquals(preview.logo, pending.logo)
        assertEquals(preview.background, pending.background)
        assertEquals(preview.defaultVideoId, pending.defaultVideoId)
        assertEquals(0, requests)
        advanceTimeBy(349); runCurrent(); assertEquals(0, requests)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, requests); assertEquals("Suomeksi", values.last().description)
        assertEquals(listOf(null, "Suomeksi"), values.map { it.description })
        job.join()
    }

    @Test fun catalogSynopsisStaysHiddenUntilMetadataFinishes() = runTest {
        val response = CompletableDeferred<AddonMedia?>()
        val resolver = AddonHeroSynopsis({ _, _, _ -> null }, { _, _, _ -> response.await() })
        val values = mutableListOf<AddonMedia>()
        val job = launch { resolver.resolve("profile", "owner", media()).toList(values) }
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf<String?>(null), values.map { it.description })
        response.complete(media(description = "Suomeksi")); job.join()
        assertEquals(listOf(null, "Suomeksi"), values.map { it.description })
    }

    @Test fun rapidFocusCancellationNeverStartsNetwork() = runTest {
        var requests = 0
        val resolver = AddonHeroSynopsis({ _, _, _ -> null }, { _, _, _ -> requests++; media() })
        repeat(12) {
            val job = launch { resolver.resolve("profile", "owner", media(id = "$it")).toList() }
            advanceTimeBy(200); job.cancelAndJoin()
        }
        assertEquals(0, requests)
    }

    @Test fun cancelledInFlightResponseCannotPublishAndDoesNotCreateFailureCooldown() = runTest {
        var requests = 0
        var cancelled = false
        val pending = CompletableDeferred<AddonMedia>()
        val resolver = AddonHeroSynopsis({ _, _, _ -> null }, { _, _, _ ->
            requests++
            if (requests == 1) try { pending.await() } finally { cancelled = true }
            else media(description = "New response")
        })
        val old = mutableListOf<AddonMedia>()
        val job = launch { resolver.resolve("profile", "owner", media()).toList(old) }
        advanceTimeBy(500); runCurrent(); job.cancelAndJoin()
        pending.complete(media(description = "Obsolete response"))
        assertTrue(cancelled); assertEquals(listOf<String?>(null), old.map { it.description })
        assertEquals("New response", resolver.resolve("profile", "owner", media()).toList().last().description)
        assertEquals(2, requests)
    }

    @Test fun timeoutFallsBackAndCooldownAvoidsHammeringUnavailableProvider() = runTest {
        var requests = 0
        val resolver = AddonHeroSynopsis({ _, _, _ -> null }, { _, _, _ -> requests++; delay(60_000); media() },
            clock = { testScheduler.currentTime })
        val preview = media()
        assertEquals(listOf(null, "English catalog"), resolver.resolve("profile", "owner", preview).toList().map { it.description })
        assertEquals(8_470, testScheduler.currentTime)
        // A known failed lookup returns its fallback directly, without another blank flash.
        assertEquals(listOf(preview), resolver.resolve("profile", "owner", preview).toList())
        assertEquals(1, requests)
        advanceTimeBy(30_000)
        assertEquals(listOf(null, "English catalog"), resolver.resolve("profile", "owner", preview).toList().map { it.description })
        assertEquals(2, requests)
    }

    @Test fun cacheLookupIsBoundedAndFreshDetailsOverrideAnEarlierFailure() = runTest {
        var cached: AddonMedia? = null
        var slowCache = true
        var requests = 0
        val resolver = AddonHeroSynopsis({ _, _, _ -> if (slowCache) delay(60_000); cached },
            { _, _, _ -> requests++; throw AddonException(AddonFailure.NETWORK) }, clock = { testScheduler.currentTime })
        assertEquals(listOf(null, "English catalog"), resolver.resolve("profile", "owner", media()).toList().map { it.description })
        assertEquals(670, testScheduler.currentTime)
        slowCache = false; cached = media(description = "Opened details now cached")
        assertEquals("Opened details now cached", resolver.resolve("profile", "owner", media()).toList().single().description)
        assertEquals(1, requests)
    }

    @Test fun missingOrWrongTypeSynopsisKeepsPreviewAndIsolatesFailuresByOwnerAndProfile() = runTest {
        var requests = 0
        val resolver = AddonHeroSynopsis({ _, _, _ -> null }, { _, _, _ ->
            requests++; media(description = "Wrong series", type = "series")
        }, clock = { testScheduler.currentTime })
        val preview = media()
        assertEquals(listOf(null, "English catalog"), resolver.resolve("profile", "owner", preview).toList().map { it.description })
        resolver.resolve("profile", "owner", preview).toList(); assertEquals(1, requests)
        resolver.resolve("profile", "other-owner", preview).toList()
        resolver.resolve("other-profile", "owner", preview).toList(); assertEquals(3, requests)
        for (description in listOf(null, "", " ")) {
            val empty = AddonHeroSynopsis({ _, _, _ -> media(description = description) }, { _, _, _ -> media(description = description) })
            assertEquals(listOf(null, "English catalog"), empty.resolve("profile", "owner", preview).toList().map { it.description })
        }
    }

    @Test fun accessRevocationPropagatesInsteadOfPublishingPrivateMetadata() = runTest {
        for (failure in listOf(AddonFailure.ACCESS_DENIED, AddonFailure.CONFLICT, AddonFailure.NOT_FOUND)) {
            val resolver = AddonHeroSynopsis({ _, _, _ -> null }, { _, _, _ -> throw AddonException(failure) })
            val values = mutableListOf<AddonMedia>()
            try { resolver.resolve("profile", "owner", media()).toList(values); fail("Revocation swallowed") }
            catch (error: AddonException) { assertEquals(failure, error.failure) }
            assertEquals(listOf<String?>(null), values.map { it.description })
        }
        val cacheDenied = AddonHeroSynopsis({ _, _, _ -> throw AddonException(AddonFailure.ACCESS_DENIED) }, { _, _, _ -> error("No request") })
        val values = mutableListOf<AddonMedia>()
        try { cacheDenied.resolve("profile", "owner", media()).toList(values); fail("Cache denial swallowed") }
        catch (error: AddonException) { assertEquals(AddonFailure.ACCESS_DENIED, error.failure) }
        assertTrue(values.isEmpty())
    }

    @Test fun resolverAllowsOnlyOneConcurrentNetworkLookup() = runTest {
        var active = 0; var maximum = 0
        val resolver = AddonHeroSynopsis({ _, _, _ -> null }, { _, _, _ ->
            active++; maximum = maxOf(maximum, active)
            try { delay(1000); media(description = "Translated") } finally { active-- }
        })
        val jobs = (1..3).map { launch { resolver.resolve("profile", "owner", media(id = "$it")).toList() } }
        advanceUntilIdle(); jobs.forEach { it.join() }
        assertEquals(1, maximum); assertEquals(0, active)
    }
}
