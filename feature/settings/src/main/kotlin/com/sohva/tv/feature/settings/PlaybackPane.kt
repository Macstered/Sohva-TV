package com.sohva.tv.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.player.BufferProfile
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.ReconnectPolicy
import com.sohva.tv.core.model.player.SkipStep
import com.sohva.tv.core.model.player.SubtitleBackground
import com.sohva.tv.core.model.player.SubtitleColor
import com.sohva.tv.core.model.player.SubtitleSize
import com.sohva.tv.core.model.settings.VodLanguageSlot
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.TvIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Settings › Playback does to the world (spec 70 §4.7); the app implements it. */
interface PlaybackSettingsServices {
    fun settings(): Flow<PlaybackSettings>

    fun autoPlayNext(): Flow<Boolean>

    suspend fun setBuffer(value: BufferProfile)

    suspend fun setReconnect(value: ReconnectPolicy)

    suspend fun setSkipStep(value: SkipStep)

    suspend fun setMatchFrameRate(on: Boolean)

    suspend fun setAutoPlayNext(on: Boolean)

    suspend fun setPictureInPicture(on: Boolean)

    suspend fun setSubtitleSize(value: SubtitleSize)

    suspend fun setSubtitleColor(value: SubtitleColor)

    suspend fun setSubtitleBackground(value: SubtitleBackground)

    suspend fun setVodLanguage(slot: VodLanguageSlot, code: String?)
}

/** Settings › Playback for the life of the Settings screen; every write runs off the main thread. */
class PlaybackSettingsHolder internal constructor(private val services: PlaybackSettingsServices, private val scope: CoroutineScope) {
    val settings: StateFlow<PlaybackSettings> = services.settings().stateIn(scope, SharingStarted.Eagerly, PlaybackSettings())
    val autoPlayNext: StateFlow<Boolean> = services.autoPlayNext().stateIn(scope, SharingStarted.Eagerly, true)

    fun write(block: suspend PlaybackSettingsServices.() -> Unit) {
        scope.launch { services.block() }
    }
}

/**
 * Settings › Playback (spec 70 §4.7): buffer, recovery, skip step and the three switches; the
 * subtitle look; the VOD languages. The buffer row is the section's first control (SET-FR-14).
 */
@Composable
internal fun PlaybackPane(playback: PlaybackSettingsHolder, start: FocusRequester) {
    val s by playback.settings.collectAsStateWithLifecycle()
    val next by playback.autoPlayNext.collectAsStateWithLifecycle()
    SettingsGroup {
        ChoiceRow(
            stringResource(R.string.playback_buffer_title), s.buffer, SettingsLabels.buffers(), { v -> playback.write { setBuffer(v) } },
            "settings-playback-buffer", Modifier.focusRequester(start), TvIcons.Play, stringResource(R.string.playback_buffer_help),
        )
        ChoiceRow(
            stringResource(R.string.playback_reconnect_title), s.reconnect, SettingsLabels.reconnects(), { v -> playback.write { setReconnect(v) } },
            "settings-playback-reconnect", icon = TvIcons.Refresh, subtitle = stringResource(R.string.playback_reconnect_help), divider = true,
        )
        ChoiceRow(
            stringResource(R.string.playback_seek_step_title), s.skipStep, SettingsLabels.skipSteps(), { v -> playback.write { setSkipStep(v) } },
            "settings-playback-seek-step", icon = TvIcons.Replay, subtitle = stringResource(R.string.playback_seek_step_help), divider = true,
        )
        SwitchRow(
            stringResource(R.string.auto_frame_rate_title), s.matchFrameRate, { playback.write { setMatchFrameRate(!s.matchFrameRate) } },
            "settings-auto-frame-rate", TvIcons.Aspect, stringResource(R.string.auto_frame_rate_help),
        )
        SwitchRow(
            stringResource(R.string.auto_play_next_episode_title), next, { playback.write { setAutoPlayNext(!next) } },
            "settings-auto-play-next", TvIcons.Forward, stringResource(R.string.auto_play_next_episode_help),
        )
        SwitchRow(
            stringResource(R.string.picture_in_picture_title), s.pictureInPicture, { playback.write { setPictureInPicture(!s.pictureInPicture) } },
            "settings-picture-in-picture", TvIcons.Aspect, stringResource(R.string.picture_in_picture_help),
        )
    }
    SettingsGroup {
        ChoiceRow(
            stringResource(R.string.subtitle_size_title), s.subtitleSize, SettingsLabels.subtitleSizes(), { v -> playback.write { setSubtitleSize(v) } },
            "settings-subtitle-size", icon = TvIcons.Subtitles, subtitle = stringResource(R.string.subtitle_style_help),
        )
        ChoiceRow(
            stringResource(R.string.subtitle_color_title), s.subtitleColor, SettingsLabels.subtitleColors(), { v -> playback.write { setSubtitleColor(v) } },
            "settings-subtitle-color", icon = TvIcons.Subtitles, divider = true,
        )
        ChoiceRow(
            stringResource(R.string.subtitle_background_title), s.subtitleBackground, SettingsLabels.subtitleBackgrounds(),
            { v -> playback.write { setSubtitleBackground(v) } }, "settings-subtitle-background", icon = TvIcons.Subtitles, divider = true,
        )
    }
    SettingsGroup {
        SettingsOverline(stringResource(R.string.preferred_languages_title))
        val languages = SettingsLabels.vodLanguages()
        for ((i, slot) in VodLanguageSlot.entries.withIndex()) {
            ChoiceRow(
                stringResource(slotTitle(slot)), slot.of(s.vodLanguages), languages, { v -> playback.write { setVodLanguage(slot, v) } },
                "settings-vod-language-${slot.name.lowercase()}",
                icon = if (slot == VodLanguageSlot.AUDIO || slot == VodLanguageSlot.AUDIO_SECOND) TvIcons.Audio else TvIcons.Subtitles,
                subtitle = if (i == 0) stringResource(R.string.preferred_languages_help) else null,
                divider = i > 0,
            )
        }
    }
}

private fun slotTitle(slot: VodLanguageSlot): Int = when (slot) {
    VodLanguageSlot.AUDIO -> R.string.preferred_audio_primary
    VodLanguageSlot.AUDIO_SECOND -> R.string.preferred_audio_secondary
    VodLanguageSlot.SUBTITLES -> R.string.preferred_subtitle_primary
    VodLanguageSlot.SUBTITLES_SECOND -> R.string.preferred_subtitle_secondary
}
