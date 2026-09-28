package com.sohva.tv.core.data.prefs

import com.sohva.tv.core.data.backup.BackupPreferences
import com.sohva.tv.core.data.backup.BackupProfile
import com.sohva.tv.core.data.backup.ProfileKept
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.core.model.profile.Profile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sohva.tv.core.data.metadata.MetadataPreferences
import com.sohva.tv.core.model.player.BufferProfile
import com.sohva.tv.core.model.profile.Household
import com.sohva.tv.core.model.profile.Profiles
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
import com.sohva.tv.core.model.player.VodLanguages
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.settings.VodLanguageSlot
import com.sohva.tv.core.model.settings.StartSnapshot
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.vod.CustomGroup
import com.sohva.tv.core.model.vod.CustomGroups
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

    /** [profileId]'s last channel (spec 30 PLAY-FR-57), under `last_channel_id[:id]` (spec 04 §6). */
    fun lastChannel(profileId: String): Flow<String?> = key(stringPreferencesKey(Profiles.key(LAST_CHANNEL_KEY, profileId))) { it }

    /** [profileId]'s Home layout (spec 02 HOME-FR-86), under `home_layout[:id]`; absent = the default. */
    fun homeLayout(profileId: String): Flow<HomeLayout> = key(homeLayoutKey(profileId), HomeLayout::decode)

    /** The stored text of [profileId]'s layout for the backup (HOME-FR-91); null for the default. */
    suspend fun homeLayoutText(profileId: String): String? = store.data.first()[homeLayoutKey(profileId)]

    /** Writes [profileId]'s layout; the default is stored as no value. */
    suspend fun setHomeLayout(profileId: String, layout: HomeLayout) {
        val text = layout.encode()
        store.edit { if (text == null) it.remove(homeLayoutKey(profileId)) else it[homeLayoutKey(profileId)] = text }
    }

    private fun homeLayoutKey(profileId: String) = stringPreferencesKey(Profiles.key(HOME_LAYOUT_KEY, profileId))

    /** The household (spec 04 §6): profiles, the active one, ask at start, "a PIN exists". */
    val household: Flow<Household> = store.data.map(::householdOf).distinctUntilChanged()

    /** Changes the household in one write; [change] runs inside the edit, so concurrent changes do not lose each other. */
    suspend fun editHousehold(change: (Household) -> Household) {
        store.edit { prefs ->
            val next = change(householdOf(prefs))
            prefs[PROFILES] = Profiles.encode(next.stored)
            if (next.activeId == Profiles.DEFAULT_ID) prefs.remove(ACTIVE_PROFILE) else prefs[ACTIVE_PROFILE] = next.activeId
            prefs[ASK_PROFILE] = next.askAtStart
            prefs[PIN_CONFIGURED] = next.pinConfigured
            if (next.stored.isEmpty()) prefs.remove(PROFILES)
        }
    }

    /** Removes [profileId]'s own keys (PROF-FR-06); its database rows go separately. */
    suspend fun forgetProfile(profileId: String) {
        if (profileId == Profiles.DEFAULT_ID) return
        store.edit {
            it.remove(stringPreferencesKey(Profiles.key(LAST_CHANNEL_KEY, profileId)))
            it.remove(homeLayoutKey(profileId))
        }
    }

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

    /** Groups of your own (spec 42 ORG-FR-60): device-wide, in their saved order. Parsed where collected (off the main thread). */
    val customGroups: Flow<List<CustomGroup>> = store.data.map { it[CUSTOM_GROUPS] }.distinctUntilChanged().map(CustomGroupCodec::decode)

    /** Saves or replaces [group] by id (ORG-FR-60); an unusable group or a 25th changes nothing. */
    suspend fun saveCustomGroup(group: CustomGroup) {
        store.edit { it[CUSTOM_GROUPS] = CustomGroupCodec.encode(CustomGroups.save(CustomGroupCodec.decode(it[CUSTOM_GROUPS]), group)) }
    }

    suspend fun deleteCustomGroup(id: String) {
        store.edit { it[CUSTOM_GROUPS] = CustomGroupCodec.encode(CustomGroups.delete(CustomGroupCodec.decode(it[CUSTOM_GROUPS]), id)) }
    }

    /** `editors_show_hidden`, shared by channel management and the Library manager (spec 21 CHAN-FR-16). */
    val editorsShowHidden: Flow<Boolean> = store.data.map { it[EDITORS_SHOW_HIDDEN] ?: true }.distinctUntilChanged()

    suspend fun setEditorsShowHidden(value: Boolean) {
        store.edit { it[EDITORS_SHOW_HIDDEN] = value }
    }

    /** The library manager's last group and source per room (spec 42 ORG-28): `manager_group_<ROOM>`, `manager_source_<ROOM>`. */
    suspend fun managerLocation(room: String): Pair<String?, String?> {
        val p = store.data.first()
        return p[stringPreferencesKey("manager_group_$room")] to p[stringPreferencesKey("manager_source_$room")]
    }

    suspend fun setManagerLocation(room: String, group: String?, source: String?) {
        store.edit {
            val g = stringPreferencesKey("manager_group_$room")
            val s = stringPreferencesKey("manager_source_$room")
            if (group == null) it.remove(g) else it[g] = group.take(2_048)
            if (source == null) it.remove(s) else it[s] = source.take(2_048)
        }
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
    suspend fun playback(): PlaybackSettings = playbackOf(store.data.first())

    /** Settings › Playback's rows (spec 70 §4.7), re-emitted only when one of them changes. */
    val playbackSettings: Flow<PlaybackSettings> = store.data.map(::playbackOf).distinctUntilChanged()

    /** "Continue to the next episode" as Settings shows it (SET-34). */
    val autoPlayNext: Flow<Boolean> = store.data.map { it[AUTO_PLAY_NEXT] ?: true }.distinctUntilChanged()

    // ---- Sohva Sport (spec 60 §6) ----------------------------------------------------------------

    /** What the viewer follows; each key reads its default until first written (SPORT-FR-09). */
    val sportFollows: Flow<SportFollows> = store.data.map { p ->
        SportFollows(
            sports = p[FOLLOWED_SPORTS]?.mapNotNullTo(HashSet(), SportType::fromStored) ?: SportFollows.DEFAULT.sports,
            competitions = p[FOLLOWED_COMPETITIONS] ?: SportFollows.DEFAULT.competitions,
        )
    }.distinctUntilChanged()

    /** Writes both keys: the first toggle stores the defaults plus the change (SPORT-FR-09). */
    suspend fun setSportFollows(follows: SportFollows) {
        store.edit {
            it[FOLLOWED_SPORTS] = follows.sports.mapTo(HashSet()) { s -> s.name }
            it[FOLLOWED_COMPETITIONS] = follows.competitions
        }
    }

    /** The channel country/language order, canonical codes (SPORT-FR-10); empty is the default order. */
    val sportsPriority: Flow<List<String>> = store.data.map { p -> p[SPORTS_PRIORITY]?.split(',')?.filter { it.isNotBlank() }.orEmpty() }.distinctUntilChanged()

    suspend fun setSportsPriority(codes: List<String>) {
        store.edit { if (codes.isEmpty()) it.remove(SPORTS_PRIORITY) else it[SPORTS_PRIORITY] = codes.joinToString(",") }
    }

    /** A profile's favourite games (SPORT-FR-96). */
    fun favouriteEventsOf(profileId: String): Flow<Set<String>> =
        store.data.map { it[stringSetPreferencesKey(Profiles.key(FAVOURITE_EVENTS_KEY, profileId))].orEmpty() }.distinctUntilChanged()

    suspend fun setBuffer(value: BufferProfile) = store.edit { it[BUFFER_PROFILE] = value.name }.let { }

    suspend fun setReconnect(value: ReconnectPolicy) = store.edit { it[RECONNECT_POLICY] = value.name }.let { }

    suspend fun setSkipStep(value: SkipStep) = store.edit { it[SEEK_STEP] = value.name }.let { }

    suspend fun setMatchFrameRate(on: Boolean) = store.edit { it[AUTO_FRAME_RATE] = on }.let { }

    suspend fun setAutoPlayNext(on: Boolean) = store.edit { it[AUTO_PLAY_NEXT] = on }.let { }

    suspend fun setPictureInPicture(on: Boolean) = store.edit { it[PICTURE_IN_PICTURE] = on }.let { }

    suspend fun setSubtitleSize(value: SubtitleSize) = store.edit { it[SUBTITLE_SIZE] = value.name }.let { }

    suspend fun setSubtitleColor(value: SubtitleColor) = store.edit { it[SUBTITLE_COLOR] = value.name }.let { }

    suspend fun setSubtitleBackground(value: SubtitleBackground) = store.edit { it[SUBTITLE_BACKGROUND] = value.name }.let { }

    /** One VOD language row; the partner row is cleared when it holds the same language (SET-FR-72). */
    suspend fun setVodLanguage(slot: VodLanguageSlot, code: String?) {
        store.edit { prefs ->
            val next = VodLanguageSlot.choose(playbackOf(prefs).vodLanguages, slot, code)
            fun put(key: Preferences.Key<String>, value: String?) = if (value == null) prefs.remove(key) else prefs[key] = value
            put(AUDIO_PRIMARY, next.audio)
            put(AUDIO_SECONDARY, next.audioSecond)
            put(SUBTITLE_PRIMARY, next.subtitles)
            put(SUBTITLE_SECONDARY, next.subtitlesSecond)
        }
    }

    suspend fun setShowChannelNumbers(on: Boolean) = store.edit { it[SHOW_CHANNEL_NUMBERS] = on }.let { }

    /** Null follows the TV's zone from now on (SET-FR-65). */
    suspend fun setTimeZone(id: String?) = store.edit { if (id == null) it.remove(TIME_ZONE) else it[TIME_ZONE] = id }.let { }

    private fun playbackOf(p: Preferences): PlaybackSettings {
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
            vodLanguages = VodLanguages(
                audio = VodLanguages.stored(p[AUDIO_PRIMARY]),
                audioSecond = VodLanguages.stored(p[AUDIO_SECONDARY]),
                subtitles = VodLanguages.stored(p[SUBTITLE_PRIMARY]),
                subtitlesSecond = VodLanguages.stored(p[SUBTITLE_SECONDARY]),
            ),
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

    suspend fun setLastChannel(profileId: String, key: String) {
        store.edit { it[stringPreferencesKey(Profiles.key(LAST_CHANNEL_KEY, profileId))] = key }
    }

    /** One read for the first frame. Call off the main thread. */
    suspend fun startSnapshot(): StartSnapshot {
        val prefs = store.data.first()
        val household = householdOf(prefs)
        return StartSnapshot(
            theme = ColorThemeId.fromStored(prefs[THEME]),
            scale = InterfaceScale.fromStored(prefs[SCALE]),
            startupScreen = StartupScreen.fromStored(prefs[STARTUP_SCREEN]),
            lastChannel = prefs[stringPreferencesKey(Profiles.key(LAST_CHANNEL_KEY, household.active.id))],
            household = household,
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

    // ---- Backup (spec 71 §6.2, §6.4, BACKUP-FR-22) ------------------------------------------------

    /** Every backed-up setting in one read; the per-profile lists come from [favouriteEvents] and the tables. */
    suspend fun backupPreferences(): BackupPreferences {
        val p = store.data.first()
        val household = householdOf(p)
        return BackupPreferences(
            timeZoneId = p[TIME_ZONE],
            profiles = household.stored.filter { !it.name.isNullOrBlank() }.map { BackupProfile(it.id, it.name!!, it.colorIndex) },
            activeProfileId = household.active.id,
            askProfileAtStart = household.askAtStart,
            lastGuideSourceId = p[LAST_GUIDE_SOURCE],
            startupScreen = StartupScreen.fromStored(p[STARTUP_SCREEN]).name,
            remoteChannelKeyMode = p[REMOTE_CHANNEL_KEY_MODE] ?: "DPAD_AND_CHANNEL_KEYS",
            remoteMappings = p[REMOTE_MAPPINGS]?.toList(),
            metadataLanguage = p[METADATA_LANGUAGE],
            interfaceScale = InterfaceScale.fromStored(p[SCALE]).name,
            colorTheme = ColorThemeId.fromStored(p[THEME]).id,
            followedSports = p[FOLLOWED_SPORTS]?.toList(),
            followedCompetitionKeys = p[FOLLOWED_COMPETITIONS]?.toList(),
            sportsChannelPriority = p[SPORTS_PRIORITY]?.split(',')?.filter { it.isNotBlank() },
            refreshInterval = RefreshInterval.fromStored(p[REFRESH_INTERVAL]).name,
            bufferProfile = BufferProfile.fromStored(p[BUFFER_PROFILE]).name,
            seekStep = SkipStep.fromStored(p[SEEK_STEP]).name,
            subtitleSize = SubtitleSize.fromStored(p[SUBTITLE_SIZE]).name,
            subtitleColor = SubtitleColor.fromStored(p[SUBTITLE_COLOR]).name,
            subtitleBackground = SubtitleBackground.fromStored(p[SUBTITLE_BACKGROUND]).name,
            reconnectPolicy = ReconnectPolicy.fromStored(p[RECONNECT_POLICY]).name,
            autoPlayNext = p[AUTO_PLAY_NEXT] ?: true,
            pictureInPicture = p[PICTURE_IN_PICTURE] ?: false,
            autoFrameRate = p[AUTO_FRAME_RATE] ?: true,
            editorsShowHidden = p[EDITORS_SHOW_HIDDEN] ?: true,
            showChannelNumbers = p[SHOW_CHANNEL_NUMBERS] ?: true,
            preferredCopy = PreferredCopy.entries.firstOrNull { it.name == p[PREFERRED_COPY] }?.name ?: PreferredCopy.NONE.name,
            audioPrimary = p[AUDIO_PRIMARY],
            audioSecondary = p[AUDIO_SECONDARY],
            subtitlesPrimary = p[SUBTITLE_PRIMARY],
            subtitlesSecondary = p[SUBTITLE_SECONDARY],
            customGroupsJson = p[CUSTOM_GROUPS],
        )
    }

    /** A profile's favourite matches (Sohva Sport, M8), kept under beta 23's key meanwhile. */
    suspend fun favouriteEvents(profileId: String): List<String> =
        store.data.first()[stringSetPreferencesKey(Profiles.key(FAVOURITE_EVENTS_KEY, profileId))]?.sorted().orEmpty()

    suspend fun lastChannelOf(profileId: String): String? = store.data.first()[stringPreferencesKey(Profiles.key(LAST_CHANNEL_KEY, profileId))]

    /**
     * Replaces the backed-up settings with [p] in one write, leaving device-local ones (spec 71
     * BACKUP-FR-24): every key of [BACKED_UP] and every per-profile key is removed, then written from
     * the backup. [pinConfigured] is whether the backup brought a PIN. "Match the display" keeps the
     * TV's value when the file has none (BACKUP-FR-23).
     */
    suspend fun restoreBackup(p: BackupPreferences, profiles: Map<String, ProfileKept>, pinConfigured: Boolean) {
        store.edit { prefs ->
            val keptAutoFrameRate = prefs[AUTO_FRAME_RATE]
            for (key in prefs.asMap().keys.toList()) {
                val name = key.name
                if (name in BACKED_UP || PER_PROFILE.any { name == it || name.startsWith("$it:") }) prefs.remove(key)
            }
            fun put(key: Preferences.Key<String>, value: String?) = if (value == null) prefs.remove(key) else prefs[key] = value
            val household = Household(
                stored = p.profiles.map { Profile(it.id, it.name, it.color) },
                activeId = p.activeProfileId,
                askAtStart = p.askProfileAtStart,
                pinConfigured = pinConfigured,
            )
            prefs[PROFILES] = Profiles.encode(household.stored)
            if (household.stored.isEmpty()) prefs.remove(PROFILES)
            if (household.activeId != Profiles.DEFAULT_ID) prefs[ACTIVE_PROFILE] = household.activeId
            prefs[ASK_PROFILE] = household.askAtStart
            prefs[PIN_CONFIGURED] = pinConfigured
            put(TIME_ZONE, p.timeZoneId)
            put(LAST_GUIDE_SOURCE, p.lastGuideSourceId)
            prefs[STARTUP_SCREEN] = p.startupScreen
            prefs[REMOTE_CHANNEL_KEY_MODE] = p.remoteChannelKeyMode
            p.remoteMappings?.let { prefs[REMOTE_MAPPINGS] = it.toSet() }
            put(METADATA_LANGUAGE, p.metadataLanguage)
            put(SCALE, p.interfaceScale)
            put(THEME, p.colorTheme)
            p.followedSports?.let { prefs[FOLLOWED_SPORTS] = it.toSet() }
            p.followedCompetitionKeys?.let { prefs[FOLLOWED_COMPETITIONS] = it.toSet() }
            p.sportsChannelPriority?.let { prefs[SPORTS_PRIORITY] = it.joinToString(",") }
            put(REFRESH_INTERVAL, p.refreshInterval)
            put(BUFFER_PROFILE, p.bufferProfile)
            put(SEEK_STEP, p.seekStep)
            put(SUBTITLE_SIZE, p.subtitleSize)
            put(SUBTITLE_COLOR, p.subtitleColor)
            put(SUBTITLE_BACKGROUND, p.subtitleBackground)
            put(RECONNECT_POLICY, p.reconnectPolicy)
            prefs[AUTO_PLAY_NEXT] = p.autoPlayNext
            prefs[PICTURE_IN_PICTURE] = p.pictureInPicture
            (p.autoFrameRate ?: keptAutoFrameRate)?.let { prefs[AUTO_FRAME_RATE] = it }
            prefs[EDITORS_SHOW_HIDDEN] = p.editorsShowHidden
            prefs[SHOW_CHANNEL_NUMBERS] = p.showChannelNumbers
            put(PREFERRED_COPY, p.preferredCopy)
            put(AUDIO_PRIMARY, VodLanguages.stored(p.audioPrimary))
            put(AUDIO_SECONDARY, VodLanguages.stored(p.audioSecondary))
            put(SUBTITLE_PRIMARY, VodLanguages.stored(p.subtitlesPrimary))
            put(SUBTITLE_SECONDARY, VodLanguages.stored(p.subtitlesSecondary))
            put(CUSTOM_GROUPS, p.customGroupsJson)
            for ((id, kept) in profiles) {
                kept.lastChannelId?.let { prefs[stringPreferencesKey(Profiles.key(LAST_CHANNEL_KEY, id))] = it }
                if (kept.favouriteEventIds.isNotEmpty()) prefs[stringSetPreferencesKey(Profiles.key(FAVOURITE_EVENTS_KEY, id))] = kept.favouriteEventIds.toSet()
                // Stored as read back, so what the file brings is what Home draws (HOME-FR-91).
                HomeLayout.decode(kept.homeLayout).encode()?.let { prefs[homeLayoutKey(id)] = it }
            }
        }
    }

    private fun householdOf(prefs: Preferences): Household = Household(
        stored = Profiles.decode(prefs[PROFILES]),
        activeId = prefs[ACTIVE_PROFILE] ?: Profiles.DEFAULT_ID,
        askAtStart = prefs[ASK_PROFILE] ?: true,
        pinConfigured = prefs[PIN_CONFIGURED] ?: false,
    )

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
        private const val LAST_CHANNEL_KEY = "last_channel_id"
        private const val FAVOURITE_EVENTS_KEY = "favourite_event_ids"
        private const val HOME_LAYOUT_KEY = "home_layout"

        // Sohva Sport's (spec 60, M8) under beta 23's names, so a restored backup keeps them until then.
        private val FOLLOWED_SPORTS = stringSetPreferencesKey("followed_sports")
        private val FOLLOWED_COMPETITIONS = stringSetPreferencesKey("followed_competitions")
        private val SPORTS_PRIORITY = stringPreferencesKey("sports_channel_priority")
        private val PROFILES = stringPreferencesKey("profiles")
        private val ACTIVE_PROFILE = stringPreferencesKey("active_profile_id")
        private val ASK_PROFILE = booleanPreferencesKey("ask_profile_at_start")
        private val PIN_CONFIGURED = booleanPreferencesKey("parental_pin_configured")
        private val REMOTE_MAPPINGS = stringSetPreferencesKey("remote_mappings")
        private val REMINDER_OVERLAY_ASKED = booleanPreferencesKey("reminder_overlay_asked")
        private val EDITORS_SHOW_HIDDEN = booleanPreferencesKey("editors_show_hidden")
        private val CUSTOM_GROUPS = stringPreferencesKey("custom_catalogue_groups")

        // Beta 23's "Remote channel browser" setting: read (never shown) until a mapping is written.
        private val REMOTE_CHANNEL_KEY_MODE = stringPreferencesKey("remote_channel_key_mode")
        private val BUFFER_PROFILE = stringPreferencesKey("playback_buffer_profile")
        private val RECONNECT_POLICY = stringPreferencesKey("playback_reconnect_policy")
        private val SEEK_STEP = stringPreferencesKey("playback_seek_step")
        private val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next_episode")
        private val PREFERRED_COPY = stringPreferencesKey("preferred_catalogue_copy")
        private val METADATA_LANGUAGE = stringPreferencesKey("metadata_language")

        // Spec 70 SET-39 under beta 23's names; Settings offers them in M7.
        private val AUDIO_PRIMARY = stringPreferencesKey("preferred_audio_language")
        private val AUDIO_SECONDARY = stringPreferencesKey("secondary_audio_language")
        private val SUBTITLE_PRIMARY = stringPreferencesKey("preferred_subtitle_language")
        private val SUBTITLE_SECONDARY = stringPreferencesKey("secondary_subtitle_language")
        private val METADATA_KEY_REFUSED = booleanPreferencesKey("metadata_key_refused")
        private val AUTO_FRAME_RATE = booleanPreferencesKey("auto_frame_rate")
        private val PICTURE_IN_PICTURE = booleanPreferencesKey("picture_in_picture")
        private val SUBTITLE_SIZE = stringPreferencesKey("subtitle_text_size")
        private val SUBTITLE_COLOR = stringPreferencesKey("subtitle_text_color")
        private val SUBTITLE_BACKGROUND = stringPreferencesKey("subtitle_background")

        /**
         * The backup's key table (spec 71 BACKUP-FR-22): what a backup carries and a restore
         * replaces. [DEVICE_LOCAL] is what stays on the TV. A test fails when a key is in neither.
         */
        internal val BACKED_UP: Set<String> = setOf(
            "color_theme", "interface_scale", "startup_screen", "playlist_epg_refresh_interval", "last_guide_source_id",
            "show_channel_numbers", "time_zone", "remote_mappings", "remote_channel_key_mode", "editors_show_hidden",
            "custom_catalogue_groups", "playback_buffer_profile", "playback_reconnect_policy", "playback_seek_step",
            "auto_play_next_episode", "preferred_catalogue_copy", "metadata_language", "preferred_audio_language",
            "secondary_audio_language", "preferred_subtitle_language", "secondary_subtitle_language", "auto_frame_rate",
            "picture_in_picture", "subtitle_text_size", "subtitle_text_color", "subtitle_background", "profiles",
            "active_profile_id", "ask_profile_at_start", "parental_pin_configured", "followed_sports", "followed_competitions",
            "sports_channel_priority",
        )

        /** Per-profile keys (`<base>` and `<base>:<profileId>`), carried in `profileData`. */
        internal val PER_PROFILE: Set<String> = setOf(LAST_CHANNEL_KEY, FAVOURITE_EVENTS_KEY, HOME_LAYOUT_KEY)

        /** Kept by a restore: this TV's own state (spec 71 §6.3). */
        internal val DEVICE_LOCAL: Set<String> = setOf("reminder_overlay_asked", "metadata_key_refused")
    }
}
