package com.streammate.tv.iptv.xtream

import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamM3uCompatibilityTest {
    @Test
    fun `derives exact Xtream access from a compatible get php playlist URL`() {
        val source = IptvSourceConfiguration(
            id = "provider",
            name = "Provider",
            type = IptvSourceType.M3U,
            importScope = IptvImportScope.VOD,
            m3uUrl = "http://provider.example:2095/get.php?username=user%2Btv&password=secret%2Fvalue&type=m3u_plus&output=ts",
        )

        val derived = requireNotNull(source.derivedXtreamSourceOrNull())

        assertEquals(IptvSourceType.XTREAM, derived.type)
        assertEquals(IptvImportScope.VOD, derived.importScope)
        assertEquals("http://provider.example:2095", derived.xtreamBaseUrl)
        assertEquals("user+tv", derived.xtreamUsername)
        assertEquals("secret/value", derived.xtreamPassword)
        assertNull(derived.m3uUrl)
    }

    @Test
    fun `keeps ordinary M3U sources on the generic parser path`() {
        val source = IptvSourceConfiguration(
            id = "generic",
            name = "Generic",
            type = IptvSourceType.M3U,
            m3uUrl = "https://provider.example/channels.m3u",
        )

        assertNull(source.derivedXtreamSourceOrNull())
    }
}
