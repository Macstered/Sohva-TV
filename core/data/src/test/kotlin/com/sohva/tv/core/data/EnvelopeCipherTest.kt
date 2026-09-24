package com.sohva.tv.core.data

import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.KeyProvider
import com.sohva.tv.core.data.security.SecretsUnreadableException
import com.sohva.tv.core.data.security.WrappedKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class EnvelopeCipherTest {
    private class MemoryKeyStore(var value: String? = null, val accept: Boolean = true) : WrappedKeyStore {
        var writes = 0
        override fun read(): String? = value
        override fun write(value: String): Boolean {
            writes++
            if (accept) this.value = value
            return accept
        }
    }

    private fun softwareKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val wrapping = softwareKey()
    private val provider = KeyProvider { wrapping }

    private fun cipher(store: WrappedKeyStore, keys: KeyProvider = provider) =
        EnvelopeCipher(EnvelopeSpec.MAIN, keys, store)

    @Test
    fun roundTripWithAFreshIvPerValue() {
        val cipher = cipher(MemoryKeyStore())
        val a = cipher.encrypt("päivää ✓")
        val b = cipher.encrypt("päivää ✓")
        assertTrue(a.startsWith("v2:"))
        assertNotEquals(a, b)
        assertEquals("päivää ✓", cipher.decrypt(a))
    }

    @Test
    fun newDataKeyIsSavedBeforeItsFirstUseAndReusedLater() {
        val store = MemoryKeyStore()
        val sealed = cipher(store).encrypt("secret")
        assertNotNull(store.value)
        assertTrue(store.value!!.startsWith("v1:"))
        assertEquals("secret", cipher(store).decrypt(sealed))
        assertEquals(1, store.writes)
    }

    @Test
    fun aDataKeyThatCannotBeSavedIsNeverUsed() {
        assertThrows(SecretsUnreadableException::class.java) { cipher(MemoryKeyStore(accept = false)).encrypt("x") }
    }

    @Test
    fun tamperedValueFailsTheTagCheck() {
        val cipher = cipher(MemoryKeyStore())
        val sealed = cipher.encrypt("secret")
        val last = sealed.last()
        val tampered = sealed.dropLast(1) + (if (last == '0') '1' else '0')
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(tampered) }
    }

    @Test
    fun aKeyThatDoesNotUnwrapIsNeverOverwritten() {
        val store = MemoryKeyStore()
        cipher(store).encrypt("secret")
        val saved = store.value
        val otherKeystore = KeyProvider { softwareKey() }
        assertThrows(SecretsUnreadableException::class.java) { cipher(store, otherKeystore).encrypt("x") }
        assertEquals(saved, store.value)
        assertEquals(1, store.writes)
    }

    @Test
    fun aWrongLengthKeyIsUnreadableNotReplaced() {
        // A validly wrapped data key of 16 bytes instead of 32.
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, wrapping)
        c.updateAAD(EnvelopeSpec.MAIN.keystoreAad.toByteArray())
        val sealed = c.doFinal("00112233445566778899aabbccddeeff".toByteArray())
        val shortKey = "v1:${c.iv.toHex()}:${sealed.toHex()}"
        val store = MemoryKeyStore(value = shortKey)
        assertThrows(SecretsUnreadableException::class.java) { cipher(store).encrypt("x") }
        assertEquals(shortKey, store.value)
        assertEquals(0, store.writes)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
