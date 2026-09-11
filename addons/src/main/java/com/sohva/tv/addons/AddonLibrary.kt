package com.sohva.tv.addons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Provider-owned title identity, never an episode or a source URL. */
class AddonLibraryIdentity(val installationId: String, val media: AddonMediaKey) {
    fun key(profile: String) = addonCacheKey(listOf(profile, installationId, media.type, media.id))
    internal fun validate() {
        if (media.type !in setOf("movie", "series") || listOf(installationId, media.id).any { it.isBlank() || it.length > 2048 }) fail(AddonFailure.INVALID_REQUEST)
    }
    override fun toString() = "AddonLibraryIdentity([redacted])"
}

class AddonLibraryTitle(val identity: AddonLibraryIdentity, val artwork: AddonWatchArtwork, val releaseInfo: String?, val addedAtMillis: Long) {
    fun preview() = AddonMedia(identity.media, artwork.name, artwork.poster, "poster", artwork.background, null, releaseInfo, emptyList())
    override fun toString() = "AddonLibraryTitle([redacted])"
}
class AddonLibraryRow(val key: String, val profileId: String, val encryptedPayload: String, val addedAtMillis: Long) {
    override fun toString() = "AddonLibraryRow([redacted])"
}
interface AddonLibraryPersistence {
    suspend fun get(profile: String, key: String): AddonLibraryRow?
    suspend fun list(profile: String): List<AddonLibraryRow>
    /** Returns false at capacity. Must be atomic; never evict a saved title. */
    suspend fun put(row: AddonLibraryRow): Boolean
    suspend fun remove(profile: String, key: String)
}

/** Independent encrypted bookmarks. No addon client, history writes or provider synchronization. */
class AddonLibraryRepository(private val persistence: AddonLibraryPersistence, private val cipher: AddonSecretCipher,
    private val access: AddonManagementAccess, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    suspend fun get(profile: String, identity: AddonLibraryIdentity): AddonLibraryTitle? = operation(profile) {
        identity.validate(); persistence.get(profile, identity.key(profile))?.let { decode(profile, it) }
    }
    suspend fun list(profile: String): List<AddonLibraryTitle> = operation(profile) {
        val rows = persistence.list(profile)
        if (rows.size > MAX_TITLES) fail(AddonFailure.STORAGE)
        rows.map { decode(profile, it) }
    }
    suspend fun add(profile: String, identity: AddonLibraryIdentity, artwork: AddonWatchArtwork, releaseInfo: String? = null) = operation(profile) {
        identity.validate(); artwork.validate()
        if ((releaseInfo?.length ?: 0) > 256) fail(AddonFailure.INVALID_REQUEST)
        val old = persistence.get(profile, identity.key(profile))?.let { decode(profile, it) }
        val value = AddonLibraryTitle(identity, artwork, releaseInfo, old?.addedAtMillis ?: clock())
        val payload = cipher.encrypt(buildJsonObject {
            put("version", 1); put("installation", identity.installationId)
            put("type", identity.media.type); put("id", identity.media.id)
            put("name", artwork.name); artwork.poster?.let { put("poster", it) }; artwork.background?.let { put("background", it) }
            releaseInfo?.let { put("year", it) }; put("added", value.addedAtMillis)
        }.toString())
        if (payload.length > 128 * 1024) fail(AddonFailure.INVALID_REQUEST)
        authorize(profile)
        if (!persistence.put(AddonLibraryRow(identity.key(profile), profile, payload, value.addedAtMillis))) fail(AddonFailure.RESPONSE_TOO_LARGE)
    }
    suspend fun remove(profile: String, identity: AddonLibraryIdentity) = operation(profile) {
        identity.validate(); persistence.remove(profile, identity.key(profile))
    }
    private fun decode(profile: String, row: AddonLibraryRow): AddonLibraryTitle {
        if (row.profileId != profile || row.encryptedPayload.length > 128 * 1024) fail(AddonFailure.STORAGE)
        val data = Json.parseToJsonElement(cipher.decrypt(row.encryptedPayload)).jsonObject
        fun text(key: String) = data.getValue(key).jsonPrimitive.content
        if (data.getValue("version").jsonPrimitive.int != 1) fail(AddonFailure.STORAGE)
        val identity = AddonLibraryIdentity(text("installation"), AddonMediaKey(text("type"), text("id"))).also { it.validate() }
        if (identity.key(profile) != row.key) fail(AddonFailure.STORAGE)
        val art = AddonWatchArtwork(text("name"), data["poster"]?.jsonPrimitive?.contentOrNull, data["background"]?.jsonPrimitive?.contentOrNull).also { it.validate() }
        val year = data["year"]?.jsonPrimitive?.contentOrNull
        val added = data.getValue("added").jsonPrimitive.long
        if ((year?.length ?: 0) > 256 || added != row.addedAtMillis) fail(AddonFailure.STORAGE)
        return AddonLibraryTitle(identity, art, year, added)
    }
    private suspend fun authorize(profile: String) {
        validateProfile(profile)
        if (!access.allowed(profile)) fail(AddonFailure.ACCESS_DENIED)
    }
    private suspend fun <T> operation(profile: String, action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { authorize(profile); val result = action(); authorize(profile); result }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: AddonException) { throw AddonException(error.failure) }
            catch (_: Exception) { fail(AddonFailure.STORAGE) }
        }
    }
    companion object { const val MAX_TITLES = 1000 }
}
