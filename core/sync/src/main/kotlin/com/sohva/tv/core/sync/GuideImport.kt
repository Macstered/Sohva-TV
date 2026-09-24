package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.EpgChannelEntity
import com.sohva.tv.core.data.database.ProgrammeEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.collections.LongHashSet
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.text.Keys
import com.sohva.tv.core.net.http.ProviderRequest
import com.sohva.tv.core.net.xmltv.ProgrammeFilter
import com.sohva.tv.core.net.xmltv.XmlTvChannel
import com.sohva.tv.core.net.xmltv.XmlTvProgramme
import com.sohva.tv.core.net.xmltv.XmlTvReader
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The guide import (spec 10 §4.16): a new snapshot is written beside the active one, checked by
 * the guards, and handed back for activation; on any failure, cancellation included, it is removed
 * and the active guide is untouched.
 */
internal class GuideImport(private val env: ImportEnvironment) {
    class Result(val programmes: Int, val maxDurationMs: Long)

    private class Batch(val channels: List<EpgChannelEntity>, val programmes: List<ProgrammeEntity>)

    /** Writes snapshot [ImportJob.generation]; the caller activates it and then calls [sweep]. */
    suspend fun run(url: String, job: ImportJob, activeSnapshot: Long?): Result {
        val now = env.clock.wallMillis()
        val keep = withContext(env.dispatchers.bulkWrite) { GuideKeep.load(env.db, job.sourceId, now) }
        var seen = 0
        var kept = 0
        var maxDuration = 0L
        try {
            pipeline<Batch>(env.dispatchers, produce = { send ->
                env.http.get(url, ProviderRequest.SOURCE) { body ->
                    val reader = XmlTvReader(body, keep)
                    val keys = LongHashSet(16_384)
                    var channels = ArrayList<EpgChannelEntity>()
                    var programmes = ArrayList<ProgrammeEntity>()
                    var chars = 0L
                    while (true) {
                        when (val record = reader.next() ?: break) {
                            is XmlTvChannel -> channels += EpgChannelEntity(job.sourceId, job.generation, record.id, record.displayName, record.iconUrl)
                            is XmlTvProgramme -> {
                                // Exact duplicates collapse; the first copy in the feed wins (SRC-FR-84).
                                if (!keys.add(java.lang.Long.parseUnsignedLong(record.key, 16))) continue
                                programmes += programme(job, record)
                                chars += record.title.length + (record.subTitle?.length ?: 0) + (record.description?.length ?: 0) +
                                    (record.categories?.length ?: 0)
                                maxDuration = maxOf(maxDuration, record.stopMillis - record.startMillis)
                                kept++
                            }
                        }
                        if (programmes.size >= PROGRAMME_BATCH || chars >= BATCH_CHARS || channels.size >= CHANNEL_BATCH) {
                            send(Batch(channels, programmes))
                            channels = ArrayList()
                            programmes = ArrayList()
                            chars = 0
                        }
                    }
                    if (channels.isNotEmpty() || programmes.isNotEmpty()) send(Batch(channels, programmes))
                    seen = reader.seenProgrammes
                }
            }, consume = { batch ->
                env.pauseGate.awaitTurn(WorkOrigin.VIEWER)
                env.db.runInTransaction {
                    if (batch.channels.isNotEmpty()) env.db.guideImport().insertChannels(batch.channels)
                    if (batch.programmes.isNotEmpty()) env.db.guideImport().insertProgrammes(batch.programmes)
                }
                job.advance(batch.programmes.size)
            })
            if (seen == 0) throw AppException(AppError.EpgEmpty)
            if (kept == 0) throw AppException(AppError.EpgUnmatched)
            return Result(kept, maxDuration)
        } catch (e: Throwable) {
            withContext(NonCancellable) { sweep(job.sourceId, keep = activeSnapshot ?: NO_SNAPSHOT) }
            throw e
        }
    }

    /** Deletes every snapshot of the source but [keep], in short transactions on the writer thread. */
    suspend fun sweep(sourceId: String, keep: Long): Unit = withContext(env.dispatchers.bulkWrite) {
        val dao = env.db.guideImport()
        do {
            val deleted = env.db.runInTransaction<Int> { dao.sweepProgrammes(sourceId, keep, SWEEP_CHUNK) }
        } while (deleted > 0)
        do {
            val deleted = env.db.runInTransaction<Int> { dao.sweepChannels(sourceId, keep, SWEEP_CHUNK) }
        } while (deleted > 0)
    }

    private fun programme(job: ImportJob, p: XmlTvProgramme) = ProgrammeEntity(
        sourceId = job.sourceId, snapshot = job.generation, epgId = p.channelId, startAt = p.startMillis, stopAt = p.stopMillis,
        title = p.title, subtitle = p.subTitle, description = p.description, categories = p.categories, programmeKey = p.key,
    )

    companion object {
        /** ≤ 1,000 rows or ≤ 2 MB of text per transaction (spec 10 SRC-L-09). */
        const val PROGRAMME_BATCH = 1_000
        const val BATCH_CHARS = 1_000_000L
        const val CHANNEL_BATCH = 250
        const val SWEEP_CHUNK = 2_000
        const val NO_SNAPSHOT = -1L
    }
}

/**
 * Which programmes a guide import writes (spec 10 SRC-FR-83 with plan/09 A5): only for guide ids
 * the source's channels answer to (every id when none has one), starting less than 8 days ahead,
 * and in the past only as far as the viewer can go: catch-up channels back to the shorter of their
 * archive and the guide's 24.5-hour reach, other channels 3 hours. Ids are held as 64-bit hashes.
 */
internal class GuideKeep private constructor(
    private val now: Long,
    private val all: LongHashSet?,
    private val catchupOneDay: LongHashSet,
    private val catchupLonger: LongHashSet,
) : ProgrammeFilter {
    override fun keep(channelId: String, startMillis: Long, stopMillis: Long): Boolean {
        if (startMillis >= now + FUTURE_MS) return false
        if (all == null) return stopMillis > now - RECENT_PAST_MS
        val id = Keys.hash64(channelId)
        val past = when {
            id in catchupLonger -> GUIDE_REACH_MS
            id in catchupOneDay -> DAY_MS
            id in all -> RECENT_PAST_MS
            else -> return false
        }
        return stopMillis > now - past
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
        const val FUTURE_MS = 8 * DAY_MS
        const val RECENT_PAST_MS = 3 * HOUR_MS
        const val GUIDE_REACH_MS = 24 * HOUR_MS + HOUR_MS / 2
        private const val PAGE = 2_000

        /** Reads the source's guide ids in keyset pages (never the whole channel list at once). */
        fun load(db: SohvaDatabase, sourceId: String, now: Long): GuideKeep {
            val all = LongHashSet(4_096)
            val oneDay = LongHashSet(16)
            val longer = LongHashSet(1_024)
            var afterEpg = ""
            var afterId = 0L
            while (true) {
                val page = db.channelImport().epgIdsPage(sourceId, afterEpg, afterId, PAGE)
                if (page.isEmpty()) break
                for (row in page) {
                    val id = Keys.hash64(row.epgId)
                    all.add(id)
                    val days = row.catchupDays
                    if (row.catchupType != null && days != null) if (days >= 2) longer.add(id) else oneDay.add(id)
                }
                afterEpg = page.last().epgId
                afterId = page.last().id
            }
            return GuideKeep(now, all.takeIf { it.size > 0 }, oneDay, longer)
        }
    }
}
