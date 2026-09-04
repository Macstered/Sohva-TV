package com.streammate.tv.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiSourceGuideDatabaseTest {
    private lateinit var database: StreamMateDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun sourceSnapshotsActivateIndependentlyAndDisabledSourcesDisappear() = runBlocking {
        val dao = database.guideDao()
        dao.upsertSourceState(source("source-a", "Primary", priority = 10))
        dao.upsertSourceState(source("source-b", "Backup", priority = 0))
        dao.upsertChannels(listOf(channel("source-a", "a-1", "source-a:one", "One")))
        dao.upsertChannels(listOf(channel("source-b", "b-1", "source-b:two", "Two")))
        dao.activatePlaylistSnapshot("source-a", "a-1", 1, 1)
        dao.activatePlaylistSnapshot("source-b", "b-1", 1, 1)

        assertEquals(listOf("One", "Two"), dao.observeGuide(1).first().map { it.name })

        dao.upsertChannels(listOf(channel("source-a", "a-staged", "source-a:failed", "Failed")))
        dao.deleteChannelSnapshot("source-a", "a-staged")

        assertEquals(listOf("One", "Two"), dao.observeGuide(1).first().map { it.name })

        dao.upsertChannels(listOf(channel("source-a", "a-2", "source-a:three", "Three")))
        dao.activatePlaylistSnapshot("source-a", "a-2", 1, 2)

        assertEquals(listOf("Three", "Two"), dao.observeGuide(1).first().map { it.name })
        assertNull(dao.getActiveChannel("source-a:one"))

        dao.upsertSourceState(source("source-b", "Backup", priority = 0, enabled = false))

        assertEquals(listOf("Three"), dao.observeGuide(1).first().map { it.name })
    }

    @Test
    fun timelineKeepsChannelsWithoutEpgAndReturnsOnlyOverlappingProgrammes() = runBlocking {
        val dao = database.guideDao()
        dao.upsertSourceState(source("source-a", "Primary", priority = 10))
        dao.upsertChannels(
            listOf(
                channel("source-a", "playlist", "source-a:one", "One", tvgId = "one.fi"),
                channel("source-a", "playlist", "source-a:two", "Two"),
            ),
        )
        dao.activatePlaylistSnapshot("source-a", "playlist", 2, 1)
        dao.upsertProgrammes(
            listOf(
                TvProgrammeEntity(
                    sourceId = "source-a",
                    snapshotId = "epg",
                    programmeId = "current",
                    xmltvChannelId = "one.fi",
                    startEpochMillis = 100,
                    stopEpochMillis = 200,
                    title = "Current show",
                    subtitle = null,
                    description = "Description",
                    categories = "Sport",
                ),
                TvProgrammeEntity(
                    sourceId = "source-a",
                    snapshotId = "epg",
                    programmeId = "outside",
                    xmltvChannelId = "one.fi",
                    startEpochMillis = 400,
                    stopEpochMillis = 500,
                    title = "Outside window",
                    subtitle = null,
                    description = null,
                    categories = "",
                ),
            ),
        )
        dao.activateEpgSnapshot("source-a", "epg", 2, 1)

        val rows = dao.observeGuideTimeline(fromEpochMillis = 150, toEpochMillis = 300).first()

        assertEquals(listOf("One", "Two"), rows.map { it.channelName })
        assertEquals("Current show", rows.first().programmeTitle)
        assertNull(rows.last().programmeId)

        dao.upsertProgrammes(
            listOf(
                TvProgrammeEntity(
                    sourceId = "source-a",
                    snapshotId = "epg",
                    programmeId = "manual",
                    xmltvChannelId = "manual.fi",
                    startEpochMillis = 100,
                    stopEpochMillis = 250,
                    title = "Manually mapped",
                    subtitle = null,
                    description = null,
                    categories = "",
                ),
            ),
        )
        dao.upsertChannelPreference(
            ChannelPreferenceEntity(
                channelId = "source-a:one",
                sourceId = "source-a",
                customName = "Renamed One",
                customGroupTitle = "Favourites",
                hidden = false,
                sortOrder = 0,
                manualXmltvChannelId = "manual.fi",
                updatedAtEpochMillis = 2,
            ),
        )

        val customized = dao.observeGuideTimeline(150, 300).first()
        assertEquals("Renamed One", customized.first().channelName)
        assertEquals("Favourites", customized.first().groupTitle)
        assertEquals("Manually mapped", customized.first().programmeTitle)

        dao.upsertChannelPreference(
            dao.channelPreference("source-a:one")!!.copy(hidden = true),
        )
        assertEquals(listOf("Two"), dao.observeGuideTimeline(150, 300).first().map { it.channelName })
    }

    @Test
    fun epgOffsetAppliesPerSourceToExistingGuideQueries() = runBlocking {
        val dao = database.guideDao()
        val hour = 60L * 60L * 1_000L
        dao.upsertSourceState(source("source-a", "Corrected", priority = 10, epgOffsetMinutes = 60))
        dao.upsertSourceState(source("source-b", "Unchanged", priority = 0))
        dao.upsertChannels(
            listOf(
                channel("source-a", "playlist-a", "source-a:one", "One", tvgId = "one.fi"),
                channel("source-b", "playlist-b", "source-b:two", "Two", tvgId = "two.fi"),
            ),
        )
        dao.activatePlaylistSnapshot("source-a", "playlist-a", 1, 1)
        dao.activatePlaylistSnapshot("source-b", "playlist-b", 1, 1)
        dao.upsertProgrammes(
            listOf(
                programme("source-a", "epg-a", "a-show", "one.fi", hour, 2 * hour, "Shifted show"),
                programme("source-b", "epg-b", "b-show", "two.fi", hour, 2 * hour, "Raw show"),
            ),
        )
        dao.activateEpgSnapshot("source-a", "epg-a", 1, 1)
        dao.activateEpgSnapshot("source-b", "epg-b", 1, 1)

        val currentAtTwoThirty = dao.observeGuide(2 * hour + hour / 2).first()
        assertEquals("Shifted show", currentAtTwoThirty.first { it.sourceId == "source-a" }.currentProgrammeTitle)
        assertNull(currentAtTwoThirty.first { it.sourceId == "source-b" }.currentProgrammeTitle)
        assertEquals(2 * hour, currentAtTwoThirty.first { it.sourceId == "source-a" }.programmeStartEpochMillis)
        assertEquals(3 * hour, currentAtTwoThirty.first { it.sourceId == "source-a" }.programmeStopEpochMillis)

        val timeline = dao.observeGuideTimeline(2 * hour, 3 * hour).first()
        val shiftedTimeline = timeline.first { it.sourceId == "source-a" }
        assertEquals("Shifted show", shiftedTimeline.programmeTitle)
        assertEquals(2 * hour, shiftedTimeline.programmeStartEpochMillis)
        assertNull(timeline.first { it.sourceId == "source-b" }.programmeId)

        val searchResult = dao.searchGuide("Shifted", 10).single()
        assertEquals(2 * hour, searchResult.startEpochMillis)
        assertEquals(3 * hour, searchResult.stopEpochMillis)

        val candidates = dao.programmeCandidates(2 * hour, 2 * hour).single()
        assertEquals("Shifted show", candidates.programmeTitle)
        assertEquals(2 * hour, candidates.programmeStartEpochMillis)

        dao.upsertSourceState(source("source-a", "Corrected", priority = 10, epgOffsetMinutes = -60))
        val correctedImmediately = dao.observeGuide(hour / 2).first()
            .first { it.sourceId == "source-a" }
        assertEquals("Shifted show", correctedImmediately.currentProgrammeTitle)
        assertEquals(0L, correctedImmediately.programmeStartEpochMillis)
    }

    @Test
    fun channelCustomizationCanBeReplacedFromValidatedBackup() = runBlocking {
        val dao = database.guideDao()
        dao.upsertChannelPreference(
            ChannelPreferenceEntity("old", "source", "Old", null, false, null, null, 1),
        )
        dao.upsertCustomChannelList(CustomChannelListEntity("old-list", "Old list", 0, 1))
        dao.upsertCustomChannelListMember(CustomChannelListMemberEntity("old-list", "old", 0))

        val preference = ChannelPreferenceEntity("new", "source", "New", "News", true, 3, "epg", 2)
        val list = CustomChannelListEntity("new-list", "New list", 0, 2)
        val member = CustomChannelListMemberEntity("new-list", "new", 0)
        dao.replaceChannelCustomization(listOf(preference), listOf(list), listOf(member))

        assertNull(dao.channelPreference("old"))
        assertEquals(listOf(preference), dao.channelPreferences())
        assertEquals(listOf(list), dao.customChannelLists())
        assertEquals(listOf(member), dao.customChannelListMembers())
    }

    @Test
    fun stagedEpgMatchCountsProgrammesAgainstActiveChannelsOnly() = runBlocking {
        val dao = database.guideDao()
        dao.upsertSourceState(source("source-a", "Primary", priority = 10))
        dao.upsertChannels(
            listOf(
                channel("source-a", "playlist", "source-a:one", "One", tvgId = "one.fi"),
                channel("source-a", "playlist", "source-a:two", "Two", tvgId = ""),
                channel("source-a", "playlist", "source-a:three", "Three"),
                channel("source-a", "stale", "source-a:old", "Old", tvgId = "old.fi"),
            ),
        )
        dao.activatePlaylistSnapshot("source-a", "playlist", 3, 1)
        dao.upsertChannelPreference(
            ChannelPreferenceEntity(
                channelId = "source-a:three",
                sourceId = "source-a",
                customName = null,
                customGroupTitle = null,
                hidden = false,
                sortOrder = null,
                manualXmltvChannelId = "mapped.fi",
                updatedAtEpochMillis = 1,
            ),
        )
        dao.upsertProgrammes(
            listOf(
                programme("source-a", "staged", "p1", "one.fi", 100, 200, "Matches by EPG id"),
                programme("source-a", "staged", "p2", "mapped.fi", 100, 200, "Matches by manual mapping"),
                programme("source-a", "staged", "p3", "old.fi", 100, 200, "Only an inactive snapshot has this id"),
                programme("source-a", "staged", "p4", "nowhere.fi", 100, 200, "Unknown channel"),
                programme("source-a", "other", "p5", "one.fi", 100, 200, "Different snapshot"),
            ),
        )

        val match = dao.stagedEpgMatch("source-a", "staged")

        // One by id, one by manual mapping; the blank-id channel does not count as mappable.
        assertEquals(2, match.matchedProgrammes)
        assertEquals(2, match.mappableChannels)
        assertEquals(StagedEpgMatchRow(0, 2), dao.stagedEpgMatch("source-a", "missing"))
        assertEquals(StagedEpgMatchRow(0, 0), dao.stagedEpgMatch("source-b", "staged"))
    }

    private fun source(
        id: String,
        name: String,
        priority: Int,
        enabled: Boolean = true,
        epgOffsetMinutes: Int = 0,
    ) = IptvSourceStateEntity(
        sourceId = id,
        name = name,
        type = "M3U",
        enabled = enabled,
        connectionLimit = 1,
        priority = priority,
        updatedAtEpochMillis = 1,
        epgOffsetMinutes = epgOffsetMinutes,
    )

    private fun programme(
        sourceId: String,
        snapshotId: String,
        programmeId: String,
        xmltvChannelId: String,
        startEpochMillis: Long,
        stopEpochMillis: Long,
        title: String,
    ) = TvProgrammeEntity(
        sourceId = sourceId,
        snapshotId = snapshotId,
        programmeId = programmeId,
        xmltvChannelId = xmltvChannelId,
        startEpochMillis = startEpochMillis,
        stopEpochMillis = stopEpochMillis,
        title = title,
        subtitle = null,
        description = null,
        categories = "",
    )

    private fun channel(
        sourceId: String,
        snapshotId: String,
        channelId: String,
        name: String,
        tvgId: String? = null,
    ) = IptvChannelEntity(
        sourceId = sourceId,
        snapshotId = snapshotId,
        channelId = channelId,
        tvgId = tvgId,
        name = name,
        normalizedName = name.lowercase(),
        groupTitle = null,
        logoUrl = null,
        encryptedStreamUrl = "encrypted",
        userAgent = null,
        referrer = null,
        lastSeenEpochMillis = 1,
    )
}
