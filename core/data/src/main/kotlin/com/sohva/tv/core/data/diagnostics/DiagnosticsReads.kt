package com.sohva.tv.core.data.diagnostics

import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.source.RefreshStatusStore
import com.sohva.tv.core.model.source.SourceHealth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A refresh state with its times, for the diagnostics file (spec 72 §7.6). */
data class RefreshRecord(val health: SourceHealth, val attemptedAt: Long?, val succeededAt: Long?, val failedAt: Long?)

/** The database's part of Save diagnostics: read only when the viewer saves (ABOUT-NFR-05). */
class DiagnosticsReads(private val db: SohvaDatabase, private val status: RefreshStatusStore, private val io: CoroutineDispatcher) {
    /** Every refresh state: at most three per source (≤ 300 rows). */
    suspend fun refreshRecords(): List<RefreshRecord> = withContext(io) {
        db.sourceStatus().all().mapNotNull { row ->
            status.toHealth(row)?.let { RefreshRecord(it, row.lastAttemptAt, row.lastSuccessAt, row.lastFailureAt) }
        }
    }

    suspend fun sqliteVersion(): String = withContext(io) {
        db.openHelper.readableDatabase.query("SELECT sqlite_version()").use { c -> if (c.moveToFirst()) c.getString(0) else "?" }
    }
}
