package com.sohva.tv.core.player

import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.sohva.tv.core.model.player.PlaybackCause
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 30 §11: the connection limiter, the placeholder and headers, the failure detail and causes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerEngineTest {
    private val stream = ResolvedStream(
        key = "fixture-0:c1", address = "http://provider.example/live/user/secret/1.m3u8", sourceId = "fixture-0",
        sourceName = "Fixture", connectionLimit = 1, title = "Northstar", userAgent = null, referrer = "http://provider.example/",
    )

    @Test
    fun leasesRespectTheLimitAndReleaseOnce() {
        val leases = ConnectionLeases()
        val first = leases.acquire("s", 1)
        assertNotNull(first)
        assertNull(leases.acquire("s", 1))
        assertNotNull(leases.acquire("other", 1))
        first!!.release()
        first.release()
        assertEquals(0, leases.inUse("s"))
        // Release before acquire: ten zaps in a row at limit 1 never hit the limit.
        var held = leases.acquire("s", 1)
        repeat(10) {
            held?.release()
            held = leases.acquire("s", 1)
            assertNotNull(held)
        }
    }

    @Test
    fun placeholderBecomesTheAddressAndEveryRequestCarriesTheHeaders() {
        val registry = StreamRegistry("Sohva TV/test (Android TV 11)")
        val placeholder = registry.register(stream)
        assertEquals("sohva", placeholder.scheme)
        assertTrue(!placeholder.toString().contains("secret"))
        val manifest = registry.resolver.resolveDataSpec(DataSpec(placeholder))
        assertEquals(stream.address, manifest.uri.toString())
        assertEquals("Sohva TV/test (Android TV 11)", manifest.httpRequestHeaders["User-Agent"])
        assertEquals("http://provider.example/", manifest.httpRequestHeaders["Referer"])
        // A segment named by the manifest is passed through with the same headers (L-23).
        val segment = registry.resolver.resolveDataSpec(DataSpec(Uri.parse("http://provider.example/seg/1.ts")))
        assertEquals("http://provider.example/seg/1.ts", segment.uri.toString())
        assertEquals("http://provider.example/", segment.httpRequestHeaders["Referer"])
        // The playlist's own agent wins.
        registry.register(stream.copy(userAgent = "VLC/3.0"))
        assertEquals("VLC/3.0", registry.resolver.resolveDataSpec(DataSpec(placeholder)).httpRequestHeaders["User-Agent"])
        registry.clear()
        assertTrue(runCatching { registry.resolver.resolveDataSpec(DataSpec(placeholder)) }.exceptionOrNull() is IOException)
    }

    @Test
    fun mimeTypeFromTheRealAddress() {
        assertEquals(MimeTypes.APPLICATION_M3U8, StreamRegistry.mimeType(stream.address))
        assertEquals(MimeTypes.APPLICATION_MPD, StreamRegistry.mimeType("http://provider.example/a.mpd"))
        assertNull(StreamRegistry.mimeType("http://provider.example/live/1.ts"))
    }

    @Test
    fun failuresNameTheCauseAndARedactedDetail() {
        val refused = PlaybackException(
            "Source error",
            HttpDataSource.InvalidResponseCodeException(403, "Forbidden", null, emptyMap(), DataSpec(Uri.parse(stream.address)), ByteArray(0)),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )
        val failure = PlaybackErrors.of(refused)
        assertEquals(PlaybackCause.REFUSED, failure.cause)
        assertTrue(failure.detail, failure.detail.startsWith("ERROR_CODE_IO_BAD_HTTP_STATUS · InvalidResponseCodeException: "))
        val network = PlaybackException("x", IOException("failed to connect to provider.example/192.0.2.7 (port 80)"), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertEquals(PlaybackCause.NETWORK, PlaybackErrors.cause(network))
        assertTrue(!PlaybackErrors.detail(network).contains("192.0.2.7"))
        assertTrue(PlaybackErrors.detail(network).length <= "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED".length + 3 + "IOException: ".length + 140)
        assertEquals(PlaybackCause.DECODER, PlaybackErrors.cause(PlaybackException("d", null, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)))
        assertEquals(PlaybackCause.OTHER, PlaybackErrors.cause(PlaybackException("u", null, PlaybackException.ERROR_CODE_UNSPECIFIED)))
    }
}
