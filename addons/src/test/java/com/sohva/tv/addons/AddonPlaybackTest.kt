package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AddonPlaybackTest {
    @Test fun playbackChecksProfileEnabledRevisionAndRefreshesExpiredUrl(): Unit = runBlocking {
        MockWebServer().use { server ->
            val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
            val installed = store.install("adult", AddonEndpoint.parse(server.url("/manifest.json").toString(), true), """{"id":"fixture","version":"1","name":"Fixture","types":["movie"],"resources":["stream"]}""")
            val resolver = AddonPlaybackAccess(AddonClient(), store, AddonManagementAccess { it == "adult" })
            val old = AddonSourceParser.streams("""{"streams":[{"name":"Fixture","url":"https://example.invalid/old","behaviorHints":{"filename":"fixture.mp4"}}]}""").single()
            val selected = AddonPlaybackSelection(installed.installationId, installed.revision, AddonMediaKey("movie", "tt1"), old)
            resolver.check("adult", selected)
            expectFailure(AddonFailure.ACCESS_DENIED) { resolver.check("child", selected) }
            server.enqueue(MockResponse().setBody("""{"streams":[{"name":"Fixture","url":"https://example.invalid/fresh","behaviorHints":{"filename":"fixture.mp4"}}]}"""))
            assertTrue(resolver.refresh("adult", selected).stream.url!!.endsWith("/fresh"))
            server.enqueue(MockResponse().setBody("""{"streams":[]}"""))
            expectFailure(AddonFailure.CONFLICT) { resolver.refresh("adult", selected) }
            store.setEnabled("adult", installed.installationId, false)
            expectFailure(AddonFailure.NOT_FOUND) { resolver.check("adult", selected) }
            store.setEnabled("adult", installed.installationId, true)
            expectFailure(AddonFailure.CONFLICT) { resolver.check("adult", selected) }
        }
    }
}
