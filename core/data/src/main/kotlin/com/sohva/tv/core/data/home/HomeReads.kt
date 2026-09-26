package com.sohva.tv.core.data.home

import com.sohva.tv.core.data.database.DEFAULT_PROFILE
import com.sohva.tv.core.data.database.SohvaDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A programme with its shown times (the source's guide offset applied). */
data class NowProgramme(val id: Long, val title: String, val subtitle: String?, val startAt: Long, val stopAt: Long)

/** A Recently watched channels card (spec 02 HOME-FR-34), with the programme on at [HomeReads.recentChannels]'s moment. */
data class RecentChannel(val id: Long, val key: String, val name: String, val logoUrl: String?, val number: Int?, val programme: NowProgramme?)

/** Home's own reads; Continue watching is [com.sohva.tv.core.data.vod.ProgressStore.continueWatching]. */
class HomeReads(private val db: SohvaDatabase, private val io: CoroutineDispatcher, private val profile: () -> String = { DEFAULT_PROFILE }) {
    /**
     * The profile's recent channels that still resolve (visible, enabled source), newest first,
     * at most [RECENT_CARDS], each with the programme on at [now] (HOME-FR-34, -35).
     */
    suspend fun recentChannels(now: Long): List<RecentChannel> = withContext(io) {
        val keys = db.viewer().recentKeys(profile())
        if (keys.isEmpty()) return@withContext emptyList()
        val byKey = db.home().channelsOfKeys(keys, profile()).associateBy { it.key }
        keys.mapNotNull(byKey::get).take(RECENT_CARDS).map { c ->
            val offset = c.offsetMinutes * MINUTE_MS
            val programme = c.epgId?.takeIf { it.isNotBlank() }
                ?.let { db.home().programmeAt(c.sourceId, it, now - offset) }
                ?.takeIf { it.stopAt + offset > now }
                ?.let { NowProgramme(it.id, it.title, it.subtitle, it.startAt + offset, it.stopAt + offset) }
            RecentChannel(c.id, c.key, c.name, c.logoUrl, c.number, programme)
        }
    }

    companion object {
        const val RECENT_CARDS: Int = 6
        private const val MINUTE_MS = 60_000L
    }
}
