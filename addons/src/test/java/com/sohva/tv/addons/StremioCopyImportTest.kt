package com.sohva.tv.addons

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class StremioCopyImportTest {
    private val link = StremioImportLink("ABCD", "https://link.stremio.com/ABCD")
    private fun client(server: MockWebServer, maxBytes: Int = 2 * 1024 * 1024, timeout: Long = 2000) =
        StremioCopyClient(server.url("/"), server.url("/"), timeout, maxBytes)
    private fun reply(body: String) = MockResponse().setBody(body)
    private fun collection() = reply("""{"result":{"addons":[{"transportUrl":"https://example.invalid/Secret/manifest.json"}]}}""")

    @Test fun liveWaitingErrorKeepsSameLinkUntilAuthorization(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(reply("""{"result":{"code":"ABCD","link":"https://link.stremio.com/ABCD"}}"""))
            repeat(3) { server.enqueue(reply("""{"error":{"code":101,"message":"Fixture waiting"}}""")) }
            server.enqueue(reply("""{"result":{"authKey":"DeviceSecret"}}"""))
            server.enqueue(reply("""{"result":{"authKey":"AccountSecret"}}"""))
            server.enqueue(collection())
            val events = StremioCopyImport(AddonManagementAccess { true }, client(server), pollMillis = 1).copy("adult").toList()
            assertEquals(2, events.size)
            assertTrue(events.first() is StremioCopyEvent.Waiting)
            assertEquals(1, (events.last() as StremioCopyEvent.Ready).addons.count)
            val paths = List(7) { checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)).path }
            assertEquals(listOf("/api/create?type=Create") + List(4) { "/api/read?type=Read&code=ABCD" } +
                listOf("/api/loginWithToken", "/api/addonCollectionGet"), paths)
            assertEquals(7, server.requestCount)
        }
    }

    @Test fun waitingResponsesNeverReachAccountEndpoints(): Unit = runBlocking {
        MockWebServer().use { server ->
            val pending = listOf(
                """{"error":{"code":101,"message":"Secret"}}""",
                """{"error":{"code":101},"result":null}""",
                """{"result":null}""",
                """{"result":{"success":false}}""",
            )
            pending.forEach { body ->
                server.enqueue(reply(body))
                var accessChecks = 0
                assertNull(client(server).readAddons(link) { accessChecks++ })
                assertEquals(1, accessChecks)
                assertEquals("/api/read?type=Read&code=ABCD", server.takeRequest(1, TimeUnit.SECONDS)?.path)
            }
            assertEquals(pending.size, server.requestCount)
        }
    }

    @Test fun onlyNumericWaitingErrorWithoutResultIsAcceptedOnRead(): Unit = runBlocking {
        MockWebServer().use { server ->
            val rejected = listOf(
                """{"error":{"code":100,"message":"Secret"}}""",
                """{"error":{"code":102,"message":"Secret"}}""",
                """{"error":{"code":"101","message":"Secret"}}""",
                """{"error":{"code":101.0,"message":"Secret"}}""",
                """{"error":{"code":101},"result":{"authKey":"Secret"}}""",
                """{"error":{"code":101},"result":{"success":false}}""",
            )
            rejected.forEach { body ->
                server.enqueue(reply(body))
                val failure = expectFailure(AddonFailure.INVALID_RESPONSE) { client(server).readAddons(link) }
                assertFalse(failure.stackTraceToString().contains("Secret"))
            }
            assertEquals(rejected.size, server.requestCount)
        }
    }

    @Test fun waitingCodeOnCreateLoginOrCollectionStillFailsClosed(): Unit = runBlocking {
        MockWebServer().use { server ->
            val pending = """{"error":{"code":101,"message":"Secret"}}"""
            server.enqueue(reply(pending))
            expectFailure(AddonFailure.INVALID_RESPONSE) { client(server).createLink() }
            server.enqueue(reply("""{"result":{"authKey":"DeviceSecret"}}"""))
            server.enqueue(reply(pending))
            expectFailure(AddonFailure.INVALID_RESPONSE) { client(server).readAddons(link) }
            server.enqueue(reply("""{"result":{"authKey":"DeviceSecret"}}"""))
            server.enqueue(reply("""{"result":{"authKey":"AccountSecret"}}"""))
            server.enqueue(reply(pending))
            expectFailure(AddonFailure.INVALID_RESPONSE) { client(server).readAddons(link) }
            assertEquals(6, server.requestCount)
        }
    }

    @Test fun copyOnlyUsesOfficialRequestShapesAndNeverFetchesAddonProviders(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(reply("""{"result":{"code":"ABCD","link":"https://link.stremio.com/ABCD","qrcode":"https://evil.invalid/secret"}}"""))
            server.enqueue(reply("""{"result":{"success":false}}"""))
            server.enqueue(reply("""{"result":{"success":true,"authKey":"DeviceSecret"}}"""))
            server.enqueue(reply("""{"result":{"authKey":"AccountSecret","user":{"email":"ignored"}}}"""))
            server.enqueue(collection())
            val events = StremioCopyImport(AddonManagementAccess { true }, client(server), pollMillis = 1).copy("adult").toList()
            assertEquals(2, events.size)
            val ready = events.last() as StremioCopyEvent.Ready
            assertEquals(1, ready.addons.count)
            assertFalse(ready.addons.toString().contains("Secret"))
            val requests = List(5) { checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)) }
            assertEquals(listOf("/api/create?type=Create", "/api/read?type=Read&code=ABCD",
                "/api/read?type=Read&code=ABCD", "/api/loginWithToken", "/api/addonCollectionGet"), requests.map { it.path })
            assertEquals(listOf("GET", "GET", "GET", "POST", "POST"), requests.map { it.method })
            val login = Json.parseToJsonElement(requests[3].body.readUtf8()).jsonObject
            assertEquals(setOf("type", "token"), login.keys)
            val get = Json.parseToJsonElement(requests[4].body.readUtf8()).jsonObject
            assertEquals(JsonPrimitive(false), get["update"])
            assertEquals(JsonPrimitive("AccountSecret"), get["authKey"])
            assertTrue(requests.all { it.getHeader("Cookie") == null && it.getHeader("Authorization") == null })
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun hostileActivationUrlsAndUntrustedErrorsFailWithoutDisclosure(): Unit = runBlocking {
        MockWebServer().use { server ->
            for (url in listOf("http://link.stremio.com/ABCD", "https://link.stremio.com.evil.invalid/ABCD",
                "https://link.stremio.com/ABCD".replace("https://", "https://user:Secret@"), "https://link.stremio.com/Other",
                "https://link.stremio.com/ABCD?redirect=Secret", "https://link.stremio.com:444/ABCD")) {
                server.enqueue(reply("""{"result":{"code":"ABCD","link":"$url"}}"""))
                val error = expectFailure(AddonFailure.INVALID_RESPONSE) { client(server).createLink() }
                assertFalse(error.toString().contains("Secret"))
                assertNull(error.cause)
            }
            server.enqueue(reply("""{"error":{"code":1,"message":"Secret"}}"""))
            expectFailure(AddonFailure.INVALID_RESPONSE) { client(server).readAddons(link) }
        }
    }

    @Test fun redirectsHttpErrorsAndKnownOrChunkedOversizeAreBounded(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://evil.invalid/Secret"))
            expectFailure(AddonFailure.REDIRECT) { client(server).createLink() }
            server.enqueue(MockResponse().setResponseCode(429).setBody("Secret"))
            assertEquals(429, expectFailure(AddonFailure.HTTP_ERROR) { client(server).createLink() }.httpStatus)
            server.enqueue(reply("x".repeat(1025)))
            server.enqueue(MockResponse().setChunkedBody("x".repeat(1025), 64))
            repeat(2) { expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { client(server, maxBytes = 1024).createLink() } }
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun cancellationAndTimeoutStopNetwork(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val waiting = async { client(server).createLink() }
            yield()
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            waiting.cancelAndJoin()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            expectFailure(AddonFailure.TIMEOUT) { client(server, timeout = 100).createLink() }
        }
    }

    @Test fun compressedPayloadAndNestedJsonAreStillBounded(): Unit = runBlocking {
        MockWebServer().use { server ->
            val compressed = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(compressed).use { it.write("x".repeat(4096).toByteArray()) }
            server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(okio.Buffer().write(compressed.toByteArray())))
            expectFailure(AddonFailure.RESPONSE_TOO_LARGE) { client(server, maxBytes = 1024).createLink() }
            server.enqueue(reply("[".repeat(200) + "]".repeat(200)))
            expectFailure(AddonFailure.INVALID_RESPONSE) { client(server).createLink() }
        }
    }

    @Test fun collectionAndLoginFailuresNeverExposeCredentialsOrRetry(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(reply("""{"result":{"authKey":"DeviceSecret"}}"""))
            server.enqueue(reply("""{"error":{"message":"DeviceSecret","code":401}}"""))
            val failure = expectFailure(AddonFailure.INVALID_RESPONSE) { client(server).readAddons(link) }
            assertFalse(failure.stackTraceToString().contains("DeviceSecret"))
            assertEquals(2, server.requestCount)
            server.enqueue(reply("""{"result":{"authKey":"DeviceSecret"}}"""))
            server.enqueue(reply("""{"result":{"authKey":"AccountSecret"}}"""))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.invalid/leak"))
            expectFailure(AddonFailure.REDIRECT) { client(server).readAddons(link) }
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun sessionExpiresAndDoesNotRestartItself() = runTest {
        var creates = 0
        var reads = 0
        val api = object : StremioCopyApi {
            override suspend fun createLink(): StremioImportLink { creates++; return link }
            override suspend fun readAddons(link: StremioImportLink, recheckAccess: suspend () -> Unit): AddonCopyList? { reads++; return null }
        }
        expectFailure(AddonFailure.TIMEOUT) {
            StremioCopyImport(AddonManagementAccess { true }, api, lifetimeMillis = 1000, pollMillis = 300).copy("adult").toList()
        }
        assertEquals(1, creates)
        assertEquals(3, reads)
    }

    @Test fun restrictedProfilesAndRevocationDoNotReadTheAccount() = runTest {
        var allowed = false
        var creates = 0
        var reads = 0
        val api = object : StremioCopyApi {
            override suspend fun createLink(): StremioImportLink { creates++; allowed = false; return link }
            override suspend fun readAddons(link: StremioImportLink, recheckAccess: suspend () -> Unit): AddonCopyList? { reads++; return null }
        }
        val importer = StremioCopyImport(AddonManagementAccess { allowed }, api, pollMillis = 1)
        expectFailure(AddonFailure.ACCESS_DENIED) { importer.copy("adult").toList() }
        assertEquals(0, creates)
        allowed = true
        expectFailure(AddonFailure.ACCESS_DENIED) { importer.copy("adult").toList() }
        assertEquals(1, creates)
        assertEquals(0, reads)
    }

    @Test fun accessIsCheckedBetweenCredentialExchangeAndCollectionRead(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(reply("""{"result":{"authKey":"DeviceSecret"}}"""))
            server.enqueue(reply("""{"result":{"authKey":"AccountSecret"}}"""))
            var checks = 0
            expectFailure(AddonFailure.ACCESS_DENIED) {
                client(server).readAddons(link) { checks++; if (checks == 3) fail(AddonFailure.ACCESS_DENIED) }
            }
            assertEquals(2, server.requestCount)
        }
    }
}
