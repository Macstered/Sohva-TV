package com.sohva.tv.app

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.prefs.LocaleStore
import kotlinx.coroutines.runBlocking
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
            graph.data.database.openHelper.writableDatabase.execSQL("DELETE FROM favourite_channel")
            graph.data.database.openHelper.writableDatabase.execSQL("DELETE FROM recent_channel")
        }
        graph.guideFocusChannel = null
    }
}
