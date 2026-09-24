package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.EpgChannelEntity
import com.sohva.tv.core.data.database.EpisodeEntity
import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SeriesEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceStatusEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportDaoTest {
    // Main-thread queries only because Robolectric runs the test there; the app never allows them.
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() = db.close()

    private fun channel(source: String, local: String, epg: String? = null, hash: Long = 1) = ChannelEntity(
        key = "$source:$local", sourceId = source, groupId = null, name = local, sortName = local, tvgId = epg, epgId = epg,
        logoUrl = null, streamUrlEnc = "enc", userAgent = null, referrer = null, playlistOrder = 0, providerNumber = null,
        number = null, displayRank = 0, visible = true, catchupType = null, catchupSource = null, catchupDays = null,
        catchupTz = null, xtreamStreamId = null, contentHash = hash, generation = 1,
    )

    @Test
    fun channelDiffStepsStayInsideTheSource() {
        val dao = db.channelImport()
        // "a" and "a.b" share a text prefix: the range must still separate them.
        dao.insert((1..5).map { channel("a", "c$it") } + (1..3).map { channel("a.b", "c$it") } + channel("ab", "c1"))
        val range = KeyRange.channels("a")
        assertEquals(5, dao.count(range.from, range.until))
        assertEquals(listOf(1L, 1L), dao.hashes(listOf("a:c1", "a:c2", "zz:none")).map { it.hash })

        val walked = ArrayList<String>()
        var after = range.from
        while (true) {
            val page = dao.keysPage(after, range.until, 2)
            if (page.isEmpty()) break
            walked += page.map { it.key }
            after = page.last().key
        }
        assertEquals((1..5).map { "a:c$it" }, walked)

        val stored = dao.hashes(listOf("a:c1")).single()
        dao.update(listOf(channel("a", "c1", hash = 9).copy(id = stored.id)))
        assertEquals(9L, dao.hashes(listOf("a:c1")).single().hash)
        dao.delete(dao.hashes(listOf("a:c4", "a:c5")).map { it.id })
        assertEquals(3, dao.count(range.from, range.until))
        assertEquals(3, KeyRange.channels("a.b").let { dao.count(it.from, it.until) })
    }

    @Test
    fun epgIdPagesKeepDuplicatesAcrossPageEdges() {
        val dao = db.channelImport()
        dao.insert(listOf(channel("a", "1", "x.fi"), channel("a", "2", "x.fi"), channel("a", "3", "x.fi"), channel("a", "4", "y.fi"), channel("a", "5"), channel("b", "1", "x.fi")))
        val seen = ArrayList<String>()
        var afterEpg = ""
        var afterId = 0L
        while (true) {
            val page = dao.epgIdsPage("a", afterEpg, afterId, 2)
            if (page.isEmpty()) break
            seen += page.map { it.epgId }
            afterEpg = page.last().epgId
            afterId = page.last().id
        }
        assertEquals(listOf("x.fi", "x.fi", "x.fi", "y.fi"), seen)
    }

    @Test
    fun orphanEpisodesGoWithTheirSeries() {
        val series = db.seriesImport()
        val ids = series.insert(
            listOf("s1", "s2").map {
                SeriesEntity(key = "series:a:$it", sourceId = "a", providerId = it, groupId = null, name = it, sortName = it, year = null,
                    rating = null, ratingX10 = null, posterUrl = null, backdropUrl = null, plot = null, providerOrder = 0, genre = null,
                    workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = 0, generation = 1)
            },
        )
        val episodes = db.episodeImport()
        episodes.insert(
            ids.flatMapIndexed { i, seriesId ->
                (1..3).map { n ->
                    EpisodeEntity(key = "vod:episode:a:$i-$n", seriesId = seriesId, sourceId = "a", providerId = "$i-$n", season = 1, number = n,
                        name = null, streamUrlEnc = "enc", plot = null, durationSeconds = null, thumbnailUrl = null, contentHash = 0, generation = 1)
                }
            },
        )
        series.delete(listOf(ids[0]))
        val range = KeyRange.episodes("a")
        assertEquals(2, episodes.deleteOrphans(range.from, range.until, 2))
        assertEquals(1, episodes.deleteOrphans(range.from, range.until, 2))
        assertEquals(0, episodes.deleteOrphans(range.from, range.until, 2))
        assertEquals(3, episodes.keysPage(range.from, range.until, 10).size)
    }

    @Test
    fun guideSweepKeepsTheActiveSnapshotAndOtherSources() {
        val guide = db.guideImport()
        fun programmes(source: String, snapshot: Long, count: Int) = (1..count).map {
            ProgrammeEntity(sourceId = source, snapshot = snapshot, epgId = "x", startAt = it * 1000L, stopAt = it * 1000L + 1,
                title = "t", subtitle = null, description = null, categories = null, programmeKey = "k$it")
        }
        guide.insertProgrammes(programmes("a", 1, 5) + programmes("a", 2, 3) + programmes("a", 3, 2) + programmes("b", 1, 4))
        guide.insertChannels(listOf(EpgChannelEntity("a", 1, "x", "X", null), EpgChannelEntity("a", 2, "x", "X", null), EpgChannelEntity("b", 1, "x", "X", null)))
        var total = 0
        while (true) {
            val deleted = guide.sweepProgrammes("a", keep = 2, limit = 3)
            if (deleted == 0) break
            total += deleted
        }
        assertEquals(7, total)
        assertEquals(1, guide.sweepChannels("a", keep = 2, limit = 10))
        val remaining = db.openHelper.readableDatabase.query("SELECT source_id, snapshot, COUNT(*) FROM programme GROUP BY 1, 2 ORDER BY 1, 2")
        val rows = ArrayList<String>()
        remaining.use { while (it.moveToNext()) rows += "${it.getString(0)}/${it.getLong(1)}=${it.getInt(2)}" }
        assertEquals(listOf("a/2=3", "b/1=4"), rows)
    }

    @Test
    fun runningRefreshesReadAsInterruptedAfterARestart() {
        val status = db.sourceStatus()
        fun row(kind: String, state: String) = SourceStatusEntity("a", kind, state, 1, null, null, null, null, 0, 2, 3, null, null)
        status.upsert(row("playlist", "running"))
        status.upsert(row("epg", "success"))
        assertEquals(1, status.markInterrupted(now = 99))
        val interrupted = status.get("a", "playlist")!!
        assertEquals("failed", interrupted.status)
        assertEquals("interrupted", interrupted.errorCode)
        assertEquals(99L, interrupted.lastFailureAt)
        assertEquals(3, interrupted.consecutiveFailures)
        assertEquals("success", status.get("a", "epg")!!.status)
    }
}
