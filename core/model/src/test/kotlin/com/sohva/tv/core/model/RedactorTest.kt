package com.sohva.tv.core.model

import com.sohva.tv.core.model.diagnostics.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedactorTest {
    private fun r(text: String): String? = Redactor.redact(text)

    @Test
    fun blankIsNull() {
        assertNull(Redactor.redact(null))
        assertNull(Redactor.redact("   "))
    }

    @Test
    fun specExampleKeepsOnlySchemeHostAndPort() {
        assertEquals(
            "Failed https://provider.example:8443/<redacted>",
            r("Failed https://viewer:hunter2@provider.example:8443/live/abc?token=topsecret"),
        )
    }

    @Test
    fun trailingPunctuationStaysOutsideTheUrl() {
        assertEquals(
            "(see http://provider.example/<redacted>).",
            r("(see http://provider.example/list.m3u?user=a)."),
        )
    }

    @Test
    fun schemeIsLowerCasedAndUnparsableUrlsAreReplacedWhole() {
        assertEquals("http://provider.example/<redacted>", r("HTTP://provider.example/a"))
        assertEquals("<redacted-url>", r("http://%zz/list"))
        assertEquals("x <redacted-url>", r("x http:///no-host"))
    }

    @Test
    fun betaTwentyThreeSecretNames() {
        assertEquals(
            "api_key=<redacted> apikey=<redacted> api-key=<redacted> token=<redacted>",
            r("api_key=1 apikey=2 api-key=3 token=4"),
        )
        assertEquals(
            "username=<redacted>&password=<redacted> user=<redacted> pass=<redacted>",
            r("username=u&password=p user=u pass=p"),
        )
    }

    @Test
    fun namesBeyondBetaTwentyThree() {
        assertEquals(
            "key=<redacted> auth=<redacted> access_token=<redacted> refresh_token=<redacted> code=<redacted>",
            r("key=a auth=b access_token=c refresh_token=d code=e"),
        )
    }

    @Test
    fun authorizationHeader() {
        assertEquals("Authorization: <redacted> next", r("Authorization: Bearer abc.def next"))
        assertEquals("authorization=<redacted>", r("authorization=Basic dXNlcjpwYXNz"))
    }

    @Test
    fun xtreamPathWithoutScheme() {
        assertEquals(
            "provider.example/live/<redacted>/<redacted>/1.ts",
            r("provider.example/live/viewer/hunter2/1.ts"),
        )
        assertEquals(
            "at /timeshift/<redacted>/<redacted>/60/2026-09-24:20-00/7.ts",
            r("at /timeshift/viewer/hunter2/60/2026-09-24:20-00/7.ts"),
        )
    }

    @Test
    fun wordsThatOnlyContainANameAreKept() {
        assertEquals("monkey=1 passport=2", r("monkey=1 passport=2"))
    }
}
