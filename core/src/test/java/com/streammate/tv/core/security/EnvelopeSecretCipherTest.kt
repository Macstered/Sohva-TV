package com.streammate.tv.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeSecretCipherTest {
    /** Stands in for the keystore: reversible, and counts how often it is asked. */
    private class CountingKeystoreCipher : SecretCipher {
        var calls = 0
        override fun encrypt(plainText: String): String {
            calls += 1
            return "v1:keystore:" + plainText.reversed()
        }
        override fun decrypt(encoded: String): String {
            calls += 1
            require(encoded.startsWith("v1:keystore:")) { "Unsupported encrypted value" }
            return encoded.removePrefix("v1:keystore:").reversed()
        }
    }

    private class MemoryKeyStore : WrappedKeyStore {
        var wrapped: String? = null
        override fun load(): String? = wrapped
        override fun save(wrapped: String) { this.wrapped = wrapped }
    }

    @Test
    fun `values round trip without touching the keystore after the first call`() {
        val keystore = CountingKeystoreCipher()
        val cipher = EnvelopeSecretCipher(keystore, MemoryKeyStore())

        val encoded = (1..200).map { cipher.encrypt("http://provider.example/live/$it.ts") }
        encoded.forEachIndexed { index, value ->
            assertTrue(value.startsWith("v2:"))
            assertEquals("http://provider.example/live/${index + 1}.ts", cipher.decrypt(value))
        }
        // One wrap of the generated data key; nothing per value.
        assertEquals(1, keystore.calls)
        assertNotEquals(encoded[0], encoded[1])
    }

    @Test
    fun `the same data key comes back on the next run`() {
        val keystore = CountingKeystoreCipher()
        val store = MemoryKeyStore()
        val encoded = EnvelopeSecretCipher(keystore, store).encrypt("secret")
        assertNotNull(store.wrapped)

        val nextRun = EnvelopeSecretCipher(keystore, store)
        assertEquals("secret", nextRun.decrypt(encoded))
        // One wrap on the first run, one unwrap on the second.
        assertEquals(2, keystore.calls)
    }

    @Test
    fun `values written by the keystore cipher still decrypt`() {
        val keystore = CountingKeystoreCipher()
        val legacy = keystore.encrypt("http://provider.example/old.ts")
        val cipher = EnvelopeSecretCipher(keystore, MemoryKeyStore())
        assertEquals("http://provider.example/old.ts", cipher.decrypt(legacy))
    }

    @Test
    fun `a tampered value is rejected`() {
        val cipher = EnvelopeSecretCipher(CountingKeystoreCipher(), MemoryKeyStore())
        val encoded = cipher.encrypt("secret")
        val tampered = encoded.dropLast(2) + if (encoded.endsWith("00")) "11" else "00"
        val failed = runCatching { cipher.decrypt(tampered) }.isFailure
        assertTrue(failed)
    }
}
