package com.sohva.tv.core.net.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Spec 72 §7.1, ABOUT-FR-09, -10: the feed parses as GitHub writes it; downloads stream and hash. */
class UpdateHttpTest {
    private val server = MockWebServer()
    private val http = UpdateHttp(OkHttpClient())

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun theReleaseListReadsWhatTheUpdaterNeeds() = runBlocking {
        val feed = """
            [
              {"url":"x","tag_name":"v0.2.0-beta.1","name":"Sohva TV 0.2.0-beta.1","draft":false,"prerelease":true,
               "body":"Android build **101**.","author":{"login":"someone"},
               "assets":[
                 {"name":"SHA256SUMS.txt","browser_download_url":"https://downloads.example/SHA256SUMS.txt","size":320,"uploader":{}},
                 {"name":"broken","size":1},
                 {"name":"sohva-tv-0.2.0-beta.1.apk","browser_download_url":"https://downloads.example/a.apk","size":7800000}
               ]},
              {"tag_name":null,"body":"no tag"},
              {"tag_name":"v0.1.0-beta.23","draft":true,"prerelease":false,"body":null,"assets":null}
            ]
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(feed).build())
        val releases = http.releases(server.url("/releases"))
        assertEquals(2, releases.size)
        val first = releases[0]
        assertEquals("v0.2.0-beta.1", first.tag)
        assertTrue(first.prerelease && !first.draft)
        assertEquals(listOf("SHA256SUMS.txt", "sohva-tv-0.2.0-beta.1.apk"), first.assets.map { it.name })
        assertEquals(7_800_000L, first.assets[1].size)
        assertTrue(releases[1].draft && releases[1].body.isEmpty() && releases[1].assets.isEmpty())
        assertEquals("application/vnd.github+json", server.takeRequest().headers["Accept"])
    }

    @Test
    fun aDownloadStreamsToTheFileWithItsDigestAndPercents() = runBlocking {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
        val file = File.createTempFile("update", ".apk")
        try {
            val percents = ArrayList<Int>()
            val digest = http.download(server.url("/a.apk"), file, expected = 0, progress = percents::add)
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, digest)
            assertEquals(100, percents.last())
            assertEquals(percents.distinct(), percents)
        } finally {
            file.delete()
        }
    }

    @Test
    fun aProfileOverItsLimitAndAFailedStatusAreRefused() = runBlocking {
        server.enqueue(MockResponse.Builder().body(Buffer().write(ByteArray(2_000))).build())
        server.enqueue(MockResponse.Builder().code(404).build())
        for (path in listOf("/big.dm", "/missing.dm")) {
            try {
                http.bytes(server.url(path), max = 1_000)
                fail("$path read")
            } catch (_: UpdateHttpException) {
            }
        }
    }
}
