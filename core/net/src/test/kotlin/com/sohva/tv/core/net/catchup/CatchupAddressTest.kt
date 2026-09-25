package com.sohva.tv.core.net.catchup

import com.sohva.tv.core.model.guide.CatchupRules
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 22 §11: the archive address for every scheme, the refusals, and CATCH-FR-10. */
class CatchupAddressTest {
    // 24 Aug 2026: start 18:30, stop 20:00, now 21:00 UTC (CATCH-FR-24).
    private val start = 1_787_596_200_000L
    private val stop = 1_787_601_600_000L
    private val now = 1_787_605_200_000L
    private val helsinki = ZoneId.of("Europe/Helsinki")

    private fun build(
        type: String?,
        live: String = "http://provider.example/live/one.ts",
        template: String? = null,
        streamId: String? = null,
        zone: String? = null,
        fallback: ZoneId = ZoneOffset.UTC,
        from: Long = start,
        to: Long = stop,
        at: Long = now,
    ): String? = CatchupAddress.build(CatchupRequest(live, type, template, streamId, zone, fallback, from, to, at))

    @Test
    fun appendAddsTheTemplateToTheLiveAddress() {
        assertEquals(
            "http://provider.example/live/one.ts?start=1787596200&end=1787601600&duration=5400",
            build("append", template = "?start={utc}&end=\${end}&duration={duration}"),
        )
    }

    @Test
    fun timeshiftAddsUtcAndLutc() {
        assertEquals(
            "https://provider.example/live?id=7&utc=1787596200&lutc=1787605200",
            build("timeshift", live = "https://provider.example/live?id=7"),
        )
        assertEquals(
            "http://provider.example/live/one.ts?utc=1787596200&lutc=1787605200",
            build(" Shift ", live = "http://provider.example/live/one.ts"),
        )
    }

    @Test
    fun defaultRendersFormatsDurationAndOffsetInTheTvZone() {
        assertEquals(
            "https://provider.example/20260824-21-30?minutes=90&offset=150",
            build(
                "default",
                template = "https://provider.example/{utc:Ymd-H-M}?minutes={duration:60}&offset={offset:60}",
                fallback = helsinki,
            ),
        )
    }

    @Test
    fun xtreamBuildsTheTimeshiftPath() {
        assertEquals(
            "http://provider.example:8080/timeshift/user@mail/password/90/2026-08-24:21-30/1477.ts",
            build("xtream", live = "http://provider.example:8080/live/user%40mail/password/1477.m3u8", streamId = "1477", zone = "Europe/Helsinki"),
        )
        // xc without a `live` segment: the credentials and the id come from the last three segments.
        assertEquals(
            "http://provider.example/timeshift/account/password/90/2026-08-24:18-30/42.ts",
            build("xc", live = "http://provider.example/account/password/42"),
        )
        // An invalid panel zone falls back to the TV's.
        assertEquals(
            "http://provider.example/timeshift/account/password/90/2026-08-24:21-30/42.ts",
            build("xc", live = "http://provider.example/account/password/42", zone = "Mars/Olympus", fallback = helsinki),
        )
    }

    @Test
    fun tokenFormsAndFieldsAreCaseAware() {
        assertEquals(
            "http://provider.example/a?s=1787596200&t=1787596200&min=30&mon=08",
            build("default", template = "http://provider.example/a?s=\${start}&t={START}&min={M}&mon={m}"),
        )
        assertEquals(
            "http://provider.example/a?d=5400",
            build("default", template = "http://provider.example/a?d={duration:x}"),
        )
    }

    @Test
    fun unsafeOrUnusableTemplatesAreRefused() {
        assertNull(build("flussonic", template = "http://provider.example/a"))
        assertNull(build("default", template = "file:///sdcard/a.ts"))
        assertNull(build("default", template = "http://provider.example/{catchup-id}"))
        assertNull(build("default", template = "http://provider.example/a", from = stop, to = stop))
        assertNull(build("default", template = "http://provider.example/a", from = now + 1, to = now + 60_000))
        assertNull(build("default", template = "http://provider.example/{utc:Y%m}"))
        assertNull(build("default", template = "http://provider.example/{utc: }"))
        assertNull(build("default", template = "http://provider.example/{duration:0}"))
        assertNull(build("default", template = "http://provider.example/{offset:x}"))
        assertNull(build("default", template = "http://provider.example/{foo}"))
        assertNull(build("default", template = null))
        assertNull(build("append", template = " "))
        assertNull(build("xtream", live = "http://provider.example/live/user/1477.ts"))
        assertNull(build("xtream", live = "http://provider.example/live/user/pass/%3Cbad%3E.ts", streamId = "no/good"))
        assertNull(build("xtream", live = "rtmp://provider.example/live/user/pass/1.ts"))
    }

    @Test
    fun theRequestNeverPrintsItsAddresses() {
        val text = CatchupRequest("http://provider.example/live/u/secret/1.ts", "xc", "?t={utc}", null, null, ZoneOffset.UTC, start, stop, now).toString()
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("provider.example"))
    }

    @Test
    fun availabilityFollowsTypeDaysAndWindow() {
        val day = 24L * 60 * 60 * 1000
        for (type in listOf("shift", "timeshift", "xtream", "xc")) assertTrue(type, CatchupRules.offers(type, 7, false, start, now))
        for (type in listOf("default", "append", "vod")) {
            assertTrue(type, CatchupRules.offers(type, 7, true, start, now))
            assertFalse("$type without a template", CatchupRules.offers(type, 7, false, start, now))
        }
        assertFalse(CatchupRules.offers("flussonic", 7, true, start, now))
        assertFalse(CatchupRules.offers(" ", 7, true, start, now))
        assertFalse(CatchupRules.offers("shift", null, false, start, now))
        assertFalse(CatchupRules.offers("shift", 0, false, start, now))
        assertTrue(CatchupRules.offers("shift", 1, false, now - day, now))
        assertFalse(CatchupRules.offers("shift", 1, false, now - day - 1, now))
        assertTrue(CatchupRules.offers("shift", 365, false, now - 365 * day, now))
        assertFalse("capped at 365 days", CatchupRules.offers("shift", 400, false, now - 366 * day, now))
        assertFalse("future programmes", CatchupRules.offers("shift", 7, false, now + 1, now))
        assertTrue("on air", CatchupRules.offers("shift", 7, false, now, now))
    }
}
