package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.EpisodeEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.ProfileAllowedGroupEntity
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SearchIndex
import com.sohva.tv.core.data.database.SearchTable
import com.sohva.tv.core.data.database.SeriesEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.data.live.ChannelEffects
import com.sohva.tv.core.data.search.SearchReads
import com.sohva.tv.core.model.search.SearchTerms
import com.sohva.tv.core.model.text.SortNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 03 §11: the index follows the tables, matching is by word prefix, and hidden rows never appear. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchReadsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .addCallback(SearchIndex.Callback)
        .allowMainThreadQueries()
        .build()
    private var profile = "default"
    private val reads = SearchReads(db, Dispatchers.Unconfined) { profile }

    @After
    fun close() = db.close()

    private fun film(id: Int, name: String, group: Long, visible: Boolean = true) = MovieEntity(
        key = "vod:movie:a:$id", sourceId = "a", providerId = "$id", groupId = group, name = name, sortName = SortNames.of(name),
        year = 2001, rating = null, ratingX10 = null, posterUrl = null, streamUrlEnc = "enc", plot = null, providerOrder = id, genre = null,
        workKey = null, primaryCopy = true, visible = visible, itemPosition = null, contentHash = 1, generation = 1,
    )

    private fun channel(id: Int, name: String, epg: String) = ChannelEntity(
        key = "a:c$id", sourceId = "a", groupId = null, name = name, sortName = SortNames.of(name), providerName = name, providerGroupId = null,
        providerLogoUrl = null, tvgId = epg, epgId = epg, logoUrl = null, streamUrlEnc = "enc", userAgent = null, referrer = null,
        playlistOrder = id, providerNumber = null, number = null, displayRank = ChannelEffects.playlistRank(id), visible = true,
        catchupType = null, catchupSource = null, catchupDays = null, catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
    )

    private fun seed() = runBlocking {
        db.sources().upsert(SourceEntity("a", "First", "XTREAM", true, 0, 1, "BOTH", 0, 0, 0))
        val g = db.groupImport().insert(
            ContentGroupEntity(sourceId = "a", room = "MOVIES", groupKey = "id:1", name = "Drama", providerOrder = 0, itemCount = 3, shown = true, position = 0, sortMode = null),
        )
        db.movieImport().insert(listOf(film(1, "The Matchmaker", g), film(2, "Mätch Point", g), film(3, "Hidden Match", g, visible = false)))
        db.channelImport().insert(listOf(channel(1, "Match TV", "m.tv"), channel(2, "News", "n.tv")))
        val sid = db.seriesImport().insert(
            listOf(
                SeriesEntity(
                    key = "series:a:s1", sourceId = "a", providerId = "s1", groupId = null, name = "Matching Hearts", sortName = SortNames.of("Matching Hearts"),
                    year = null, rating = null, ratingX10 = null, posterUrl = null, backdropUrl = null, plot = null, providerOrder = 0, genre = null,
                    workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
                ),
            ),
        ).single()
        db.episodeImport().insert(
            (1..2).map { n ->
                EpisodeEntity(
                    key = "vod:episode:a:e$n", seriesId = sid, sourceId = "a", providerId = "e$n", season = 1, number = n, name = "Part $n",
                    streamUrlEnc = "enc", plot = null, durationSeconds = null, thumbnailUrl = null, contentHash = 1, generation = 1,
                )
            },
        )
        db.guideImport().insertProgrammes(
            listOf(
                ProgrammeEntity(sourceId = "a", snapshot = 2, epgId = "n.tv", startAt = 1_000, stopAt = 2_000, title = "Match of the Day", subtitle = null, description = null, categories = null, programmeKey = "k1"),
                ProgrammeEntity(sourceId = "a", snapshot = 1, epgId = "n.tv", startAt = 1_000, stopAt = 2_000, title = "Match Old Snapshot", subtitle = null, description = null, categories = null, programmeKey = "k2"),
            ),
        )
        db.sourceStatus().upsert(SourceStatusEntity("a", "epg", "success", 0, 0, null, null, null, 1, 0, 2, 2, 0))
        // As the imports end: new rows into the index in one statement.
        SearchIndex.catchUp(db, *SearchTable.entries.toTypedArray())
    }

    @Test
    fun wordPrefixesMatchAcrossCaseAndAccentsAndHiddenTitlesStayOut() = runBlocking {
        seed()
        assertEquals(listOf("Mätch Point", "The Matchmaker"), reads.films("MATCH").map { it.name })
        // Inside a word does not match: prefixes only (decision "Search matching").
        assertEquals(emptyList<String>(), reads.films("atch").map { it.name })
        val live = reads.live("match")
        assertEquals(listOf("Match TV"), live.channels.map { it.name })
        // Only the active guide snapshot, reached through the channel's EPG id.
        assertEquals(listOf("Match of the Day" to "News"), live.programmes.map { it.title to it.channelName })
        // A series name finds its episodes.
        assertEquals(listOf("Part 1", "Part 2"), reads.episodes("hearts").map { it.name })
    }

    /** Spec 04 PROF-FR-23, SEARCH-18: a restricted profile finds only what its groups hold, episodes included. */
    @Test
    fun aRestrictedProfileFindsOnlyItsGroups() = runBlocking {
        seed()
        profile = "kids"
        db.profiles().allow(ProfileAllowedGroupEntity("kids", "MOVIES", "id:9"))
        db.profiles().allow(ProfileAllowedGroupEntity("kids", "SERIES", "id:9"))
        db.profiles().allow(ProfileAllowedGroupEntity("kids", "LIVE", "id:9"))
        assertEquals(emptyList<String>(), reads.films("match").map { it.name })
        assertEquals(emptyList<String>(), reads.series("matching").map { it.name })
        assertEquals(emptyList<String>(), reads.episodes("hearts").map { it.name })
        val live = reads.live("match")
        assertEquals(emptyList<String>(), live.channels.map { it.name } + live.programmes.map { it.title })
        db.profiles().allow(ProfileAllowedGroupEntity("kids", "MOVIES", "id:1"))
        assertEquals(listOf("Mätch Point", "The Matchmaker"), reads.films("match").map { it.name })
        profile = "default"
        assertEquals(listOf("Part 1", "Part 2"), reads.episodes("hearts").map { it.name })
    }

    @Test
    fun theIndexFollowsRenamesAndDeletes() = runBlocking {
        seed()
        db.openHelper.writableDatabase.execSQL("UPDATE movie SET name = 'Renamed' WHERE key = 'vod:movie:a:1'")
        assertEquals(listOf("Renamed"), reads.films("ren").map { it.name })
        assertEquals(listOf("Mätch Point"), reads.films("match").map { it.name })
        db.openHelper.writableDatabase.execSQL("DELETE FROM channel WHERE key = 'a:c1'")
        assertEquals(emptyList<String>(), reads.live("match").channels.map { it.name })
    }

    @Test
    fun termsAreCutTrimmedAndQuoted() {
        assertNull(SearchTerms.term(" a "))
        assertEquals("ab", SearchTerms.term("  ab "))
        assertEquals(80, SearchTerms.cut("x".repeat(100)).length)
        assertEquals("\"Star*\" \"Wars*\"", SearchTerms.match("Star-Wars"))
        assertNull(SearchTerms.match("--"))
    }
}
