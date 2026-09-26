package com.sohva.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sohva.tv.core.model.player.BufferProfile
import com.sohva.tv.core.model.player.ReconnectPolicy
import com.sohva.tv.core.model.player.SkipStep
import com.sohva.tv.core.model.player.SubtitleBackground
import com.sohva.tv.core.model.player.SubtitleColor
import com.sohva.tv.core.model.player.SubtitleSize
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.PickerChoice

/** Every Settings choice list with its labels (spec 70 §6.1), in the order the pickers show them. */
internal object SettingsLabels {
    @Composable
    fun languages(): List<PickerChoice<String?>> =
        listOf(PickerChoice<String?>(null, stringResource(R.string.interface_language_system))) +
            GeneralSettings.LANGUAGES.map { PickerChoice<String?>(it, stringResource(languageName(it))) }

    @StringRes
    private fun languageName(tag: String): Int = when (tag) {
        "en" -> R.string.interface_language_en
        "fi" -> R.string.interface_language_fi
        "es" -> R.string.interface_language_es
        "pt" -> R.string.interface_language_pt
        "de" -> R.string.interface_language_de
        "sv" -> R.string.interface_language_sv
        else -> R.string.interface_language_it
    }

    @Composable
    fun scales(): List<PickerChoice<InterfaceScale>> = InterfaceScale.entries.map {
        val name = when (it) {
            InterfaceScale.NORMAL -> R.string.interface_scale_normal
            InterfaceScale.COMPACT -> R.string.interface_scale_compact
            InterfaceScale.SMALL -> R.string.interface_scale_small
            InterfaceScale.SMALLER -> R.string.interface_scale_smaller
        }
        PickerChoice(it, stringResource(R.string.interface_scale_option, stringResource(name), Math.round(it.factor * 100)))
    }

    @Composable
    fun themes(): List<PickerChoice<ColorThemeId>> = ColorThemeId.entries.map {
        val (name, description) = when (it) {
            ColorThemeId.ORIGINAL -> R.string.color_theme_original to R.string.color_theme_original_description
            ColorThemeId.NORDIC_SLATE -> R.string.color_theme_nordic_slate to R.string.color_theme_nordic_slate_description
            ColorThemeId.COZY_HEARTH -> R.string.color_theme_cozy_hearth to R.string.color_theme_cozy_hearth_description
            ColorThemeId.CYBER_PLUM -> R.string.color_theme_cyber_plum to R.string.color_theme_cyber_plum_description
            ColorThemeId.NORD -> R.string.color_theme_nord to R.string.color_theme_nord_description
            ColorThemeId.EVERFOREST -> R.string.color_theme_everforest to R.string.color_theme_everforest_description
            ColorThemeId.KANAGAWA -> R.string.color_theme_kanagawa to R.string.color_theme_kanagawa_description
        }
        PickerChoice(it, stringResource(name), stringResource(description))
    }

    @Composable
    fun startups(): List<PickerChoice<StartupScreen>> = listOf(
        PickerChoice(StartupScreen.HOME, stringResource(R.string.startup_home)),
        PickerChoice(StartupScreen.GUIDE, stringResource(R.string.startup_guide)),
        PickerChoice(StartupScreen.LAST_CHANNEL, stringResource(R.string.startup_last_channel), stringResource(R.string.startup_last_channel_help)),
    )

    @Composable
    fun buffers(): List<PickerChoice<BufferProfile>> = listOf(
        PickerChoice(BufferProfile.DEFAULT, stringResource(R.string.playback_buffer_default), stringResource(R.string.playback_buffer_default_help)),
        PickerChoice(BufferProfile.LOW_LATENCY, stringResource(R.string.playback_buffer_low_latency), stringResource(R.string.playback_buffer_low_latency_help)),
        PickerChoice(BufferProfile.STABILITY, stringResource(R.string.playback_buffer_stability), stringResource(R.string.playback_buffer_stability_help)),
    )

    @Composable
    fun reconnects(): List<PickerChoice<ReconnectPolicy>> = listOf(
        PickerChoice(ReconnectPolicy.STANDARD, stringResource(R.string.playback_reconnect_standard), stringResource(R.string.playback_reconnect_standard_help)),
        PickerChoice(ReconnectPolicy.PERSISTENT, stringResource(R.string.playback_reconnect_persistent), stringResource(R.string.playback_reconnect_persistent_help)),
    )

    @Composable
    fun skipSteps(): List<PickerChoice<SkipStep>> = listOf(
        PickerChoice(SkipStep.TEN_SECONDS, stringResource(R.string.playback_seek_step_10s)),
        PickerChoice(SkipStep.THIRTY_SECONDS, stringResource(R.string.playback_seek_step_30s)),
        PickerChoice(SkipStep.ONE_MINUTE, stringResource(R.string.playback_seek_step_1m)),
        PickerChoice(SkipStep.TWO_MINUTES, stringResource(R.string.playback_seek_step_2m)),
    )

    @Composable
    fun subtitleSizes(): List<PickerChoice<SubtitleSize>> = listOf(
        PickerChoice(SubtitleSize.FOLLOW_TV, stringResource(R.string.subtitle_follow_tv)),
        PickerChoice(SubtitleSize.SMALL, stringResource(R.string.subtitle_size_small)),
        PickerChoice(SubtitleSize.NORMAL, stringResource(R.string.subtitle_size_normal)),
        PickerChoice(SubtitleSize.LARGE, stringResource(R.string.subtitle_size_large)),
        PickerChoice(SubtitleSize.VERY_LARGE, stringResource(R.string.subtitle_size_very_large)),
    )

    @Composable
    fun subtitleColors(): List<PickerChoice<SubtitleColor>> = listOf(
        PickerChoice(SubtitleColor.FOLLOW_TV, stringResource(R.string.subtitle_follow_tv)),
        PickerChoice(SubtitleColor.WHITE, stringResource(R.string.subtitle_color_white)),
        PickerChoice(SubtitleColor.YELLOW, stringResource(R.string.subtitle_color_yellow)),
    )

    @Composable
    fun subtitleBackgrounds(): List<PickerChoice<SubtitleBackground>> = listOf(
        PickerChoice(SubtitleBackground.FOLLOW_TV, stringResource(R.string.subtitle_follow_tv)),
        PickerChoice(SubtitleBackground.NONE, stringResource(R.string.subtitle_background_none)),
        PickerChoice(SubtitleBackground.SHADOW, stringResource(R.string.subtitle_background_shadow)),
        PickerChoice(SubtitleBackground.BOX, stringResource(R.string.subtitle_background_box)),
    )

    /** Automatic, then the eleven languages, named in the interface language (SET-FR-72, L10N-06). */
    @Composable
    fun vodLanguages(): List<PickerChoice<String?>> = listOf(
        null to R.string.language_automatic, "fi" to R.string.language_finnish, "en" to R.string.language_english,
        "sv" to R.string.language_swedish, "da" to R.string.language_danish, "no" to R.string.language_norwegian,
        "et" to R.string.language_estonian, "de" to R.string.language_german, "fr" to R.string.language_french,
        "es" to R.string.language_spanish, "it" to R.string.language_italian, "nl" to R.string.language_dutch,
    ).map { (code, label) -> PickerChoice(code, stringResource(label)) }
}
