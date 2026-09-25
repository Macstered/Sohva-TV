package com.sohva.tv.app

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.metadata.MetadataSettings
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.core.data.source.ServiceKeys
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.rules.ExternalResource

/**
 * Resets every persisted setting that can change the first screen, through the stores
 * themselves, before and after each test (lessons 7.5). Extend it whenever a new persisted
 * setting can change start-up: language, theme, size and start screen, and the sources (removed
 * through the import runner, so their rows and secrets go too); favourites, recents and the
 * guide's session channel; profiles join in their milestone. Refuses to run against anything but the debug app.
 */
class ClearStateRule : ExternalResource() {
    override fun before() = reset()

    override fun after() = reset()

    private fun reset() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        check(app.packageName == "com.streammate.tv.debug") { "clear-state runs only against the debug app, not ${app.packageName}" }
        val graph = (app as SohvaApplication).graph
        LocaleStore(app).setLanguageTag(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        }
        runBlocking {
            graph.data.preferences.resetToDefaults()
            graph.data.sources.all().forEach { graph.sync.runner.remove(it.id) }
            // Favourites and recents outlive their channels by design; tests start without them.
            // So do the household's channel edits, lists, locks and reminders (M3), positions and organisation rules (M4).
            for (table in listOf("favourite_channel", "recent_channel", "channel_custom", "channel_list", "channel_list_member", "locked_channel", "reminder", "watch_progress", "organization_rule")) {
                graph.data.database.openHelper.writableDatabase.execSQL("DELETE FROM $table")
            }
            // Metadata (M4b): the keys and switches, what was looked up, and the production endpoints.
            for (key in listOf(ServiceKeys.TMDB_TOKEN, ServiceKeys.TMDB_ENABLED, MetadataSettings.TVMAZE_ENABLED)) graph.data.secrets.write(key, null)
            for (table in listOf("metadata_match", "metadata_cache", "metadata_pin", "metadata_queue")) {
                graph.data.database.openHelper.writableDatabase.execSQL("DELETE FROM $table")
            }
            graph.metadata.settings.reload()
            graph.metadata.useEndpoints("https://api.themoviedb.org/3/".toHttpUrl(), "https://api.tvmaze.com/".toHttpUrl())
        }
        graph.guideFocusChannel = null
        // The walls' browse sessions live for the process (VOD-FR-56); each test starts at a first visit.
        graph.browseSessions.values.forEach { it.clear() }
        // Continue watching lives for the process (spec 02 HOME-FR-24): read again for this test's rows.
        graph.continueFeed.retry()
        // The ringing queue lives in the process; the tables were emptied above, so this also cancels the alarm.
        graph.reminders.resetForTests()
        runBlocking { graph.reminders.reschedule() }
        graph.liveReadsOverride = null
    }
}
