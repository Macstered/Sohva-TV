package com.sohva.tv.addons

import java.net.InetAddress
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test

class AddonPhoneSessionTest {
    private val client = OkHttpClient()
    @Test fun requiresTokenAndSameOriginThenDeliversOnlyTransientInput() {
        AddonPhoneSession(InetAddress.getLoopbackAddress()).use { session ->
            val body = "https://example.invalid/private/manifest.json".toRequestBody("text/plain".toMediaType())
            fun send(origin: String, token: String): Int = client.newCall(Request.Builder().url("${session.origin}/submit")
                .header("Origin", origin).header("Authorization", "Bearer $token").post(body).build()).execute().use { it.code }
            assertEquals(403, send(session.origin, "wrong"))
            assertEquals(403, send("https://example.invalid", session.pairingUrl.substringAfter('#')))
            assertNull(session.submission.value)
            assertEquals(200, send(session.origin, session.pairingUrl.substringAfter('#')))
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(1000) { session.submission.first { it != null } } }
            assertEquals("https://example.invalid/private/manifest.json", session.takeSubmission())
            assertNull(session.submission.value)
            assertFalse(session.toString().contains(session.pairingUrl.substringAfter('#')))
        }
    }
    @Test fun pageDoesNotExposeTokenAndUsesNoExternalResources() {
        AddonPhoneSession(InetAddress.getLoopbackAddress()).use { session ->
            client.newCall(Request.Builder().url(session.origin).build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("no-store", it.header("Cache-Control"))
                val page = it.body.string()
                assertFalse(page.contains(session.pairingUrl.substringAfter('#')))
                assertTrue(page.contains("not encrypted"))
                assertTrue(page.contains("type=\"file\" accept=\".txt,text/plain\""))
                assertTrue(page.contains("Selecting a file replaces the list below; it does not send it."))
                val script = page.substringAfter("<script>").substringBefore("</script>")
                val hash = java.util.Base64.getEncoder().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256").digest(script.toByteArray()))
                val policy = it.header("Content-Security-Policy")!!
                assertTrue(policy.contains("frame-ancestors 'none'"))
                assertTrue(policy.contains("script-src 'sha256-$hash'"))
                assertTrue(script.contains("MAX_BYTES = ${AddonImportText.MAX_BYTES}, MAX_ENTRIES = ${AddonImportText.MAX_ENTRIES}"))
            }
        }
    }
    @Test fun invalidListDoesNotConsumePairingOrWriteAConfiguration() {
        AddonPhoneSession(InetAddress.getLoopbackAddress()).use { session ->
            client.newCall(Request.Builder().url("${session.origin}/submit")
                .header("Origin", session.origin).header("Authorization", "Bearer ${session.pairingUrl.substringAfter('#')}")
                .post("\u0000".toRequestBody("text/plain".toMediaType())).build()).execute().use { assertEquals(400, it.code) }
            assertNull(session.submission.value)
            assertTrue(session.running.value)
        }
    }
    @Test fun expiryClosesEvenAnIncompleteConnection() {
        AddonPhoneSession(InetAddress.getLoopbackAddress(), lifetimeMillis = 200).use { session ->
            val uri = java.net.URI(session.origin)
            java.net.Socket(uri.host, uri.port).use { socket ->
                socket.getOutputStream().write("GET / HTTP/1.1\r\n".toByteArray())
                kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(2000) { session.running.first { !it } } }
                socket.soTimeout = 1000
                assertEquals(-1, socket.getInputStream().read())
                assertNull(session.submission.value)
            }
        }
    }

    @Test fun phoneFileListAcceptsBomBlankLinesCrLfAndAll32Entries() {
        AddonPhoneSession(InetAddress.getLoopbackAddress()).use { session ->
            val urls = (1..32).joinToString("\r\n\r\n") { "https://example.invalid/file-$it/manifest.json" }
            assertEquals(200, submit(session, ("\uFEFF" + urls).toByteArray()))
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(1000) { session.submission.first { it != null } } }
            assertEquals(urls, session.takeSubmission())
            assertNull(session.takeSubmission())
        }
    }

    @Test fun invalidFilesAreRejectedWithoutConsumingSessionThenValidListCanBeSent() {
        AddonPhoneSession(InetAddress.getLoopbackAddress()).use { session ->
            val invalid = listOf(byteArrayOf(), byteArrayOf(0xC3.toByte(), 0x28), "a\u0000b".toByteArray(),
                (1..33).joinToString("\n") { "https://example.invalid/$it" }.toByteArray())
            invalid.forEach {
                assertEquals(400, submit(session, it))
                assertNull(session.submission.value)
                assertTrue(session.running.value)
            }
            // Oversize is rejected from headers, before body reads. Do not race an HTTP
            // client's large body write against the server's intentional early close.
            val uri = java.net.URI(session.origin)
            java.net.Socket(uri.host, uri.port).use { socket ->
                socket.soTimeout = 1000
                socket.getOutputStream().write(("POST /submit HTTP/1.1\r\nHost: ${uri.authority}\r\n" +
                    "Origin: ${session.origin}\r\nAuthorization: Bearer ${session.pairingUrl.substringAfter('#')}\r\n" +
                    "Content-Type: text/plain\r\nContent-Length: ${AddonImportText.MAX_BYTES + 1}\r\n\r\n").toByteArray())
                assertTrue(socket.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 400 "))
            }
            assertNull(session.submission.value)
            assertTrue(session.running.value)
            assertEquals(200, submit(session, "https://example.invalid/valid/manifest.json".toByteArray()))
        }
    }

    private fun submit(session: AddonPhoneSession, bytes: ByteArray): Int = client.newCall(Request.Builder()
        .url("${session.origin}/submit").header("Origin", session.origin)
        .header("Authorization", "Bearer ${session.pairingUrl.substringAfter('#')}")
        .post(bytes.toRequestBody("text/plain".toMediaType())).build()).execute().use { it.code }
}
