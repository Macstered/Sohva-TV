package com.streammate.tv.app

import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.SportsFollowDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.sportMatePreferences by preferencesDataStore(name = "streammate_preferences")

data class AppPreferences(
    /** The zone the guide, catch-up and Sohva Sport show times in: the TV's own unless one is chosen. */
    val timeZoneId: String = DEFAULT_TIME_ZONE,
    /** True while no zone has been chosen and [timeZoneId] is the TV's own. */
    val timeZoneFollowsDevice: Boolean = false,
    val favouriteEventIds: Set<String> = emptySet(),
    val favouriteChannelIds: Set<String> = emptySet(),
    val recentChannelIds: List<String> = emptyList(),
    val lastChannelId: String? = null,
    val lastGuideSourceId: String? = null,
    val startupScreen: StartupScreen = StartupScreen.HOME,
    val lockedChannelIds: Set<String> = emptySet(),
    val parentalPinConfigured: Boolean = false,
    /** The household's profiles beyond the implicit first one; see [Profiles]. */
    val profiles: List<Profile> = emptyList(),
    /** Whose favourites, recents, positions and locks the fields above are. */
    val activeProfileId: String = Profiles.DEFAULT_ID,
    /** Whether the app asks who is watching at start once there is more than one profile. */
    val askProfileAtStart: Boolean = true,
    val remoteChannelKeyMode: RemoteChannelKeyMode = RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS,
    /** What each remote button does while watching; see [RemoteMappings]. */
    val remoteMappings: RemoteMappings = RemoteMappings.DEFAULTS,
    /** TMDB language for titles, plots and artwork; see [MetadataLanguages]. */
    val metadataLanguage: String = "en-US",
    /** How large the whole interface is drawn; see [InterfaceScale]. */
    val interfaceScale: InterfaceScale = InterfaceScale.DEFAULT,
    val autoFrameRateEnabled: Boolean = true,
    val autoPlayNextEpisodeEnabled: Boolean = true,
    /** Home while watching shrinks the picture to a corner over the launcher instead of stopping it. */
    val pictureInPictureEnabled: Boolean = false,
    val followedSports: Set<SportType> = SportsFollowDefaults.sports,
    val followedCompetitionKeys: Set<String> = SportsFollowDefaults.competitionKeys,
    val playlistEpgRefreshInterval: PlaylistEpgRefreshInterval = PlaylistEpgRefreshInterval.DEFAULT,
    val playbackBufferProfile: PlaybackBufferProfile = PlaybackBufferProfile.DEFAULT,
    val playbackSeekStep: PlaybackSeekStep = PlaybackSeekStep.DEFAULT,
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.DEFAULT,
    val subtitleTextColor: SubtitleTextColor = SubtitleTextColor.DEFAULT,
    val subtitleBackground: SubtitleBackground = SubtitleBackground.DEFAULT,
    val playbackReconnectPolicy: PlaybackReconnectPolicy = PlaybackReconnectPolicy.STANDARD,
    val hiddenLiveCategories: Set<String> = emptySet(),
    val hiddenMovieCategories: Set<String> = emptySet(),
    val hiddenSeriesCategories: Set<String> = emptySet(),
    val preferredAudioLanguage: String? = null,
    val secondaryAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val secondarySubtitleLanguage: String? = null,
    /** Rows of the genre rail the viewer defined for themselves. */
    val customCatalogueGroups: List<CatalogueCustomGroup> = emptyList(),
    /** Which copy of a duplicated film the library should stand on. */
    val preferredCatalogueCopy: CataloguePreferredCopy = CataloguePreferredCopy.NONE,
) {
    companion object {
        const val DEFAULT_TIME_ZONE = "Europe/Helsinki"
    }
}

enum class PlaylistEpgRefreshInterval(val hours: Long) {
    ONE_HOUR(1),
    TWO_HOURS(2),
    FOUR_HOURS(4),
    TEN_HOURS(10),
    TWENTY_FOUR_HOURS(24),
    ;

    companion object {
        val DEFAULT = TWENTY_FOUR_HOURS

        fun fromStoredValue(value: String?): PlaylistEpgRefreshInterval =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * What matters in this house, when a film is carried by more than one playlist.
 *
 * The wall shows one card per film and something has to decide which copy that
 * card is, which the Watch button then plays. This is that decision, made once
 * rather than every time.
 *
 * [NONE] is the default deliberately. A house that cares gets exactly what it
 * asked for, and a house that has not been asked gets the library in the order
 * its playlists arrived - which is what it had before this setting existed.
 * Everything a preference cannot separate is left in that order too.
 */
enum class CataloguePreferredCopy {
    NONE,
    FINNISH_AUDIO,
    FINNISH_SUBTITLES,
    LARGEST_PICTURE,
    ;

    companion object {
        fun fromStoredValue(value: String?): CataloguePreferredCopy =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

enum class PlaybackBufferProfile {
    DEFAULT,
    LOW_LATENCY,
    STABILITY,
    ;

    companion object {
        fun fromStoredValue(value: String?): PlaybackBufferProfile =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** How far one press of Left or Right, or a mapped skip button, moves playback. */
enum class PlaybackSeekStep(val millis: Long) {
    TEN_SECONDS(10_000L),
    THIRTY_SECONDS(30_000L),
    ONE_MINUTE(60_000L),
    TWO_MINUTES(120_000L),
    ;

    companion object {
        val DEFAULT = TEN_SECONDS
        fun fromStoredValue(value: String?): PlaybackSeekStep = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * How subtitles look. Each choice has "follow the TV" as its default, which
 * keeps the TV's own accessibility caption settings where the TV has them;
 * a TV without that screen is why these exist.
 */
enum class SubtitleTextSize(val factor: Float?) {
    FOLLOW_TV(null),
    SMALL(0.8f),
    NORMAL(1f),
    LARGE(1.3f),
    VERY_LARGE(1.6f),
    ;

    companion object {
        val DEFAULT = FOLLOW_TV
        fun fromStoredValue(value: String?): SubtitleTextSize = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

enum class SubtitleTextColor(val argb: Int?) {
    FOLLOW_TV(null),
    WHITE(0xFFFFFFFF.toInt()),
    YELLOW(0xFFFFE14D.toInt()),
    ;

    companion object {
        val DEFAULT = FOLLOW_TV
        fun fromStoredValue(value: String?): SubtitleTextColor = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

enum class SubtitleBackground {
    FOLLOW_TV,
    NONE,
    SHADOW,
    BOX,
    ;

    companion object {
        val DEFAULT = FOLLOW_TV
        fun fromStoredValue(value: String?): SubtitleBackground = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

enum class PlaybackReconnectPolicy {
    STANDARD,
    PERSISTENT,
    ;

    companion object {
        fun fromStoredValue(value: String?): PlaybackReconnectPolicy =
            entries.firstOrNull { it.name == value } ?: STANDARD
    }
}

enum class StartupScreen {
    HOME,
    GUIDE,
    LAST_CHANNEL,
}

enum class RemoteChannelKeyMode {
    DPAD_AND_CHANNEL_KEYS,
    CHANNEL_KEYS_ONLY,
}

class AppPreferencesRepository(
    private val context: Context,
) {
    val preferences: Flow<AppPreferences> = context.sportMatePreferences.data.map { values ->
        val profileId = values.activeProfileId()
        AppPreferences(
            // Nothing chosen means the TV's own zone. This used to fall back to
            // Helsinki, and a tester six hours away read every programme wrong.
            timeZoneId = values[TIME_ZONE] ?: deviceTimeZoneId(),
            timeZoneFollowsDevice = values[TIME_ZONE] == null,
            favouriteEventIds = values[favouriteEventIdsKey(profileId)]?.toSet().orEmpty(),
            favouriteChannelIds = values[favouriteChannelIdsKey(profileId)]?.toSet().orEmpty(),
            recentChannelIds = values[recentChannelIdsKey(profileId)]
                ?.split(RECENT_SEPARATOR)
                ?.filter(String::isNotBlank)
                .orEmpty(),
            lastChannelId = values[lastChannelIdKey(profileId)],
            lastGuideSourceId = values[LAST_GUIDE_SOURCE_ID],
            startupScreen = values[STARTUP_SCREEN]
                ?.let { stored -> StartupScreen.entries.firstOrNull { it.name == stored } }
                ?: StartupScreen.HOME,
            lockedChannelIds = values[lockedChannelIdsKey(profileId)]?.toSet().orEmpty(),
            parentalPinConfigured = values[PARENTAL_PIN_CONFIGURED] ?: false,
            profiles = Profiles.decode(values[PROFILES]),
            activeProfileId = profileId,
            askProfileAtStart = values[ASK_PROFILE_AT_START] ?: true,
            remoteChannelKeyMode = values[REMOTE_CHANNEL_KEY_MODE]
                ?.let { stored -> RemoteChannelKeyMode.entries.firstOrNull { it.name == stored } }
                ?: RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS,
            remoteMappings = RemoteMappings.fromStored(
                values[REMOTE_MAPPINGS],
                values[REMOTE_CHANNEL_KEY_MODE]
                    ?.let { stored -> RemoteChannelKeyMode.entries.firstOrNull { it.name == stored } }
                    ?: RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS,
            ),
            metadataLanguage = values[METADATA_LANGUAGE]?.takeIf(MetadataLanguages::isSupported)
                ?: MetadataLanguages.defaultFor(AppLocale.stored(context)),
            interfaceScale = InterfaceScale.fromStored(values[INTERFACE_SCALE]),
            autoFrameRateEnabled = values[AUTO_FRAME_RATE] ?: true,
            autoPlayNextEpisodeEnabled = values[AUTO_PLAY_NEXT_EPISODE] ?: true,
            pictureInPictureEnabled = values[PICTURE_IN_PICTURE] ?: false,
            followedSports = values[FOLLOWED_SPORTS]
                ?.mapNotNull { stored -> SportType.entries.firstOrNull { it.name == stored } }
                ?.toSet()
                ?: SportsFollowDefaults.sports,
            followedCompetitionKeys = values[FOLLOWED_COMPETITIONS]?.toSet()
                ?: SportsFollowDefaults.competitionKeys,
            playlistEpgRefreshInterval = PlaylistEpgRefreshInterval.fromStoredValue(
                values[PLAYLIST_EPG_REFRESH_INTERVAL],
            ),
            playbackBufferProfile = PlaybackBufferProfile.fromStoredValue(values[PLAYBACK_BUFFER_PROFILE]),
            playbackSeekStep = PlaybackSeekStep.fromStoredValue(values[PLAYBACK_SEEK_STEP]),
            subtitleTextSize = SubtitleTextSize.fromStoredValue(values[SUBTITLE_TEXT_SIZE]),
            subtitleTextColor = SubtitleTextColor.fromStoredValue(values[SUBTITLE_TEXT_COLOR]),
            subtitleBackground = SubtitleBackground.fromStoredValue(values[SUBTITLE_BACKGROUND]),
            preferredCatalogueCopy = CataloguePreferredCopy.fromStoredValue(values[PREFERRED_CATALOGUE_COPY]),
            playbackReconnectPolicy = PlaybackReconnectPolicy.fromStoredValue(
                values[PLAYBACK_RECONNECT_POLICY],
            ),
            hiddenLiveCategories = values[HIDDEN_LIVE_CATEGORIES]?.toSet().orEmpty(),
            hiddenMovieCategories = values[HIDDEN_MOVIE_CATEGORIES]?.toSet().orEmpty(),
            hiddenSeriesCategories = values[HIDDEN_SERIES_CATEGORIES]?.toSet().orEmpty(),
            preferredAudioLanguage = values[PREFERRED_AUDIO_LANGUAGE],
            secondaryAudioLanguage = values[SECONDARY_AUDIO_LANGUAGE],
            preferredSubtitleLanguage = values[PREFERRED_SUBTITLE_LANGUAGE],
            secondarySubtitleLanguage = values[SECONDARY_SUBTITLE_LANGUAGE],
            customCatalogueGroups = decodeCustomGroups(values[CUSTOM_CATALOGUE_GROUPS]),
        )
    }

    suspend fun setTimeZone(timeZoneId: String) {
        context.sportMatePreferences.edit { values -> values[TIME_ZONE] = timeZoneId }
    }

    /** Back to the TV's own zone, now and whenever it changes. */
    suspend fun followDeviceTimeZone() {
        context.sportMatePreferences.edit { values -> values.remove(TIME_ZONE) }
    }

    suspend fun setFavourite(eventId: String, favourite: Boolean) {
        context.sportMatePreferences.edit { values ->
            val key = favouriteEventIdsKey(values.activeProfileId())
            val updated = values[key]?.toMutableSet() ?: mutableSetOf()
            if (favourite) updated.add(eventId) else updated.remove(eventId)
            values[key] = updated
        }
    }

    suspend fun setFavouriteChannel(channelId: String, favourite: Boolean) {
        context.sportMatePreferences.edit { values ->
            val key = favouriteChannelIdsKey(values.activeProfileId())
            val updated = values[key]?.toMutableSet() ?: mutableSetOf()
            if (favourite) updated.add(channelId) else updated.remove(channelId)
            values[key] = updated
        }
    }

    suspend fun recordRecentChannel(channelId: String) {
        context.sportMatePreferences.edit { values ->
            val profileId = values.activeProfileId()
            val recentKey = recentChannelIdsKey(profileId)
            val recent = values[recentKey]
                ?.split(RECENT_SEPARATOR)
                ?.filter(String::isNotBlank)
                .orEmpty()
            values[recentKey] = (listOf(channelId) + recent.filterNot(channelId::equals))
                .take(MAX_RECENT_CHANNELS)
                .joinToString(RECENT_SEPARATOR)
            values[lastChannelIdKey(profileId)] = channelId
        }
    }

    // ---- profiles ----

    suspend fun setActiveProfile(profileId: String) {
        context.sportMatePreferences.edit { values ->
            if (profileId == Profiles.DEFAULT_ID) values.remove(ACTIVE_PROFILE_ID) else values[ACTIVE_PROFILE_ID] = profileId
        }
    }

    /** A new viewer, empty-handed; null when the household is full. */
    suspend fun addProfile(name: String, colorIndex: Int): Profile? {
        var added: Profile? = null
        context.sportMatePreferences.edit { values ->
            val current = Profiles.decode(values[PROFILES])
            if (current.count { it.id != Profiles.DEFAULT_ID } + 1 >= Profiles.MAX_PROFILES) return@edit
            val profile = Profile(Profiles.newId(), name.trim().take(Profiles.MAX_NAME_LENGTH), colorIndex.coerceIn(0, Profiles.COLOR_COUNT - 1))
            values[PROFILES] = Profiles.encode(current + profile)
            added = profile
        }
        return added
    }

    suspend fun renameProfile(profileId: String, name: String, colorIndex: Int? = null) {
        context.sportMatePreferences.edit { values ->
            val current = Profiles.decode(values[PROFILES])
            val trimmed = name.trim().take(Profiles.MAX_NAME_LENGTH)
            val updated = if (current.any { it.id == profileId }) {
                current.map { if (it.id == profileId) it.copy(name = trimmed, colorIndex = colorIndex ?: it.colorIndex) else it }
            } else if (profileId == Profiles.DEFAULT_ID) {
                current + Profile(Profiles.DEFAULT_ID, trimmed, colorIndex ?: 0)
            } else {
                current
            }
            values[PROFILES] = Profiles.encode(updated)
        }
    }

    /** Removes a profile and everything it kept; the default profile stays. */
    suspend fun removeProfile(profileId: String) {
        if (profileId == Profiles.DEFAULT_ID) return
        context.sportMatePreferences.edit { values ->
            values[PROFILES] = Profiles.encode(Profiles.decode(values[PROFILES]).filterNot { it.id == profileId })
            values.remove(favouriteEventIdsKey(profileId))
            values.remove(favouriteChannelIdsKey(profileId))
            values.remove(recentChannelIdsKey(profileId))
            values.remove(lastChannelIdKey(profileId))
            values.remove(lockedChannelIdsKey(profileId))
            if (values[ACTIVE_PROFILE_ID] == profileId) values.remove(ACTIVE_PROFILE_ID)
        }
    }

    suspend fun setAskProfileAtStart(ask: Boolean) {
        context.sportMatePreferences.edit { values -> values[ASK_PROFILE_AT_START] = ask }
    }

    /** What [profileId] keeps, for a backup. */
    suspend fun profileData(profileId: String): ProfileData {
        val values = context.sportMatePreferences.data.first()
        return ProfileData(
            favouriteEventIds = values[favouriteEventIdsKey(profileId)]?.toSet().orEmpty(),
            favouriteChannelIds = values[favouriteChannelIdsKey(profileId)]?.toSet().orEmpty(),
            recentChannelIds = values[recentChannelIdsKey(profileId)]?.split(RECENT_SEPARATOR)?.filter(String::isNotBlank).orEmpty(),
            lastChannelId = values[lastChannelIdKey(profileId)],
            lockedChannelIds = values[lockedChannelIdsKey(profileId)]?.toSet().orEmpty(),
        )
    }

    /** Puts back what each profile kept, after [restore] has laid down everything else. */
    suspend fun restoreProfileData(data: Map<String, ProfileData>, parentalPinConfigured: Boolean) {
        context.sportMatePreferences.edit { values ->
            data.forEach { (profileId, kept) ->
                values[favouriteEventIdsKey(profileId)] = kept.favouriteEventIds
                values[favouriteChannelIdsKey(profileId)] = kept.favouriteChannelIds
                values[recentChannelIdsKey(profileId)] = kept.recentChannelIds.take(MAX_RECENT_CHANNELS).joinToString(RECENT_SEPARATOR)
                kept.lastChannelId?.let { values[lastChannelIdKey(profileId)] = it }
                if (parentalPinConfigured) values[lockedChannelIdsKey(profileId)] = kept.lockedChannelIds
            }
        }
    }

    fun managerLocation(room: String): kotlinx.coroutines.flow.Flow<Pair<String?, String?>> =
        context.sportMatePreferences.data.map { values ->
            values[stringPreferencesKey("manager_group_$room")] to values[stringPreferencesKey("manager_source_$room")]
        }

    /** Whether the viewer has been shown, once, how to let reminders open the app. */
    suspend fun reminderOverlayAsked(): Boolean =
        context.sportMatePreferences.data.map { values -> values[booleanPreferencesKey("reminder_overlay_asked")] ?: false }.first()

    suspend fun setReminderOverlayAsked() {
        context.sportMatePreferences.edit { values -> values[booleanPreferencesKey("reminder_overlay_asked")] = true }
    }

    /** The catalogue state the film-identity pass last completed for; see OrganizationRepository. */
    suspend fun movieIdentityMark(): String? =
        context.sportMatePreferences.data.map { values -> values[stringPreferencesKey("movie_identity_mark")] }.first()

    suspend fun setMovieIdentityMark(mark: String) {
        context.sportMatePreferences.edit { values -> values[stringPreferencesKey("movie_identity_mark")] = mark.take(4096) }
    }

    suspend fun setManagerLocation(room: String, group: String?, source: String?) {
        context.sportMatePreferences.edit { values ->
            val groupKey = stringPreferencesKey("manager_group_$room")
            val sourceKey = stringPreferencesKey("manager_source_$room")
            if (group == null) values.remove(groupKey) else values[groupKey] = group.take(2048)
            if (source == null) values.remove(sourceKey) else values[sourceKey] = source.take(2048)
        }
    }

    suspend fun setLastGuideSourceId(sourceId: String?) {
        val normalized = sourceId?.trim()?.take(MAX_SOURCE_ID_LENGTH)?.takeIf(String::isNotBlank)
        context.sportMatePreferences.edit { values ->
            if (normalized == null) {
                values.remove(LAST_GUIDE_SOURCE_ID)
            } else {
                values[LAST_GUIDE_SOURCE_ID] = normalized
            }
        }
    }

    suspend fun setStartupScreen(startupScreen: StartupScreen) {
        context.sportMatePreferences.edit { values -> values[STARTUP_SCREEN] = startupScreen.name }
    }

    suspend fun setChannelLocked(channelId: String, locked: Boolean) {
        context.sportMatePreferences.edit { values ->
            val key = lockedChannelIdsKey(values.activeProfileId())
            val updated = values[key]?.toMutableSet() ?: mutableSetOf()
            if (locked) updated.add(channelId) else updated.remove(channelId)
            values[key] = updated
        }
    }

    suspend fun setParentalPinConfigured(configured: Boolean) {
        context.sportMatePreferences.edit { values ->
            values[PARENTAL_PIN_CONFIGURED] = configured
            // The PIN is the household's; without it no profile keeps a locked set.
            if (!configured) {
                (Profiles.decode(values[PROFILES]).map { it.id } + Profiles.DEFAULT_ID).distinct()
                    .forEach { values.remove(lockedChannelIdsKey(it)) }
            }
        }
    }

    suspend fun setRemoteChannelKeyMode(mode: RemoteChannelKeyMode) {
        context.sportMatePreferences.edit { values -> values[REMOTE_CHANNEL_KEY_MODE] = mode.name }
    }

    suspend fun setRemoteMapping(slot: RemoteSlot, action: RemoteAction) {
        context.sportMatePreferences.edit { values ->
            val current = RemoteMappings.fromStored(
                values[REMOTE_MAPPINGS],
                values[REMOTE_CHANNEL_KEY_MODE]
                    ?.let { stored -> RemoteChannelKeyMode.entries.firstOrNull { it.name == stored } }
                    ?: RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS,
            )
            values[REMOTE_MAPPINGS] = current.with(slot, action).encode()
        }
    }

    suspend fun setMetadataLanguage(tag: String) {
        require(MetadataLanguages.isSupported(tag)) { "Unsupported metadata language" }
        context.sportMatePreferences.edit { values -> values[METADATA_LANGUAGE] = tag }
    }

    suspend fun setInterfaceScale(scale: InterfaceScale) {
        context.sportMatePreferences.edit { values -> values[INTERFACE_SCALE] = scale.name }
    }

    suspend fun resetRemoteMappings() {
        context.sportMatePreferences.edit { values -> values[REMOTE_MAPPINGS] = RemoteMappings.DEFAULTS.encode() }
    }

    suspend fun setAutoFrameRateEnabled(enabled: Boolean) {
        context.sportMatePreferences.edit { values -> values[AUTO_FRAME_RATE] = enabled }
    }

    suspend fun setAutoPlayNextEpisodeEnabled(enabled: Boolean) {
        context.sportMatePreferences.edit { values -> values[AUTO_PLAY_NEXT_EPISODE] = enabled }
    }

    suspend fun setPictureInPictureEnabled(enabled: Boolean) {
        context.sportMatePreferences.edit { values -> values[PICTURE_IN_PICTURE] = enabled }
    }

    suspend fun setFollowedSport(sport: SportType, followed: Boolean) {
        context.sportMatePreferences.edit { values ->
            val updated = values[FOLLOWED_SPORTS]
                ?.mapNotNullTo(mutableSetOf()) { stored ->
                    SportType.entries.firstOrNull { it.name == stored }
                }
                ?: SportsFollowDefaults.sports.toMutableSet()
            if (followed) updated.add(sport) else updated.remove(sport)
            values[FOLLOWED_SPORTS] = updated.mapTo(mutableSetOf()) { it.name }
        }
    }

    suspend fun setFollowedCompetition(preferenceKey: String, followed: Boolean) {
        context.sportMatePreferences.edit { values ->
            val updated = values[FOLLOWED_COMPETITIONS]?.toMutableSet()
                ?: SportsFollowDefaults.competitionKeys.toMutableSet()
            if (followed) updated.add(preferenceKey) else updated.remove(preferenceKey)
            values[FOLLOWED_COMPETITIONS] = updated
        }
    }

    suspend fun setPlaylistEpgRefreshInterval(interval: PlaylistEpgRefreshInterval) {
        context.sportMatePreferences.edit { values ->
            values[PLAYLIST_EPG_REFRESH_INTERVAL] = interval.name
        }
    }

    suspend fun setPreferredCatalogueCopy(preferred: CataloguePreferredCopy) {
        context.sportMatePreferences.edit { values ->
            values[PREFERRED_CATALOGUE_COPY] = preferred.name
        }
    }

    suspend fun setPlaybackBufferProfile(profile: PlaybackBufferProfile) {
        context.sportMatePreferences.edit { values ->
            values[PLAYBACK_BUFFER_PROFILE] = profile.name
        }
    }

    suspend fun setPlaybackSeekStep(step: PlaybackSeekStep) {
        context.sportMatePreferences.edit { values ->
            values[PLAYBACK_SEEK_STEP] = step.name
        }
    }

    suspend fun setSubtitleTextSize(size: SubtitleTextSize) {
        context.sportMatePreferences.edit { values -> values[SUBTITLE_TEXT_SIZE] = size.name }
    }

    suspend fun setSubtitleTextColor(color: SubtitleTextColor) {
        context.sportMatePreferences.edit { values -> values[SUBTITLE_TEXT_COLOR] = color.name }
    }

    suspend fun setSubtitleBackground(background: SubtitleBackground) {
        context.sportMatePreferences.edit { values -> values[SUBTITLE_BACKGROUND] = background.name }
    }

    suspend fun setPlaybackReconnectPolicy(policy: PlaybackReconnectPolicy) {
        context.sportMatePreferences.edit { values ->
            values[PLAYBACK_RECONNECT_POLICY] = policy.name
        }
    }

    /**
     * Adds a group, or replaces the one with the same id.
     *
     * A group with nothing in it is refused rather than stored: it would sit in
     * the rail collecting the whole library under whatever name it was given.
     */
    suspend fun saveCustomCatalogueGroup(group: CatalogueCustomGroup) {
        val sanitized = group.copy(name = group.name.trim().take(MAX_GROUP_NAME_LENGTH))
        if (!sanitized.isUsable) return
        context.sportMatePreferences.edit { values ->
            val existing = decodeCustomGroups(values[CUSTOM_CATALOGUE_GROUPS])
            val updated = existing.filterNot { it.id == sanitized.id } + sanitized
            values[CUSTOM_CATALOGUE_GROUPS] = encodeCustomGroups(updated.take(MAX_CUSTOM_GROUPS))
        }
    }

    suspend fun deleteCustomCatalogueGroup(id: String) {
        context.sportMatePreferences.edit { values ->
            val remaining = decodeCustomGroups(values[CUSTOM_CATALOGUE_GROUPS])
                .filterNot { it.id == id }
            values[CUSTOM_CATALOGUE_GROUPS] = encodeCustomGroups(remaining)
        }
    }

    suspend fun setCategoryHidden(room: CategoryRoom, category: String, hidden: Boolean) {
        val normalized = category.trim().takeIf(String::isNotEmpty) ?: return
        val key = when (room) {
            CategoryRoom.LIVE_TV -> HIDDEN_LIVE_CATEGORIES
            CategoryRoom.MOVIES -> HIDDEN_MOVIE_CATEGORIES
            CategoryRoom.SERIES -> HIDDEN_SERIES_CATEGORIES
        }
        context.sportMatePreferences.edit { values ->
            val updated = values[key]?.toMutableSet() ?: mutableSetOf()
            val existing = updated.firstOrNull { it.equals(normalized, ignoreCase = true) }
            if (hidden) {
                if (existing == null) updated.add(normalized)
            } else if (existing != null) {
                updated.remove(existing)
            }
            values[key] = updated
        }
    }

    suspend fun setPreferredLanguage(slot: PreferredLanguageSlot, languageCode: String?) {
        val normalized = languageCode?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
        val key = when (slot) {
            PreferredLanguageSlot.PRIMARY_AUDIO -> PREFERRED_AUDIO_LANGUAGE
            PreferredLanguageSlot.SECONDARY_AUDIO -> SECONDARY_AUDIO_LANGUAGE
            PreferredLanguageSlot.PRIMARY_SUBTITLE -> PREFERRED_SUBTITLE_LANGUAGE
            PreferredLanguageSlot.SECONDARY_SUBTITLE -> SECONDARY_SUBTITLE_LANGUAGE
        }
        context.sportMatePreferences.edit { values ->
            if (normalized == null) values.remove(key) else values[key] = normalized
        }
    }

    suspend fun restore(restored: AppPreferences) {
        context.sportMatePreferences.edit { values ->
            values.clear()
            val profileId = restored.activeProfileId
            values[PROFILES] = Profiles.encode(restored.profiles)
            if (profileId == Profiles.DEFAULT_ID) values.remove(ACTIVE_PROFILE_ID) else values[ACTIVE_PROFILE_ID] = profileId
            values[ASK_PROFILE_AT_START] = restored.askProfileAtStart
            if (restored.timeZoneFollowsDevice) values.remove(TIME_ZONE) else values[TIME_ZONE] = restored.timeZoneId
            values[favouriteEventIdsKey(profileId)] = restored.favouriteEventIds
            values[favouriteChannelIdsKey(profileId)] = restored.favouriteChannelIds
            values[recentChannelIdsKey(profileId)] = restored.recentChannelIds
                .take(MAX_RECENT_CHANNELS)
                .joinToString(RECENT_SEPARATOR)
            restored.lastChannelId?.let { values[lastChannelIdKey(profileId)] = it }
            restored.lastGuideSourceId?.let { values[LAST_GUIDE_SOURCE_ID] = it }
            values[STARTUP_SCREEN] = restored.startupScreen.name
            values[PARENTAL_PIN_CONFIGURED] = restored.parentalPinConfigured
            values[REMOTE_CHANNEL_KEY_MODE] = restored.remoteChannelKeyMode.name
            values[REMOTE_MAPPINGS] = restored.remoteMappings.encode()
            values[METADATA_LANGUAGE] = restored.metadataLanguage
            values[INTERFACE_SCALE] = restored.interfaceScale.name
            values[AUTO_FRAME_RATE] = restored.autoFrameRateEnabled
            values[AUTO_PLAY_NEXT_EPISODE] = restored.autoPlayNextEpisodeEnabled
            values[PICTURE_IN_PICTURE] = restored.pictureInPictureEnabled
            values[FOLLOWED_SPORTS] = restored.followedSports.mapTo(mutableSetOf()) { it.name }
            values[FOLLOWED_COMPETITIONS] = restored.followedCompetitionKeys
            values[PLAYLIST_EPG_REFRESH_INTERVAL] = restored.playlistEpgRefreshInterval.name
            values[PLAYBACK_BUFFER_PROFILE] = restored.playbackBufferProfile.name
            values[PLAYBACK_SEEK_STEP] = restored.playbackSeekStep.name
            values[SUBTITLE_TEXT_SIZE] = restored.subtitleTextSize.name
            values[SUBTITLE_TEXT_COLOR] = restored.subtitleTextColor.name
            values[SUBTITLE_BACKGROUND] = restored.subtitleBackground.name
            values[PREFERRED_CATALOGUE_COPY] = restored.preferredCatalogueCopy.name
            values[PLAYBACK_RECONNECT_POLICY] = restored.playbackReconnectPolicy.name
            values[HIDDEN_LIVE_CATEGORIES] = restored.hiddenLiveCategories
            values[HIDDEN_MOVIE_CATEGORIES] = restored.hiddenMovieCategories
            values[HIDDEN_SERIES_CATEGORIES] = restored.hiddenSeriesCategories
            values[CUSTOM_CATALOGUE_GROUPS] = encodeCustomGroups(
                restored.customCatalogueGroups.take(MAX_CUSTOM_GROUPS),
            )
            restored.preferredAudioLanguage?.let { values[PREFERRED_AUDIO_LANGUAGE] = it }
            restored.secondaryAudioLanguage?.let { values[SECONDARY_AUDIO_LANGUAGE] = it }
            restored.preferredSubtitleLanguage?.let { values[PREFERRED_SUBTITLE_LANGUAGE] = it }
            restored.secondarySubtitleLanguage?.let { values[SECONDARY_SUBTITLE_LANGUAGE] = it }
            if (restored.parentalPinConfigured) {
                values[lockedChannelIdsKey(restored.activeProfileId)] = restored.lockedChannelIds
            }
        }
    }

    private fun Preferences.activeProfileId(): String = this[ACTIVE_PROFILE_ID] ?: Profiles.DEFAULT_ID
    private fun favouriteEventIdsKey(profileId: String) = stringSetPreferencesKey(Profiles.keyName("favourite_event_ids", profileId))
    private fun favouriteChannelIdsKey(profileId: String) = stringSetPreferencesKey(Profiles.keyName("favourite_channel_ids", profileId))
    private fun recentChannelIdsKey(profileId: String) = stringPreferencesKey(Profiles.keyName("recent_channel_ids", profileId))
    private fun lastChannelIdKey(profileId: String) = stringPreferencesKey(Profiles.keyName("last_channel_id", profileId))
    private fun lockedChannelIdsKey(profileId: String) = stringSetPreferencesKey(Profiles.keyName("locked_channel_ids", profileId))

    private companion object {
        val TIME_ZONE = stringPreferencesKey("time_zone")
        val PROFILES = stringPreferencesKey("profiles")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val ASK_PROFILE_AT_START = booleanPreferencesKey("ask_profile_at_start")
        val LAST_GUIDE_SOURCE_ID = stringPreferencesKey("last_guide_source_id")
        val STARTUP_SCREEN = stringPreferencesKey("startup_screen")
        val PARENTAL_PIN_CONFIGURED = booleanPreferencesKey(
            "parental_pin_configured",
        )
        val REMOTE_CHANNEL_KEY_MODE = stringPreferencesKey("remote_channel_key_mode")
        val REMOTE_MAPPINGS = stringSetPreferencesKey("remote_mappings")
        val METADATA_LANGUAGE = stringPreferencesKey("metadata_language")
        val INTERFACE_SCALE = stringPreferencesKey("interface_scale")
        val AUTO_FRAME_RATE = booleanPreferencesKey("auto_frame_rate")
        val AUTO_PLAY_NEXT_EPISODE = booleanPreferencesKey("auto_play_next_episode")
        val PICTURE_IN_PICTURE = booleanPreferencesKey("picture_in_picture")
        val FOLLOWED_SPORTS = stringSetPreferencesKey("followed_sports")
        val FOLLOWED_COMPETITIONS = stringSetPreferencesKey("followed_competitions")
        val PLAYLIST_EPG_REFRESH_INTERVAL = stringPreferencesKey("playlist_epg_refresh_interval")
        val PLAYBACK_BUFFER_PROFILE = stringPreferencesKey("playback_buffer_profile")
        val PLAYBACK_SEEK_STEP = stringPreferencesKey("playback_seek_step")
        val SUBTITLE_TEXT_SIZE = stringPreferencesKey("subtitle_text_size")
        val SUBTITLE_TEXT_COLOR = stringPreferencesKey("subtitle_text_color")
        val SUBTITLE_BACKGROUND = stringPreferencesKey("subtitle_background")
        val PLAYBACK_RECONNECT_POLICY = stringPreferencesKey("playback_reconnect_policy")
        val HIDDEN_LIVE_CATEGORIES = stringSetPreferencesKey("hidden_live_categories")
        val HIDDEN_MOVIE_CATEGORIES = stringSetPreferencesKey("hidden_movie_categories")
        val HIDDEN_SERIES_CATEGORIES = stringSetPreferencesKey("hidden_series_categories")
        val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
        val SECONDARY_AUDIO_LANGUAGE = stringPreferencesKey("secondary_audio_language")
        val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
        val SECONDARY_SUBTITLE_LANGUAGE = stringPreferencesKey("secondary_subtitle_language")
        val CUSTOM_CATALOGUE_GROUPS = stringPreferencesKey("custom_catalogue_groups")
        val PREFERRED_CATALOGUE_COPY = stringPreferencesKey("preferred_catalogue_copy")
        const val RECENT_SEPARATOR = "\u001F"
        const val MAX_RECENT_CHANNELS = 20
        const val MAX_SOURCE_ID_LENGTH = 128

        /** More rows than this is a menu rather than a shortcut. */
        const val MAX_CUSTOM_GROUPS = 24
        const val MAX_GROUP_NAME_LENGTH = 40
    }
}

/**
 * Custom groups as they sit in the preference, and back again.
 *
 * Written by hand rather than with a serialisation library because this module
 * carries none, and the shape is four fields. Anything unreadable - a group
 * written by a later version, a genre this one has never heard of - is dropped
 * rather than allowed to throw: a preference file that cannot be parsed would
 * take every other setting down with it.
 */
internal fun encodeCustomGroups(groups: List<CatalogueCustomGroup>): String {
    val array = JSONArray()
    groups.forEach { group ->
        array.put(
            JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("genres", JSONArray(group.genres.map(CatalogueGenre::wireValue)))
                group.fromYear?.let { put("fromYear", it) }
                group.toYear?.let { put("toYear", it) }
                group.minRating?.let { put("minRating", it) }
            },
        )
    }
    return array.toString()
}

internal fun decodeCustomGroups(value: String?): List<CatalogueCustomGroup> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(value)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = item.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val genresJson = item.optJSONArray("genres")
            val genres = (0 until (genresJson?.length() ?: 0))
                .mapNotNull { CatalogueGenre.fromWireValue(genresJson?.optString(it)) }
                .toSet()
            CatalogueCustomGroup(
                id = id,
                name = name,
                genres = genres,
                fromYear = if (item.has("fromYear")) item.optInt("fromYear") else null,
                toYear = if (item.has("toYear")) item.optInt("toYear") else null,
                minRating = if (item.has("minRating")) item.optDouble("minRating") else null,
            ).takeIf(CatalogueCustomGroup::isUsable)
        }
    }.getOrDefault(emptyList())
}

enum class CategoryRoom {
    LIVE_TV,
    MOVIES,
    SERIES,
}

enum class PreferredLanguageSlot {
    PRIMARY_AUDIO,
    SECONDARY_AUDIO,
    PRIMARY_SUBTITLE,
    SECONDARY_SUBTITLE,
}

/** The TV's own zone as an id the rest of the app formats with. */
fun deviceTimeZoneId(): String = java.util.TimeZone.getDefault().id
