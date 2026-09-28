package com.sohva.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.sohva.tv.core.data.prefs.AppPreferences
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.core.model.settings.StartSnapshot
import com.sohva.tv.core.model.settings.StartupScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesTest {
    /**
     * In memory: these tests cover key names, defaults and narrow flows. DataStore's own file
     * storage cannot rename its temporary file on a Windows JVM; persistence is checked on device.
     */
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private fun prefs(): AppPreferences = AppPreferences(MemoryStore())

    @Test
    fun defaultsBeforeAnythingIsSaved() = runTest {
        assertEquals(
            StartSnapshot(ColorThemeId.ORIGINAL, InterfaceScale.NORMAL, StartupScreen.HOME),
            prefs().startSnapshot(),
        )
    }

    @Test
    fun savedValuesComeBackInTheSnapshot() = runTest {
        val prefs = prefs()
        prefs.setTheme(ColorThemeId.KANAGAWA)
        prefs.setScale(InterfaceScale.SMALLER)
        prefs.setStartupScreen(StartupScreen.GUIDE)
        assertEquals(
            StartSnapshot(ColorThemeId.KANAGAWA, InterfaceScale.SMALLER, StartupScreen.GUIDE),
            prefs.startSnapshot(),
        )
    }

    @Test
    fun changingOneSettingDoesNotWakeObserversOfAnother() = runTest {
        val prefs = prefs()
        val themes = mutableListOf<ColorThemeId>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { prefs.theme.toList(themes) }
        prefs.setScale(InterfaceScale.COMPACT)
        prefs.setStartupScreen(StartupScreen.LAST_CHANNEL)
        prefs.setTheme(ColorThemeId.NORD)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(ColorThemeId.ORIGINAL, ColorThemeId.NORD), themes)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        assertEquals(ColorThemeId.ORIGINAL, ColorThemeId.fromStored("sepia"))
        assertEquals(InterfaceScale.NORMAL, InterfaceScale.fromStored("HUGE"))
        assertEquals(StartupScreen.HOME, StartupScreen.fromStored(null))
    }

    /** Spec 71 BACKUP-FR-22: every stored key is either carried by backups or explicitly kept on the TV. */
    /** Spec 02 HOME-FR-86, -91: each profile its own layout; the default is no value; removing a profile removes it. */
    @Test
    fun eachProfileHasItsOwnHomeLayout() = runTest {
        val p = prefs()
        assertEquals(com.sohva.tv.core.model.home.HomeLayout.DEFAULT, p.homeLayout("p1").first())
        val kids = com.sohva.tv.core.model.home.HomeLayout.DEFAULT.withShown("recommended", false)
        p.setHomeLayout("p1", kids)
        assertEquals(kids, p.homeLayout("p1").first())
        assertEquals("the default profile keeps its own", com.sohva.tv.core.model.home.HomeLayout.DEFAULT, p.homeLayout("default").first())
        assertEquals(kids.encode(), p.homeLayoutText("p1"))
        p.setHomeLayout("p1", com.sohva.tv.core.model.home.HomeLayout.DEFAULT)
        assertEquals(null, p.homeLayoutText("p1"))
        p.setHomeLayout("p1", kids)
        p.forgetProfile("p1")
        assertEquals(null, p.homeLayoutText("p1"))
    }

    /** HOME-FR-91: a restore writes each profile's layout from the file and clears the ones the file lacks. */
    @Test
    fun aRestoreBringsEachProfilesLayout() = runTest {
        val p = prefs()
        p.setHomeLayout("gone", com.sohva.tv.core.model.home.HomeLayout.DEFAULT.withShown("recommended", false))
        val kids = "recent-channels,-watch-next,continue-watching,todays-sport,recommended"
        p.restoreBackup(
            com.sohva.tv.core.data.backup.BackupPreferences(timeZoneId = null, profiles = listOf(com.sohva.tv.core.data.backup.BackupProfile("kids", "Kids", 1))),
            mapOf("kids" to com.sohva.tv.core.data.backup.ProfileKept(homeLayout = kids), "default" to com.sohva.tv.core.data.backup.ProfileKept()),
            pinConfigured = false,
        )
        assertEquals(kids, p.homeLayoutText("kids"))
        assertEquals(null, p.homeLayoutText("default"))
        assertEquals("a profile the file does not have keeps nothing", null, p.homeLayoutText("gone"))
    }

    @Test
    fun everyKeyIsBackedUpOrDeviceLocal() {
        val keys = AppPreferences::class.java.declaredFields
            .filter { androidx.datastore.preferences.core.Preferences.Key::class.java.isAssignableFrom(it.type) }
            .map { field -> field.isAccessible = true; (field.get(null) as androidx.datastore.preferences.core.Preferences.Key<*>).name }
        org.junit.Assert.assertTrue("no keys found", keys.size > 20)
        val unknown = keys.filter { it !in AppPreferences.BACKED_UP && it !in AppPreferences.DEVICE_LOCAL }
        assertEquals(emptyList<String>(), unknown)
    }
}
