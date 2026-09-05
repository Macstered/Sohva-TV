package com.streammate.tv.core.security

import android.content.Context
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Where the wrapped data key lives between runs. */
interface WrappedKeyStore {
    fun load(): String?
    fun save(wrapped: String)
}

class PreferencesWrappedKeyStore(context: Context) : WrappedKeyStore {
    private val preferences = context.applicationContext
        .getSharedPreferences("streammate_secret_envelope", Context.MODE_PRIVATE)

    override fun load(): String? = preferences.getString(KEY, null)

    override fun save(wrapped: String) {
        preferences.edit().putString(KEY, wrapped).apply()
    }

    private companion object {
        const val KEY = "data_key_v2"
    }
}

/**
 * Envelope encryption for stream URLs and credentials.
 *
 * Every value used to be encrypted directly with the Android Keystore key,
 * which means a trip through the keystore daemon per value: a few
 * milliseconds each on a Shield, and a playlist refresh or a catalogue import
 * makes tens of thousands of them. That was most of a ten-minute import.
 *
 * Now a random 256-bit data key is generated once, wrapped with the keystore
 * key through [keystoreCipher], and stored. Each run unwraps it once and
 * encrypts in software from then on, in microseconds. The keystore still
 * guards the data key; nothing in the database can be read without it.
 *
 * Values written before this, prefixed `v1:`, still decrypt through the
 * keystore cipher, so nothing has to be migrated: a refresh rewrites them.
 */
class EnvelopeSecretCipher(
    private val keystoreCipher: SecretCipher,
    private val wrappedKeyStore: WrappedKeyStore,
    private val random: SecureRandom = SecureRandom(),
) : SecretCipher {
    @Volatile
    private var dataKey: SecretKey? = null

    override fun encrypt(plainText: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, dataKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(ASSOCIATED_DATA)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "$VERSION:${iv.toHex()}:${encrypted.toHex()}"
    }

    override fun decrypt(encoded: String): String {
        if (!encoded.startsWith("$VERSION:")) return keystoreCipher.decrypt(encoded)
        val parts = encoded.split(':', limit = 3)
        require(parts.size == 3) { "Unsupported encrypted value" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, dataKey(), GCMParameterSpec(TAG_LENGTH_BITS, parts[1].hexToBytes()))
        cipher.updateAAD(ASSOCIATED_DATA)
        return cipher.doFinal(parts[2].hexToBytes()).toString(Charsets.UTF_8)
    }

    private fun dataKey(): SecretKey {
        dataKey?.let { return it }
        synchronized(this) {
            dataKey?.let { return it }
            val raw = wrappedKeyStore.load()
                ?.let { wrapped -> keystoreCipher.decrypt(wrapped).hexToBytes() }
                ?.takeIf { it.size == KEY_LENGTH_BYTES }
                ?: ByteArray(KEY_LENGTH_BYTES).also(random::nextBytes).also { generated ->
                    wrappedKeyStore.save(keystoreCipher.encrypt(generated.toHex()))
                }
            return SecretKeySpec(raw, "AES").also { dataKey = it }
        }
    }

    private fun ByteArray.toHex(): String = CharArray(size * 2).also { encoded ->
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            encoded[index * 2] = HEX_DIGITS[value ushr 4]
            encoded[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
        }
    }.concatToString()

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid encrypted value" }
        return ByteArray(length / 2) { index ->
            val offset = index * 2
            val high = this[offset].digitToIntOrNull(16)
            val low = this[offset + 1].digitToIntOrNull(16)
            require(high != null && low != null) { "Invalid encrypted value" }
            ((high shl 4) or low).toByte()
        }
    }

    private companion object {
        const val VERSION = "v2"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
        const val KEY_LENGTH_BYTES = 32
        const val HEX_DIGITS = "0123456789abcdef"
        val ASSOCIATED_DATA = "streammate-secret-v2".toByteArray(Charsets.UTF_8)
    }
}
