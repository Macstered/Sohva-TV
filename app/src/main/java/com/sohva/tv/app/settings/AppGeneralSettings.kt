package com.sohva.tv.app.settings

import android.app.LocaleManager
import android.os.Build
import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.core.model.player.BufferProfile
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.ReconnectPolicy
import com.sohva.tv.core.model.player.SkipStep
import com.sohva.tv.core.model.player.SubtitleBackground
import com.sohva.tv.core.model.player.SubtitleColor
import com.sohva.tv.core.model.player.SubtitleSize
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.core.model.settings.TimeZones
import com.sohva.tv.core.model.settings.VodLanguageSlot
import com.sohva.tv.core.model.settings.ZoneRow
import com.sohva.tv.feature.settings.GeneralSettingsServices
import com.sohva.tv.feature.settings.PlaybackSettingsServices
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Settings › General's own rows on the preferences (spec 70 §4.5); [applyLanguage] recreates the activity. */
class AppGeneralSettings(private val graph: AppGraph, private val applyLanguage: (String?) -> Unit) : GeneralSettingsServices {
    private val io get() = graph.dispatchers.io
    private val prefs get() = graph.data.preferences

    /** From Android 13 the platform holds the choice; below it, the small locale file (spec 74). */
    override suspend fun languageTag(): String? = withContext(io) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            graph.app.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language
        } else {
            LocaleStore(graph.app).languageTag()
        }
    }

    override fun applyLanguage(tag: String?) = applyLanguage.invoke(tag)

    override fun scale(): Flow<InterfaceScale> = prefs.scale.flowOn(io)

    override suspend fun setScale(value: InterfaceScale) = withContext(io) { prefs.setScale(value) }

    override fun theme(): Flow<ColorThemeId> = prefs.theme.flowOn(io)

    override suspend fun setTheme(value: ColorThemeId) = withContext(io) { prefs.setTheme(value) }

    override fun channelNumbers(): Flow<Boolean> = prefs.showChannelNumbers.flowOn(io)

    override suspend fun setChannelNumbers(on: Boolean) = withContext(io) { prefs.setShowChannelNumbers(on) }

    override fun timeZone(): Flow<String?> = prefs.timeZone.flowOn(io)

    override suspend fun setTimeZone(id: String?) = withContext(io) { prefs.setTimeZone(id) }

    override fun deviceZone(): ZoneRow = TimeZones.row(ZoneId.systemDefault().id, Instant.now())

    override suspend fun zones(): List<ZoneRow> = withContext(graph.dispatchers.ui) { TimeZones.rows(ZoneId.getAvailableZoneIds(), Instant.now()) }

    override fun startupScreen(): Flow<StartupScreen> = prefs.startupScreen.flowOn(io)

    override suspend fun setStartupScreen(value: StartupScreen) = withContext(io) { prefs.setStartupScreen(value) }
}

/** Settings › Playback on the preferences (spec 70 §4.7); the player reads them at the next playback. */
class AppPlaybackSettings(private val graph: AppGraph) : PlaybackSettingsServices {
    private val io get() = graph.dispatchers.io
    private val prefs get() = graph.data.preferences

    override fun settings(): Flow<PlaybackSettings> = prefs.playbackSettings.flowOn(io)

    override fun autoPlayNext(): Flow<Boolean> = prefs.autoPlayNext.flowOn(io)

    override suspend fun setBuffer(value: BufferProfile) = withContext(io) { prefs.setBuffer(value) }

    override suspend fun setReconnect(value: ReconnectPolicy) = withContext(io) { prefs.setReconnect(value) }

    override suspend fun setSkipStep(value: SkipStep) = withContext(io) { prefs.setSkipStep(value) }

    override suspend fun setMatchFrameRate(on: Boolean) = withContext(io) { prefs.setMatchFrameRate(on) }

    override suspend fun setAutoPlayNext(on: Boolean) = withContext(io) { prefs.setAutoPlayNext(on) }

    override suspend fun setPictureInPicture(on: Boolean) = withContext(io) { prefs.setPictureInPicture(on) }

    override suspend fun setSubtitleSize(value: SubtitleSize) = withContext(io) { prefs.setSubtitleSize(value) }

    override suspend fun setSubtitleColor(value: SubtitleColor) = withContext(io) { prefs.setSubtitleColor(value) }

    override suspend fun setSubtitleBackground(value: SubtitleBackground) = withContext(io) { prefs.setSubtitleBackground(value) }

    override suspend fun setVodLanguage(slot: VodLanguageSlot, code: String?) = withContext(io) { prefs.setVodLanguage(slot, code) }
}
