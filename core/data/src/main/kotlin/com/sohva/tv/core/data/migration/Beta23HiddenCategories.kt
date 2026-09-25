package com.sohva.tv.core.data.migration

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.org.LegacyCategories
import com.sohva.tv.core.data.org.OrgPass
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.vod.PreferredCopy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Beta 23's hidden categories (`hidden_live_categories`, `hidden_movie_categories`,
 * `hidden_series_categories` in its DataStore file) become organisation rules once (spec 42
 * ORG-15). Runs after the first frame; reads the old file only, and does nothing when there is no
 * old file or the marker rule already exists. A file that cannot be read is left for a later start.
 */
class Beta23HiddenCategories(private val context: Context, private val db: SohvaDatabase, private val io: CoroutineDispatcher) {
    suspend fun run() = withContext(io) {
        val file = context.preferencesDataStoreFile(OLD_FILE)
        if (!file.exists()) return@withContext
        val rules = OrgRules(db)
        if (rules.all().any { it.key == LegacyCategories.MARKER }) return@withContext
        val scope = CoroutineScope(io + SupervisorJob())
        val prefs = try {
            runCatching { PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).data.first() }.getOrNull()
        } finally {
            // Released before returning, so a later DataStore on the same file is not refused.
            scope.coroutineContext[Job]?.cancelAndJoin()
        } ?: return@withContext
        val hidden = KEYS.mapValues { (_, key) -> prefs[stringSetPreferencesKey(key)].orEmpty() }
        val changes = LegacyCategories.changes(rules.all(), hidden)
        if (rules.change(changes) is Outcome.Ok) OrgPass(db, rules, LibraryPasses(db)).afterChange(changes.map { it.key }, PreferredCopy.NONE)
    }

    companion object {
        /** Beta 23's `preferencesDataStore(name = "streammate_preferences")`. */
        const val OLD_FILE = "streammate_preferences"

        private val KEYS = mapOf(
            OrgRoom.LIVE to "hidden_live_categories",
            OrgRoom.MOVIES to "hidden_movie_categories",
            OrgRoom.SERIES to "hidden_series_categories",
        )
    }
}
