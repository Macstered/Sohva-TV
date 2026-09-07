package com.streammate.tv.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import com.streammate.tv.iptv.R
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.CataloguePreferredCopy
import com.streammate.tv.app.PlaybackBufferProfile
import com.streammate.tv.app.PlaybackReconnectPolicy
import com.streammate.tv.app.InterfaceScale
import com.streammate.tv.app.PreferredLanguageSlot
import com.streammate.tv.app.StartupScreen
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.model.SportType
import com.streammate.tv.iptv.repository.SourceRefreshHealth
import java.util.UUID

/** Labels, option lists and small pure helpers the settings rows and pickers read from. */

/** Which choice list is open over the page; [key] names the row that opened it and takes focus back. */
internal sealed class SettingsPickerTarget(val key: String) {
    object InterfaceLanguage : SettingsPickerTarget("interface-language")
    object InterfaceScale : SettingsPickerTarget("interface-scale")
    object Startup : SettingsPickerTarget("startup")
    object RefreshInterval : SettingsPickerTarget("refresh-interval")
    object Buffer : SettingsPickerTarget("buffer")
    object Reconnect : SettingsPickerTarget("reconnect")
    data class Language(val slot: PreferredLanguageSlot) : SettingsPickerTarget("language-" + slot.name.lowercase())
    object MetadataLanguage : SettingsPickerTarget("metadata-language")
    object PreferredCopy : SettingsPickerTarget("preferred-copy")
}

@Composable
internal fun PlaybackBufferProfile.localizedHelp(): String = stringResource(
    when (this) {
        PlaybackBufferProfile.DEFAULT -> R.string.playback_buffer_default_help
        PlaybackBufferProfile.LOW_LATENCY -> R.string.playback_buffer_low_latency_help
        PlaybackBufferProfile.STABILITY -> R.string.playback_buffer_stability_help
    },
)

@Composable
internal fun PlaybackReconnectPolicy.localizedHelp(): String = stringResource(
    when (this) {
        PlaybackReconnectPolicy.STANDARD -> R.string.playback_reconnect_standard_help
        PlaybackReconnectPolicy.PERSISTENT -> R.string.playback_reconnect_persistent_help
    },
)

@Composable
internal fun interfaceScaleOptions(): List<Pair<InterfaceScale, String>> = listOf(
    InterfaceScale.NORMAL to stringResource(R.string.interface_scale_normal),
    InterfaceScale.COMPACT to stringResource(R.string.interface_scale_compact),
    InterfaceScale.SMALL to stringResource(R.string.interface_scale_small),
)

@Composable
internal fun interfaceLanguageOptions(): List<Pair<String?, String>> = listOf(
    null to stringResource(R.string.interface_language_system),
    "en" to stringResource(R.string.interface_language_en),
    "fi" to stringResource(R.string.interface_language_fi),
)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun preferredLanguageOptions(): List<Pair<String?, String>> = listOf(
    null to stringResource(R.string.language_automatic),
    "fi" to stringResource(R.string.language_finnish),
    "en" to stringResource(R.string.language_english),
    "sv" to stringResource(R.string.language_swedish),
    "da" to stringResource(R.string.language_danish),
    "no" to stringResource(R.string.language_norwegian),
    "et" to stringResource(R.string.language_estonian),
    "de" to stringResource(R.string.language_german),
    "fr" to stringResource(R.string.language_french),
    "es" to stringResource(R.string.language_spanish),
    "it" to stringResource(R.string.language_italian),
    "nl" to stringResource(R.string.language_dutch),
)

internal fun AppPreferences.languageFor(slot: PreferredLanguageSlot): String? = when (slot) {
    PreferredLanguageSlot.PRIMARY_AUDIO -> preferredAudioLanguage
    PreferredLanguageSlot.SECONDARY_AUDIO -> secondaryAudioLanguage
    PreferredLanguageSlot.PRIMARY_SUBTITLE -> preferredSubtitleLanguage
    PreferredLanguageSlot.SECONDARY_SUBTITLE -> secondarySubtitleLanguage
}

internal fun PreferredLanguageSlot.pairedSlot(): PreferredLanguageSlot = when (this) {
    PreferredLanguageSlot.PRIMARY_AUDIO -> PreferredLanguageSlot.SECONDARY_AUDIO
    PreferredLanguageSlot.SECONDARY_AUDIO -> PreferredLanguageSlot.PRIMARY_AUDIO
    PreferredLanguageSlot.PRIMARY_SUBTITLE -> PreferredLanguageSlot.SECONDARY_SUBTITLE
    PreferredLanguageSlot.SECONDARY_SUBTITLE -> PreferredLanguageSlot.PRIMARY_SUBTITLE
}

internal fun PreferredLanguageSlot.labelResource(): Int = when (this) {
    PreferredLanguageSlot.PRIMARY_AUDIO -> R.string.preferred_audio_primary
    PreferredLanguageSlot.SECONDARY_AUDIO -> R.string.preferred_audio_secondary
    PreferredLanguageSlot.PRIMARY_SUBTITLE -> R.string.preferred_subtitle_primary
    PreferredLanguageSlot.SECONDARY_SUBTITLE -> R.string.preferred_subtitle_secondary
}

@Composable
internal fun StartupScreen.localizedLabel(): String = when (this) {
    StartupScreen.HOME -> stringResource(R.string.startup_home)
    StartupScreen.GUIDE -> stringResource(R.string.startup_guide)
    StartupScreen.LAST_CHANNEL -> stringResource(R.string.startup_last_channel)
}

/**
 * Named for what the viewer wants rather than for what the app measures: the
 * ranking behind these reads a provider's claim, and saying so in a button
 * would be a paragraph.
 */
@Composable
internal fun CataloguePreferredCopy.localizedLabel(): String = when (this) {
    CataloguePreferredCopy.NONE -> stringResource(R.string.preferred_copy_none)
    CataloguePreferredCopy.FINNISH_AUDIO -> stringResource(R.string.preferred_copy_finnish_audio)
    CataloguePreferredCopy.FINNISH_SUBTITLES -> stringResource(R.string.preferred_copy_finnish_subtitles)
    CataloguePreferredCopy.LARGEST_PICTURE -> stringResource(R.string.preferred_copy_largest_picture)
}

@Composable
internal fun PlaybackBufferProfile.localizedLabel(): String = when (this) {
    PlaybackBufferProfile.DEFAULT -> stringResource(R.string.playback_buffer_default)
    PlaybackBufferProfile.LOW_LATENCY -> stringResource(R.string.playback_buffer_low_latency)
    PlaybackBufferProfile.STABILITY -> stringResource(R.string.playback_buffer_stability)
}

@Composable
internal fun PlaybackReconnectPolicy.localizedLabel(): String = when (this) {
    PlaybackReconnectPolicy.STANDARD -> stringResource(R.string.playback_reconnect_standard)
    PlaybackReconnectPolicy.PERSISTENT -> stringResource(R.string.playback_reconnect_persistent)
}

internal val SportType.settingsLabelRes: Int
    get() = when (this) {
        SportType.FOOTBALL -> R.string.sports_follow_football
        SportType.ICE_HOCKEY -> R.string.sports_follow_hockey
        SportType.AUSTRALIAN_FOOTBALL -> R.string.sports_follow_afl
        SportType.BASKETBALL -> R.string.sports_follow_basketball
        SportType.BASEBALL -> R.string.sports_follow_baseball
        SportType.HANDBALL -> R.string.sports_follow_handball
        SportType.RUGBY -> R.string.sports_follow_rugby
        SportType.VOLLEYBALL -> R.string.sports_follow_volleyball
    }

internal fun formatEpgOffset(minutes: Int): String {
    if (minutes == 0) return "0 min"
    val sign = if (minutes > 0) "+" else "−"
    val absoluteMinutes = kotlin.math.abs(minutes)
    val hours = absoluteMinutes / 60
    val remainingMinutes = absoluteMinutes % 60
    return when {
        remainingMinutes == 0 -> "$sign$hours h"
        hours == 0 -> "$sign$remainingMinutes min"
        else -> "$sign$hours h $remainingMinutes min"
    }
}

/**
 * What the dot on a source chip should say.
 *
 * A source switched off is dim whatever its history; one whose last refresh
 * failed is red; everything else is fine. The message never carries the error
 * text, which can contain a URL with credentials in it.
 */
internal fun sourceChipStatus(
    source: IptvSourceConfiguration,
    health: List<SourceRefreshHealth>,
): SettingsSourceStatus = when {
    !source.enabled -> SettingsSourceStatus.DISABLED
    health.any { it.sourceId == source.id && it.status == "failed" } -> SettingsSourceStatus.FAILING
    else -> SettingsSourceStatus.HEALTHY
}

internal fun newM3uSourceId(): String = "m3u-${UUID.randomUUID()}"

internal fun newXtreamSourceId(): String = "xtream-${UUID.randomUUID()}"

/** What a source's row says under its name: its kind, and the last refresh failure if there is one. */
internal fun sourceRowSubtitle(
    source: IptvSourceConfiguration,
    status: SettingsSourceStatus,
    health: List<com.streammate.tv.iptv.repository.SourceRefreshHealth>,
    resources: android.content.res.Resources,
): String {
    val kind = if (source.type == IptvSourceType.M3U) "M3U" else "Xtream"
    if (status != SettingsSourceStatus.FAILING) return kind
    val failure = health.firstOrNull { it.sourceId == source.id && it.status == "failed" }
        ?.let { readableImportError(resources, it.lastError) }
    return if (failure.isNullOrBlank()) kind else "$kind · $failure"
}
