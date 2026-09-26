package com.sohva.tv.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Beta 23's `.smbak` writer, restated from spec 71 §7.1 with the platform's primitives and not the
 * app's cipher, so the backup test checks the app against a second implementation. Backup files
 * are never committed (public-source audit); the test seals the fixture payload when it runs.
 */
object Beta23Envelope {
    private const val ITERATIONS = 210_000

    fun seal(plaintext: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, ITERATIONS, 256)).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("streammate-backup-v1".toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(plaintext)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(0x534D424B)
            d.writeInt(1)
            d.writeInt(ITERATIONS)
            d.writeInt(salt.size)
            d.write(salt)
            d.writeInt(iv.size)
            d.write(iv)
            d.writeInt(sealed.size)
            d.write(sealed)
        }
        return out.toByteArray()
    }
}
