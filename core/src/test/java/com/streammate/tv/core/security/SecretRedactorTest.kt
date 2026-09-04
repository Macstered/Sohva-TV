package com.streammate.tv.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {
    @Test
    fun `removes credentials paths and query strings from URLs`() {
        val result = SecretRedactor.redact(
            "Failed https://viewer:hunter2@provider.example:8443/live/abc?token=topsecret",
        )!!

        assertEquals("Failed https://provider.example:8443/<redacted>", result)
        assertFalse(result.contains("hunter2"))
        assertFalse(result.contains("topsecret"))
    }

    @Test
    fun `redacts named secrets outside URLs`() {
        val result = SecretRedactor.redact("token=abc username=sami password=secret")!!

        assertTrue(result.contains("token=<redacted>"))
        assertTrue(result.contains("username=<redacted>"))
        assertTrue(result.contains("password=<redacted>"))
    }

    @Test
    fun `reports nothing to show rather than inventing a message`() {
        // The caller has a Context and can pick a localised fallback; this
        // cannot, so it must not bake one language into the result.
        assertNull(SecretRedactor.redact(null))
        assertNull(SecretRedactor.redact("   "))
    }
}
