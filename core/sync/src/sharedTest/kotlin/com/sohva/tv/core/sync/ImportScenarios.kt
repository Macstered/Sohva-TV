package com.sohva.tv.core.sync

import com.sohva.tv.core.model.source.RefreshKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.Assert.assertEquals

/** Import scenarios run on the JVM and on the emulator alike. */
object ImportScenarios {
    /**
     * Two imports of one source at once (spec 10 SRC-FR-75, beta 23's ConcurrentPlaylistImportTest):
     * both succeed and the source keeps every channel and its whole guide. The dangerous overlap is
     * the guide's: the first activation's sweep deletes the second import's half-written snapshot.
     * Checked to fail with the per-kind lock removed (the active guide then lacks programmes).
     */
    fun twoGuideImportsOfOneSourceOverlap(): Unit = runBlocking {
        SyncHarness(parseThreads = 2).use { h2 ->
            h2.addM3u(guide = "/guide.xml")
            val playlist = "#EXTM3U\n" + (0 until 300).joinToString("\n") { "#EXTINF:-1 tvg-id=\"c$it\",Channel $it\nhttp://s.example/$it.ts" }
            h2.serve("/list.m3u", playlist)
            val start = h2.clock.now / 3_600_000 * 3_600_000
            val format = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val guide = StringBuilder("<tv>")
            for (c in 0 until 300) for (p in 0 until 40) {
                guide.append("<programme channel=\"c$c\" start=\"${format.format(start + p * 1_800_000L)} +0000\" ")
                    .append("stop=\"${format.format(start + (p + 1) * 1_800_000L)} +0000\"><title>P$p</title></programme>")
            }
            guide.append("</tv>")
            val body = guide.toString()
            h2.runner.sync("m3u-1", setOf(RefreshKind.PLAYLIST)).join()
            // Slow data, so the two guide imports really overlap.
            h2.serve("/guide.xml") { MockResponse.Builder().body(body).throttleBody(64 * 1024, 30, TimeUnit.MILLISECONDS).build() }
            val first = h2.runner.sync("m3u-1", setOf(RefreshKind.EPG, RefreshKind.PLAYLIST))
            delay(150)
            val second = h2.runner.sync("m3u-1", setOf(RefreshKind.EPG, RefreshKind.CATALOGUE))
            listOf(first, second).joinAll()
            assertEquals(listOf("epg|success|12000", "playlist|success|300"), h2.query("SELECT kind, status, item_count FROM source_status WHERE kind <> 'catalogue' ORDER BY kind"))
            val active = h2.query(
                "SELECT COUNT(*) FROM programme p JOIN source_status s ON s.source_id = p.source_id AND s.kind = 'epg' AND p.snapshot = s.epg_snapshot",
            ).single().toInt()
            assertEquals(12_000, active)
            assertEquals(300, h2.query("SELECT COUNT(*) FROM channel").single().toInt())
        }
    }
}
