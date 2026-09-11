package com.sohva.tv.addons

import com.sohva.tv.addons.storage.AddonDao
import com.sohva.tv.addons.storage.AddonInstallationEntity
import com.sohva.tv.addons.storage.AddonPersistence
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fixture for store logic; does not pretend to validate Android's Room runtime. */
internal class MemoryAddonPersistence : AddonPersistence, AddonDao {
    val rows = mutableMapOf<String, AddonInstallationEntity>()
    private val mutex = Mutex()
    override val dao: AddonDao get() = this
    override suspend fun <T> transaction(block: suspend () -> T): T = mutex.withLock {
        val before = rows.toMap()
        try { block() } catch (error: Throwable) {
            rows.clear()
            rows.putAll(before)
            throw error
        }
    }
    override suspend fun list(profileId: String) = rows.values.filter { it.profileId == profileId }.sortedBy { it.position }
    override suspend fun insert(entity: AddonInstallationEntity) {
        check(rows.values.none { it.installationId == entity.installationId || (it.profileId == entity.profileId && it.endpointFingerprint == entity.endpointFingerprint) })
        rows[entity.installationId] = entity
    }
    override suspend fun refresh(profileId: String, id: String, revision: Long, payload: String, now: Long): Int {
        val row = rows[id]?.takeIf { it.profileId == profileId && it.revision == revision } ?: return 0
        rows[id] = row.copy(encryptedPayload = payload, revision = revision + 1, updatedAtMillis = now)
        return 1
    }
    override suspend fun setEnabled(profileId: String, id: String, enabled: Boolean): Int {
        val row = rows[id]?.takeIf { it.profileId == profileId } ?: return 0
        rows[id] = row.copy(enabled = enabled, revision = row.revision + 1)
        return 1
    }
    override suspend fun remove(profileId: String, id: String): Int {
        if (rows[id]?.profileId != profileId) return 0
        rows.remove(id)
        return 1
    }
    override suspend fun setPosition(profileId: String, id: String, position: Int) {
        val row = rows[id]?.takeIf { it.profileId == profileId } ?: return
        rows[id] = row.copy(position = position, revision = row.revision + 1)
    }
}

/** Real randomized AES-GCM, with a throwaway key generated inside each test only. */
internal class TestAddonCipher : AddonSecretCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plaintext.toByteArray()))
    }
    override fun decrypt(ciphertext: String): String {
        val bytes = Base64.getDecoder().decode(ciphertext)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}
