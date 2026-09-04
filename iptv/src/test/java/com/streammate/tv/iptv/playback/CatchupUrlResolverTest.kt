package com.streammate.tv.iptv.playback

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatchupUrlResolverTest {
    private val resolver = CatchupUrlResolver()
    private val start = Instant.parse("2026-08-24T18:30:00Z").toEpochMilli()
    private val stop = Instant.parse("2026-08-24T20:00:00Z").toEpochMilli()
    private val now = Instant.parse("2026-08-24T21:00:00Z").toEpochMilli()

    @Test
    fun `append mode renders programme values`() {
        val result = resolver.resolve(
            request(
                type = "append",
                source = "?start={utc}&end=${'$'}{end}&duration={duration}",
            ),
        )

        assertEquals(
            "http://provider.test/live/one.ts?start=1787596200&end=1787601600&duration=5400",
            result,
        )
    }

    @Test
    fun `shift mode uses programme start and current time`() {
        val result = resolver.resolve(request(type = "timeshift", liveUrl = "https://provider.test/live?id=7"))

        assertEquals(
            "https://provider.test/live?id=7&utc=1787596200&lutc=1787605200",
            result,
        )
    }

    @Test
    fun `custom time duration and offset formats are supported`() {
        val result = resolver.resolve(
            request(
                type = "default",
                source = "https://provider.test/{utc:Ymd-H-M}?minutes={duration:60}&offset={offset:60}",
                zone = "Europe/Helsinki",
            ),
        )

        assertEquals("https://provider.test/20260824-21-30?minutes=90&offset=150", result)
    }

    @Test
    fun `xtream mode builds provider archive URL`() {
        val result = resolver.resolve(
            request(
                type = "xtream",
                liveUrl = "http://provider.test:8080/live/user%40mail/password/1477.m3u8",
                streamId = "1477",
                zone = "Europe/Helsinki",
            ),
        )

        assertEquals(
            "http://provider.test:8080/timeshift/user@mail/password/90/2026-08-24:21-30/1477.ts",
            result,
        )
    }

    @Test
    fun `xc mode infers credentials and stream ID`() {
        val result = resolver.resolve(
            request(
                type = "xc",
                liveUrl = "http://provider.test/account/password/42",
            ),
        )

        assertEquals(
            "http://provider.test/timeshift/account/password/90/2026-08-24:18-30/42.ts",
            result,
        )
    }

    @Test
    fun `unsafe unsupported and unresolved requests are rejected`() {
        assertNull(resolver.resolve(request(type = "unknown")))
        assertNull(resolver.resolve(request(type = "default", source = "file:///recording.ts")))
        assertNull(
            resolver.resolve(
                request(type = "default", source = "https://provider.test/{catchup-id}"),
            ),
        )
        assertNull(
            resolver.resolve(
                request(type = "append", source = "?start={utc}", startMillis = stop),
            ),
        )
    }

    private fun request(
        type: String,
        source: String? = null,
        liveUrl: String = "http://provider.test/live/one.ts",
        streamId: String? = null,
        zone: String? = "UTC",
        startMillis: Long = start,
    ) = CatchupUrlRequest(
        liveUrl = liveUrl,
        catchupType = type,
        catchupSource = source,
        xtreamStreamId = streamId,
        timeZoneId = zone,
        programmeStartEpochMillis = startMillis,
        programmeStopEpochMillis = stop,
        nowEpochMillis = now,
    )
}
