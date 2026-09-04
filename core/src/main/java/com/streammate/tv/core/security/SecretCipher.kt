package com.streammate.tv.core.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(encoded: String): String
}

class AesGcmSecretCipher(
    private val keyProvider: () -> SecretKey,
) : SecretCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Android Keystore keys require the provider to generate a fresh IV for
        // encryption. Supplying our own secure random IV is rejected on-device.
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(ASSOCIATED_DATA)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "$VERSION:${cipher.iv.toHex()}:${encrypted.toHex()}"
    }

    override fun decrypt(encoded: String): String {
        val parts = encoded.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == VERSION) { "Unsupported encrypted value" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(TAG_LENGTH_BITS, parts[1].hexToBytes()),
        )
        cipher.updateAAD(ASSOCIATED_DATA)
        return cipher.doFinal(parts[2].hexToBytes()).toString(Charsets.UTF_8)
    }

    /**
     * Hex is part of the persisted cipher format, but Formatter is an
     * exceptionally expensive way to produce it. A playlist refresh encrypts
     * thousands of stream URLs; formatting every byte built a Formatter,
     * locale data and several temporary strings, making encryption the hottest
     * sampled task and forcing concurrent GC while the TV UI was active.
     */
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
        const val VERSION = "v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val HEX_DIGITS = "0123456789abcdef"
        val ASSOCIATED_DATA = "streammate-secret-v1".toByteArray(Charsets.UTF_8)
    }
}
