package com.streammate.tv.core.security

import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceType
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class IptvSourceConfigurationCodecTest {
    @Test
    fun `round trips m3u and xtream sources without exposing credentials in toString`() {
        val sources = listOf(
            IptvSourceConfiguration(
                id = "m3u-home",
                name = "Home IPTV",
                type = IptvSourceType.M3U,
                importScope = IptvImportScope.LIVE_TV,
                connectionLimit = 2,
                epgOffsetMinutes = 90,
                m3uUrl = "http://provider.example/private/list.m3u",
                xmlTvUrl = "http://provider.example/private/guide.xml",
            ),
            IptvSourceConfiguration(
                id = "xtream-main",
                name = "Xtream",
                type = IptvSourceType.XTREAM,
                enabled = false,
                priority = 10,
                importScope = IptvImportScope.VOD,
                epgOffsetMinutes = -30,
                xtreamBaseUrl = "https://xtream.example",
                xtreamUsername = "viewer",
                xtreamPassword = "secret-password",
            ),
        )

        val decoded = IptvSourceConfigurationCodec.decode(
            IptvSourceConfigurationCodec.encode(sources),
        )

        assertEquals(sources, decoded)
        assertFalse(decoded.last().toString().contains("secret-password"))
        assertFalse(decoded.first().toString().contains("provider.example"))
    }

    @Test
    fun `rejects duplicate source IDs`() {
        val source = IptvSourceConfiguration(
            id = "duplicate",
            name = "One",
            type = IptvSourceType.M3U,
        )

        assertThrows(IllegalArgumentException::class.java) {
            IptvSourceConfigurationCodec.encode(listOf(source, source.copy(name = "Two")))
        }
    }

    @Test
    fun `rejects corrupted payload`() {
        assertThrows(IllegalArgumentException::class.java) {
            IptvSourceConfigurationCodec.decode("not-base64")
        }
    }

    @Test
    fun `version one sources migrate to both import scopes`() {
        val decoded = IptvSourceConfigurationCodec.decode(legacyVersionOnePayload())

        assertEquals(IptvImportScope.BOTH, decoded.single().importScope)
        assertEquals(0, decoded.single().epgOffsetMinutes)
        assertEquals("http://provider.example/list.m3u", decoded.single().m3uUrl)
    }

    @Test
    fun `version two sources migrate to zero epg offset`() {
        val decoded = IptvSourceConfigurationCodec.decode(versionTwoPayload())

        assertEquals(IptvImportScope.LIVE_TV, decoded.single().importScope)
        assertEquals(0, decoded.single().epgOffsetMinutes)
    }

    @Test
    fun `rejects epg offsets outside approved range or step`() {
        assertThrows(IllegalArgumentException::class.java) {
            sourceWithOffset(15)
        }
        assertThrows(IllegalArgumentException::class.java) {
            sourceWithOffset(12 * 60 + 30)
        }
    }

    private fun legacyVersionOnePayload(): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x53544D53)
            data.writeInt(1)
            data.writeInt(1)
            data.writeLegacyString("legacy")
            data.writeLegacyString("Legacy")
            data.writeLegacyString("M3U")
            data.writeBoolean(true)
            data.writeInt(1)
            data.writeInt(0)
            data.writeLegacyNullableString("http://provider.example/list.m3u")
            data.writeLegacyNullableString("http://provider.example/guide.xml")
            repeat(3) { data.writeBoolean(false) }
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private fun versionTwoPayload(): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x53544D53)
            data.writeInt(2)
            data.writeInt(1)
            data.writeLegacyString("version-two")
            data.writeLegacyString("Version two")
            data.writeLegacyString("M3U")
            data.writeBoolean(true)
            data.writeInt(1)
            data.writeInt(0)
            data.writeLegacyNullableString("http://provider.example/list.m3u")
            data.writeLegacyNullableString("http://provider.example/guide.xml")
            repeat(3) { data.writeBoolean(false) }
            data.writeLegacyString("LIVE_TV")
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private fun sourceWithOffset(epgOffsetMinutes: Int) = IptvSourceConfiguration(
        id = "offset",
        name = "Offset",
        type = IptvSourceType.M3U,
        epgOffsetMinutes = epgOffsetMinutes,
    )

    private fun DataOutputStream.writeLegacyString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeLegacyNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeLegacyString(value)
    }
}
