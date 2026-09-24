package com.sohva.tv.core.data.source

import com.sohva.tv.core.data.database.SohvaDatabase

/** The facts the background refresh worker decides on (spec 10 SRC-FR-98), without handing out the database. */
class RefreshFacts(private val db: SohvaDatabase) {
    /** Enabled live-TV sources that have never completed a playlist import. */
    suspend fun liveSourcesNeverImported(): Int = db.sources().liveSourcesNeverImported()

    /** Refreshes of [kinds] (ids) that failed at or after [sinceMillis]. */
    suspend fun failuresSince(sinceMillis: Long, kinds: List<String>): Int = db.sourceStatus().failuresSince(sinceMillis, kinds)
}
