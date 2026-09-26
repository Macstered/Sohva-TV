package com.sohva.tv.feature.sport

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.ProfileAllowedGroupEntity
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.sport.EventStatus
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.Decision
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.TeamVariants
import com.sohva.tv.feature.sport.pairing.PairingScan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 60 §11 "Matcher" (streamed pages) and "Persistence" on a real schema: channel pages in row-id
 * order, a batch's programmes in the window with the source's offset, one guide id shared by
 * several channels, hidden channels and groups left out, and winners kept across page boundaries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PairingScanTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java).allowMainThreadQueries().build()
    private val kickOff = 1_790_445_600_000L // a round hour on 2026-09-26
    private val minute = 60_000L
    private val game = SportEvent(
        "api-sports:football:1", SportType.FOOTBALL, "39", "Premier League", null, Side("Northbridge", null), Side("Harbor", null),
        kickOff, 20 * 60, EventStatus.SCHEDULED, null, null, null,
    )

    @After
    fun close() = db.close()

    private fun channel(i: Int, name: String, epg: String?, group: Long?, visible: Boolean = true) = ChannelEntity(
        key = "s:c$i", sourceId = "s", groupId = group, name = name, sortName = name.lowercase(), providerName = name, providerGroupId = group,
        providerLogoUrl = null, tvgId = epg, epgId = epg, logoUrl = null, streamUrlEnc = "enc", userAgent = null, referrer = null,
        playlistOrder = i, providerNumber = null, number = null, displayRank = i.toLong(), visible = visible, catchupType = null,
        catchupSource = null, catchupDays = null, catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
    )

    private fun programme(epg: String, start: Long, title: String, snapshot: Long = 3) = ProgrammeEntity(
        sourceId = "s", snapshot = snapshot, epgId = epg, startAt = start, stopAt = start + 120 * minute, title = title,
        subtitle = null, description = "A long description ".repeat(100), categories = null, programmeKey = "p-$epg-$start",
    )

    /** 600 filler channels around the interesting ones, so pages of 256 break in between. */
    private fun seed(offsetMinutes: Int = 0) = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", offsetMinutes, 0, 0))
        db.sourceStatus().upsert(SourceStatusEntity("s", "epg", "success", null, null, null, null, null, 0, 0, 3, 3, null))
        val shown = db.groupImport().insert(ContentGroupEntity(sourceId = "s", room = "LIVE", groupKey = "sport", name = "Sport", providerOrder = 0, itemCount = 1, shown = true, position = 0, sortMode = null))
        val hidden = db.groupImport().insert(ContentGroupEntity(sourceId = "s", room = "LIVE", groupKey = "hidden", name = "Hidden", providerOrder = 1, itemCount = 1, shown = false, position = 1, sortMode = null))
        val rows = ArrayList<ChannelEntity>()
        for (i in 0 until 600) rows += channel(i, "Filler $i", "f$i", shown)
        rows += channel(600, "Sport One HD", "sport1", shown)
        rows += channel(601, "Sport One FI", "sport1", shown)
        rows += channel(602, "EVENT: Northbridge - Harbor", null, shown)
        rows += channel(603, "Northbridge v Harbor (hidden channel)", null, shown, visible = false)
        rows += channel(604, "Northbridge v Harbor (hidden group)", null, hidden)
        for (i in 605 until 900) rows += channel(i, "Filler $i", "f$i", shown)
        db.channelImport().insert(rows)
        val stored = kickOff - offsetMinutes * minute
        db.guideImport().insertProgrammes(
            listOf(
                programme("sport1", stored - 15 * minute, "Football: Northbridge v Harbor"),
                programme("sport1", stored - 60 * minute, "Harbor preview"),
                programme("sport1", stored + 200 * minute, "Northbridge v Harbor (repeat)"),
                programme("sport1", stored, "Northbridge v Harbor", snapshot = 2),
            ) + (0 until 900).map { programme("f${it % 600}", stored + (it % 7) * minute, "Unrelated $it") },
        )
    }

    private fun scan(decisions: Map<Pair<String, String>, Decision> = emptyMap()) = runBlocking {
        PairingScan(db.pairing()).let { it to it.run(listOf(game), TeamVariants.aliases(emptyMap()), decisions).getValue(game.id) }
    }

    @Test
    fun pagesFindTheGuideAndNameMatchesOfVisibleChannelsOnly() {
        seed()
        val (scan, streams) = scan()
        assertEquals(listOf("s:c600", "s:c601", "s:c602"), streams.map { it.channelKey }.sorted())
        val guide = streams.first { it.channelKey == "s:c600" }
        assertEquals(MatchSource.GUIDE, guide.source)
        assertEquals("Football: Northbridge v Harbor", guide.programmeTitle)
        assertEquals(-15L, guide.offsetMinutes)
        assertEquals(Confidence.AVAILABLE, guide.confidence)
        // One guide id, two channels: both get the programme.
        assertEquals(guide.programmeId, streams.first { it.channelKey == "s:c601" }.programmeId)
        assertEquals(MatchSource.NAME, streams.first { it.channelKey == "s:c602" }.source)
        // 900 visible channels in pages of 256 read four pages; the window keeps the programme reads small.
        assertEquals(4, scan.lastStats.channelQueries)
        assertTrue(scan.lastStats.programmeQueries in 4..40)
    }

    @Test
    fun theSourceOffsetShiftsTheWindowAndTheStart() {
        seed(offsetMinutes = 60)
        val (_, streams) = scan()
        val guide = streams.first { it.channelKey == "s:c600" }
        assertEquals(-15L, guide.offsetMinutes)
        assertEquals(kickOff - 15 * minute, guide.programmeStartMillis)
    }

    @Test
    fun aDecidedChannelWithoutEvidenceStaysListed() {
        seed()
        val (_, streams) = scan(mapOf((game.id to "s:c5") to Decision.REJECTED))
        assertEquals(Confidence.REJECTED, streams.first { it.channelKey == "s:c5" }.confidence)
    }

    @Test
    fun aRestrictedProfileIsOfferedOnlyItsGroups() = runBlocking {
        seed()
        db.profiles().allow(ProfileAllowedGroupEntity("kids", "LIVE", "news"))
        assertEquals(emptyList<String>(), db.pairing().allowed(listOf("s:c600", "s:c602"), "kids"))
        assertEquals(listOf("s:c600", "s:c602"), db.pairing().allowed(listOf("s:c600", "s:c602"), "adult").sorted())
    }
}
