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
}
