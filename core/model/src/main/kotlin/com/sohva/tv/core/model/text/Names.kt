package com.sohva.tv.core.model.text

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Name normalisation of plan/04 §6: NFKD, combining marks removed, lower-cased in the root locale,
 * every run outside `[a-z0-9]` one space, trimmed. Non-Latin letters become spaces. Part of the
 * compatibility contract: channel ids and group keys depend on it.
 */
object NameNormalizer {
    private val marks = Regex("\\p{M}+")
    private val other = Regex("[^a-z0-9]+")

    fun normalize(name: String): String {
        val decomposed = Normalizer.normalize(name, Normalizer.Form.NFKD)
        return other.replace(marks.replace(decomposed, "").lowercase(Locale.ROOT), " ").trim()
    }
}

/**
 * The identities of plan/04 §6 that imports produce. Reusing one digest per caller (they are not
 * thread-safe) and hex by table keeps hashing off the import's hot path (spec 10 SRC-FR-66).
 */
class StableIds {
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    /** Lower-case SHA-256 hex of [text] in UTF-8. */
    fun sha256Hex(text: String): String = hex(digest.digest(text.toByteArray(Charsets.UTF_8)), 32)

    /** M3U entry id: `tvgId|normalisedName` when a tvg-id exists, else `normalisedName|streamUrl`. */
    fun m3uEntryId(tvgId: String?, normalizedName: String, streamUrl: String): String =
        if (!tvgId.isNullOrBlank()) sha256Hex("$tvgId|$normalizedName") else sha256Hex("$normalizedName|$streamUrl")

    /** Programme key: the first 16 hex characters of SHA-256 of `channel|start|stop|title`. */
    fun programmeKey(epgId: String, startMillis: Long, stopMillis: Long, title: String): String =
        hex(digest.digest("$epgId|$startMillis|$stopMillis|$title".toByteArray(Charsets.UTF_8)), 8)

    private fun hex(bytes: ByteArray, count: Int): String {
        val out = CharArray(count * 2)
        for (i in 0 until count) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
    }
}

/** Keys that group, look up and filter, per plan/04 §6. */
object Keys {
    fun globalChannelId(sourceId: String, localId: String): String = "$sourceId:$localId"

    /** Xtream channel local id: `xtream-<stream_id>`. */
    fun xtreamChannelLocalId(streamId: String): String = "xtream-$streamId"

    fun movieKey(sourceId: String, movieId: String): String = "vod:movie:$sourceId:$movieId"

    fun seriesKey(sourceId: String, seriesId: String): String = "series:$sourceId:$seriesId"

    fun episodeKey(sourceId: String, episodeId: String): String = "vod:episode:$sourceId:$episodeId"

    /** `id:<providerGroupId>` when the provider gives an id, else `name:` + trimmed lower-case title. */
    fun groupKey(providerGroupId: String?, title: String?): String =
        if (!providerGroupId.isNullOrBlank()) "id:$providerGroupId" else "name:" + title.orEmpty().trim().lowercase(Locale.ROOT)

    /**
     * A 64-bit FNV-1a hash of a string, for in-memory sets of ids (the EPG keep-set, keys seen by a
     * diff import): about 8 bytes per id instead of a String (spec 10 SRC-L-04).
     */
    fun hash64(text: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (c in text) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }
}
