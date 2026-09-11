package com.sohva.tv.addons

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AddonWatchIdentity(val metadataInstallationId: String, val media: AddonMediaKey, val video: AddonMediaKey) {
    internal fun key(profileId: String) = addonCacheKey(listOf(profileId, metadataInstallationId, media.type, media.id, video.type, video.id))
    override fun toString() = "AddonWatchIdentity([redacted])"
}
/** A durable display snapshot, not a stored metadata response or playback URL. */
class AddonWatchArtwork(val name: String, val poster: String?, val background: String? = null) {
    internal fun validate() {
        if (name.isBlank() || name.length > 2048 || listOfNotNull(poster, background).any {
            it.length > 8192 || it.toHttpUrlOrNull()?.let { url -> url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null } != false
        }) fail(AddonFailure.INVALID_REQUEST)
    }
    override fun toString() = "AddonWatchArtwork([redacted])"
    companion object {
        fun from(media: AddonMedia, fallback: AddonMedia? = null) = AddonWatchArtwork(media.name.take(2048), media.poster ?: fallback?.poster, media.background ?: fallback?.background)
    }
}
class AddonWatchProgress(val identity: AddonWatchIdentity, val title: String, val positionMillis: Long, val durationMillis: Long, val updatedAtMillis: Long, val completed: Boolean,
    val artwork: AddonWatchArtwork? = null) {
    val resumePositionMillis: Long get() = if (completed) 0 else positionMillis
    override fun toString() = "AddonWatchProgress([redacted])"
}
class AddonProgressSession internal constructor(internal val profileId: String, internal val identity: AddonWatchIdentity, internal val title: String, internal val token: String,
    internal val artwork: AddonWatchArtwork?) {
    override fun toString() = "AddonProgressSession([redacted])"
}
class AddonProgressRow(val key: String, val profileId: String, val encryptedPayload: String, val updatedAtMillis: Long) {
    override fun toString() = "AddonProgressRow([redacted])"
}
interface AddonProgressPersistence {
    suspend fun get(profileId: String, key: String): AddonProgressRow?
    suspend fun recent(profileId: String): List<AddonProgressRow>
    /** Atomic upsert/prune to 200 rows per profile. */
    suspend fun put(row: AddonProgressRow)
    suspend fun remove(profileId: String, key: String)
}

/** Independent encrypted history. Never stores source URLs, header values or subtitle URLs. */
class AddonProgressRepository(
    private val persistence: AddonProgressPersistence, private val cipher: AddonSecretCipher,
    private val access: AddonManagementAccess, private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, Pair<String, Long>>()
    suspend fun begin(profileId: String, identity: AddonWatchIdentity, title: String, artwork: AddonWatchArtwork? = null): AddonProgressSession = operation(profileId) {
        validate(identity)
        artwork?.validate()
        if (title.isBlank() || title.length > 2048) fail(AddonFailure.INVALID_REQUEST)
        val token = UUID.randomUUID().toString()
        sessions[identity.key(profileId)] = token to -1L
        if (sessions.size > 512) sessions.remove(sessions.keys.first())
        AddonProgressSession(profileId, identity, title, token, artwork)
    }
    suspend fun get(profileId: String, identity: AddonWatchIdentity): AddonWatchProgress? = operation(profileId) {
        validate(identity)
        persistence.get(profileId, identity.key(profileId))?.let { decode(it, profileId) }
    }
    suspend fun recent(profileId: String): List<AddonWatchProgress> = operation(profileId) {
        persistence.recent(profileId).map { decode(it, profileId) }
    }
    suspend fun save(session: AddonProgressSession, sequence: Long, positionMillis: Long, durationMillis: Long, ended: Boolean = false) = operation(session.profileId) {
        val key = session.identity.key(session.profileId)
        val active = sessions[key]
        if (active?.first != session.token || sequence <= active.second) return@operation
        if (positionMillis < 0 || positionMillis > MAX_DURATION || durationMillis > MAX_DURATION) fail(AddonFailure.INVALID_REQUEST)
        val old = persistence.get(session.profileId, key)?.let { decode(it, session.profileId) }
        val duration = durationMillis.takeIf { it > 0 } ?: old?.durationMillis ?: 0
        val position = if (duration > 0) positionMillis.coerceAtMost(duration) else positionMillis
        val completed = ended || (duration > 0 && position >= duration * 0.95)
        val progress = AddonWatchProgress(session.identity, session.title, position, duration, clock(), completed, mergeArtwork(session.artwork, old?.artwork))
        persistence.put(AddonProgressRow(key, session.profileId, cipher.encrypt(encode(progress)), progress.updatedAtMillis))
        sessions[key] = session.token to sequence
    }
    /** Repair artwork on existing history only. Never create progress, mark watched or reorder it. */
    suspend fun updateArtwork(profileId: String, identity: AddonWatchIdentity, artwork: AddonWatchArtwork) = operation(profileId) {
        validate(identity); artwork.validate()
        val key = identity.key(profileId)
        val old = persistence.get(profileId, key)?.let { decode(it, profileId) } ?: return@operation
        val value = AddonWatchProgress(old.identity, old.title, old.positionMillis, old.durationMillis, old.updatedAtMillis, old.completed, mergeArtwork(artwork, old.artwork))
        persistence.put(AddonProgressRow(key, profileId, cipher.encrypt(encode(value)), old.updatedAtMillis))
    }
    suspend fun remove(profileId: String, identity: AddonWatchIdentity) = operation(profileId) {
        val key = identity.key(profileId)
        sessions.remove(key)
        persistence.remove(profileId, key)
    }
    private fun validate(identity: AddonWatchIdentity) {
        if (listOf(identity.metadataInstallationId, identity.media.type, identity.media.id, identity.video.type, identity.video.id).any { it.isBlank() || it.length > 2048 }) fail(AddonFailure.INVALID_REQUEST)
    }
    private fun encode(value: AddonWatchProgress) = buildJsonObject {
        put("version", 1); put("installation", value.identity.metadataInstallationId)
        put("mediaType", value.identity.media.type); put("mediaId", value.identity.media.id)
        put("videoType", value.identity.video.type); put("videoId", value.identity.video.id)
        put("title", value.title); put("position", value.positionMillis); put("duration", value.durationMillis)
        put("updated", value.updatedAtMillis); put("completed", value.completed)
        value.artwork?.let { art -> put("artwork", buildJsonObject {
            put("name", art.name); art.poster?.let { put("poster", it) }; art.background?.let { put("background", it) }
        }) }
    }.toString()
    private fun decode(row: AddonProgressRow, profileId: String): AddonWatchProgress {
        if (row.profileId != profileId || row.encryptedPayload.length > 128 * 1024) fail(AddonFailure.STORAGE)
        val obj = Json.parseToJsonElement(cipher.decrypt(row.encryptedPayload)).jsonObject
        fun text(key: String) = obj.getValue(key).jsonPrimitive.content
        fun number(key: String) = obj.getValue(key).jsonPrimitive.long
        if (number("version") != 1L) fail(AddonFailure.STORAGE)
        val identity = AddonWatchIdentity(text("installation"), AddonMediaKey(text("mediaType"), text("mediaId")), AddonMediaKey(text("videoType"), text("videoId")))
        validate(identity)
        if (identity.key(profileId) != row.key) fail(AddonFailure.STORAGE)
        val artwork = (obj["artwork"] as? JsonObject)?.let { art ->
            AddonWatchArtwork(art.getValue("name").jsonPrimitive.content, art["poster"]?.jsonPrimitive?.contentOrNull, art["background"]?.jsonPrimitive?.contentOrNull).also { it.validate() }
        }
        return AddonWatchProgress(identity, text("title"), number("position"), number("duration"), number("updated"), obj.getValue("completed").jsonPrimitive.boolean, artwork)
    }
    private suspend fun <T> operation(profileId: String, action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                validateProfile(profileId)
                if (!access.allowed(profileId)) fail(AddonFailure.ACCESS_DENIED)
                action()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) { throw AddonException(error.failure) }
            catch (_: Exception) { fail(AddonFailure.STORAGE) }
        }
    }
    private fun mergeArtwork(new: AddonWatchArtwork?, old: AddonWatchArtwork?): AddonWatchArtwork? =
        new?.let { AddonWatchArtwork(it.name, it.poster ?: old?.poster, it.background ?: old?.background) } ?: old
    companion object { private const val MAX_DURATION = 7 * 24 * 60 * 60 * 1000L }
}
