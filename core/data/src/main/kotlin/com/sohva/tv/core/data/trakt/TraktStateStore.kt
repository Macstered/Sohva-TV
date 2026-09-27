package com.sohva.tv.core.data.trakt

import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.TraktStateEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** What the Trakt sync reads and writes (spec 51 FR-22); a fake stands in for it in JVM tests. */
interface TraktStateTable {
    /** One page of [profile]'s rows of [kind] after [after], in key order. */
    suspend fun page(profile: String, kind: String, after: String, limit: Int): List<TraktStateEntity>

    /** Deletes and upserts in one transaction; a no-op when both are empty. */
    suspend fun write(profile: String, deletes: List<String>, upserts: List<TraktStateEntity>)

    suspend fun count(profile: String): Int
}

/**
 * The `trakt_state` cache (spec 51 §6). The sync writes only the rows that changed (§9 rule), so
 * observers re-run only when something did; [revision] moves once per write that changed rows.
 */
class TraktStateStore(private val db: SohvaDatabase, private val io: CoroutineDispatcher) : TraktStateTable {
    private val dao get() = db.trakt()
    private val _revision = MutableStateFlow(0L)

    /** The history revision (FR-22 step 5): overlays and resume reads refresh when it moves. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    override suspend fun page(profile: String, kind: String, after: String, limit: Int): List<TraktStateEntity> =
        withContext(io) { dao.page(profile, kind, after, limit) }

    override suspend fun write(profile: String, deletes: List<String>, upserts: List<TraktStateEntity>) {
        if (deletes.isEmpty() && upserts.isEmpty()) return
        withContext(io) {
            // Chunks of 500 keep each statement under SQLite's bound-variable limit (FR-22 step 5).
            db.runInTransaction {
                deletes.chunked(CHUNK).forEach { dao.delete(profile, it) }
                upserts.chunked(CHUNK).forEach { dao.upsert(it) }
            }
        }
        _revision.value++
    }

    /**
     * The rows of the titles on screen (§9 rule: only visible keys, never the whole table), by
     * either id; a row found by both comes once.
     */
    suspend fun rows(profile: String, imdbs: Collection<String>, tmdbs: Collection<Long>): List<TraktStateEntity> = withContext(io) {
        val out = LinkedHashMap<String, TraktStateEntity>()
        imdbs.distinct().chunked(LOOKUP).forEach { chunk -> dao.byImdb(profile, chunk).forEach { out[it.key] = it } }
        tmdbs.distinct().chunked(LOOKUP).forEach { chunk -> dao.byTmdb(profile, chunk).forEach { out[it.key] = it } }
        out.values.toList()
    }

    override suspend fun count(profile: String): Int = withContext(io) { dao.count(profile) }

    /** Disconnect, a different account, or a removed profile (FR-10, §6). */
    suspend fun forget(profile: String) {
        withContext(io) { dao.deleteProfile(profile) }
        _revision.value++
    }

    companion object {
        const val CHUNK: Int = 500
        const val LOOKUP: Int = 200
    }
}
