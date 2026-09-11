package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AddonManagerTest {
    private val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())

    @Test fun accessIsDeniedByDefaultBeforeAnyNetworkRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            val manager = AddonManager(AddonClient(), store)
            expectFailure(AddonFailure.ACCESS_DENIED) { manager.install("child", server.url("/manifest.json").toString(), true) }
            expectFailure(AddonFailure.ACCESS_DENIED) { manager.list("child") }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun manualInstallRefreshAndDuplicateReuse(): Unit = runBlocking {
        MockWebServer().use { server ->
            val manager = AddonManager(AddonClient(), store, AddonManagementAccess { true })
            server.enqueue(MockResponse().setBody(aioManifest()))
            val url = server.url("/Secret/manifest.json").toString()
            val first = manager.install("adult", url, true)
            assertEquals(first.installationId, manager.install("adult", url, true).installationId)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody(aioManifest("Changed")))
            assertTrue(manager.refresh("adult", first.installationId))
            assertEquals("Changed", manager.list("adult").single().manifest.name)
        }
    }
    @Test fun authorizationIsRecheckedAfterTheNetworkRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            var checks = 0
            val manager = AddonManager(AddonClient(), store, AddonManagementAccess { ++checks == 1 })
            server.enqueue(MockResponse().setBody(aioManifest()))
            expectFailure(AddonFailure.ACCESS_DENIED) { manager.install("adult", server.url("/manifest.json").toString(), true) }
            assertTrue(store.list("adult").isEmpty())
        }
    }
    @Test fun failedRefreshKeepsOtherAddonsAndTheExistingManifest(): Unit = runBlocking {
        MockWebServer().use { server ->
            val manager = AddonManager(AddonClient(), store, AddonManagementAccess { true })
            server.enqueue(MockResponse().setBody(aioManifest()))
            val first = manager.install("adult", server.url("/manifest.json").toString(), true)
            server.enqueue(MockResponse().setResponseCode(503))
            expectFailure(AddonFailure.HTTP_ERROR) { manager.refresh("adult", first.installationId) }
            assertEquals("Metadata fixture", manager.list("adult").single().manifest.name)
        }
    }
}
