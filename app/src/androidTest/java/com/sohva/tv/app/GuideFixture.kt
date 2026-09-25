package com.sohva.tv.app

import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SearchIndex
import com.sohva.tv.core.data.database.SearchTable
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.data.live.ChannelEffects
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.text.SortNames

/**
 * A synthetic guide written straight into the app's database (fictional names, reserved
 * addresses): [sources] sources, each with [groups] groups of [perGroup] channels, numbered
 * from 1, and half-hour-ish programmes from 13 hours back to 9 hours ahead of now, so fixtures
 * never expire (AGENTS.md §8 "Anchor test dates to now").
 */
object GuideFixture {
    const val MIN: Long = GuideWindow.MINUTE_MS

    fun seed(
        graph: AppGraph,
        sources: Int = 1,
        groups: Int = 3,
        perGroup: Int = 40,
        stream: (sourceId: String, index: Int) -> String = { _, i -> "http://192.0.2.10/live/$i.ts" },
        withGuide: Boolean = true,
        userAgent: String? = null,
        referrer: String? = null,
        /** Programmes for the first this many channels of each source only (owner-scale fixtures). */
        guideFor: Int = Int.MAX_VALUE,
        /** One sealed address for every channel, instead of sealing each (owner-scale fixtures). */
        sealedStream: String? = null,
        /** The playlist's catch-up type for each channel (null = none), with [catchupDays] of archive. */
        catchupType: (index: Int) -> String? = { null },
        catchupDays: Int = 7,
    ) {
        val db = graph.data.database
        val now = System.currentTimeMillis()
        // The source row goes last: the guide opens once a source with visible channels exists.
        run {
            for (s in 0 until sources) {
                val sourceId = "fixture-$s"
                val channels = ArrayList<ChannelEntity>()
                val programmes = ArrayList<ProgrammeEntity>()
                var index = 0
                for (g in 0 until groups) {
                    val groupId = db.groupImport().insert(
                        ContentGroupEntity(
                            sourceId = sourceId, room = "LIVE", groupKey = "g$g", name = GROUP_NAMES[g % GROUP_NAMES.size],
                            providerOrder = g, itemCount = perGroup, shown = true, position = g, sortMode = null,
                        ),
                    )
                    for (c in 0 until perGroup) {
                        val name = "${CHANNEL_NAMES[index % CHANNEL_NAMES.size]} ${index + 1}"
                        val epg = "e$s-$index"
                        // Ranked as an import writes them (GUIDE-13): the group's block, then the playlist order.
                        channels += ChannelEntity(
                            key = "$sourceId:c$index", sourceId = sourceId, groupId = groupId, name = name, sortName = SortNames.of(name),
                            providerName = name, providerGroupId = groupId, providerLogoUrl = null,
                            tvgId = epg, epgId = epg, logoUrl = null, streamUrlEnc = sealedStream ?: graph.data.cipher.encrypt(stream(sourceId, index)),
                            userAgent = userAgent, referrer = referrer, playlistOrder = index, providerNumber = index + 1, number = index + 1,
                            displayRank = ChannelEffects.rank(ChannelEffects.providerBlock(g), ChannelEffects.playlistRank(index)), visible = true, catchupType = catchupType(index), catchupSource = null,
                            catchupDays = catchupType(index)?.let { catchupDays },
                            catchupTz = null, xtreamStreamId = null, contentHash = 1, generation = 1,
                        )
                        if (withGuide && index < guideFor) programmes += schedule(sourceId, epg, index, now)
                        index++
                    }
                }
                channels.chunked(2_000).forEach { db.channelImport().insert(it) }
                if (withGuide) {
                    programmes.chunked(2_000).forEach { db.guideImport().insertProgrammes(it) }
                    db.sourceStatus().upsert(
                        SourceStatusEntity(sourceId, "epg", "success", now, now, null, null, null, programmes.size, 0, 1, 1, 90 * MIN),
                    )
                }
                db.sourceStatus().upsert(SourceStatusEntity(sourceId, "playlist", "success", now, now, null, null, null, channels.size, 0, 1, null, null))
                kotlinx.coroutines.runBlocking {
                    db.sources().upsert(SourceEntity(sourceId, "Fixture ${'A' + s}", "M3U", true, sources - s, 1, "LIVE_TV", 0, now, now))
                }
            }
            // As the playlist and guide imports end: the new rows into Search's index.
            SearchIndex.catchUp(db, SearchTable.CHANNEL, SearchTable.PROGRAMME)
        }
    }

    private fun schedule(sourceId: String, epg: String, channel: Int, now: Long): List<ProgrammeEntity> {
        val out = ArrayList<ProgrammeEntity>()
        var t = (now / (30 * MIN)) * (30 * MIN) - 13 * 60 * MIN + (channel % 3) * 10 * MIN
        var n = 0
        while (t < now + 9 * 60 * MIN) {
            val length = LENGTHS[(channel + n) % LENGTHS.size] * MIN
            out += ProgrammeEntity(
                sourceId = sourceId, snapshot = 1, epgId = epg, startAt = t, stopAt = t + length,
                title = TITLES[(channel * 7 + n) % TITLES.size], subtitle = null,
                description = if (n % 2 == 0) "A fictional programme for the guide fixture." else null,
                categories = CATEGORIES[(channel + n) % CATEGORIES.size], programmeKey = "%016x".format((channel.toLong() shl 20) + n),
            )
            t += length
            n++
        }
        return out
    }

    private val GROUP_NAMES = listOf("News", "Sport", "Kids", "Film", "Music")
    private val CHANNEL_NAMES = listOf("Northstar", "Meridian", "Pulse HD", "Summit", "Harbor FHD", "Lumen", "Cobalt", "Ember")
    private val TITLES = listOf("Morning Signal", "Harbor Routes", "North Horizon", "Glass Kitchen", "Silent Weather", "Hidden Lighthouse", "Studio Eleven")
    private val CATEGORIES = listOf("News", "Sport", "Kids", "Film", "Music", null)
    private val LENGTHS = longArrayOf(30, 45, 60, 25, 90)
}
