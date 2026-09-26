package com.sohva.tv.feature.settings

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.core.model.settings.ZoneRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Settings › General's own rows do to the world (spec 70 §4.5); the app implements it. */
interface GeneralSettingsServices {
    /** The chosen interface language tag, or null for the TV's own (read off the main thread). */
    suspend fun languageTag(): String?

    /** Stores [tag] and applies it; the activity is recreated (SET-FR-50). */
    fun applyLanguage(tag: String?)

    fun scale(): Flow<InterfaceScale>

    suspend fun setScale(value: InterfaceScale)

    fun theme(): Flow<ColorThemeId>

    suspend fun setTheme(value: ColorThemeId)

    fun channelNumbers(): Flow<Boolean>

    suspend fun setChannelNumbers(on: Boolean)

    /** The chosen zone id, or null when following the TV. */
    fun timeZone(): Flow<String?>

    suspend fun setTimeZone(id: String?)

    /** The TV's own zone as a row (for "TV's own (…)"). */
    fun deviceZone(): ZoneRow

    /** Every zone of the dialog, built off the main thread (SET-FR-60). */
    suspend fun zones(): List<ZoneRow>

    fun startupScreen(): Flow<StartupScreen>

    suspend fun setStartupScreen(value: StartupScreen)
}

@Immutable
data class GeneralState(
    val language: String? = null,
    val scale: InterfaceScale = InterfaceScale.NORMAL,
    val theme: ColorThemeId = ColorThemeId.ORIGINAL,
    val channelNumbers: Boolean = true,
    val zone: String? = null,
    val startup: StartupScreen = StartupScreen.HOME,
)

/** Settings › General's own rows for the life of the Settings screen, owned by [SettingsModel]. */
class GeneralSettings internal constructor(private val services: GeneralSettingsServices, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(GeneralState())
    val state: StateFlow<GeneralState> = _state.asStateFlow()

    init {
        scope.launch { _state.update { it.copy(language = services.languageTag()?.takeIf { tag -> tag in LANGUAGES }) } }
        scope.launch { services.scale().collect { v -> _state.update { it.copy(scale = v) } } }
        scope.launch { services.theme().collect { v -> _state.update { it.copy(theme = v) } } }
        scope.launch { services.channelNumbers().collect { v -> _state.update { it.copy(channelNumbers = v) } } }
        scope.launch { services.timeZone().collect { v -> _state.update { it.copy(zone = v) } } }
        scope.launch { services.startupScreen().collect { v -> _state.update { it.copy(startup = v) } } }
    }

    /** A different language restarts the screen in it (SET-FR-50); the same one does nothing (SET-FR-21). */
    fun setLanguage(tag: String?) {
        if (tag == _state.value.language) return
        _state.update { it.copy(language = tag) }
        services.applyLanguage(tag)
    }

    fun setScale(value: InterfaceScale) = write { services.setScale(value) }

    fun setTheme(value: ColorThemeId) = write { services.setTheme(value) }

    fun toggleChannelNumbers() = write { services.setChannelNumbers(!_state.value.channelNumbers) }

    fun setZone(id: String?) = write { services.setTimeZone(id) }

    fun setStartup(value: StartupScreen) = write { services.setStartupScreen(value) }

    fun deviceZone(): ZoneRow = services.deviceZone()

    suspend fun zones(): List<ZoneRow> = services.zones()

    private fun write(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    companion object {
        /** The picker's tags after System default (SET-FR-50). */
        val LANGUAGES: List<String> = listOf("en", "fi", "es", "pt", "de", "sv", "it")
    }
}
