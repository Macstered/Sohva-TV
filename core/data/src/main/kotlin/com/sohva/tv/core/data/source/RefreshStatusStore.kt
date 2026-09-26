package com.sohva.tv.core.data.source

import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.error.AppErrors
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.RefreshState
import com.sohva.tv.core.model.source.SourceHealth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The refresh state of every source, for Settings' rows and health line (spec 10 SRC-FR-37…39).
 * `source_status` has at most three rows per source (≤ 300), so observing it whole is cheap, and
 * it updates while background imports run.
 */
class RefreshStatusStore(private val db: SohvaDatabase, private val io: CoroutineDispatcher) {
    fun observe(): Flow<List<SourceHealth>> = db.sourceStatus().observeAll().map { rows -> rows.mapNotNull(::toHealth) }

    /** One kind's stored state, read now (after an import finishes, before any observer has caught up). */
    suspend fun get(sourceId: String, kind: RefreshKind): SourceHealth? = withContext(io) {
        db.sourceStatus().get(sourceId, kind.id)?.let(::toHealth)
    }

    /** Films and series a source has now, for "Imported … and …" (SRC-FR-31); two index counts. */
    suspend fun catalogueCounts(sourceId: String): Pair<Int, Int> = withContext(io) {
        val films = KeyRange.movies(sourceId).let { db.movieImport().count(it.from, it.until) }
        val series = KeyRange.series(sourceId).let { db.seriesImport().count(it.from, it.until) }
        films to series
    }

    internal fun toHealth(row: SourceStatusEntity): SourceHealth? {
        val kind = RefreshKind.fromId(row.kind) ?: return null
        val state = RefreshState.fromStored(row.status)
        val error = row.errorCode?.takeIf { state == RefreshState.FAILED }?.let { code ->
            AppErrors.restore(code, row.errorArgs?.split(ARG_SEPARATOR).orEmpty())
        }
        return SourceHealth(row.sourceId, kind, state, error, row.itemCount, row.consecutiveFailures)
    }

    private companion object {
        const val ARG_SEPARATOR = '\u001F'
    }
}
