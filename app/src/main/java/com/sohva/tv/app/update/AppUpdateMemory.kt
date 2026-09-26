package com.sohva.tv.app.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sohva.tv.core.sync.update.DownloadedUpdate
import com.sohva.tv.core.sync.update.UpdateMemory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The updater's small memory in beta 23's file `streammate_updates` (spec 72 §6): the last
 * successful check and each build's own release notes. Opened on first use, off the main thread
 * (ABOUT-NFR-01).
 */
class AppUpdateMemory(context: Context, private val io: CoroutineDispatcher) : UpdateMemory {
    private val app = context.applicationContext
    private val prefs: SharedPreferences by lazy { app.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

    override suspend fun lastCheck(): Long? = withContext(io) { prefs.getLong(LAST_CHECK, -1L).takeIf { it >= 0 } }

    override suspend fun setLastCheck(millis: Long) = withContext(io) { prefs.edit { putLong(LAST_CHECK, millis) } }

    override suspend fun notes(versionCode: Int): String? = withContext(io) { prefs.getString("$NOTES$versionCode", null) }

    override suspend fun setNotes(versionCode: Int, notes: String) = withContext(io) { prefs.edit { putString("$NOTES$versionCode", notes) } }

    override suspend fun downloaded(): DownloadedUpdate? = withContext(io) {
        val parts = prefs.getString(DOWNLOADED, null)?.split(SEPARATOR) ?: return@withContext null
        if (parts.size != 7) return@withContext null
        DownloadedUpdate(
            version = parts[0], build = parts[1].toIntOrNull() ?: return@withContext null, notes = parts[2].ifEmpty { null },
            apkName = parts[3], apkDigest = parts[4], profileName = parts[5].ifEmpty { null }, profileDigest = parts[6].ifEmpty { null },
        )
    }

    override suspend fun setDownloaded(update: DownloadedUpdate?) = withContext(io) {
        prefs.edit {
            if (update == null) {
                remove(DOWNLOADED)
            } else {
                val fields = listOf(update.version, update.build.toString(), update.notes.orEmpty(), update.apkName, update.apkDigest, update.profileName.orEmpty(), update.profileDigest.orEmpty())
                putString(DOWNLOADED, fields.joinToString(SEPARATOR.toString()))
            }
        }
    }

    private companion object {
        const val DOWNLOADED = "downloaded_update"
        const val SEPARATOR = '\u001F'
        const val FILE = "streammate_updates"
        const val LAST_CHECK = "last_check_epoch_millis"
        const val NOTES = "installed_notes_"
    }
}
