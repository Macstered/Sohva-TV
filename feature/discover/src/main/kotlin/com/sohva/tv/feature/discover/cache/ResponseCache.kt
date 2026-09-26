package com.sohva.tv.feature.discover.cache

import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.discover.net.CacheHints
import com.sohva.tv.feature.discover.protocol.CatalogPage
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.store.DiscoverCipher
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.io.File
import okio.Buffer

/** A cached answer: its body, and whether it is still fresh or may stand in when the network fails. */
class CachedBody(val body: Buffer, val fresh: Boolean, val staleAllowed: Boolean, val storedAt: Long)

/** A shelf's first titles, cached apart so a shelf never decrypts and parses a whole page (spec 50 §9). */
class CachedPreview(val page: CatalogPage, val fresh: Boolean, val staleAllowed: Boolean, val storedAt: Long)

/**
 * Discover's response cache (spec 50 ADDON-FR-122…124, §6, §9 "Cache"): catalog and meta answers
 * only, one file per key in `no_backup/addon-responses`, each sealed with the envelope cipher
 * (binary AES-GCM, the key hash as associated data, so a swapped file never opens). Recency lives
 * in memory; the directory stays under 32 MiB and 128 files, least recently used first out. An
 * answer that could never be served (`no-store`, or zero freshness without stale use) is not
 * written. Unreadable files are misses. Calls come from the io dispatcher.
 */
class ResponseCache(private val dir: File, private val cipher: DiscoverCipher, private val clock: Clock) {
    private val recency = LinkedHashMap<String, Long>(64, 0.75f, true)
    private var loaded = false

    @Synchronized
    fun read(key: String): CachedBody? {
        val bytes = open(file(key, BODY), key) ?: return null
        val input = Buffer().write(bytes)
        val stored = input.readLong()
        val expires = input.readLong()
        val staleAllowed = input.readByte().toInt() == 1
        return CachedBody(input, fresh(stored, expires), staleAllowed, stored)
    }

    @Synchronized
    fun write(key: String, body: Buffer, hints: CacheHints) {
        val now = clock.wallMillis()
        val ttl = ttl(hints)
        if (hints.noStore || (ttl == 0L && !hints.staleAllowed)) {
            delete(key)
            return
        }
        val out = Buffer().writeLong(now).writeLong(now + ttl).writeByte(if (hints.staleAllowed) 1 else 0)
        body.copyTo(out)
        save(file(key, BODY), key, out.readByteArray())
    }

    @Synchronized
    fun readPreview(key: String): CachedPreview? {
        val bytes = open(file(key, PREVIEW), key) ?: return null
        val input = Buffer().write(bytes)
        val stored = input.readLong()
        val expires = input.readLong()
        val staleAllowed = input.readByte().toInt() == 1
        val page = runCatching { PreviewJson.read(input) }.getOrNull() ?: return null.also { delete(key) }
        return CachedPreview(page, fresh(stored, expires), staleAllowed, stored)
    }

    @Synchronized
    fun writePreview(key: String, page: CatalogPage, hints: CacheHints) {
        val now = clock.wallMillis()
        val ttl = ttl(hints)
        if (hints.noStore || (ttl == 0L && !hints.staleAllowed)) {
            file(key, PREVIEW).delete()
            return
        }
        val out = Buffer().writeLong(now).writeLong(now + ttl).writeByte(if (hints.staleAllowed) 1 else 0)
        PreviewJson.write(out, page)
        save(file(key, PREVIEW), key, out.readByteArray())
    }

    @Synchronized
    fun delete(key: String) {
        for (kind in listOf(BODY, PREVIEW)) {
            val f = file(key, kind)
            if (f.delete()) recency.remove(f.name)
        }
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        recency.clear()
    }

    private fun fresh(stored: Long, expires: Long): Boolean {
        val now = clock.wallMillis()
        return stored <= now && now < expires
    }

    private fun ttl(hints: CacheHints): Long = (hints.maxAgeSeconds ?: DEFAULT_TTL_S).coerceIn(0, MAX_TTL_S) * 1_000

    private fun file(key: String, kind: String): File = File(dir, "$key$kind")

    private fun open(file: File, key: String): ByteArray? {
        if (!file.isFile) return null
        index()
        return try {
            val plain = cipher.open(file.readBytes(), (key + file.name.takeLast(2)).toByteArray())
            recency[file.name] = file.length()
            plain
        } catch (e: Exception) {
            // Corrupt, swapped or written with a lost key: a miss (ADDON-FR-123).
            file.delete()
            recency.remove(file.name)
            null
        }
    }

    private fun save(file: File, key: String, plain: ByteArray) {
        index()
        dir.mkdirs()
        // Leftover temporaries from a killed write go first.
        dir.listFiles { f -> f.name.startsWith(TEMP) }?.forEach { it.delete() }
        val sealed = cipher.seal(plain, (key + file.name.takeLast(2)).toByteArray())
        val temp = File.createTempFile(TEMP, ".tmp", dir)
        temp.writeBytes(sealed)
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                temp.delete()
                return
            }
        }
        recency[file.name] = file.length()
        evict()
    }

    /** The index starts from the files on disk, oldest modified first; afterwards only memory is touched. */
    private fun index() {
        if (loaded) return
        loaded = true
        dir.listFiles()?.filter { !it.name.startsWith(TEMP) }?.sortedBy { it.lastModified() }?.forEach { recency[it.name] = it.length() }
    }

    private fun evict() {
        var total = recency.values.sum()
        val it = recency.entries.iterator()
        while ((total > MAX_BYTES || recency.size > MAX_FILES) && it.hasNext()) {
            val e = it.next()
            File(dir, e.key).delete()
            total -= e.value
            it.remove()
        }
    }

    companion object {
        const val DIR: String = "addon-responses"
        private const val BODY = ".b"
        private const val PREVIEW = ".p"
        private const val TEMP = "response-"
        private const val DEFAULT_TTL_S = 300L
        private const val MAX_TTL_S = 86_400L
        private const val MAX_BYTES = 32L * 1024 * 1024
        private const val MAX_FILES = 128

        /** The cache key (FR-122): profile, installation and its revision, the request; never the URL. */
        fun key(profile: String, installation: String, revision: Long, resource: String, type: String, id: String, extras: Map<String, String>): String =
            com.sohva.tv.feature.discover.protocol.Hashes.parts(
                profile, installation, revision.toString(), resource, type, id,
                extras.toSortedMap().entries.joinToString("&") { "${it.key.length}:${it.key}=${it.value.length}:${it.value}" },
            )
    }
}

/** The preview entry: the first titles of a shelf and the page's raw count, as small JSON. */
internal object PreviewJson {
    fun write(out: Buffer, page: CatalogPage) {
        JsonWriter.of(out).use { w ->
            w.beginObject()
            w.name("count").value(page.receivedCount.toLong())
            w.name("items").beginArray()
            page.items.forEach { m ->
                w.beginObject()
                w.name("type").value(m.type)
                w.name("id").value(m.id)
                w.name("name").value(m.name)
                m.poster?.let { w.name("poster").value(it) }
                m.background?.let { w.name("background").value(it) }
                m.logo?.let { w.name("logo").value(it) }
                m.description?.let { w.name("description").value(it) }
                m.releaseInfo?.let { w.name("releaseInfo").value(it) }
                if (m.genres.isNotEmpty()) {
                    w.name("genres").beginArray()
                    m.genres.forEach { w.value(it) }
                    w.endArray()
                }
                m.runtime?.let { w.name("runtime").value(it) }
                m.imdbRating?.let { w.name("imdbRating").value(it) }
                m.defaultVideoId?.let { w.name("defaultVideoId").value(it) }
                w.endObject()
            }
            w.endArray()
            w.endObject()
        }
    }

    fun read(input: Buffer): CatalogPage {
        var count = 0
        val items = ArrayList<MetaPreview>()
        JsonReader.of(input).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "count" -> count = r.nextInt()
                    "items" -> {
                        r.beginArray()
                        while (r.hasNext()) items += item(r)
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        return CatalogPage(items, count)
    }

    private fun item(r: JsonReader): MetaPreview {
        val f = HashMap<String, String>()
        var genres: List<String> = emptyList()
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            if (name == "genres") {
                val g = ArrayList<String>()
                r.beginArray()
                while (r.hasNext()) g += r.nextString()
                r.endArray()
                genres = g
            } else {
                f[name] = r.nextString()
            }
        }
        r.endObject()
        return MetaPreview(
            f.getValue("type"), f.getValue("id"), f.getValue("name"), f["poster"], f["background"], f["logo"], f["description"],
            f["releaseInfo"], genres, f["runtime"], f["imdbRating"], f["defaultVideoId"],
        )
    }
}
