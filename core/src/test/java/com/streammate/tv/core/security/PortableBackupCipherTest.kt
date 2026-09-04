package com.streammate.tv.core.security

import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableBackupCipherTest {
    @Test
    fun roundTripUsesPortablePassphraseEncryption() {
        val original = "StreamMate backup sisältää ääkkösiä".toByteArray()
        val encrypted = PortableBackupCipher.encrypt(original, "correct horse".toCharArray())

        val decrypted = PortableBackupCipher.decrypt(encrypted, "correct horse".toCharArray())

        assertArrayEquals(original, decrypted)
    }

    @Test
    fun wrongPassphraseAndTamperingAreRejected() {
        val encrypted = PortableBackupCipher.encrypt("private".toByteArray(), "correct horse".toCharArray())

        val wrongPassword = assertThrows(LocalizedException::class.java) {
            PortableBackupCipher.decrypt(encrypted, "wrong password".toCharArray())
        }
        assertEquals(R.string.error_backup_wrong_passphrase, wrongPassword.messageResource)

        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        val tampered = assertThrows(LocalizedException::class.java) {
            PortableBackupCipher.decrypt(encrypted, "correct horse".toCharArray())
        }
        assertEquals(R.string.error_backup_wrong_passphrase, tampered.messageResource)
    }
}
