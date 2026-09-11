package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AddonBatchImportTest {
    private val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
    @Test fun selectingCopiesOnlyChosenNewEntriesInOriginalOrder(): Unit = runBlocking {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setBody(aioManifest())) }
            val importer = AddonBatchImport(AddonClient(), store, AddonManagementAccess { true })
            val input = listOf("first", "second", "third").joinToString("\n") { server.url("/$it/manifest.json").toString() }
            val preview = importer.preview("adult", input, true)
            assertTrue(importer.commit(preview.selecting(emptySet())).isEmpty())
            val result = importer.commit(preview.selecting(setOf(3, 1, 999)))
            assertEquals(listOf(1, 3), result.map { it.line })
            assertEquals(2, store.list("adult").size)
            assertTrue(store.list("adult").first().endpoint.exportConfiguredUrl().contains("/first/"))
        }
    }
    @Test fun batchBoundsAndDefaultDenialMakeNoNetworkRequests(): Unit = runBlocking {
        val denied = AddonBatchImport(AddonClient(), store)
        expectFailure(AddonFailure.ACCESS_DENIED) { denied.preview("adult", "https://example.invalid/") }
        val importer = AddonBatchImport(AddonClient(), store, AddonManagementAccess { true })
        for (input in listOf("\n  \n", "x".repeat(262_145), List(33) { "https://example.invalid/$it/" }.joinToString("\n"))) {
            expectFailure(AddonFailure.INVALID_REQUEST) { importer.preview("adult", input) }
        }
        assertTrue(store.list("adult").isEmpty())
    }
    @Test fun existingDisabledInstallationIsNeitherRefetchedNorReenabled(): Unit = runBlocking {
        MockWebServer().use { server ->
            val endpoint = AddonEndpoint.parse(server.url("/configured/manifest.json").toString(), true)
            val installed = store.install("adult", endpoint, aioManifest())
            store.setEnabled("adult", installed.installationId, false)
            val importer = AddonBatchImport(AddonClient(), store, AddonManagementAccess { true })
            val result = importer.commit(importer.preview("adult", endpoint.exportConfiguredUrl(), true)).single()
            assertEquals(AddonImportStatus.ALREADY_INSTALLED, result.status)
            assertFalse(store.list("adult").single().enabled)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun previewIsNonMutatingAndCommitDeduplicatesInInputOrder(): Unit = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(aioManifest())) }
            val importer = AddonBatchImport(AddonClient(), store, AddonManagementAccess { true })
            val first = server.url("/Config").toString()
            val second = server.url("/Other/manifest.json").toString()
            val preview = importer.preview("adult", "$first\n\n$first/manifest.json\n$second", true)
            assertTrue(store.list("adult").isEmpty())
            assertEquals(listOf(AddonImportStatus.READY, AddonImportStatus.DUPLICATE_INPUT, AddonImportStatus.READY), preview.entries.map { it.status })
            assertFalse(preview.toString().contains("Config"))
            assertEquals(2, importer.commit(preview).count { it.status == AddonImportStatus.INSTALLED })
            assertEquals(2, importer.commit(preview).count { it.status == AddonImportStatus.ALREADY_INSTALLED })
            assertEquals(2, server.requestCount)
            assertTrue(store.list("adult").first().endpoint.exportConfiguredUrl().endsWith("/Config/manifest.json"))
        }
    }
    @Test fun failedManifestDoesNotDiscardOtherAddonsAndAccessIsRechecked(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody(aioManifest()))
            var allowed = true
            val importer = AddonBatchImport(AddonClient(), store, AddonManagementAccess { allowed })
            val preview = importer.preview("adult", "${server.url("/Bad/")}\n${server.url("/Good/")}", true)
            assertEquals(AddonFailure.HTTP_ERROR, preview.entries.first().failure)
            allowed = false
            expectFailure(AddonFailure.ACCESS_DENIED) { importer.commit(preview) }
            assertTrue(store.list("adult").isEmpty())
            allowed = true
            assertEquals(1, importer.commit(preview).count { it.status == AddonImportStatus.INSTALLED })
        }
    }
}
