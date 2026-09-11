package com.sohva.tv.addons

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull

class CachedAddonResponse(val body: String, val storedAtMillis: Long, val expiresAtMillis: Long, val staleAllowed: Boolean = true) {
    fun fresh(now: Long) = now in storedAtMillis until expiresAtMillis
    override fun toString() = "CachedAddonResponse([redacted])"
}

interface AddonResponseCache {
    suspend fun get(key: String): CachedAddonResponse?
    suspend fun put(key: String, response: CachedAddonResponse)
    suspend fun remove(key: String)
}

/** Disposable encrypted cache, outside Android backup. Corruption/storage pressure is a miss. */
class EncryptedFileAddonCache(
    private val directory: File,
    private val cipher: AddonSecretCipher,
    private val maxBytes: Long = 16L * 1024 * 1024,
    private val maxEntries: Int = 48,
) : AddonResponseCache {
    private val mutex = Mutex()
    init { require(maxBytes in 1024..64L * 1024 * 1024); require(maxEntries in 1..256) }

    override suspend fun get(key: String): CachedAddonResponse? = disk {
        val file = file(key)
        if (!file.isFile || file.length() > maxBytes) return@disk null
        val root = Json.parseToJsonElement(cipher.decrypt(file.readText())) as? JsonObject ?: return@disk null
        if ((root["key"] as? JsonPrimitive)?.content != key) return@disk null
        val body = (root["body"] as? JsonPrimitive)?.content ?: return@disk null
        val stored = (root["stored"] as? JsonPrimitive)?.longOrNull ?: return@disk null
        val expires = (root["expires"] as? JsonPrimitive)?.longOrNull ?: return@disk null
        file.setLastModified(System.currentTimeMillis())
        CachedAddonResponse(body, stored, expires, (root["staleAllowed"] as? JsonPrimitive)?.booleanOrNull ?: false)
    }
    override suspend fun put(key: String, response: CachedAddonResponse) {
        disk {
            if (!directory.isDirectory && !directory.mkdirs()) return@disk null
            directory.listFiles()?.filter { it.name.startsWith("response-") && it.extension == "tmp" }?.forEach { it.delete() }
            val encoded = cipher.encrypt(JsonObject(mapOf(
                "key" to JsonPrimitive(key), "body" to JsonPrimitive(response.body),
                "stored" to JsonPrimitive(response.storedAtMillis), "expires" to JsonPrimitive(response.expiresAtMillis),
                "staleAllowed" to JsonPrimitive(response.staleAllowed),
            )).toString()).toByteArray()
            val target = file(key)
            if (encoded.size > maxBytes) { target.delete(); return@disk null }
            val temporary = File.createTempFile("response-", ".tmp", directory)
            try {
                temporary.outputStream().use { it.write(encoded) }
                if (!temporary.renameTo(target)) { target.delete(); temporary.renameTo(target) }
            } finally { temporary.delete() }
            val files = directory.listFiles()?.filter { it.extension == "cache" }?.sortedByDescending { it.lastModified() }.orEmpty()
            var bytes = 0L
            files.forEachIndexed { index, file ->
                bytes += file.length()
                if (index >= maxEntries || bytes > maxBytes) file.delete()
            }
        }
    }
    override suspend fun remove(key: String) { disk { file(key).delete() } }
    private fun file(key: String): File {
        require(key.matches(Regex("[a-f0-9]{64}")))
        return File(directory, "$key.cache")
    }
    private suspend fun <T> disk(block: () -> T): T? = withContext(Dispatchers.IO) {
        mutex.withLock { try { block() } catch (_: Exception) { null } }
    }
}

/** Length-prefixing avoids separator collisions in opaque provider IDs/extras. */
internal fun addonCacheKey(parts: List<String>): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray()).joinToString("") { "%02x".format(it) }
