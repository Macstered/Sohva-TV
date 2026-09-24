package com.sohva.tv.core.data.database

/**
 * The key range holding one source's rows of a table: every content key starts with a fixed
 * prefix and the source id (plan/04 §6), and source ids cannot contain `:`, so
 * `[prefix + id + ":", prefix + id + ";")` is exactly that source. Walking the UNIQUE key index over
 * the range replaces a `source_id` index on each large table.
 */
data class KeyRange(val from: String, val until: String) {
    companion object {
        private fun of(prefix: String, sourceId: String): KeyRange = KeyRange("$prefix$sourceId:", "$prefix$sourceId;")

        fun channels(sourceId: String): KeyRange = of("", sourceId)

        fun movies(sourceId: String): KeyRange = of("vod:movie:", sourceId)

        fun series(sourceId: String): KeyRange = of("series:", sourceId)

        fun episodes(sourceId: String): KeyRange = of("vod:episode:", sourceId)
    }
}

/** A row's id, key and stored content hash: what the diff import compares. */
data class KeyedHash(val id: Long, val key: String, val hash: Long)

/** A row's id and key: what the sweep of unseen rows walks. */
data class KeyedId(val id: Long, val key: String)
