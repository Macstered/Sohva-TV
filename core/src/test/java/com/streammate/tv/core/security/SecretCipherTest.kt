package com.streammate.tv.core.security

import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmSecretCipher(keyProvider = { key })

    @Test
    fun `round trips a secret without storing plaintext`() {
        val secret = "https://provider.example/playlist?username=viewer&password=hunter2"

        val encrypted = cipher.encrypt(secret)

        assertNotEquals(secret, encrypted)
        assertEquals(secret, cipher.decrypt(encrypted))
    }

    @Test
    fun `uses a fresh nonce for every encryption`() {
        assertNotEquals(cipher.encrypt("same value"), cipher.encrypt("same value"))
    }

    @Test
    fun `rejects tampered ciphertext`() {
        val encrypted = cipher.encrypt("secret")
        val replacement = if (encrypted.last() == '0') '1' else '0'
        val tampered = encrypted.dropLast(1) + replacement

        assertThrows(Exception::class.java) { cipher.decrypt(tampered) }
    }
}
