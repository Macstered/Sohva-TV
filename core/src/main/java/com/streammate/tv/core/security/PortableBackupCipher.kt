package com.streammate.tv.core.security

import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object PortableBackupCipher {
    fun encrypt(plainText: ByteArray, passphrase: CharArray): ByteArray {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) {
            throw LocalizedException(R.string.error_backup_passphrase_too_short, MIN_PASSPHRASE_LENGTH)
        }
        if (plainText.size > MAX_PLAINTEXT_BYTES) {
            throw LocalizedException(R.string.error_backup_too_large)
        }
        val salt = ByteArray(SALT_BYTES).also(SECURE_RANDOM::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SECURE_RANDOM::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(ASSOCIATED_DATA)
        val encrypted = cipher.doFinal(plainText)
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(PBKDF2_ITERATIONS)
                output.writeInt(salt.size)
                output.write(salt)
                output.writeInt(iv.size)
                output.write(iv)
                output.writeInt(encrypted.size)
                output.write(encrypted)
            }
        }.toByteArray()
    }

    fun decrypt(payload: ByteArray, passphrase: CharArray): ByteArray {
        if (payload.size > MAX_ENCRYPTED_BYTES) {
            throw LocalizedException(R.string.error_backup_too_large)
        }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            if (input.readInt() != MAGIC) {
                throw LocalizedException(R.string.error_backup_not_streammate)
            }
            if (input.readInt() != VERSION) {
                throw LocalizedException(R.string.error_backup_version_unsupported)
            }
            val iterations = input.readInt()
            if (iterations !in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
                throw LocalizedException(R.string.error_backup_key_format)
            }
            val salt = input.readSizedBytes(SALT_BYTES, SALT_BYTES)
            val iv = input.readSizedBytes(IV_BYTES, IV_BYTES)
            val encrypted = input.readSizedBytes(TAG_BYTES, MAX_ENCRYPTED_BYTES)
            if (input.read() != -1) {
                throw LocalizedException(R.string.error_backup_trailing_data)
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase, salt, iterations),
                GCMParameterSpec(TAG_LENGTH_BITS, iv),
            )
            cipher.updateAAD(ASSOCIATED_DATA)
            try {
                cipher.doFinal(encrypted)
            } catch (error: AEADBadTagException) {
                throw LocalizedException(
                    R.string.error_backup_wrong_passphrase,
                    cause = error,
                )
            }
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
    ): SecretKeySpec {
        val specification = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KEY_DERIVATION).generateSecret(specification).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            specification.clearPassword()
        }
    }

    private fun DataInputStream.readSizedBytes(minimum: Int, maximum: Int): ByteArray {
        val size = readInt()
        if (size !in minimum..maximum) {
            throw LocalizedException(R.string.error_backup_structure)
        }
        return ByteArray(size).also(::readFully)
    }

    const val MIN_PASSPHRASE_LENGTH = 8
    const val MAX_ENCRYPTED_BYTES = 10 * 1024 * 1024
    private const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
    private const val MAGIC = 0x534D424B
    private const val VERSION = 1
    private const val PBKDF2_ITERATIONS = 210_000
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_LENGTH_BITS = 256
    private const val KEY_DERIVATION = "PBKDF2WithHmacSHA256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val ASSOCIATED_DATA = "streammate-backup-v1".toByteArray(Charsets.UTF_8)
    private val SECURE_RANDOM = SecureRandom()
}
