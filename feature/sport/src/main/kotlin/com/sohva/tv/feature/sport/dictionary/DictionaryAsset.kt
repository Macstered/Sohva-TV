package com.sohva.tv.feature.sport.dictionary

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject

/** Nothing is opened at construction. Today calls this on the serial bulk dispatcher. */
class DictionaryAsset(private val openAsset: (String) -> InputStream, private val directory: () -> File) {
    private data class Manifest(val revision: String, val hash: String, val bytes: Long)
    private val lock = Mutex()
    // One small manifest per installed app; no participants or catalogue rows remain cached.
    private var manifest: Manifest? = null

    suspend fun <T> read(block: suspend (SQLiteDatabase, String) -> T): T? = lock.withLock {
        var destination: File? = null
        try {
            val selected = manifest ?: readManifest().also { manifest = it }
            val folder = directory().apply { if (!isDirectory && !mkdirs()) throw IOException("dictionary directory") }
            destination = File(folder, "${selected.revision}.db")
            val receipt = File(folder, "${selected.revision}.verified")
            if (!destination.isFile || destination.length() != selected.bytes || !receipt.isFile || receipt.length() != 64L || receipt.readText() != selected.hash) {
                install(selected, destination, receipt)
            }
            SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                db.rawQuery("PRAGMA user_version", null).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getInt(0) != FORMAT) throw IOException("dictionary format")
                }
                block(db, selected.revision)
            }.also {
                // This class serialises readers and closes each connection before obsolete files go.
                folder.listFiles()?.filter { file -> file != destination && file != receipt && owned(file.name) }
                    ?.forEach { file -> file.delete() }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SQLiteException) {
            destination?.delete()
            null
        } catch (error: IOException) {
            null
        } catch (error: JSONException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun readManifest(): Manifest {
        val bytes = openAsset("sport-dictionary.json").use { input ->
            val buffer = ByteArray(MAX_MANIFEST + 1)
            var size = 0
            while (size < buffer.size) {
                val n = input.read(buffer, size, buffer.size - size)
                if (n < 0) break
                size += n
            }
            if (size > MAX_MANIFEST) throw IOException("dictionary manifest size")
            buffer.copyOf(size)
        }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        if (json.getInt("format") != FORMAT || json.getInt("normalisation") != NORMALISATION) throw IOException("dictionary manifest version")
        val revision = json.getString("revision")
        val hash = json.getString("sha256")
        val size = json.getLong("bytes")
        if (!hex(revision) || !hex(hash) || size !in 1..MAX_BYTES) throw IOException("dictionary manifest values")
        return Manifest(revision, hash, size)
    }

    private suspend fun install(selected: Manifest, destination: File, receipt: File) {
        val temporary = File(destination.parentFile, "${selected.revision}.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            openAsset("sport-dictionary.db").use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BLOCK)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > selected.bytes) throw IOException("dictionary copy size")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        // Let a visibility/generation update cancel this serial worker promptly.
                        yield()
                    }
                    output.fd.sync()
                }
            }
            if (copied != selected.bytes || digest.digest().joinToString("") { "%02x".format(it) } != selected.hash) throw IOException("dictionary checksum")
            currentCoroutineContext().ensureActive()
            if (!temporary.renameTo(destination)) throw IOException("dictionary atomic install")
            receipt.writeText(selected.hash)
        } finally {
            temporary.delete()
        }
    }

    private fun owned(name: String): Boolean = name.substringBefore('.').let(::hex) &&
        name.substringAfter('.') in setOf("db", "verified", "tmp")

    private fun hex(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private companion object {
        const val FORMAT = 1
        const val NORMALISATION = 1
        const val MAX_MANIFEST = 4_096
        const val COPY_BLOCK = 32 * 1024
        // The complete synthetic schema measures 13.5 MB on disk; copying stays at 32 KiB.
        // APK compression/size is checked separately, and lookup memory is bounded independently.
        const val MAX_BYTES = 16L * 1024 * 1024
    }
}
