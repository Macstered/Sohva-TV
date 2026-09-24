package com.sohva.tv.core.data.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Names and associated data that identify one envelope store (spec 73 §4.1–4.2). The rebuild's
 * own store and beta 23's stores differ only in these values, so the one-time importer opens the
 * old store with the same code.
 */
data class EnvelopeSpec(
    val keystoreAlias: String,
    val prefsFile: String,
    val wrappedKeyName: String,
    val keystoreAad: String,
    val valueAad: String,
) {
    companion object {
        /** The rebuild's own store: new names, so nothing collides with beta 23's (decision A1). */
        val MAIN: EnvelopeSpec = EnvelopeSpec(
            keystoreAlias = "sohva.secrets.v1",
            prefsFile = "sohva_secret_envelope",
            wrappedKeyName = "data_key",
            keystoreAad = "sohva-secret-v1",
            valueAad = "sohva-secret-v2",
        )

        /** Beta 23's main store, read only by the importer (spec 73 SEC-FR-01..07). */
        val BETA23_MAIN: EnvelopeSpec = EnvelopeSpec(
            keystoreAlias = "sportmate.iptv.v1",
            prefsFile = "streammate_secret_envelope",
            wrappedKeyName = "data_key_v2",
            keystoreAad = "streammate-secret-v1",
            valueAad = "streammate-secret-v2",
        )
    }
}

/** The wrapping key: an Android Keystore key on a device, a software key in JVM tests. */
fun interface KeyProvider {
    fun wrappingKey(): SecretKey
}

/** Where the wrapped data key lives. [write] must be synchronous and durable before returning. */
interface WrappedKeyStore {
    fun read(): String?
    fun write(value: String): Boolean
}

/** The data key exists but cannot be used; secrets written with it are unreadable (spec 73 §8). */
class SecretsUnreadableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Envelope encryption (spec 73 SEC-FR-02..07): the Keystore key wraps a random 256-bit software
 * data key once per process; values are then encrypted in software.
 *
 * - `v1:<iv>:<ct>` — AES-GCM with the wrapping key itself (provider-chosen IV).
 * - `v2:<iv>:<ct>` — AES-GCM with the data key, a fresh 12-byte IV per value.
 *
 * Rebuild rules: a new wrapped key is saved synchronously before its first use; a stored key that
 * fails to unwrap or has the wrong length is never overwritten, it makes every call throw
 * [SecretsUnreadableException] so the app can say so plainly and offer a restore.
 *
 * Every call does Keystore or crypto work: call only from the io dispatcher (SEC-NFR-01).
 */
class EnvelopeCipher(
    private val spec: EnvelopeSpec,
    private val keyProvider: KeyProvider,
    private val wrappedKeyStore: WrappedKeyStore,
    private val random: SecureRandom = SecureRandom(),
) {
    @Volatile
    private var dataKey: SecretKey? = null

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, dataKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(spec.valueAad.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "$V2:${Hex.encode(iv)}:${Hex.encode(sealed)}"
    }

    fun decrypt(value: String): String {
        if (!value.startsWith("$V2:")) return keystoreDecrypt(value)
        val (iv, sealed) = parts(value)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, dataKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(spec.valueAad.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(sealed), Charsets.UTF_8)
    }

    private fun dataKey(): SecretKey = dataKey ?: synchronized(this) {
        dataKey ?: loadOrCreateDataKey().also { dataKey = it }
    }

    private fun loadOrCreateDataKey(): SecretKey {
        val stored = wrappedKeyStore.read()
        if (stored == null) {
            val raw = ByteArray(DATA_KEY_BYTES).also(random::nextBytes)
            // Beta 23 wraps the key's lower-case hex; the same construction serves both stores.
            val wrapped = keystoreEncrypt(Hex.encode(raw))
            if (!wrappedKeyStore.write(wrapped)) throw SecretsUnreadableException("could not save the data key")
            return SecretKeySpec(raw, "AES")
        }
        val raw = try {
            Hex.decode(keystoreDecrypt(stored))
        } catch (e: Exception) {
            throw SecretsUnreadableException("the stored data key does not unwrap", e)
        }
        if (raw.size != DATA_KEY_BYTES) throw SecretsUnreadableException("the stored data key has ${raw.size} bytes")
        return SecretKeySpec(raw, "AES")
    }

    private fun keystoreEncrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Keystore keys require randomised encryption: the provider chooses the IV.
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.wrappingKey())
        cipher.updateAAD(spec.keystoreAad.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "$V1:${Hex.encode(cipher.iv)}:${Hex.encode(sealed)}"
    }

    private fun keystoreDecrypt(value: String): String {
        require(value.startsWith("$V1:")) { "unknown secret format" }
        val (iv, sealed) = parts(value)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.wrappingKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(spec.keystoreAad.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(sealed), Charsets.UTF_8)
    }

    private fun parts(value: String): Pair<ByteArray, ByteArray> {
        val fields = value.split(':')
        require(fields.size == 3) { "malformed secret" }
        return Hex.decode(fields[1]) to Hex.decode(fields[2])
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        const val DATA_KEY_BYTES = 32
        const val V1 = "v1"
        const val V2 = "v2"
    }
}
