package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.data.database.ProfileAllowedGroupEntity
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.data.live.LiveStore
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 1_790_279_220_000L
    private val clock = object : Clock {
        override fun wallMillis(): Long = now++
        override fun monotonicNanos(): Long = 0
    }
    private val store = LiveStore(db, Dispatchers.Unconfined, clock)
    private val min = GuideWindow.MINUTE_MS

    @After
    fun close() = db.close()

    private fun seed(channels: Int = 450): Pair<Long, Long> = runBlocking {
        db.sources().upsert(SourceEntity("s", "Fixture", "M3U", true, 0, 1, "BOTH", 60, 0, 0))
        val news = db.groupImport().insert(group("news", "News", 0, channels))
        val sport = db.groupImport().insert(group("sport", "Sport", 1, 2))
        val rows = ArrayList<ChannelEntity>()
        for (i in 0 until channels) {
            // Ranks deliberately not in id order, with a tie, to prove the keyset key is (rank, id).
            val rank = if (i == 7) 6L * 1024 else i.toLong() * 1024
            rows += channel("c$i", "Channel $i", news, rank, number = if (i == 4) 12 else null, epg = "e$i")
        }
        rows += channel("x0", "Äijä Sport", sport, 100_000, number = null, epg = "ex")
        rows += channel("x1", "Hidden", sport, 100_001, number = null, epg = null, visible = false)
        rows += channel("u0", "No group", null, 200_000, number = null, epg = null)
        db.channelImport().insert(rows)
        news to sport
    }

    private fun group(key: String, name: String, order: Int, count: Int) = ContentGroupEntity(
        sourceId = "s", room = "LIVE", groupKey = key, name = name, providerOrder = order, itemCount = count,
        shown = true, position = order, sortMode = null,
    )

    private fun channel(local: String, name: String, group: Long?, rank: Long, number: Int?, epg: String?, visible: Boolean = true) =
        ChannelEntity(
            key = "s:$local", sourceId = "s", groupId = group, name = name, sortName = SortNames.of(name), providerName = name, providerGroupId = group,
            providerLogoUrl = null, tvgId = epg, epgId = epg,
            logoUrl = null, streamUrlEnc = "enc", userAgent = null, referrer = null, playlistOrder = 0, providerNumber = number,
            number = number, displayRank = rank, visible = visible, catchupType = null, catchupSource = null, catchupDays = null,
            catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
        )

    @Test
    fun listsArePagedInDisplayOrderWithoutHoldingRows() = runBlocking {
        val (news, _) = seed()
        val list = store.open(ListSpec.Group("s", news))
        assertEquals(450, list.size)
        assertEquals(3, list.pageCount)
        val all = (0 until list.pageCount).flatMap { store.page(list, it) }
        assertEquals(450, all.size)
        assertEquals(all.sortedWith(compareBy({ it.rank }, { it.id })), all)
        assertEquals("News", all.first().groupName)
        assertEquals(ChannelList.PAGE, store.page(list, 0).size)
        for (i in listOf(0, 7, 199, 200, 399, 449)) assertEquals(i, store.indexOf(list, all[i]))

        val source = store.open(ListSpec.All("s"))
        assertEquals(452, source.size)
        assertEquals(1, store.open(ListSpec.Ungrouped("s")).size)
        assertEquals(listOf("News", "Sport"), store.rail("s").map { it.name })
        assertEquals(listOf("s"), store.sources.first().map { it.id })
    }

    /** Spec 04 PROF-FR-23: a restricted profile sees only its groups; widening restores the rest. */
    @Test
    fun aRestrictedProfileSeesOnlyItsGroups() = runBlocking {
        val (news, sport) = seed(channels = 5)
        val kids = LiveStore(db, Dispatchers.Unconfined, clock) { "kids" }
        db.profiles().allow(ProfileAllowedGroupEntity("kids", "LIVE", "sport"))
        assertEquals(listOf("Sport"), kids.rail("s").map { it.name })
        // All channels holds only the allowed group's visible channel; channels without a group are out.
        assertEquals(listOf("s:x0"), kids.page(kids.open(ListSpec.All("s")), 0).map { it.key })
        assertEquals(0, kids.open(ListSpec.Ungrouped("s")).size)
        assertEquals(0, kids.open(ListSpec.Group("s", news)).size)
        assertEquals(1, kids.open(ListSpec.Group("s", sport)).size)
        assertTrue(kids.allowed("s:x0"))
        assertFalse(kids.allowed("s:c1"))
        // Favourites and recents are the profile's own, and narrowed.
        kids.toggleFavourite("s:c1")
        kids.toggleFavourite("s:x0")
        assertEquals(1, kids.favourites("s").ids.size)
        assertEquals(0, store.favourites("s").ids.size)
        // Numbers dial only allowed channels.
        assertEquals(-1, kids.dial(kids.open(ListSpec.All("s")), 12))
        // The unrestricted profile is untouched, and widening restores the rest.
        assertEquals(7, store.open(ListSpec.All("s")).size)
        db.profiles().disallow("kids", "LIVE", "sport")
        assertEquals(7, kids.open(ListSpec.All("s")).size)
        assertTrue(kids.allowed("s:c1"))
    }

    @Test
    fun aListWhoseSizeIsAMultipleOfThePageHasNoEmptyPage() = runBlocking {
        val (news, _) = seed(channels = 400)
        val list = store.open(ListSpec.Group("s", news))
        assertEquals(2, list.pageCount)
        assertEquals(200, store.page(list, 1).size)
        assertTrue(store.page(list, 2).isEmpty())
    }

    @Test
    fun dialOwnNumberThenPosition() = runBlocking {
        val (news, _) = seed()
        val list = store.open(ListSpec.Group("s", news))
        assertEquals(4, store.dial(list, 12))
        // Position 3 (channel 4) has no own number.
        assertEquals(2, store.dial(list, 3))
        // Position 4 has its own number 12: "5" finds nothing.
        assertEquals(-1, store.dial(list, 5))
        assertEquals(-1, store.dial(list, 9999))
        assertEquals(4, store.dial(store.open(ListSpec.All("s")), 12))
    }

    @Test
    fun favouritesAndRecentsAreOrderedAndBounded() = runBlocking {
        seed()
        assertTrue(store.toggleFavourite("s:c9"))
        assertTrue(store.toggleFavourite("s:c2"))
        assertTrue(store.toggleFavourite("other:c1"))
        val favourites = store.favourites("s")
        assertEquals(2, favourites.ids.size)
        val rows = store.page(store.open(favourites), 0)
        assertEquals(listOf("s:c2", "s:c9"), rows.map { it.key })
        assertFalse(store.toggleFavourite("s:c9"))
        assertEquals(setOf("s:c2", "other:c1"), store.favouriteKeys().first())

        for (i in 0 until 25) store.recordWatched("s:c$i")
        store.recordWatched("s:c3")
        val recents = store.page(store.open(store.recents("s")), 0).map { it.key }
        assertEquals(20, recents.size)
        assertEquals("s:c3", recents.first())
        assertEquals("s:c24", recents[1])
    }

    @Test
    fun schedulesShiftByTheOffsetAndCollapseDuplicates() = runBlocking {
        seed()
        val windowStart = GuideWindow.anchor(now)
        db.sourceStatus().upsert(
            SourceStatusEntity("s", "epg", "success", null, null, null, null, null, 0, 0, 1, epgSnapshot = 5, epgMaxDurationMs = 3 * 60 * min),
        )
        // Stored times are the provider's; the source's offset is +60 min.
        val base = windowStart - 60 * min
        val programmes = listOf(
            programme("e1", base, base + 30 * min, "Early"),
            programme("e1", base + 30 * min, base + 90 * min, "Evening news", categories = "News"),
            programme("e1", base + 30 * min, base + 60 * min, "Shorter duplicate"),
            programme("e1", base + 10 * 60 * min, base + 11 * 60 * min, "Far later"),
            programme("e2", base, base + 30 * min, "Other snapshot", snapshot = 4),
        )
        db.guideImport().insertProgrammes(programmes)
        val source = LiveSource("s", "Fixture", 60)
        val schedules = store.schedules(source, listOf("e1", "e2"), windowStart)
        val e1 = schedules.getValue("e1")
        assertEquals(listOf("Early", "Evening news"), e1.map { it.title })
        assertEquals(windowStart, e1.first().start)
        assertEquals("News", e1[1].firstCategory)
        assertFalse(schedules.containsKey("e2"))

        val found = store.search(ListSpec.Group("s", 1), source, "evening", windowStart)
        assertEquals(1, found.ids.size)
        val byName = store.search(ListSpec.All("s"), source, "aija", windowStart)
        assertEquals(1, byName.ids.size)
        assertEquals(0, store.search(ListSpec.All("s"), source, "100%", windowStart).ids.size)
    }

    private fun programme(epg: String, start: Long, stop: Long, title: String, categories: String? = null, snapshot: Long = 5) =
        ProgrammeEntity(
            sourceId = "s", snapshot = snapshot, epgId = epg, startAt = start, stopAt = stop, title = title, subtitle = null,
            description = null, categories = categories, programmeKey = "$epg$start",
        )
}
