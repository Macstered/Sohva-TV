package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.source.ImportRoute
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.text.Keys
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.net.http.ProviderRequest
import com.sohva.tv.core.net.m3u.M3uEntry
import com.sohva.tv.core.net.m3u.M3uKind
import com.sohva.tv.core.net.m3u.M3uReader
import com.sohva.tv.core.net.xtream.XtreamClient
import com.sohva.tv.core.net.xtream.XtreamList
import com.sohva.tv.core.net.xtream.XtreamLiveStream
import com.sohva.tv.core.net.xtream.XtreamUrls
import com.sohva.tv.core.sync.diff.ContentHash
import com.sohva.tv.core.sync.diff.GroupRef
import com.sohva.tv.core.sync.diff.GroupResolver
import com.sohva.tv.core.sync.diff.KeyedDiff
import com.sohva.tv.core.sync.diff.Room
import com.sohva.tv.core.sync.diff.Row
import com.sohva.tv.core.sync.diff.Tables
import kotlinx.coroutines.withContext

/**
 * The playlist import (spec 10 §4.15): live channels, written as a diff in batches of 250 by key.
 * An M3U playlist is streamed through the reader; an Xtream account through `get_live_streams`.
 * Rows the playlist no longer has are deleted only after a complete parse that found entries.
 */
internal class LiveImport(private val env: ImportEnvironment) {
    class Result(val channels: Int, val headerGuideUrl: String?)

    private class Target(val diff: KeyedDiff<ChannelEntity>, val groups: GroupResolver, val storedBefore: Int)

    suspend fun m3u(route: ImportRoute.M3u, scope: ImportScope, job: ImportJob): Result {
        val target = open(job.sourceId)
        var parsed = 0
        var headerGuide: String? = null
        pipeline<List<Row<ChannelEntity>>>(env.dispatchers, produce = { send ->
            env.http.get(route.playlistUrl, ProviderRequest.SOURCE) { body ->
                val reader = M3uReader(body)
                var batch = ArrayList<Row<ChannelEntity>>(BATCH)
                while (true) {
                    val entry = reader.next() ?: break
                    parsed++
                    // Scope BOTH leaves films and series to the catalogue import (SRC-FR-82).
                    if (scope == ImportScope.BOTH && entry.kind != M3uKind.LIVE) continue
                    batch += m3uRow(job, entry)
                    if (batch.size == BATCH) {
                        send(batch)
                        batch = ArrayList(BATCH)
                    }
                }
                if (batch.isNotEmpty()) send(batch)
                headerGuide = reader.headerGuideUrl
            }
        }, consume = { rows -> write(target, rows, job) })
        if (parsed == 0) throw AppException(AppError.PlaylistEmpty)
        return Result(finish(target, job.sourceId), headerGuide)
    }

    suspend fun xtream(route: ImportRoute.Xtream, job: ImportJob): Result {
        val client = XtreamClient(env.http, route.account)
        val urls = XtreamUrls(route.account)
        val zone = client.accountInfo().serverTimeZone
        val categories = client.categories(XtreamList.LIVE).associate { it.id to it.name }
        val target = open(job.sourceId)
        var streams = 0
        pipeline<List<Row<ChannelEntity>>>(env.dispatchers, produce = { send ->
            var batch = ArrayList<Row<ChannelEntity>>(BATCH)
            client.liveStreams { stream ->
                batch += xtreamRow(job, stream, streams, categories, urls, zone)
                streams++
                if (batch.size == BATCH) {
                    send(batch)
                    batch = ArrayList(BATCH)
                }
            }
            if (batch.isNotEmpty()) send(batch)
        }, consume = { rows -> write(target, rows, job) })
        // An empty answer never replaces channels a source already has (SRC-FR-80 rebuild rule).
        if (streams == 0 && target.storedBefore > 0) throw AppException(AppError.PlaylistEmpty)
        return Result(finish(target, job.sourceId), null)
    }

    private suspend fun open(sourceId: String): Target = withContext(env.dispatchers.bulkWrite) {
        val groups = GroupResolver(env.db.groupImport(), sourceId, Room.LIVE)
        val range = KeyRange.channels(sourceId)
        Target(KeyedDiff(Tables.channels(env.db, sourceId), groups), groups, env.db.channelImport().count(range.from, range.until))
    }

    private suspend fun write(target: Target, rows: List<Row<ChannelEntity>>, job: ImportJob) {
        // A running import keeps going during playback but leaves the decoder room (SRC-L-25).
        env.pauseGate.awaitTurn(WorkOrigin.VIEWER)
        env.db.runInTransaction { target.diff.write(rows) }
        job.advance(rows.size)
    }

    private suspend fun finish(target: Target, sourceId: String): Int = withContext(env.dispatchers.bulkWrite) {
        target.diff.sweep(env.db)
        env.db.runInTransaction { target.groups.finish(complete = true) }
        val range = KeyRange.channels(sourceId)
        env.db.channelImport().count(range.from, range.until)
    }

    private fun m3uRow(job: ImportJob, e: M3uEntry): Row<ChannelEntity> {
        val sourceId = job.sourceId
        val key = Keys.globalChannelId(sourceId, e.id)
        val name = e.name ?: env.names.channel(e.index + 1)
        val group = e.group?.let { GroupRef(Keys.groupKey(null, it), it) }
        val hash = ContentHash().add(name).add(e.tvgId).add(group?.key).add(e.logoUrl).add(e.streamUrl).add(e.userAgent)
            .add(e.referrer).add(e.index).add(e.channelNumber).add(e.catchupType).add(e.catchupSource).add(e.catchupDays).value()
        return Row(key, hash, group) { id, groupId ->
            ChannelEntity(
                id = id, key = key, sourceId = sourceId, groupId = groupId, name = name, sortName = SortNames.of(name),
                providerName = name, providerGroupId = groupId, providerLogoUrl = e.logoUrl, tvgId = e.tvgId, epgId = e.tvgId, logoUrl = e.logoUrl, streamUrlEnc = env.sealer.seal(e.streamUrl),
                userAgent = e.userAgent, referrer = e.referrer, playlistOrder = e.index, providerNumber = e.channelNumber,
                number = e.channelNumber, displayRank = e.index.toLong() * RANK_STEP, visible = true, catchupType = e.catchupType,
                catchupSource = e.catchupSource, catchupDays = e.catchupDays, catchupTz = null, xtreamStreamId = null,
                contentHash = hash, generation = job.generation,
            )
        }
    }

    private fun xtreamRow(
        job: ImportJob,
        s: XtreamLiveStream,
        index: Int,
        categories: Map<String, String>,
        urls: XtreamUrls,
        zone: String?,
    ): Row<ChannelEntity> {
        val sourceId = job.sourceId
        val key = Keys.globalChannelId(sourceId, Keys.xtreamChannelLocalId(s.streamId))
        val group = s.categoryId?.let { id -> categories[id]?.let { GroupRef(Keys.groupKey(id, it), it) } }
        val order = s.num ?: index
        val number = s.num?.takeIf { it > 0 }
        val catchupType = if (s.catchupDays != null) "xtream" else null
        val address = urls.live(s.streamId, s.extension)
        val hash = ContentHash().add(s.name).add(s.epgChannelId).add(group?.key).add(group?.name).add(s.iconUrl).add(address)
            .add(order).add(number).add(s.catchupDays).add(zone).value()
        return Row(key, hash, group) { id, groupId ->
            ChannelEntity(
                id = id, key = key, sourceId = sourceId, groupId = groupId, name = s.name, sortName = SortNames.of(s.name),
                providerName = s.name, providerGroupId = groupId, providerLogoUrl = s.iconUrl, tvgId = s.epgChannelId, epgId = s.epgChannelId, logoUrl = s.iconUrl, streamUrlEnc = env.sealer.seal(address),
                userAgent = null, referrer = null, playlistOrder = order, providerNumber = number, number = number,
                displayRank = order.toLong() * RANK_STEP, visible = true, catchupType = catchupType, catchupSource = null,
                catchupDays = s.catchupDays, catchupTz = zone, xtreamStreamId = s.streamId, contentHash = hash,
                generation = job.generation,
            )
        }
    }

    companion object {
        const val BATCH = 250

        /** Sparse ranks, so a later move renumbers one gap, not the source (plan/04 §15.3). */
        const val RANK_STEP = 1_024L
    }
}
