package com.sohva.tv.core.sync

import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.RefreshKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveImportTest {
    private val h = SyncHarness()

    @After
    fun close() = h.close()

    private fun playlist(vararg entries: String): String = "#EXTM3U\n" + entries.joinToString("\n")

    private fun entry(name: String, group: String = "News", id: String? = null, extra: String = "", url: String = "http://stream.example/$name.ts") =
        "#EXTINF:-1${id?.let { " tvg-id=\"$it\"" } ?: ""} group-title=\"$group\"$extra,$name\n$url"

    private fun playlistOnly(id: String = "m3u-1") = runBlocking { h.runner.sync(id, setOf(RefreshKind.PLAYLIST)).join() }

    private fun status(kind: String = "playlist") = h.query("SELECT status, error_code, error_args, item_count FROM source_status WHERE kind = '$kind'").single()

    @Test
    fun importsChannelsGroupsAndSealedAddresses() {
        h.addM3u()
        h.serve("/list.m3u", playlist(entry("One", id = "one.fi"), entry("Two"), entry("Three", group = "Sport", extra = " tvg-chno=\"7\"")))
        playlistOnly()
        assertEquals("success|null|null|3", status())
        assertEquals(
            listOf("One|one.fi|News|0|null", "Two|null|News|1024|null", "Three|null|Sport|2048|7"),
            h.query("SELECT c.name, c.epg_id, g.name, c.display_rank, c.number FROM channel c JOIN content_group g ON g.id = c.group_id ORDER BY c.display_rank"),
        )
        assertEquals(listOf("sealed:http://stream.example/One.ts"), h.query("SELECT stream_url_enc FROM channel WHERE name = 'One'"))
        assertEquals(listOf("News|2|0", "Sport|1|1"), h.query("SELECT name, item_count, provider_order FROM content_group WHERE room = 'LIVE' ORDER BY provider_order"))
        assertTrue(h.requests.all { it == "/list.m3u" })
    }

    @Test
    fun anUnchangedPlaylistWritesNothingAndAChangedOneOnlyWhatChanged() {
        h.addM3u()
        h.serve("/list.m3u", playlist(entry("One"), entry("Two"), entry("Three")))
        playlistOnly()
        playlistOnly()
        // Generation 1 on every row: the second import rewrote none of them.
        assertEquals(listOf("1", "1", "1"), h.query("SELECT generation FROM channel"))
        h.serve("/list.m3u", playlist(entry("One"), entry("Two", group = "Moved"), entry("Four")))
        playlistOnly()
        assertEquals(listOf("One|1", "Two|3", "Four|3"), h.query("SELECT name, generation FROM channel ORDER BY display_rank"))
        assertEquals(listOf("Moved", "News"), h.query("SELECT name FROM content_group ORDER BY name"))
    }

    @Test
    fun emptyAndForeignDocumentsKeepThePreviousChannels() {
        h.addM3u()
        h.serve("/list.m3u", playlist(entry("One"), entry("Two")))
        playlistOnly()
        h.serve("/list.m3u", "#EXTM3U\n")
        playlistOnly()
        assertEquals("failed|playlist_empty|null|2", status())
        h.serve("/list.m3u", "<html><body>Please sign in</body></html>")
        playlistOnly()
        assertEquals("failed|playlist_not_m3u|null|2", status())
        h.serve("/list.m3u", "", code = 503)
        playlistOnly()
        assertEquals("failed|http_status|503|2", status())
        assertEquals(2, h.query("SELECT COUNT(*) FROM channel").single().toInt())
        assertEquals(listOf("3"), h.query("SELECT consecutive_failures FROM source_status"))
    }

    @Test
    fun scopeBothKeepsFilmsOutOfTheGuideAndLiveTvKeepsEverything() {
        val text = playlist(entry("Live"), entry("Film", group = "Movies"), entry("Show S01E02", group = "Series"))
        h.serve("/list.m3u", text)
        h.addM3u(scope = ImportScope.BOTH)
        playlistOnly()
        assertEquals(listOf("Live"), h.query("SELECT name FROM channel"))
        h.addM3u(id = "m3u-2", scope = ImportScope.LIVE_TV)
        playlistOnly("m3u-2")
        assertEquals(3, h.query("SELECT COUNT(*) FROM channel WHERE source_id = 'm3u-2'").single().toInt())
    }

    @Test
    fun namelessEntriesGetTheTranslatedFallbackAndTheHeaderGuideFillsAnEmptyField() {
        h.addM3u()
        h.serve("/list.m3u", "#EXTM3U url-tvg=\"${h.url("/header-guide.xml")}\"\nhttp://stream.example/a.ts\n")
        h.serve("/header-guide.xml", "<tv><channel id=\"a\"/></tv>")
        runBlocking { h.runner.sync("m3u-1", setOf(RefreshKind.PLAYLIST, RefreshKind.EPG)).join() }
        assertEquals(listOf("Channel 1"), h.query("SELECT name FROM channel"))
        val saved = runBlocking { h.sources.load("m3u-1") } as com.sohva.tv.core.model.error.Outcome.Ok
        assertEquals(h.url("/header-guide.xml"), saved.value!!.secrets.xmlTvUrl)
        assertTrue("the guide was read in the same sync", "/header-guide.xml" in h.requests)
    }

    @Test
    fun twentyThousandChannels() {
        h.addM3u()
        h.serve("/list.m3u", playlist(*Array(20_000) { entry("Channel$it", group = "G${it % 80}") }))
        playlistOnly()
        assertEquals("success|null|null|20000", status())
        assertEquals(80, h.query("SELECT COUNT(*) FROM content_group").single().toInt())
    }

    @Test
    fun cancellingIsNotAFailure() {
        h.addM3u()
        h.serve("/list.m3u", playlist(entry("One")))
        playlistOnly()
        val big = playlist(*Array(5_000) { entry("C$it") })
        h.serve("/list.m3u") { MockResponse.Builder().body(big).throttleBody(4_096, 50, TimeUnit.MILLISECONDS).build() }
        runBlocking {
            val job = h.runner.sync("m3u-1", setOf(RefreshKind.PLAYLIST))
            kotlinx.coroutines.delay(300)
            job.cancel()
            job.join()
        }
        assertEquals("success|null|null|1", status())
    }
}
