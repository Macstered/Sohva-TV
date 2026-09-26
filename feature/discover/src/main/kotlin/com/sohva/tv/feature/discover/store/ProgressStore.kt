package com.sohva.tv.feature.discover.store

import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.Hashes
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer

/** A title's artwork kept with its progress, so Continue watching survives an expired cache (lesson 14). */
data class Artwork(val name: String, val poster: String?, val background: String?)

/**
 * What was watched (ADDON-FR-105): the catalog's original keys (FR-24), never a stream URL, header
 * or subtitle URL.
 */
data class WatchIdentity(val installation: String, val mediaType: String, val mediaId: String, val videoType: String, val videoId: String) {
    fun key(profile: String): String = Hashes.parts(profile, installation, mediaType, mediaId, videoType, videoId)

    override fun toString(): String = "WatchIdentity($mediaType)"
}

data class WatchEntry(
    val identity: WatchIdentity,
    val title: String,
    val positionMs: Long,
    val durationMs: Long?,
    val updatedAt: Long,
    val completed: Boolean,
    val artwork: Artwork,
) {
    /** Completed titles resume from the start (FR-107). */
    val resumeMs: Long get() = if (completed) 0 else positionMs

    val fraction: Float get() = durationMs?.takeIf { it > 0 }?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f

    override fun toString(): String = "WatchEntry($identity)"
}

/**
 * Encrypted watch progress per profile (spec 50 §4.14). One writer: playback sessions write
 * snapshots with increasing sequence numbers, and a snapshot from an older session or with a lower
 * sequence is ignored, so a late write never overwrites newer progress (FR-106). At most 200
 * entries per profile, pruned only when a new key is inserted (§9).
 */
class ProgressStore(private val dao: ProgressDao, private val cipher: DiscoverCipher, private val clock: Clock) {
    private val lock = Mutex()
    private val lastWrite = HashMap<String, Pair<Long, Long>>()
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Profile ids whose progress changed; Home recomputes its projection on these (and only when no player is up). */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    suspend fun recent(profile: String): List<WatchEntry> = dao.recent(profile, MAX_ENTRIES).mapNotNull { decode(it) }

    suspend fun get(profile: String, identity: WatchIdentity): WatchEntry? = dao.get(identity.key(profile))?.let(::decode)

    /** Home's Continue watching (spec 02 HOME-FR-17): the newest resumable entry per title; only these are decrypted. */
    suspend fun continueEntries(profile: String, limit: Int): List<WatchEntry> =
        dao.resumableTitles(profile, limit * 2).distinctBy { it.titleKey }.take(limit).mapNotNull(::decode)

    /**
     * One snapshot (FR-106, -107): the position clamped to a known duration, an unknown duration
     * keeps the last known one, completed at the end or from 95 %, durations over a week refused.
     */
    suspend fun save(
        profile: String,
        identity: WatchIdentity,
        title: String,
        artwork: Artwork,
        positionMs: Long,
        durationMs: Long?,
        ended: Boolean,
        session: Long,
        sequence: Long,
    ): Boolean = lock.withLock {
        val key = identity.key(profile)
        val last = lastWrite[key]
        if (last != null && (session < last.first || (session == last.first && sequence <= last.second))) return@withLock false
        if (durationMs != null && durationMs > WEEK_MS) throw AddonException(AddonFailure.INVALID_REQUEST)
        val old = dao.get(key)?.let(::decode)
        val duration = durationMs?.takeIf { it > 0 } ?: old?.durationMs
        val position = positionMs.coerceAtLeast(0).let { p -> duration?.let { p.coerceAtMost(it) } ?: p }
        val completed = ended || (duration != null && position >= duration * COMPLETE_PERCENT / 100)
        val entry = WatchEntry(identity, title, position, duration, clock.wallMillis(), completed, artwork)
        val titleKey = Hashes.parts(profile, identity.installation, identity.mediaType, identity.mediaId)
        dao.put(ProgressEntity(key, profile, encode(entry), entry.updatedAt, titleKey, !completed && position > 0))
        if (old == null) dao.prune(profile, MAX_ENTRIES)
        lastWrite[key] = session to sequence
        _changes.tryEmit(profile)
        true
    }

    /** FR-65: a found poster is written into an entry that still exists; nothing else changes. */
    suspend fun repairArtwork(profile: String, identity: WatchIdentity, artwork: Artwork) = lock.withLock {
        val key = identity.key(profile)
        val row = dao.get(key) ?: return@withLock
        val entry = decode(row) ?: return@withLock
        dao.put(row.copy(payload = encode(entry.copy(artwork = artwork))))
        _changes.tryEmit(profile)
    }

    suspend fun forget(profile: String, identity: WatchIdentity) = lock.withLock {
        dao.delete(identity.key(profile))
        _changes.tryEmit(profile)
    }

    suspend fun forgetProfile(profile: String) = lock.withLock {
        dao.deleteProfile(profile)
        _changes.tryEmit(profile)
    }

    private fun decode(row: ProgressEntity): WatchEntry? = runCatching {
        var installation = ""
        var mediaType = ""
        var mediaId = ""
        var videoType = ""
        var videoId = ""
        var title = ""
        var position = 0L
        var duration: Long? = null
        var updated = row.updatedAt
        var completed = false
        var name = ""
        var poster: String? = null
        var background: String? = null
        JsonReader.of(Buffer().writeUtf8(cipher.decrypt(row.payload))).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "installation" -> installation = r.nextString()
                    "mediaType" -> mediaType = r.nextString()
                    "mediaId" -> mediaId = r.nextString()
                    "videoType" -> videoType = r.nextString()
                    "videoId" -> videoId = r.nextString()
                    "title" -> title = r.nextString()
                    "position" -> position = r.nextLong()
                    "duration" -> duration = if (r.peek() == JsonReader.Token.NULL) r.nextNull<Long>() else r.nextLong()
                    "updated" -> updated = r.nextLong()
                    "completed" -> completed = r.nextBoolean()
                    "artwork" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "name" -> name = r.nextString()
                                "poster" -> poster = if (r.peek() == JsonReader.Token.NULL) r.nextNull<String>() else r.nextString()
                                "background" -> background = if (r.peek() == JsonReader.Token.NULL) r.nextNull<String>() else r.nextString()
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        WatchEntry(WatchIdentity(installation, mediaType, mediaId, videoType, videoId), title, position, duration, updated, completed, Artwork(name, poster, background))
    }.getOrNull()

    private fun encode(e: WatchEntry): String {
        val out = Buffer()
        JsonWriter.of(out).use { w ->
            w.beginObject()
            w.name("version").value(1)
            w.name("installation").value(e.identity.installation)
            w.name("mediaType").value(e.identity.mediaType)
            w.name("mediaId").value(e.identity.mediaId)
            w.name("videoType").value(e.identity.videoType)
            w.name("videoId").value(e.identity.videoId)
            w.name("title").value(e.title)
            w.name("position").value(e.positionMs)
            w.name("duration").value(e.durationMs)
            w.name("updated").value(e.updatedAt)
            w.name("completed").value(e.completed)
            w.name("artwork").beginObject().name("name").value(e.artwork.name).name("poster").value(e.artwork.poster)
                .name("background").value(e.artwork.background).endObject()
            w.endObject()
        }
        if (out.size > MAX_PAYLOAD) throw AddonException(AddonFailure.STORAGE)
        return cipher.encrypt(out.readUtf8())
    }

    companion object {
        const val MAX_ENTRIES: Int = 200
        private const val COMPLETE_PERCENT = 95
        private const val WEEK_MS = 7 * 24 * 60 * 60_000L
        private const val MAX_PAYLOAD = 128 * 1024L
    }
}
