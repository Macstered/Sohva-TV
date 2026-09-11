package com.sohva.tv.addons

/** Supplied by the host's keystore-backed cipher; this module never owns a plaintext fallback. */
interface AddonSecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

class InstalledAddon(
    val installationId: String,
    val profileId: String,
    val endpoint: AddonEndpoint,
    val manifest: AddonManifest,
    val enabled: Boolean,
    val position: Int,
    val revision: Long,
    val updatedAtMillis: Long,
) {
    override fun toString(): String = "InstalledAddon([redacted])"
}

interface AddonStore {
    suspend fun list(profileId: String): List<InstalledAddon>
    /** Idempotent by profile + exact configured endpoint. Reinstall does not reset order/enabled. */
    suspend fun install(profileId: String, endpoint: AddonEndpoint, manifestJson: String): InstalledAddon
    suspend fun refresh(profileId: String, installationId: String, expectedRevision: Long, manifestJson: String): Boolean
    suspend fun setEnabled(profileId: String, installationId: String, enabled: Boolean)
    suspend fun remove(profileId: String, installationId: String)
    suspend fun reorder(profileId: String, installationIds: List<String>)
}

internal fun validateProfile(profileId: String) {
    if (profileId.isBlank() || profileId.length > 256) fail(AddonFailure.INVALID_REQUEST)
}
