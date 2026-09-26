package com.sohva.tv.feature.discover

import com.sohva.tv.feature.discover.net.StremioException
import com.sohva.tv.feature.discover.net.StremioLink
import com.sohva.tv.feature.discover.net.StremioProblem
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Spec 50 §11 "Unit": the Stremio link flow's answers (FR-47, -49). */
class StremioLinkTest {
    private val server = MockWebServer()
    private lateinit var link: StremioLink

    @Before
    fun start() {
        server.start()
        link = StremioLink(OkHttpClient(), server.url("/"), server.url("/"))
    }

    @After
    fun stop() = server.close()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun problem(block: suspend () -> Unit): StremioProblem {
        try {
            runBlocking { block() }
        } catch (e: StremioException) {
            return e.problem
        }
        fail("no failure")
        error("unreachable")
    }

    @Test
    fun theLinkMustBeStremiosOwnForItsCode() = runBlocking {
        answer("""{"result":{"code":"ABCD12","link":"https://link.stremio.com/ABCD12","qrcode":"https://elsewhere.example/q.png"}}""")
        assertEquals("https://link.stremio.com/ABCD12", link.create().link)
        assertTrue(server.takeRequest().url.encodedPath.endsWith("/api/create"))
        answer("""{"code":"EFGH34","link":"https://link.stremio.com/EFGH34"}""")
        assertEquals("EFGH34", link.create().code)
        answer("""{"result":{"code":"ABCD12","link":"https://phish.example/ABCD12"}}""")
        assertEquals(StremioProblem.OTHER, problem { link.create() })
        answer("""{"result":{"code":"ab","link":"https://link.stremio.com/ab"}}""")
        assertEquals(StremioProblem.OTHER, problem { link.create() })
    }

    @Test
    fun readingWaitsUntilAuthorised() = runBlocking {
        answer("""{"error":{"code":101,"message":"not yet"}}""")
        assertNull(link.read("ABCD12"))
        answer("""{"result":null}""")
        assertNull(link.read("ABCD12"))
        answer("""{"result":{"success":false}}""")
        assertNull(link.read("ABCD12"))
        answer("""{"result":{"success":true,"authKey":"not-a-real-key"}}""")
        assertEquals("not-a-real-key", link.read("ABCD12"))
        answer("""{"error":{"code":2,"message":"gone"}}""")
        assertEquals(StremioProblem.OTHER, problem { link.read("ABCD12") })
    }

    @Test
    fun theAccountGivesItsAddonUrlsUpToTheLimit() = runBlocking {
        answer("""{"result":{"authKey":"not-a-real-session"}}""")
        answer("""{"result":{"addons":[{"transportUrl":"https://provider.example/a/manifest.json","manifest":{}},{"transportUrl":"https://provider.example/b/manifest.json"}]}}""")
        assertEquals(listOf("https://provider.example/a/manifest.json", "https://provider.example/b/manifest.json"), link.addons("not-a-real-token"))
        val login = server.takeRequest()
        assertTrue(login.body?.utf8().orEmpty().contains("\"type\":\"LoginWithToken\""))
        val collection = server.takeRequest()
        assertTrue(collection.body?.utf8().orEmpty().contains("\"update\":false"))
        answer("""{"result":{"authKey":"not-a-real-session"}}""")
        answer("""{"result":{"addons":[${(1..33).joinToString(",") { """{"transportUrl":"https://provider.example/$it"}""" }}]}}""")
        assertEquals(StremioProblem.TOO_MANY, problem { link.addons("not-a-real-token") })
    }
}
