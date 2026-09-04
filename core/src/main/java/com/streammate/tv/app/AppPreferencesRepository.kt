package com.streammate.tv.app

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
    val timeZoneId: String = DEFAULT_TIME_ZONE,
    val favouriteEventIds: Set<String> = emptySet(),
    val favouriteChannelIds: Set<String> = emptySet(),
    val recentChannelIds: List<String> = emptyList(),
    val lastChannelId: String? = null,
    val lastGuideSourceId: String? = null,
    val startupScreen: StartupScreen = StartupScreen.HOME,
    val lockedChannelIds: Set<String> = emptySet(),
    val parentalPinConfigured: Boolean = false,
    val remoteChannelKeyMode: RemoteChannelKeyMode = RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS,
    val autoFrameRateEnabled: Boolean = true,
    val autoPlayNextEpisodeEnabled: Boolean = true,
    val followedSports: Set<SportType> = SportsFollowDefaults.sports,
    val followedCompetitionKeys: Set<String> = SportsFollowDefaults.competitionKeys,
    val playlistEpgRefreshInterval: PlaylistEpgRefreshInterval = PlaylistEpgRefreshInterval.DEFAULT,
    val playbackBufferProfile: PlaybackBufferProfile = PlaybackBufferProfile.DEFAULT,
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
        AppPreferences(
            timeZoneId = values[TIME_ZONE] ?: AppPreferences.DEFAULT_TIME_ZONE,
            favouriteEventIds = values[FAVOURITE_EVENT_IDS]?.toSet().orEmpty(),
            favouriteChannelIds = values[FAVOURITE_CHANNEL_IDS]?.toSet().orEmpty(),
            recentChannelIds = values[RECENT_CHANNEL_IDS]
                ?.split(RECENT_SEPARATOR)
                ?.filter(String::isNotBlank)
                .orEmpty(),
            lastChannelId = values[LAST_CHANNEL_ID],
            lastGuideSourceId = values[LAST_GUIDE_SOURCE_ID],
            startupScreen = values[STARTUP_SCREEN]
                ?.let { stored -> StartupScreen.entries.firstOrNull { it.name == stored } }
                ?: StartupScreen.HOME,
            lockedChannelIds = values[LOCKED_CHANNEL_IDS]?.toSet().orEmpty(),
            parentalPinConfigured = values[PARENTAL_PIN_CONFIGURED] ?: false,
            remoteChannelKeyMode = values[REMOTE_CHANNEL_KEY_MODE]
                ?.let { stored -> RemoteChannelKeyMode.entries.firstOrNull { it.name == stored } }
                ?: RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS,
            autoFrameRateEnabled = values[AUTO_FRAME_RATE] ?: true,
            autoPlayNextEpisodeEnabled = values[AUTO_PLAY_NEXT_EPISODE] ?: true,
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

    suspend fun setFavourite(eventId: String, favourite: Boolean) {
        context.sportMatePreferences.edit { values ->
            val updated = values[FAVOURITE_EVENT_IDS]?.toMutableSet() ?: mutableSetOf()
            if (favourite) updated.add(eventId) else updated.remove(eventId)
            values[FAVOURITE_EVENT_IDS] = updated
        }
    }

    suspend fun setFavouriteChannel(channelId: String, favourite: Boolean) {
        context.sportMatePreferences.edit { values ->
            val updated = values[FAVOURITE_CHANNEL_IDS]?.toMutableSet() ?: mutableSetOf()
            if (favourite) updated.add(channelId) else updated.remove(channelId)
            values[FAVOURITE_CHANNEL_IDS] = updated
        }
    }

    suspend fun recordRecentChannel(channelId: String) {
        context.sportMatePreferences.edit { values ->
            val recent = values[RECENT_CHANNEL_IDS]
                ?.split(RECENT_SEPARATOR)
                ?.filter(String::isNotBlank)
                .orEmpty()
            values[RECENT_CHANNEL_IDS] = (listOf(channelId) + recent.filterNot(channelId::equals))
                .take(MAX_RECENT_CHANNELS)
                .joinToString(RECENT_SEPARATOR)
            values[LAST_CHANNEL_ID] = channelId
        }
    }

    fun managerLocation(room: String): kotlinx.coroutines.flow.Flow<Pair<String?, String?>> =
        context.sportMatePreferences.data.map { values ->
            values[stringPreferencesKey("manager_group_$room")] to values[stringPreferencesKey("manager_source_$room")]
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
            val updated = values[LOCKED_CHANNEL_IDS]?.toMutableSet() ?: mutableSetOf()
            if (locked) updated.add(channelId) else updated.remove(channelId)
            values[LOCKED_CHANNEL_IDS] = updated
        }
    }

    suspend fun setParentalPinConfigured(configured: Boolean) {
        context.sportMatePreferences.edit { values ->
            values[PARENTAL_PIN_CONFIGURED] = configured
            if (!configured) values.remove(LOCKED_CHANNEL_IDS)
        }
    }

    suspend fun setRemoteChannelKeyMode(mode: RemoteChannelKeyMode) {
        context.sportMatePreferences.edit { values -> values[REMOTE_CHANNEL_KEY_MODE] = mode.name }
    }

    suspend fun setAutoFrameRateEnabled(enabled: Boolean) {
        context.sportMatePreferences.edit { values -> values[AUTO_FRAME_RATE] = enabled }
    }

    suspend fun setAutoPlayNextEpisodeEnabled(enabled: Boolean) {
        context.sportMatePreferences.edit { values -> values[AUTO_PLAY_NEXT_EPISODE] = enabled }
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
            values[TIME_ZONE] = restored.timeZoneId
            values[FAVOURITE_EVENT_IDS] = restored.favouriteEventIds
            values[FAVOURITE_CHANNEL_IDS] = restored.favouriteChannelIds
            values[RECENT_CHANNEL_IDS] = restored.recentChannelIds
                .take(MAX_RECENT_CHANNELS)
                .joinToString(RECENT_SEPARATOR)
            restored.lastChannelId?.let { values[LAST_CHANNEL_ID] = it }
            restored.lastGuideSourceId?.let { values[LAST_GUIDE_SOURCE_ID] = it }
            values[STARTUP_SCREEN] = restored.startupScreen.name
            values[PARENTAL_PIN_CONFIGURED] = restored.parentalPinConfigured
            values[REMOTE_CHANNEL_KEY_MODE] = restored.remoteChannelKeyMode.name
            values[AUTO_FRAME_RATE] = restored.autoFrameRateEnabled
            values[AUTO_PLAY_NEXT_EPISODE] = restored.autoPlayNextEpisodeEnabled
            values[FOLLOWED_SPORTS] = restored.followedSports.mapTo(mutableSetOf()) { it.name }
            values[FOLLOWED_COMPETITIONS] = restored.followedCompetitionKeys
            values[PLAYLIST_EPG_REFRESH_INTERVAL] = restored.playlistEpgRefreshInterval.name
            values[PLAYBACK_BUFFER_PROFILE] = restored.playbackBufferProfile.name
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
                values[LOCKED_CHANNEL_IDS] = restored.lockedChannelIds
            }
        }
    }

    private companion object {
        val TIME_ZONE = stringPreferencesKey("time_zone")
        val FAVOURITE_EVENT_IDS = stringSetPreferencesKey("favourite_event_ids")
        val FAVOURITE_CHANNEL_IDS = stringSetPreferencesKey("favourite_channel_ids")
        val RECENT_CHANNEL_IDS = stringPreferencesKey("recent_channel_ids")
        val LAST_CHANNEL_ID = stringPreferencesKey("last_channel_id")
        val LAST_GUIDE_SOURCE_ID = stringPreferencesKey("last_guide_source_id")
        val STARTUP_SCREEN = stringPreferencesKey("startup_screen")
        val LOCKED_CHANNEL_IDS = stringSetPreferencesKey("locked_channel_ids")
        val PARENTAL_PIN_CONFIGURED = booleanPreferencesKey(
            "parental_pin_configured",
        )
        val REMOTE_CHANNEL_KEY_MODE = stringPreferencesKey("remote_channel_key_mode")
        val AUTO_FRAME_RATE = booleanPreferencesKey("auto_frame_rate")
        val AUTO_PLAY_NEXT_EPISODE = booleanPreferencesKey("auto_play_next_episode")
        val FOLLOWED_SPORTS = stringSetPreferencesKey("followed_sports")
        val FOLLOWED_COMPETITIONS = stringSetPreferencesKey("followed_competitions")
        val PLAYLIST_EPG_REFRESH_INTERVAL = stringPreferencesKey("playlist_epg_refresh_interval")
        val PLAYBACK_BUFFER_PROFILE = stringPreferencesKey("playback_buffer_profile")
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
