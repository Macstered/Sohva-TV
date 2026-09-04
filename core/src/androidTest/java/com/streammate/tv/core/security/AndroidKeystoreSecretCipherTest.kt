package com.streammate.tv.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretCipherTest {
    private val alias = "sportmate.test.iptv.secret"

    @After
    fun removeTestKey() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
    }

    @Test
    fun encryptsAndDecryptsWithAndroidKeystoreGeneratedIv() {
        val cipher = AesGcmSecretCipher(AndroidKeystoreKeyProvider(alias)::getOrCreate)
        val secret = "http://192.0.2.1:8080/m/example"

        val encrypted = cipher.encrypt(secret)

        assertNotEquals(secret, encrypted)
        assertEquals(secret, cipher.decrypt(encrypted))
    }
}
