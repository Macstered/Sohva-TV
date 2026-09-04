package com.streammate.tv.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamConfigurationValidatorTest {
    @Test
    fun `accepts trusted-lan http and trims credentials`() {
        val result = XtreamConfigurationValidator.validate(
            "http://192.0.2.10:8080/",
            " viewer ",
            "password",
        ).getOrThrow()

        assertEquals("http://192.0.2.10:8080", result.baseUrl)
        assertEquals("viewer", result.username)
    }

    @Test
    fun `rejects missing credentials`() {
        assertTrue(
            XtreamConfigurationValidator.validate("https://provider.example", "", "password").isFailure,
        )
        assertTrue(
            XtreamConfigurationValidator.validate("https://provider.example", "viewer", "").isFailure,
        )
    }
}
