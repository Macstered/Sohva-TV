package com.sohva.tv.addons.storage

import com.sohva.tv.addons.AddonEndpoint
import com.sohva.tv.addons.AddonException
import com.sohva.tv.addons.AddonFailure
import com.sohva.tv.addons.AddonManifestParser
import com.sohva.tv.addons.AddonSecretCipher
import com.sohva.tv.addons.AddonStore
import com.sohva.tv.addons.InstalledAddon
import com.sohva.tv.addons.fail
import com.sohva.tv.addons.validateProfile
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class EncryptedAddonStore(
    private val persistence: AddonPersistence,
    private val cipher: AddonSecretCipher,
    private val clock: () -> Long = System::currentTimeMillis,
) : AddonStore {
    private val dao get() = persistence.dao
    // Always read current DB rows; reuse decoding ONLY for byte-identical snapshots.
    // Thus disable/reorder/refresh/revocation checks never consult a stale list.
    private val decoded = LinkedHashMap<AddonInstallationEntity, InstalledAddon>(32, .75f, true)

    override suspend fun list(profileId: String): List<InstalledAddon> = guarded(profileId) {
        dao.list(profileId).map(::decode)
    }

    override suspend fun install(profileId: String, endpoint: AddonEndpoint, manifestJson: String): InstalledAddon = guarded(profileId) {
        validateManifest(manifestJson)
        persistence.transaction {
            val existing = dao.list(profileId)
            existing.firstOrNull { it.endpointFingerprint == endpoint.fingerprint }?.let { return@transaction decode(it) }
            if (existing.size >= 128) fail(AddonFailure.INVALID_REQUEST)
            val row = AddonInstallationEntity(UUID.randomUUID().toString(), profileId, endpoint.fingerprint,
                encode(endpoint, manifestJson), true, (existing.maxOfOrNull { it.position } ?: -1) + 1, 1, clock())
            dao.insert(row)
            decode(row)
        }
    }

    override suspend fun refresh(profileId: String, installationId: String, expectedRevision: Long, manifestJson: String): Boolean = guarded(profileId) {
        validateManifest(manifestJson)
        persistence.transaction {
            val old = dao.list(profileId).firstOrNull { it.installationId == installationId } ?: return@transaction false
            if (old.revision != expectedRevision) return@transaction false
            dao.refresh(profileId, installationId, expectedRevision, encode(decode(old).endpoint, manifestJson), clock()) == 1
        }
    }

    override suspend fun setEnabled(profileId: String, installationId: String, enabled: Boolean): Unit = guarded(profileId) {
        if (dao.setEnabled(profileId, installationId, enabled) != 1) fail(AddonFailure.NOT_FOUND)
    }
    override suspend fun remove(profileId: String, installationId: String): Unit = guarded(profileId) {
        if (dao.remove(profileId, installationId) != 1) fail(AddonFailure.NOT_FOUND)
        synchronized(decoded) { decoded.keys.removeAll { it.installationId == installationId && it.profileId == profileId } }
    }
    override suspend fun reorder(profileId: String, installationIds: List<String>): Unit = guarded(profileId) {
        persistence.transaction {
            val current = dao.list(profileId).map { it.installationId }
            if (installationIds.size != current.size || installationIds.toSet() != current.toSet()) fail(AddonFailure.CONFLICT)
            installationIds.forEachIndexed { index, id -> dao.setPosition(profileId, id, index) }
        }
    }

    private fun validateManifest(text: String) {
        if (AddonManifestParser.parse(text).configurationRequired) fail(AddonFailure.CONFIGURATION_REQUIRED)
    }
    private fun encode(endpoint: AddonEndpoint, manifest: String): String = cipher.encrypt(buildJsonObject {
        put("url", endpoint.exportConfiguredUrl())
        put("manifest", manifest)
    }.toString())

    private fun decode(row: AddonInstallationEntity): InstalledAddon = synchronized(decoded) {
        decoded[row]?.let { return@synchronized it }
        val payload = Json.parseToJsonElement(cipher.decrypt(row.encryptedPayload)).jsonObject
        // HTTP was explicitly approved at installation. Loading does not grant network access.
        val endpoint = AddonEndpoint.parse(payload.getValue("url").jsonPrimitive.content, allowInsecureHttp = true)
        if (endpoint.fingerprint != row.endpointFingerprint) fail(AddonFailure.STORAGE)
        val value = InstalledAddon(row.installationId, row.profileId, endpoint,
            AddonManifestParser.parse(payload.getValue("manifest").jsonPrimitive.content),
            row.enabled, row.position, row.revision, row.updatedAtMillis)
        decoded.keys.removeAll { it.installationId == row.installationId && it.profileId == row.profileId }
        decoded[row] = value
        while (decoded.size > 32 || decoded.keys.sumOf { it.encryptedPayload.length.toLong() } > 2_000_000L) decoded.remove(decoded.keys.first())
        value
    }

    private suspend fun <T> guarded(profileId: String, block: suspend () -> T): T {
        validateProfile(profileId)
        return try {
            withContext(Dispatchers.IO) { block() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AddonException) {
            throw error
        } catch (_: Exception) {
            fail(AddonFailure.STORAGE)
        }
    }
}
