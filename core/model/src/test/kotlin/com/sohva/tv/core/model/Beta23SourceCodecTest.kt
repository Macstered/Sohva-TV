package com.sohva.tv.core.model

import com.sohva.tv.core.model.source.Beta23SourceCodec
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.SourceType
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Cases carried over from beta 23's `IptvSourceConfigurationCodecTest` (AGENTS §3), plus the spec's refusals. */
class Beta23SourceCodecTest {
    /** Writes the SRC-FR-108 format the way beta 23 did, for fixtures. */
    private class Writer(version: Int, count: Int) {
        private val bytes = ByteArrayOutputStream()
        val data = DataOutputStream(bytes)

        init {
            data.writeInt(0x53544D53)
            data.writeInt(version)
            data.writeInt(count)
        }

        fun string(value: String) {
            val raw = value.toByteArray(Charsets.UTF_8)
            data.writeInt(raw.size)
            data.write(raw)
        }

        fun nullable(value: String?) {
            data.writeBoolean(value != null)
            if (value != null) string(value)
        }

        fun source(id: String, name: String, type: String, enabled: Boolean, limit: Int, priority: Int, secrets: List<String?>) {
            string(id)
            string(name)
            string(type)
            data.writeBoolean(enabled)
            data.writeInt(limit)
            data.writeInt(priority)
            secrets.forEach(::nullable)
        }

        fun base64(): String = Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun refused(text: String) {
        try {
            Beta23SourceCodec.decode(text)
            throw AssertionError("accepted")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun versionThreeCarriesEverything() {
        val w = Writer(3, 2)
        w.source("m3u-home", "Home IPTV", "M3U", true, 2, 0, listOf("http://provider.example/private/list.m3u", "http://provider.example/private/guide.xml", null, null, null))
        w.string("LIVE_TV")
        w.data.writeInt(90)
        w.source("xtream-main", "Xtream", "XTREAM", false, 1, 10, listOf(null, null, "https://xtream.example", "viewer", "secret-password"))
        w.string("VOD")
        w.data.writeInt(-30)
        val (m3u, xtream) = Beta23SourceCodec.decode(w.base64())
        assertEquals(SourceType.M3U, m3u.source.type)
        assertEquals(ImportScope.LIVE_TV, m3u.source.importScope)
        assertEquals(2, m3u.source.connectionLimit)
        assertEquals(90, m3u.source.epgOffsetMinutes)
        assertEquals("http://provider.example/private/guide.xml", m3u.secrets.xmlTvUrl)
        assertFalse(xtream.source.enabled)
        assertEquals(10, xtream.source.priority)
        assertEquals(-30, xtream.source.epgOffsetMinutes)
        assertEquals("secret-password", xtream.secrets.xtreamPassword)
        assertFalse(xtream.toString().contains("secret-password"))
    }

    @Test
    fun versionOneReadsAsBothScopesAndVersionTwoAsNoOffset() {
        val one = Writer(1, 1)
        one.source("legacy", "Legacy", "M3U", true, 1, 0, listOf("http://provider.example/list.m3u", "http://provider.example/guide.xml", null, null, null))
        val v1 = Beta23SourceCodec.decode(one.base64()).single()
        assertEquals(ImportScope.BOTH, v1.source.importScope)
        assertEquals(0, v1.source.epgOffsetMinutes)
        assertNull(v1.secrets.xtreamBaseUrl)
        val two = Writer(2, 1)
        two.source("version-two", "Version two", "M3U", true, 1, 0, listOf("http://provider.example/list.m3u", null, null, null, null))
        two.string("LIVE_TV")
        val v2 = Beta23SourceCodec.decode(two.base64()).single()
        assertEquals(ImportScope.LIVE_TV, v2.source.importScope)
        assertEquals(0, v2.source.epgOffsetMinutes)
    }

    @Test
    fun damagedListsAreRefused() {
        refused("not-base64!")
        refused(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)))
        refused(Writer(4, 0).base64())
        refused(Writer(3, 101).base64())
        val trailing = Writer(3, 0).also { it.data.writeByte(7) }
        refused(trailing.base64())
        val unknown = Writer(2, 1).also {
            it.source("a", "A", "STALKER", true, 1, 0, listOf(null, null, null, null, null))
            it.string("BOTH")
        }
        refused(unknown.base64())
        val duplicate = Writer(1, 2).also {
            repeat(2) { _ -> it.source("same", "A", "M3U", true, 1, 0, listOf(null, null, null, null, null)) }
        }
        refused(duplicate.base64())
        for (offset in listOf(15, 12 * 60 + 30)) {
            val badOffset = Writer(3, 1).also {
                it.source("a", "A", "M3U", true, 1, 0, listOf(null, null, null, null, null))
                it.string("BOTH")
                it.data.writeInt(offset)
            }
            refused(badOffset.base64())
        }
        val truncated = Writer(1, 1).also { it.string("only-an-id") }
        refused(truncated.base64())
        assertEquals(emptyList<Any>(), Beta23SourceCodec.decode(Writer(3, 0).base64()))
    }

    /** Spec 71 §6.2: the rebuild writes version 3, which reads back exactly (beta 23's reader is the same layout). */
    @Test
    fun theRebuildWritesVersionThreeThatReadsBack() {
        val sources = listOf(
            com.sohva.tv.core.model.source.SourceConfig(
                com.sohva.tv.core.model.source.Source("src-1", "Aurora", com.sohva.tv.core.model.source.SourceType.M3U, true, 2, 0, com.sohva.tv.core.model.source.ImportScope.LIVE_TV, -60),
                com.sohva.tv.core.model.source.SourceSecrets(m3uUrl = "https://provider.example/list.m3u", xmlTvUrl = null),
            ),
            com.sohva.tv.core.model.source.SourceConfig(
                com.sohva.tv.core.model.source.Source("x.2", "Päivä", com.sohva.tv.core.model.source.SourceType.XTREAM, false, 1, 1, com.sohva.tv.core.model.source.ImportScope.BOTH, 0),
                com.sohva.tv.core.model.source.SourceSecrets(xtreamBaseUrl = "http://192.0.2.1:8080", xtreamUsername = "viewer", xtreamPassword = "secret"),
            ),
        )
        val text = Beta23SourceCodec.encode(sources)
        assertEquals(sources, Beta23SourceCodec.decode(text))
        val bytes = java.util.Base64.getDecoder().decode(text)
        assertEquals(0x53544D53, java.nio.ByteBuffer.wrap(bytes).int)
        assertEquals(3, java.nio.ByteBuffer.wrap(bytes, 4, 4).int)
    }
}
