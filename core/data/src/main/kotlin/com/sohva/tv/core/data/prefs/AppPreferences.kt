package com.sohva.tv.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sohva.tv.core.data.metadata.MetadataPreferences
import com.sohva.tv.core.model.player.BufferProfile
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.ReconnectPolicy
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.player.SkipStep
import com.sohva.tv.core.model.player.SubtitleBackground
import com.sohva.tv.core.model.player.SubtitleColor
import com.sohva.tv.core.model.player.SubtitleSize
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.settings.StartSnapshot
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.core.model.vod.PreferredCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Household settings. Consumers observe one key each, never the whole preferences object: in
 * beta 23 every zap wrote a recent channel and re-emitted all settings to every screen, the app
 * root included (plan/03 §2.4, §4.6).
 */
class AppPreferences(private val store: DataStore<Preferences>) : MetadataPreferences {
    val theme: Flow<ColorThemeId> = key(THEME) { ColorThemeId.fromStored(it) }
    val scale: Flow<InterfaceScale> = key(SCALE) { InterfaceScale.fromStored(it) }
    val startupScreen: Flow<StartupScreen> = key(STARTUP_SCREEN) { StartupScreen.fromStored(it) }
    val refreshInterval: Flow<RefreshInterval> = key(REFRESH_INTERVAL) { RefreshInterval.fromStored(it) }

    /** The guide's source across launches (spec 20 GUIDE-FR-12), global, not per profile. */
    val lastGuideSource: Flow<String?> = key(LAST_GUIDE_SOURCE) { it }
    val showChannelNumbers: Flow<Boolean> = store.data.map { it[SHOW_CHANNEL_NUMBERS] ?: true }.distinctUntilChanged()

    /** Absent = the TV's zone (spec 20 GUIDE-FR-110). */
    val timeZone: Flow<String?> = key(TIME_ZONE) { it }

    /** The profile's last channel (spec 30 PLAY-FR-57); profile-scoped keys arrive with profiles (M6). */
    val lastChannel: Flow<String?> = key(LAST_CHANNEL) { it }

    /**
     * The remote mapping (spec 31 REMOTE-FR-30…32), decoded once per change into the player's
     * array form. Never written = the legacy channel-key setting decides; written empty = all Nothing.
     */
    val remoteMapping: Flow<RemoteMapping> =
        store.data.map { RemoteMapping.decode(it[REMOTE_MAPPINGS], it[REMOTE_CHANNEL_KEY_MODE]) }.distinctUntilChanged()

    /** Assigns one slot; the whole set is written, so the legacy setting no longer has a say (REMOTE-FR-33). */
    suspend fun setRemoteAction(button: RemoteButton, gesture: Gesture, action: RemoteAction) {
        store.edit { prefs ->
            val current = RemoteMapping.decode(prefs[REMOTE_MAPPINGS], prefs[REMOTE_CHANNEL_KEY_MODE])
            prefs[REMOTE_MAPPINGS] = current.with(button, gesture, action).encode()
        }
    }

    /** `editors_show_hidden`, shared by channel management and the Library manager (spec 21 CHAN-FR-16). */
    val editorsShowHidden: Flow<Boolean> = store.data.map { it[EDITORS_SHOW_HIDDEN] ?: true }.distinctUntilChanged()

    suspend fun setEditorsShowHidden(value: Boolean) {
        store.edit { it[EDITORS_SHOW_HIDDEN] = value }
    }

    /** Whether the "Let reminders open Sohva TV" prompt was shown; once per installation (REM-FR-05). */
    suspend fun reminderOverlayAsked(): Boolean = store.data.first()[REMINDER_OVERLAY_ASKED] ?: false

    suspend fun setReminderOverlayAsked() {
        store.edit { it[REMINDER_OVERLAY_ASKED] = true }
    }

    /** Writes the defaults explicitly (REMOTE-FR-34). */
    suspend fun resetRemoteMapping() {
        store.edit { it[REMOTE_MAPPINGS] = RemoteMapping.DEFAULTS.encode() }
    }

    /** The player's settings in one read, once per playback (spec 30 §6). */
    suspend fun playback(): PlaybackSettings {
        val p = store.data.first()
        return PlaybackSettings(
            buffer = BufferProfile.fromStored(p[BUFFER_PROFILE]),
            reconnect = ReconnectPolicy.fromStored(p[RECONNECT_POLICY]),
            skipStep = SkipStep.fromStored(p[SEEK_STEP]),
            matchFrameRate = p[AUTO_FRAME_RATE] ?: true,
            pictureInPicture = p[PICTURE_IN_PICTURE] ?: false,
            subtitleSize = SubtitleSize.fromStored(p[SUBTITLE_SIZE]),
            subtitleColor = SubtitleColor.fromStored(p[SUBTITLE_COLOR]),
            subtitleBackground = SubtitleBackground.fromStored(p[SUBTITLE_BACKGROUND]),
            showChannelNumbers = p[SHOW_CHANNEL_NUMBERS] ?: true,
            timeZone = p[TIME_ZONE],
        )
    }

    override suspend fun metadataLanguage(): String? = store.data.first()[METADATA_LANGUAGE]

    override suspend fun setMetadataLanguage(tag: String) {
        store.edit { it[METADATA_LANGUAGE] = tag }
    }

    override suspend fun metadataKeyRefused(): Boolean = store.data.first()[METADATA_KEY_REFUSED] ?: false

    override suspend fun setMetadataKeyRefused(refused: Boolean) {
        store.edit { it[METADATA_KEY_REFUSED] = refused }
    }

    /** "When a film has more than one version" (spec 40 VOD-FR-32); unknown → whichever comes first. */
    suspend fun preferredCopy(): PreferredCopy = PreferredCopy.of(store.data.first()[PREFERRED_COPY])

    /** The same, observed by Settings. */
    val preferredCopyChanges: Flow<PreferredCopy> get() = key(PREFERRED_COPY, PreferredCopy::of)

    suspend fun setPreferredCopy(copy: PreferredCopy) {
        store.edit { it[PREFERRED_COPY] = copy.name }
    }

    /** "Continue to the next episode" (spec 70 SET-34, spec 30 PLAY-FR-132); on by default. */
    suspend fun autoPlayNextEpisode(): Boolean = store.data.first()[AUTO_PLAY_NEXT] ?: true

    /** Trimmed, cut to 128 characters; blank removes the key (GUIDE-FR-12). */
    suspend fun setLastGuideSource(id: String?) {
        val value = id?.trim()?.take(128)
        store.edit { if (value.isNullOrEmpty()) it.remove(LAST_GUIDE_SOURCE) else it[LAST_GUIDE_SOURCE] = value }
    }

    suspend fun setLastChannel(key: String) {
        store.edit { it[LAST_CHANNEL] = key }
    }

    /** One read for the first frame. Call off the main thread. */
    suspend fun startSnapshot(): StartSnapshot {
        val prefs = store.data.first()
        return StartSnapshot(
            theme = ColorThemeId.fromStored(prefs[THEME]),
            scale = InterfaceScale.fromStored(prefs[SCALE]),
            startupScreen = StartupScreen.fromStored(prefs[STARTUP_SCREEN]),
            lastChannel = prefs[LAST_CHANNEL],
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

    suspend fun setRefreshInterval(value: RefreshInterval) {
        store.edit { it[REFRESH_INTERVAL] = value.name }
    }

    /** Every household setting back to its default (tests' clear-state rule; restore defaults later). */
    suspend fun resetToDefaults() {
        store.edit { it.clear() }
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
        private val REFRESH_INTERVAL = stringPreferencesKey("playlist_epg_refresh_interval")
        private val LAST_GUIDE_SOURCE = stringPreferencesKey("last_guide_source_id")
        private val SHOW_CHANNEL_NUMBERS = booleanPreferencesKey("show_channel_numbers")
        private val TIME_ZONE = stringPreferencesKey("time_zone")
        private val LAST_CHANNEL = stringPreferencesKey("last_channel_id")
        private val REMOTE_MAPPINGS = stringSetPreferencesKey("remote_mappings")
        private val REMINDER_OVERLAY_ASKED = booleanPreferencesKey("reminder_overlay_asked")
        private val EDITORS_SHOW_HIDDEN = booleanPreferencesKey("editors_show_hidden")

        // Beta 23's "Remote channel browser" setting: read (never shown) until a mapping is written.
        private val REMOTE_CHANNEL_KEY_MODE = stringPreferencesKey("remote_channel_key_mode")
        private val BUFFER_PROFILE = stringPreferencesKey("playback_buffer_profile")
        private val RECONNECT_POLICY = stringPreferencesKey("playback_reconnect_policy")
        private val SEEK_STEP = stringPreferencesKey("playback_seek_step")
        private val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next_episode")
        private val PREFERRED_COPY = stringPreferencesKey("preferred_catalogue_copy")
        private val METADATA_LANGUAGE = stringPreferencesKey("metadata_language")
        private val METADATA_KEY_REFUSED = booleanPreferencesKey("metadata_key_refused")
        private val AUTO_FRAME_RATE = booleanPreferencesKey("auto_frame_rate")
        private val PICTURE_IN_PICTURE = booleanPreferencesKey("picture_in_picture")
        private val SUBTITLE_SIZE = stringPreferencesKey("subtitle_text_size")
        private val SUBTITLE_COLOR = stringPreferencesKey("subtitle_text_color")
        private val SUBTITLE_BACKGROUND = stringPreferencesKey("subtitle_background")
    }
}
