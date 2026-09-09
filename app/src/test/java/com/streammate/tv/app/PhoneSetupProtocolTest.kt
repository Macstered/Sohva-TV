package com.streammate.tv.app

import com.streammate.tv.core.model.IptvSourceType
import java.io.ByteArrayInputStream
import kotlin.io.encoding.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSetupProtocolTest {
    @Test
    fun `a browser's form post parses to method, path, query, headers and body`() {
        val body = "t=abc&type=m3u&name=Home&m3u_url=http%3A%2F%2Fprovider.example%2Flist.m3u"
        val raw = "POST /submit?t=abc HTTP/1.1\r\nHost: 192.0.2.5:4321\r\nContent-Type: application/x-www-form-urlencoded\r\n" +
            "Content-Length: ${body.length}\r\n\r\n$body"

        val request = PhoneSetupProtocol.parseRequest(ByteArrayInputStream(raw.toByteArray(Charsets.ISO_8859_1)))!!

        assertEquals("POST", request.method)
        assertEquals("/submit", request.path)
        assertEquals("abc", request.query["t"])
        assertEquals("192.0.2.5:4321", request.headers["host"])
        assertEquals(body, request.body)
        assertEquals("http://provider.example/list.m3u", PhoneSetupProtocol.parseForm(request.body)["m3u_url"])
    }

    @Test
    fun `an oversized body and a nonsense request line are rejected`() {
        val huge = "POST / HTTP/1.1\r\nContent-Length: ${PhoneSetupProtocol.MAX_BODY_BYTES + 1}\r\n\r\n"
        assertNull(PhoneSetupProtocol.parseRequest(ByteArrayInputStream(huge.toByteArray())))
        assertNull(PhoneSetupProtocol.parseRequest(ByteArrayInputStream("garbage\r\n\r\n".toByteArray())))
    }

    @Test
    fun `an xtream form becomes a validated xtream source`() {
        val submission = PhoneSetupProtocol.submissionFrom(
            mapOf(
                "type" to "xtream",
                "name" to " Living room ",
                "xtream_url" to "http://provider.example:8080",
                "xtream_username" to "viewer",
                "xtream_password" to "secret",
            ),
        ).getOrThrow()

        val source = submission.source!!
        assertEquals(IptvSourceType.XTREAM, source.type)
        assertEquals("Living room", source.name)
        assertTrue(source.id.startsWith("xtream-"))
        assertEquals("viewer", source.xtreamUsername)
        assertEquals("secret", source.xtreamPassword)
        assertNull(submission.tmdbToken)
    }

    @Test
    fun `an m3u form keeps its optional guide address and a keys form carries no source`() {
        val m3u = PhoneSetupProtocol.submissionFrom(
            mapOf("type" to "m3u", "name" to "List", "m3u_url" to "http://provider.example/list.m3u", "xmltv_url" to ""),
        ).getOrThrow()
        assertEquals(IptvSourceType.M3U, m3u.source!!.type)
        assertEquals("http://provider.example/list.m3u", m3u.source!!.m3uUrl)
        assertNull(m3u.source!!.xmlTvUrl)

        val keys = PhoneSetupProtocol.submissionFrom(
            mapOf("type" to "keys", "tmdb_token" to " token ", "api_sports_key" to ""),
        ).getOrThrow()
        assertNull(keys.source)
        assertEquals("token", keys.tmdbToken)
        assertNull(keys.apiSportsKey)
    }

    @Test
    fun `missing or bad fields fail rather than save`() {
        assertTrue(PhoneSetupProtocol.submissionFrom(mapOf("type" to "m3u", "name" to "", "m3u_url" to "http://x")).isFailure)
        assertTrue(PhoneSetupProtocol.submissionFrom(mapOf("type" to "m3u", "name" to "List", "m3u_url" to "not a url")).isFailure)
        assertTrue(PhoneSetupProtocol.submissionFrom(mapOf("type" to "keys")).isFailure)
        assertTrue(PhoneSetupProtocol.submissionFrom(mapOf("type" to "telnet", "name" to "x")).isFailure)
    }

    @Test
    fun `tokens are eight characters from an unambiguous alphabet`() {
        repeat(20) {
            val token = PhoneSetupProtocol.newToken()
            assertEquals(8, token.length)
            assertTrue(token.all { it in "abcdefghjkmnpqrstuvwxyz23456789" })
        }
    }

    @Test
    fun `a logo posted for the channel the page was opened for becomes its bytes`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val mode = PhoneSetupMode.Logo("source:one", "One")
        val form = mapOf("type" to "logo", "channel" to "source:one", "image" to "data:image/png;base64," + Base64.encode(png))

        val submission = PhoneSetupProtocol.submissionFrom(form, mode).getOrThrow()

        assertArrayEquals(png, submission.logo!!.image)
        assertEquals("source:one", submission.logo!!.channelId)
        assertNull(submission.source)
        // Another channel, the sources page, a text file and a body that is not an image are all refused.
        assertTrue(PhoneSetupProtocol.submissionFrom(form + ("channel" to "source:two"), mode).isFailure)
        assertTrue(PhoneSetupProtocol.submissionFrom(form, PhoneSetupMode.Sources).isFailure)
        assertTrue(PhoneSetupProtocol.submissionFrom(form + ("image" to "data:text/plain;base64,aGVsbG8="), mode).isFailure)
        assertTrue(PhoneSetupProtocol.submissionFrom(form + ("image" to Base64.encode(byteArrayOf(1, 2, 3, 4, 5))), mode).isFailure)
    }

    @Test
    fun `the logo page accepts a body the sources page would refuse`() {
        val body = "x".repeat(PhoneSetupProtocol.MAX_BODY_BYTES + 1)
        val raw = "POST /submit HTTP/1.1\r\nContent-Length: ${body.length}\r\n\r\n$body"

        assertNull(PhoneSetupProtocol.parseRequest(ByteArrayInputStream(raw.toByteArray(Charsets.ISO_8859_1))))
        val request = PhoneSetupProtocol.parseRequest(
            ByteArrayInputStream(raw.toByteArray(Charsets.ISO_8859_1)),
            PhoneSetupProtocol.MAX_LOGO_BODY_BYTES,
        )
        assertEquals(body, request!!.body)
    }
}
