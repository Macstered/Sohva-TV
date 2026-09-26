package com.sohva.tv.core.model

import com.sohva.tv.core.model.backup.BackupCipher
import com.sohva.tv.core.model.backup.BackupException
import com.sohva.tv.core.model.backup.BackupProblem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Spec 71 §11 "Unit": the envelope round trip, its header, and every read fault's own problem. */
class BackupCipherTest {
    private val plaintext = "{\"formatVersion\":2}".toByteArray()

    private fun seal(password: String = "correct horse", text: ByteArray = plaintext): ByteArray =
        ByteArrayOutputStream().also { BackupCipher.write(text, text.size, password.toCharArray(), it, SecureRandom()) }.toByteArray()

    private fun problem(bytes: ByteArray, password: String = "correct horse"): BackupProblem {
        try {
            BackupCipher.read(ByteArrayInputStream(bytes), password.toCharArray())
        } catch (e: BackupException) {
            return e.problem
        }
        fail("read succeeded")
        error("unreachable")
    }

    @Test
    fun roundTripsWithAsciiAndNonAsciiPasswordsAndTheHeaderIsBeta23s() {
        for (password in listOf("correct horse", "sähkö äiti öljy")) {
            val file = seal(password)
            assertArrayEquals(plaintext, BackupCipher.read(ByteArrayInputStream(file), password.toCharArray()))
        }
        val header = ByteBuffer.wrap(seal())
        assertEquals(0x534D424B, header.int)
        assertEquals(1, header.int)
        assertEquals(210_000, header.int)
        assertEquals(16, header.int)
        header.position(32)
        assertEquals(12, header.int)
        header.position(48)
        assertEquals(plaintext.size + 16, header.int)
        assertEquals(BackupCipher.HEADER + plaintext.size + 16, seal().size)
    }

    @Test
    fun theFallbackKeyIsThePlatformsKeyByteForByte() {
        val salt = ByteArray(16) { it.toByte() }
        for (password in listOf("password", "sähkö äiti öljy", "")) {
            val spec = PBEKeySpec(password.toCharArray(), salt, 1_000, 256)
            val platform = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            assertArrayEquals(password, platform, BackupCipher.pbkdf2(BackupCipher.utf8(password.toCharArray()), salt, 1_000))
        }
    }

    @Test
    fun everyReadFaultHasItsOwnProblem() {
        val good = seal()
        assertEquals(BackupProblem.WRONG_PASSWORD, problem(good, "wrong password"))
        assertEquals(BackupProblem.WRONG_PASSWORD, problem(good.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }))
        fun patch(at: Int, value: Int) = good.copyOf().also { ByteBuffer.wrap(it).putInt(at, value) }
        assertEquals(BackupProblem.NOT_SOHVA, problem(patch(0, 0x12345678)))
        assertEquals(BackupProblem.ENVELOPE_VERSION, problem(patch(4, 2)))
        assertEquals(BackupProblem.KEY_FORMAT, problem(patch(8, 99_999)))
        assertEquals(BackupProblem.KEY_FORMAT, problem(patch(8, 1_000_001)))
        assertEquals(BackupProblem.STRUCTURE, problem(patch(12, 15)))
        assertEquals(BackupProblem.STRUCTURE, problem(patch(32, 13)))
        assertEquals(BackupProblem.STRUCTURE, problem(patch(48, 15)))
        assertEquals(BackupProblem.STRUCTURE, problem(patch(48, 10_485_761)))
        assertEquals(BackupProblem.TRAILING_DATA, problem(good + byteArrayOf(0)))
        assertEquals(BackupProblem.STRUCTURE, problem(good.copyOf(good.size - 1)))
    }

    @Test
    fun aShortPasswordAndAnOversizedPlaintextAreRefusedOnSave() {
        try {
            seal("seven77")
            fail("saved")
        } catch (e: BackupException) {
            assertEquals(BackupProblem.PASSWORD_TOO_SHORT, e.problem)
        }
        try {
            val big = ByteArray(BackupCipher.MAX_PLAINTEXT + 1)
            BackupCipher.write(big, big.size, "long enough".toCharArray(), ByteArrayOutputStream())
            fail("saved")
        } catch (e: BackupException) {
            assertEquals(BackupProblem.TOO_LARGE, e.problem)
        }
    }
}
