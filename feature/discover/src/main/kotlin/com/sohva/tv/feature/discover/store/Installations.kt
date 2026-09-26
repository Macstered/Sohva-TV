package com.sohva.tv.feature.discover.store

import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.discover.protocol.AddonEndpoint
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.AddonManifest
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer

/** Who may use Discover (ADDON-FR-02): the build allows addons, the profile is the active one and not restricted. */
interface DiscoverAccess {
    fun activeProfile(): String

    suspend fun allowed(profile: String): Boolean
}

/** Encryption for Discover payloads and cache files: the app's envelope cipher (decision "Discover storage and key"). */
interface DiscoverCipher {
    fun encrypt(plaintext: String): String

    fun decrypt(value: String): String

    fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray

    fun open(sealed: ByteArray, aad: ByteArray): ByteArray
}

/** An installed addon (ADDON-FR-29). The endpoint is a secret: [toString] shows only the id and revision. */
class Installation(
    val id: String,
    val profileId: String,
    val endpoint: AddonEndpoint,
    val manifest: AddonManifest,
    val enabled: Boolean,
    val position: Int,
    val revision: Long,
) {
    val name: String get() = manifest.name

    override fun toString(): String = "Installation($id, rev=$revision)"
}

/**
 * The installation rows of each profile (spec 50 §4.6), decrypted and parsed once into an
 * in-memory snapshot that only this store's own writes invalidate (§9 rebuild rule), so the
 * access checks around every request cost no I/O. Writes are serialised.
 */
class InstallationStore(private val dao: InstallationDao, private val cipher: DiscoverCipher, private val clock: Clock) {
    private val lock = Mutex()
    private val snapshots = HashMap<String, List<Installation>>()

    suspend fun list(profile: String): List<Installation> = lock.withLock { snapshot(profile) }

    suspend fun find(profile: String, id: String): Installation? = list(profile).firstOrNull { it.id == id }

    /** FR-29: an existing fingerprint returns the installation unchanged; else a new row at the end. */
    suspend fun add(profile: String, endpoint: AddonEndpoint, manifest: AddonManifest): Installation = lock.withLock {
        snapshot(profile).firstOrNull { it.endpoint.fingerprint == endpoint.fingerprint }?.let { return@withLock it }
        if (snapshot(profile).size >= MAX_PER_PROFILE) throw AddonException(AddonFailure.INVALID_REQUEST)
        val row = InstallationEntity(
            UUID.randomUUID().toString(), profile, endpoint.fingerprint, payload(endpoint, manifest), enabled = true,
            position = dao.lastPosition(profile) + 1, revision = 1, updatedAt = clock.wallMillis(),
        )
        try {
            dao.insert(row)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // A concurrent install of the same URL won: one identity (FR-29).
            snapshots.remove(profile)
            return@withLock snapshot(profile).first { it.endpoint.fingerprint == endpoint.fingerprint }
        }
        snapshots.remove(profile)
        snapshot(profile).first { it.id == row.installationId }
    }

    /**
     * Beta 23's installation under its own id (decision A1): progress, Library and catalog keys
     * hash that id, so they stay valid. False when the id or the URL is already there.
     */
    suspend fun restore(profile: String, id: String, endpoint: AddonEndpoint, manifest: AddonManifest, enabled: Boolean, position: Int): Boolean = lock.withLock {
        if (snapshot(profile).any { it.id == id || it.endpoint.fingerprint == endpoint.fingerprint }) return@withLock false
        val row = InstallationEntity(id, profile, endpoint.fingerprint, payload(endpoint, manifest), enabled, position, revision = 1, updatedAt = clock.wallMillis())
        try {
            dao.insert(row)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            return@withLock false
        } finally {
            snapshots.remove(profile)
        }
        true
    }

    /**
     * Writes [change] only if [of] still has the revision read before (FR-30, -31): a stale answer
     * never overwrites a newer change or resurrects a removed addon.
     */
    suspend fun update(of: Installation, manifest: AddonManifest = of.manifest, enabled: Boolean = of.enabled, position: Int = of.position): Installation =
        lock.withLock {
            val written = dao.update(of.id, of.revision, payload(of.endpoint, manifest), enabled, position, clock.wallMillis())
            snapshots.remove(of.profileId)
            if (written == 0) throw AddonException(AddonFailure.CONFLICT)
            snapshot(of.profileId).first { it.id == of.id }
        }

    /** Several position writes as one change of the profile's order (FR-31, -32). */
    suspend fun reposition(profile: String, order: List<Installation>) = lock.withLock {
        try {
            order.forEachIndexed { i, inst ->
                if (inst.position != i && dao.update(inst.id, inst.revision, payload(inst.endpoint, inst.manifest), inst.enabled, i, clock.wallMillis()) == 0) {
                    throw AddonException(AddonFailure.CONFLICT)
                }
            }
        } finally {
            snapshots.remove(profile)
        }
    }

    suspend fun remove(profile: String, id: String) = lock.withLock {
        val removed = dao.delete(id, profile)
        snapshots.remove(profile)
        if (removed == 0) throw AddonException(AddonFailure.NOT_FOUND)
    }

    suspend fun forget(profile: String) = lock.withLock {
        dao.deleteProfile(profile)
        snapshots.remove(profile)
    }

    /** Drops cached snapshots (the importer and tests write around the store). */
    suspend fun invalidate() = lock.withLock { snapshots.clear() }

    private suspend fun snapshot(profile: String): List<Installation> = snapshots.getOrPut(profile) {
        dao.of(profile).mapNotNull { row ->
            // A row that no longer decrypts or parses is skipped, never shown half-read. Stored URLs
            // were checked when installed, so they are read back under no stricter rule.
            runCatching {
                val (url, manifest) = parsePayload(cipher.decrypt(row.payload))
                Installation(row.installationId, profile, AddonEndpoint.parse(url, baseAllowed = false, allowHttp = true), manifest, row.enabled, row.position, row.revision)
            }.getOrNull()
        }
    }

    private fun payload(endpoint: AddonEndpoint, manifest: AddonManifest): String {
        val out = Buffer()
        JsonWriter.of(out).use { w ->
            w.beginObject()
            w.name("url").value(endpoint.manifestUrl.toString())
            w.name("manifest")
            ManifestJson.write(w, manifest)
            w.endObject()
        }
        return cipher.encrypt(out.readUtf8())
    }

    private fun parsePayload(json: String): Pair<String, AddonManifest> {
        var url: String? = null
        var manifest: AddonManifest? = null
        JsonReader.of(Buffer().writeUtf8(json)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "url" -> url = r.nextString()
                    "manifest" -> manifest = ManifestJson.read(r)
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        return (url ?: error("no url")) to (manifest ?: error("no manifest"))
    }

    private companion object {
        const val MAX_PER_PROFILE = 128
    }
}
