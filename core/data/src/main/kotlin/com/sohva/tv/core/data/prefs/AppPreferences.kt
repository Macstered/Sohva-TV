package com.sohva.tv.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.core.model.settings.StartSnapshot
import com.sohva.tv.core.model.settings.StartupScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Household settings. Consumers observe one key each, never the whole preferences object: in
 * beta 23 every zap wrote a recent channel and re-emitted all settings to every screen, the app
 * root included (plan/03 §2.4, §4.6).
 */
class AppPreferences(private val store: DataStore<Preferences>) {
    val theme: Flow<ColorThemeId> = key(THEME) { ColorThemeId.fromStored(it) }
    val scale: Flow<InterfaceScale> = key(SCALE) { InterfaceScale.fromStored(it) }
    val startupScreen: Flow<StartupScreen> = key(STARTUP_SCREEN) { StartupScreen.fromStored(it) }

    /** One read for the first frame. Call off the main thread. */
    suspend fun startSnapshot(): StartSnapshot {
        val prefs = store.data.first()
        return StartSnapshot(
            theme = ColorThemeId.fromStored(prefs[THEME]),
            scale = InterfaceScale.fromStored(prefs[SCALE]),
            startupScreen = StartupScreen.fromStored(prefs[STARTUP_SCREEN]),
        )
    }

    suspend fun setTheme(value: ColorThemeId) {
        store.edit { it[THEME] = value.id }
    }

    suspend fun setScale(value: InterfaceScale) {
        store.edit { it[SCALE] = value.name }
    }

    suspend fun setStartupScreen(value: StartupScreen) {
        store.edit { it[STARTUP_SCREEN] = value.name }
    }

    private fun <T> key(key: Preferences.Key<String>, parse: (String?) -> T): Flow<T> =
        store.data.map { parse(it[key]) }.distinctUntilChanged()

    companion object {
        /** A new file: beta 23's preferences are read once by the importer (decision A1). */
        const val FILE_NAME: String = "sohva_preferences"

        // Key names and stored values as beta 23 wrote them (spec 70), so backups stay readable.
        private val THEME = stringPreferencesKey("color_theme")
        private val SCALE = stringPreferencesKey("interface_scale")
        private val STARTUP_SCREEN = stringPreferencesKey("startup_screen")
    }
}
