package com.sohva.tv.feature.discover.store

import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.Hashes
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer

/** A saved title (ADDON-FR-110): identity (installation, original type and id), artwork and release info. */
data class LibraryTitle(
    val installation: String,
    val type: String,
    val id: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val year: String?,
    val addedAt: Long,
) {
    override fun toString(): String = "LibraryTitle($type)"

    companion object {
        fun key(profile: String, installation: String, type: String, id: String): String = Hashes.parts(profile, installation, type, id)
    }
}

/**
 * The Discover Library (spec 50 §4.15): at most 1,000 titles per profile, checked inside the insert
 * transaction; nothing is ever evicted; re-adding keeps the first added time. It never contacts
 * an addon and does not follow reinstalls (FR-112).
 */
class LibraryStore(private val dao: LibraryDao, private val cipher: DiscoverCipher, private val clock: Clock) {
    suspend fun titles(profile: String): List<LibraryTitle> = dao.newest(profile, CAPACITY + 1).mapNotNull(::decode)

    suspend fun contains(profile: String, installation: String, type: String, id: String): Boolean =
        dao.get(LibraryTitle.key(profile, installation, type, id)) != null

    /** False when the Library is full ("Library is full (1,000 titles)…"). */
    suspend fun add(profile: String, title: LibraryTitle): Boolean {
        val key = LibraryTitle.key(profile, title.installation, title.type, title.id)
        val kept = dao.get(key)?.let(::decode)?.addedAt ?: clock.wallMillis()
        val row = title.copy(addedAt = kept, year = title.year?.take(MAX_YEAR))
        return dao.add(LibraryEntity(key, profile, encode(row), kept), CAPACITY)
    }

    /** Beta 23's title with its own added time (decision A1); false when the Library is full. */
    suspend fun restore(profile: String, title: LibraryTitle): Boolean =
        dao.add(LibraryEntity(LibraryTitle.key(profile, title.installation, title.type, title.id), profile, encode(title), title.addedAt), CAPACITY)

    suspend fun remove(profile: String, installation: String, type: String, id: String) = dao.delete(LibraryTitle.key(profile, installation, type, id))

    suspend fun forgetProfile(profile: String) = dao.deleteProfile(profile)

    private fun decode(row: LibraryEntity): LibraryTitle? = runCatching {
        val f = HashMap<String, String?>()
        JsonReader.of(Buffer().writeUtf8(cipher.decrypt(row.payload))).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                val name = r.nextName()
                f[name] = when (r.peek()) {
                    JsonReader.Token.NULL -> r.nextNull<String>()
                    JsonReader.Token.STRING -> r.nextString()
                    else -> {
                        r.skipValue()
                        null
                    }
                }
            }
            r.endObject()
        }
        LibraryTitle(f["installation"]!!, f["type"]!!, f["id"]!!, f["name"]!!, f["poster"], f["background"], f["year"], row.addedAt)
    }.getOrNull()

    private fun encode(t: LibraryTitle): String {
        val out = Buffer()
        JsonWriter.of(out).use { w ->
            w.beginObject()
            w.name("version").value("1")
            w.name("installation").value(t.installation)
            w.name("type").value(t.type)
            w.name("id").value(t.id)
            w.name("name").value(t.name)
            w.name("poster").value(t.poster)
            w.name("background").value(t.background)
            w.name("year").value(t.year)
            w.endObject()
        }
        if (out.size > MAX_PAYLOAD) throw AddonException(AddonFailure.STORAGE)
        return cipher.encrypt(out.readUtf8())
    }

    companion object {
        const val CAPACITY: Int = 1_000
        private const val MAX_YEAR = 256
        private const val MAX_PAYLOAD = 128 * 1024L
    }
}
