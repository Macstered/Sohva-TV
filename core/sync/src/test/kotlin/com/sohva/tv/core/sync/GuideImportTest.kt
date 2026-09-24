package com.sohva.tv.core.sync

import com.sohva.tv.core.model.source.RefreshKind
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuideImportTest {
    private val h = SyncHarness()
    private val hour = 3_600_000L
    private val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }

    @After
    fun close() = h.close()

    /** A programme [fromHours]..[toHours] relative to the harness clock. */
    private fun programme(channel: String, fromHours: Double, toHours: Double, title: String = "P $channel $fromHours") =
        "<programme channel=\"$channel\" start=\"${format.format(h.clock.now + (fromHours * hour).toLong())} +0000\" " +
            "stop=\"${format.format(h.clock.now + (toHours * hour).toLong())} +0000\"><title>$title</title></programme>"

    private fun guide(vararg programmes: String) = "<tv><channel id=\"a.fi\"><display-name>A</display-name></channel>${programmes.joinToString("")}</tv>"

    private fun setUp(playlist: String) {
        h.addM3u(guide = "/guide.xml")
        h.serve("/list.m3u", playlist)
        runBlocking { h.runner.sync("m3u-1", setOf(RefreshKind.PLAYLIST)).join() }
    }

    private fun importGuide() = runBlocking { h.runner.sync("m3u-1", setOf(RefreshKind.EPG)).join() }

    private fun titles() = h.query(
        "SELECT p.title FROM programme p JOIN source_status s ON s.source_id = p.source_id AND s.kind = 'epg' " +
            "AND p.snapshot = s.epg_snapshot ORDER BY p.epg_id, p.start_at",
    )

    private fun status() = h.query("SELECT status, error_code, item_count FROM source_status WHERE kind = 'epg'").single()

    @Test
    fun keepsOwnedChannelsInsideTheWindow() {
        setUp(
            "#EXTM3U\n#EXTINF:-1 tvg-id=\"a.fi\",A\nhttp://s.example/a\n" +
                "#EXTINF:-1 tvg-id=\"c.fi\" catchup=\"default\" catchup-days=\"7\",C\nhttp://s.example/c\n" +
                "#EXTINF:-1 tvg-id=\"d.fi\" catchup=\"default\" catchup-days=\"1\",D\nhttp://s.example/d\n",
        )
        h.serve(
            "/guide.xml",
            guide(
                programme("a.fi", -5.0, -4.0, "A too old"), programme("a.fi", -2.5, -2.0, "A recent"),
                programme("a.fi", 1.0, 2.0, "A next"), programme("a.fi", 24.0 * 8, 24.0 * 8 + 1, "A beyond 8 days"),
                programme("c.fi", -24.4, -24.3, "C in reach"), programme("c.fi", -26.0, -25.0, "C past reach"),
                programme("d.fi", -23.9, -23.8, "D in archive"), programme("d.fi", -24.2, -24.1, "D past archive"),
                programme("x.fi", 1.0, 2.0, "Unknown channel"),
                programme("a.fi", 1.0, 2.0, "A next"),
            ),
        )
        importGuide()
        assertEquals(listOf("A recent", "A next", "C in reach", "D in archive"), titles())
        assertEquals("success|null|4", status())
        assertEquals(listOf("A"), h.query("SELECT display_name FROM epg_channel"))
    }

    @Test
    fun aNewGuideReplacesTheOldOneAndBadGuidesKeepIt() {
        setUp("#EXTM3U\n#EXTINF:-1 tvg-id=\"a.fi\",A\nhttp://s.example/a\n")
        h.serve("/guide.xml", guide(programme("a.fi", 0.0, 1.0, "First")))
        importGuide()
        h.serve("/guide.xml", guide(programme("a.fi", 0.0, 1.0, "Second")))
        importGuide()
        assertEquals(listOf("Second"), titles())
        assertEquals("One snapshot remains", listOf("1"), h.query("SELECT COUNT(DISTINCT snapshot) FROM programme"))

        h.serve("/guide.xml", "<tv></tv>")
        importGuide()
        assertEquals("failed|epg_empty|1", status())
        h.serve("/guide.xml", guide(programme("x.fi", 0.0, 1.0)))
        importGuide()
        assertEquals("failed|epg_unmatched|1", status())
        h.serve("/guide.xml", guide(programme("a.fi", 0.0, 1.0)).dropLast(20))
        importGuide()
        assertEquals("failed|transport_failed|1", status())
        assertEquals(listOf("Second"), titles())
        assertEquals("Staged snapshots were removed", 1, h.query("SELECT COUNT(*) FROM programme").single().toInt())
    }

    @Test
    fun channelsWithoutGuideIdsAcceptAnyGuide() {
        setUp("#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n")
        h.serve("/guide.xml", guide(programme("anything.fi", 0.0, 1.0, "Kept"), programme("anything.fi", -4.0, -3.5, "Too old")))
        importGuide()
        assertEquals(listOf("Kept"), titles())
    }

    @Test
    fun aSourceWithoutAGuideAddressSkipsTheGuide() {
        h.addM3u()
        h.serve("/list.m3u", "#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n")
        runBlocking { h.runner.sync("m3u-1").join() }
        assertEquals(emptyList<String>(), h.query("SELECT status FROM source_status WHERE kind = 'epg'"))
    }
}
