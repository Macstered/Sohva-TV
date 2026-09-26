package com.sohva.tv.feature.discover

import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.discover.store.DiscoverAccess
import com.sohva.tv.feature.discover.store.DiscoverCipher
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A software AES-GCM cipher standing in for the Keystore-backed envelope (plan/03 §4.15). */
class FakeCipher : DiscoverCipher {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: String): String = java.util.Base64.getEncoder().encodeToString(seal(plaintext.toByteArray(), AAD))

    override fun decrypt(value: String): String = String(open(java.util.Base64.getDecoder().decode(value), AAD))

    override fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        c.updateAAD(aad)
        return iv + c.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        c.updateAAD(aad)
        return c.doFinal(sealed, 12, sealed.size - 12)
    }

    private companion object {
        val AAD = "test".toByteArray()
    }
}

class FakeAccess(var active: String = "p1") : DiscoverAccess {
    val restricted = HashSet<String>()

    override fun activeProfile(): String = active

    override suspend fun allowed(profile: String): Boolean = profile == active && profile !in restricted
}

class FakeClock(var now: Long = 1_790_400_000_000L) : Clock {
    override fun wallMillis(): Long = now

    override fun monotonicNanos(): Long = now * 1_000_000
}
