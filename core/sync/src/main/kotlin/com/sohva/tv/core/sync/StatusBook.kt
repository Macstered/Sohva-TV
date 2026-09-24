package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.SourceStatusDao
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.RefreshState
import com.sohva.tv.core.model.time.Clock

/**
 * The refresh state of spec 10 SRC-FR-76…78: `running` when an import starts, `success` or
 * `failed` when it ends, and when it is cancelled the row as it was before (cancellation is not a
 * failure). Called on the writer thread only.
 */
internal class StatusBook(private val dao: SourceStatusDao, private val clock: Clock) {
    /** Marks the start; returns the previous row (to restore on cancellation) and the new generation. */
    fun start(sourceId: String, kind: RefreshKind): Pair<SourceStatusEntity?, Long> {
        val previous = dao.get(sourceId, kind.id)
        val generation = (previous?.generation ?: 0L) + 1
        dao.upsert(
            (previous ?: empty(sourceId, kind)).copy(
                status = RUNNING,
                lastAttemptAt = clock.wallMillis(),
                errorCode = null,
                errorArgs = null,
                generation = generation,
            ),
        )
        return previous to generation
    }

    fun succeed(sourceId: String, kind: RefreshKind, count: Int, epgSnapshot: Long? = null, epgMaxDurationMs: Long? = null) {
        val row = dao.get(sourceId, kind.id) ?: empty(sourceId, kind)
        dao.upsert(
            row.copy(
                status = SUCCESS,
                lastSuccessAt = clock.wallMillis(),
                errorCode = null,
                errorArgs = null,
                itemCount = count,
                consecutiveFailures = 0,
                epgSnapshot = epgSnapshot ?: row.epgSnapshot,
                epgMaxDurationMs = epgMaxDurationMs ?: row.epgMaxDurationMs,
            ),
        )
    }

    fun fail(sourceId: String, kind: RefreshKind, error: AppError) {
        val row = dao.get(sourceId, kind.id) ?: empty(sourceId, kind)
        dao.upsert(
            row.copy(
                status = FAILED,
                lastFailureAt = clock.wallMillis(),
                errorCode = error.code,
                errorArgs = error.args.takeIf { it.isNotEmpty() }?.joinToString(ARG_SEPARATOR.toString()) { it.replace(ARG_SEPARATOR, ' ') },
                consecutiveFailures = row.consecutiveFailures + 1,
            ),
        )
    }

    /** Puts back the row as it was, keeping the new generation so a snapshot number is never reused. */
    fun cancelled(sourceId: String, kind: RefreshKind, previous: SourceStatusEntity?, generation: Long) {
        dao.upsert((previous ?: empty(sourceId, kind).copy(status = IDLE)).copy(generation = generation))
    }

    fun get(sourceId: String, kind: RefreshKind): SourceStatusEntity? = dao.get(sourceId, kind.id)

    private fun empty(sourceId: String, kind: RefreshKind) = SourceStatusEntity(
        sourceId = sourceId, kind = kind.id, status = IDLE, lastAttemptAt = null, lastSuccessAt = null, lastFailureAt = null,
        errorCode = null, errorArgs = null, itemCount = 0, consecutiveFailures = 0, generation = 0, epgSnapshot = null,
        epgMaxDurationMs = null,
    )

    companion object {
        val IDLE = RefreshState.IDLE.id
        val RUNNING = RefreshState.RUNNING.id
        val SUCCESS = RefreshState.SUCCESS.id
        val FAILED = RefreshState.FAILED.id
        const val ARG_SEPARATOR = '\u001F'
    }
}
