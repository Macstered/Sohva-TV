package com.sohva.tv.app.settings

import android.net.Uri
import android.provider.DocumentsContract
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.backup.BackupService
import com.sohva.tv.core.data.backup.BackupPayload
import com.sohva.tv.core.model.backup.BackupException
import com.sohva.tv.core.model.backup.BackupProblem
import com.sohva.tv.feature.settings.BackupOutcome
import com.sohva.tv.feature.settings.BackupSettingsServices
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Settings › Backup & tools from the app graph. A restore runs in the app's scope, so leaving
 * Settings does not stop it half-way (spec 71 §9); [afterRestore] then returns to Home when the
 * restored active profile is a restricted one (§8).
 */
class AppBackupSettings(private val graph: AppGraph, private val afterRestore: () -> Unit) : BackupSettingsServices {
    private val service by lazy { BackupService(graph) }
    private val io get() = graph.dispatchers.io
    private val resolver get() = graph.app.contentResolver

    /** What [open] read, until [restore] or [discard]. */
    @Volatile private var pending: BackupPayload? = null

    override suspend fun save(target: Uri, password: CharArray): BackupOutcome = withContext(io) {
        val outcome = attempt {
            // "wt": truncate, so an older, longer file of the same name leaves nothing behind (BACKUP-FR-07 step 7).
            val out = resolver.openOutputStream(target, "wt") ?: throw BackupException(BackupProblem.OPEN)
            out.use { service.save(it, password) }
            BackupOutcome.Saved
        }
        if (outcome is BackupOutcome.Failed) outcome.copy(fileKept = !delete(target)) else outcome
    }

    override suspend fun open(target: Uri, password: CharArray): BackupOutcome = withContext(io) {
        attempt {
            val input = resolver.openInputStream(target) ?: throw BackupException(BackupProblem.OPEN)
            val payload = input.use { service.open(it, password) }
            pending = payload
            BackupOutcome.Opened(service.removedBy(payload))
        }
    }

    override suspend fun restore(): BackupOutcome {
        val payload = pending ?: return BackupOutcome.Failed(null)
        pending = null
        val outcome = graph.appScope.async(io) { attempt { service.restore(payload); BackupOutcome.Restored } }.await()
        if (outcome == BackupOutcome.Restored) {
            val active = graph.data.profiles.activeId
            if (active in graph.data.profiles.restrictedIds()) withContext(graph.dispatchers.main) { afterRestore() }
        }
        return outcome
    }

    override fun discard() {
        pending = null
    }

    override suspend fun unfinished(): Boolean = service.unfinished()

    override suspend fun clearGuide() = withContext(io) { graph.sync.runner.clearGuide() }

    private inline fun attempt(block: () -> BackupOutcome): BackupOutcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackupException) {
        BackupOutcome.Failed(e.problem, e.field)
    } catch (_: IOException) {
        // A pulled stick or a provider that refused the file (spec 71 §8): the open error's words.
        BackupOutcome.Failed(BackupProblem.OPEN)
    } catch (_: SecurityException) {
        BackupOutcome.Failed(BackupProblem.OPEN)
    } catch (_: IllegalArgumentException) {
        BackupOutcome.Failed(BackupProblem.OPEN)
    }

    /** BACKUP-FR-10: the unfinished file goes; false when the provider would not remove it. */
    private fun delete(target: Uri): Boolean = try {
        DocumentsContract.deleteDocument(resolver, target)
    } catch (_: Exception) {
        false
    }
}
