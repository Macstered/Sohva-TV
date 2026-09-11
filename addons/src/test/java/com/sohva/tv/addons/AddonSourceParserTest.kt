package com.sohva.tv.addons

import org.junit.Assert.*
import org.junit.Test

class AddonSourceParserTest {
    @Test fun directSourcesKeepHeadersInlineSubtitlesAndOnlyAllowedSubtitleExtras() {
        val stream = AddonSourceParser.streams("""{"streams":[{"name":"1080p","description":"Fixture","url":"https://media.example.invalid/video?token=secret","subtitles":[{"id":"1","lang":"eng","url":"https://sub.example.invalid/1.srt"}],"behaviorHints":{"proxyHeaders":{"request":{"Authorization":"Bearer secret","User-Agent":"Fixture"}},"videoHash":"0123456789abcdef","videoSize":1234,"filename":"fixture.mp4"}}]}""").single()
        assertEquals(AddonStreamKind.HTTP, stream.kind)
        assertEquals("Bearer secret", stream.requestHeaders["Authorization"])
        assertEquals(setOf("videoHash", "videoSize", "filename"), stream.subtitleExtras().keys)
        assertEquals("eng", stream.subtitles.single().language)
        assertFalse(stream.toString().contains("secret"))
        assertFalse(stream.subtitles.single().toString().contains("example"))
    }
    @Test fun unsupportedSourcesAreClassifiedWithoutLaunchingAnything() {
        val streams = AddonSourceParser.streams("""{"streams":[{"infoHash":"abc","fileIdx":0},{"externalUrl":"https://example.invalid"},{"ytId":"x"},{"url":"file:///private"},{"url":"http://127.0.0.1:11470/video"},null,{}]}""")
        assertEquals(listOf(AddonStreamKind.TORRENT, AddonStreamKind.EXTERNAL, AddonStreamKind.UNSUPPORTED, AddonStreamKind.UNSUPPORTED, AddonStreamKind.UNSUPPORTED), streams.map { it.kind })
        assertTrue(streams.all { it.url == null })
    }
    @Test fun unsafeHeadersRejectOnlyThatStream() {
        for (header in listOf("\"Host\":\"other\"", "\"X-Test\":\"injected\\r\\nheader\"", "\"Authorization\":\"one\",\"authorization\":\"two\"", "\"Sec-Test\":\"value\"")) {
            assertTrue(AddonSourceParser.streams("""{"streams":[{"url":"https://example.invalid/a","behaviorHints":{"proxyHeaders":{"request":{$header}}}}]}""").isEmpty())
        }
    }
    @Test fun malformedSubtitlesAreSkippedAndDuplicatesRemoved() {
        val items = AddonSourceParser.subtitles("""{"subtitles":[{"id":"1","lang":"fin","url":"https://example.invalid/a.srt"},{"id":"1","lang":"fin","url":"https://example.invalid/a.srt"},{"id":"2","lang":"eng","url":"http://localhost:11470/a"},{"id":"3","lang":"eng","url":"https://user:pass@example.invalid/a"},{"url":"https://example.invalid/a"},null]}""")
        assertEquals(1, items.size)
    }
    @Test fun malformedHeaderContainersAreNotSilentlyDropped() {
        for (hints in listOf("[]", "{\"proxyHeaders\":[]}", "{\"proxyHeaders\":{\"request\":[]}}")) {
            assertTrue(AddonSourceParser.streams("""{"streams":[{"url":"https://example.invalid/video","behaviorHints":$hints}]}""").isEmpty())
        }
    }
    @Test fun envelopeAndItemCountsAreBounded() {
        for (input in listOf("{broken", "{\"streams\":null}", "[]")) {
            try { AddonSourceParser.streams(input); fail("Expected rejection") }
            catch (error: AddonException) { assertEquals(AddonFailure.INVALID_RESPONSE, error.failure) }
        }
        try { AddonSourceParser.streams("{\"streams\":[" + List(501) { "{}" }.joinToString(",") + "]}"); fail("Expected bound") }
        catch (error: AddonException) { assertEquals(AddonFailure.RESPONSE_TOO_LARGE, error.failure) }
    }
}
