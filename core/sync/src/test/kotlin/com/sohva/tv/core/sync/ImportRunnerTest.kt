package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.RefreshKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportRunnerTest {
    private val h = SyncHarness()

    @After
    fun close() = h.close()

    private fun slowPlaylist(count: Int) {
        val body = "#EXTM3U\n" + (0 until count).joinToString("\n") { "#EXTINF:-1 group-title=\"G${it % 7}\",Channel $it\nhttp://s.example/$it.ts" }
        // Slow data: the two imports overlap for real instead of finishing before the other starts.
        h.serve("/list.m3u") { MockResponse.Builder().body(body).throttleBody(16_384, 20, TimeUnit.MILLISECONDS).build() }
    }

    /** Spec 10 SRC-FR-75; see [ImportScenarios.twoGuideImportsOfOneSourceOverlap]. */
    @Test
    fun twoImportsOfOneSourceLeaveItIntact() = ImportScenarios.twoGuideImportsOfOneSourceOverlap()

    @Test
    fun aRequestForKindsAlreadyRunningJoinsTheRunningJob() {
        h.addM3u()
        slowPlaylist(1_000)
        val first = h.runner.sync("m3u-1")
        assertSame(first, h.runner.sync("m3u-1", setOf(RefreshKind.PLAYLIST)))
        runBlocking { first.join() }
    }

    @Test
    fun removingStopsTheImportAndLeavesNothing() = runBlocking {
        h.addM3u(guide = "/guide.xml")
        h.serve("/list.m3u", "#EXTM3U\n#EXTINF:-1 tvg-id=\"a\" group-title=\"G\",A\nhttp://s.example/a\n#EXTINF:-1 group-title=\"Movies\",Film\nhttp://s.example/f.mkv\n")
        h.serve("/guide.xml", "<tv><programme channel=\"a\" start=\"20300101000000 +0000\" stop=\"20300101010000 +0000\"><title>T</title></programme></tv>")
        h.clock.now = 1_893_456_000_000L - 3_600_000L
        h.runner.sync("m3u-1").join()
        assertTrue(h.query("SELECT COUNT(*) FROM programme").single().toInt() > 0)
        h.addM3u(id = "m3u-2")
        slowPlaylist(3_000)
        val running = h.runner.sync("m3u-1", setOf(RefreshKind.PLAYLIST))
        delay(200)
        assertEquals(Outcome.Ok(Unit), h.runner.remove("m3u-1"))
        assertTrue(running.isCancelled)
        for (table in listOf("channel", "movie", "series", "episode", "programme", "epg_channel", "content_group", "source_status")) {
            assertEquals(table, "0", h.query("SELECT COUNT(*) FROM $table WHERE source_id = 'm3u-1'").single())
        }
        assertNull(h.sources.source("m3u-1"))
        assertTrue(h.secrets.values.keys.none { it.endsWith("m3u-1") })
        // Nothing comes back once the source is gone.
        h.runner.sync("m3u-1").join()
        assertEquals("0", h.query("SELECT COUNT(*) FROM channel WHERE source_id = 'm3u-1'").single())
    }

    /** M1 exit criterion 4: failing imports whose addresses carry credentials log none of them. */
    @Test
    fun noCredentialReachesTheLog() = runBlocking {
        h.addXtream()
        h.serve("/panel/player_api.php", "", code = 500)
        val getPhp = h.server.url("/get.php").newBuilder().addQueryParameter("username", "m3uviewer").addQueryParameter("password", "m3usecret").build()
        h.addM3u(playlist = getPhp.toString())
        h.runner.sync("xtream-1").join()
        h.runner.sync("m3u-1").join()
        assertTrue("the failures were logged", h.log.snapshot().any { "failed" in it })
        for (secret in listOf("viewer", "secret", "m3uviewer", "m3usecret")) {
            assertTrue("$secret in ${h.log.snapshot()}", h.log.snapshot().none { secret in it })
        }
    }

    @Test
    fun aRefreshLeftRunningByADeadProcessReadsAsInterrupted() = runBlocking {
        h.addM3u(guide = "/guide.xml")
        withContext(h.env.dispatchers.bulkWrite) {
            h.db.sourceStatus().upsert(SourceStatusEntity("m3u-1", "epg", "running", 1, null, null, null, null, 0, 0, 3, 2, null))
            h.db.guideImport().insertProgrammes(
                listOf(2L, 3L).map {
                    com.sohva.tv.core.data.database.ProgrammeEntity(sourceId = "m3u-1", snapshot = it, epgId = "a", startAt = 0, stopAt = 1,
                        title = "t", subtitle = null, description = null, categories = null, programmeKey = "k")
                },
            )
        }
        h.runner.recoverAfterRestart()
        assertEquals(listOf("failed|interrupted|1"), h.query("SELECT status, error_code, consecutive_failures FROM source_status"))
        assertEquals("The orphan snapshot 3 is gone, the active 2 kept", listOf("2"), h.query("SELECT snapshot FROM programme"))
    }
}
