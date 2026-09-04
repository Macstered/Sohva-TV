package com.streammate.tv.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvConfigurationValidatorTest {
    @Test
    fun `accepts and trims HTTP and HTTPS source URLs`() {
        val result = IptvConfigurationValidator.validate(
            "  http://192.0.2.10:8081/list.m3u  ",
            "https://provider.example/guide.xml.gz",
        ).getOrThrow()

        assertEquals("http://192.0.2.10:8081/list.m3u", result.m3uUrl)
        assertEquals("https://provider.example/guide.xml.gz", result.xmlTvUrl)
    }

    @Test
    fun `rejects unsupported schemes and malformed URLs`() {
        assertTrue(
            IptvConfigurationValidator.validate(
                "ftp://provider.example/list.m3u",
                "https://provider.example/guide.xml",
            ).isFailure,
        )
        assertTrue(
            IptvConfigurationValidator.validate(
                "not a url",
                "https://provider.example/guide.xml",
            ).isFailure,
        )
    }
}
