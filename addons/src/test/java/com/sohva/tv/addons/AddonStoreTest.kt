package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AddonStoreTest {
    private val persistence = MemoryAddonPersistence()
    private val cipher = TestAddonCipher()
    private val store = EncryptedAddonStore(persistence, cipher, clock = { 1234 })
    private fun endpoint(config: String) = AddonEndpoint.parse("https://example.invalid/$config/manifest.json")

    @Test fun configuredUrlsAndManifestPayloadsAreEncryptedAndRestorable(): Unit = runBlocking {
        val installed = store.install("adult", endpoint("PrivateConfig"), aioManifest("PrivateName"))
        val row = persistence.rows.values.single()
        assertFalse(row.toString().contains("PrivateConfig"))
        assertFalse(row.toString().contains("PrivateName"))
        assertFalse(installed.toString().contains("Private"))
        val reopened = EncryptedAddonStore(persistence, cipher).list("adult").single()
        assertEquals(installed.installationId, reopened.installationId)
        assertEquals("PrivateName", reopened.manifest.name)
        assertEquals(endpoint("PrivateConfig").exportConfiguredUrl(), reopened.endpoint.exportConfiguredUrl())
    }
    @Test fun sameManifestIdWithDifferentConfigurationsCanCoexist(): Unit = runBlocking {
        store.install("adult", endpoint("A"), aioManifest())
        store.install("adult", endpoint("B"), aioManifest())
        store.install("child", endpoint("A"), aioManifest())
        assertEquals(2, store.list("adult").size)
        assertEquals(1, store.list("child").size)
        assertTrue(store.list("other").isEmpty())
    }
    @Test fun duplicateAndConcurrentInstallsPreserveIdentityAndDisabledState(): Unit = runBlocking {
        val first = store.install("adult", endpoint("A"), aioManifest())
        store.setEnabled("adult", first.installationId, false)
        val installs = (1..8).map { async { store.install("adult", endpoint("A"), aioManifest()) } }.awaitAll()
        assertTrue(installs.all { it.installationId == first.installationId && !it.enabled })
        assertEquals(1, store.list("adult").size)
    }
    @Test fun anotherProfileCannotEditOrDeleteAnInstallation(): Unit = runBlocking {
        val first = store.install("adult", endpoint("A"), aioManifest())
        expectFailure(AddonFailure.NOT_FOUND) { store.remove("child", first.installationId) }
        expectFailure(AddonFailure.NOT_FOUND) { store.setEnabled("child", first.installationId, false) }
        assertFalse(store.refresh("child", first.installationId, first.revision, aioManifest("Changed")))
        assertTrue(store.list("adult").single().enabled)
    }
    @Test fun sameVersionRefreshUpdatesButStaleResponsesDoNot(): Unit = runBlocking {
        val first = store.install("adult", endpoint("A"), aioManifest())
        assertTrue(store.refresh("adult", first.installationId, first.revision, aioManifest("Changed")))
        assertEquals("Changed", store.list("adult").single().manifest.name)
        assertFalse(store.refresh("adult", first.installationId, first.revision, aioManifest("Stale")))
        assertEquals("Changed", store.list("adult").single().manifest.name)
    }
    @Test fun removedInstallationsCannotBeResurrectedByRefresh(): Unit = runBlocking {
        val first = store.install("adult", endpoint("A"), aioManifest())
        store.remove("adult", first.installationId)
        assertFalse(store.refresh("adult", first.installationId, first.revision, aioManifest()))
        assertTrue(store.list("adult").isEmpty())
    }
    @Test fun reorderRequiresExactlyTheCurrentProfileSet(): Unit = runBlocking {
        val first = store.install("adult", endpoint("A"), aioManifest())
        val second = store.install("adult", endpoint("B"), aioManifest())
        expectFailure(AddonFailure.CONFLICT) { store.reorder("adult", listOf(first.installationId, first.installationId)) }
        expectFailure(AddonFailure.CONFLICT) { store.reorder("child", listOf(first.installationId)) }
        store.reorder("adult", listOf(second.installationId, first.installationId))
        assertEquals(listOf(second.installationId, first.installationId), store.list("adult").map { it.installationId })
    }
    @Test fun invalidRefreshLeavesTheLastGoodSnapshot(): Unit = runBlocking {
        val first = store.install("adult", endpoint("A"), aioManifest())
        expectFailure(AddonFailure.INVALID_MANIFEST) { store.refresh("adult", first.installationId, first.revision, "{secret") }
        assertEquals(first.revision, store.list("adult").single().revision)
        expectFailure(AddonFailure.CONFIGURATION_REQUIRED) { store.install("adult", endpoint("B"), aioManifest(required = true)) }
        assertEquals(1, store.list("adult").size)
    }
    @Test fun cipherFailureIsSanitizedAndNeverBecomesPlaintextStorage(): Unit = runBlocking {
        val broken = object : AddonSecretCipher {
            override fun encrypt(plaintext: String): String = error("secret plaintext=$plaintext")
            override fun decrypt(ciphertext: String): String = error("secret")
        }
        val error = expectFailure(AddonFailure.STORAGE) { EncryptedAddonStore(persistence, broken).install("adult", endpoint("Secret"), aioManifest()) }
        assertFalse(error.toString().contains("Secret"))
        assertTrue(persistence.rows.isEmpty())
    }
}
