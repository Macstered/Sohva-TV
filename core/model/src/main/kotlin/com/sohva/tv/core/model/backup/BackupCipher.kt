package com.sohva.tv.core.model.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The `.smbak` envelope (spec 71 §7.1), written byte for byte as beta 23 writes it so backups move
 * both ways: magic "SMBK", version 1, PBKDF2-HMAC-SHA256 iterations, 16-byte salt, 12-byte IV,
 * then the AES-256-GCM ciphertext with its tag, nothing after. Pure JVM, so it is tested on the
 * host against the platform's PBKDF2.
 */
object BackupCipher {
    const val MAGIC: Int = 0x534D424B
    const val VERSION: Int = 1
    const val ITERATIONS: Int = 210_000
    const val MIN_PASSWORD: Int = 8
    const val MAX_PASSWORD: Int = 128
    const val MAX_FILE: Int = 10_485_760
    const val MAX_PLAINTEXT: Int = 8_388_608
    const val HEADER: Int = 52

    private const val SALT = 16
    private const val IV = 12
    private const val TAG_BITS = 128
    private const val TAG = 16
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val CHUNK = 64 * 1024
    private val AAD = "streammate-backup-v1".toByteArray(Charsets.UTF_8)

    /** Encrypts [plaintext] with [password] and writes the whole envelope to [out] (BACKUP-FR-07, §9 NFR-02). */
    fun write(plaintext: ByteArray, length: Int, password: CharArray, out: OutputStream, random: SecureRandom = SecureRandom()) {
        if (password.size < MIN_PASSWORD) throw BackupException(BackupProblem.PASSWORD_TOO_SHORT)
        if (length > MAX_PLAINTEXT) throw BackupException(BackupProblem.TOO_LARGE)
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, ITERATIONS, iv)
        val data = DataOutputStream(out)
        data.writeInt(MAGIC)
        data.writeInt(VERSION)
        data.writeInt(ITERATIONS)
        data.writeInt(SALT)
        data.write(salt)
        data.writeInt(IV)
        data.write(iv)
        data.writeInt(length + TAG)
        var offset = 0
        while (offset < length) {
            val n = minOf(CHUNK, length - offset)
            cipher.update(plaintext, offset, n)?.let(data::write)
            offset += n
        }
        data.write(cipher.doFinal())
        data.flush()
    }

    /**
     * Reads a whole envelope from [input] and returns the plaintext. The 52-byte header is checked
     * before the body is read (§9 NFR-03); every fault has its own problem (§4.6).
     */
    fun read(input: InputStream, password: CharArray): ByteArray {
        val data = DataInputStream(input)
        val magic = header { data.readInt() }
        if (magic != MAGIC) throw BackupException(BackupProblem.NOT_SOHVA)
        if (header { data.readInt() } != VERSION) throw BackupException(BackupProblem.ENVELOPE_VERSION)
        val iterations = header { data.readInt() }
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupException(BackupProblem.KEY_FORMAT)
        if (header { data.readInt() } != SALT) throw BackupException(BackupProblem.STRUCTURE)
        val salt = ByteArray(SALT).also { header { data.readFully(it) } }
        if (header { data.readInt() } != IV) throw BackupException(BackupProblem.STRUCTURE)
        val iv = ByteArray(IV).also { header { data.readFully(it) } }
        val length = header { data.readInt() }
        if (length < TAG || length > MAX_FILE) throw BackupException(BackupProblem.STRUCTURE)
        val body = ByteArray(length)
        header { data.readFully(body) }
        if (data.read() != -1) throw BackupException(BackupProblem.TRAILING_DATA)
        val cipher = cipher(Cipher.DECRYPT_MODE, password, salt, iterations, iv)
        return try {
            cipher.doFinal(body)
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupProblem.WRONG_PASSWORD, cause = e)
        } finally {
            Arrays.fill(body, 0)
        }
    }

    /** A short or cut file is a structural fault, never an exception text. */
    private inline fun <T> header(block: () -> T): T = try {
        block()
    } catch (e: EOFException) {
        throw BackupException(BackupProblem.STRUCTURE, cause = e)
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iterations: Int, iv: ByteArray): Cipher {
        val key = key(password, salt, iterations)
        try {
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(AAD)
            }
        } finally {
            Arrays.fill(key, 0)
        }
    }

    /**
     * 256 bits of PBKDF2-HMAC-SHA256 over the UTF-8 password, as the platform's
     * `PBKDF2WithHmacSHA256` derives them; where the platform has none (Android 6–7), the same
     * function over `HmacSHA256` (spec 71 §8, RFC 8018 §5.2).
     */
    fun key(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val platform = runCatching { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }.getOrNull()
        if (platform != null) {
            val spec = PBEKeySpec(password, salt, iterations, 256)
            try {
                return platform.generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }
        return pbkdf2(utf8(password), salt, iterations)
    }

    /** One 32-byte block of RFC 8018 PBKDF2 with HMAC-SHA256: exactly the key length needed. */
    internal fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password.takeIf { it.isNotEmpty() } ?: ByteArray(1), "HmacSHA256")) }
        if (password.isEmpty()) mac.init(EmptyKey)
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val t = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
        }
        Arrays.fill(password, 0)
        return t
    }

    internal fun utf8(password: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    /** HMAC allows an empty key; `SecretKeySpec` does not, so an empty password gets this key. */
    private object EmptyKey : javax.crypto.SecretKey {
        private fun readResolve(): Any = EmptyKey
        override fun getAlgorithm(): String = "HmacSHA256"
        override fun getFormat(): String = "RAW"
        override fun getEncoded(): ByteArray = ByteArray(0)
    }

    /** A growable buffer whose bytes can be wiped (§9 NFR-02). */
    class WipingBuffer(initial: Int = 16 * 1024) : ByteArrayOutputStream(initial) {
        val bytes: ByteArray get() = buf
        val length: Int get() = count

        override fun write(b: Int) {
            if (count + 1 > MAX_PLAINTEXT) throw BackupException(BackupProblem.TOO_LARGE)
            super.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (count + len > MAX_PLAINTEXT) throw BackupException(BackupProblem.TOO_LARGE)
            super.write(b, off, len)
        }

        fun wipe() {
            Arrays.fill(buf, 0)
            count = 0
        }
    }
}
