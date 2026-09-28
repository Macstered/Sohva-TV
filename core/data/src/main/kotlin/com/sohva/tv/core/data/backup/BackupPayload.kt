package com.sohva.tv.core.data.backup

import com.sohva.tv.core.model.source.SourceConfig

/**
 * The decrypted `.smbak` payload (spec 71 §6.2, format version 2) as typed rows, never a JSON
 * tree (§9 NFR-03). Field meanings are beta 23's; the rebuild maps them onto its own stores when
 * restoring and back when saving.
 */
data class BackupPayload(
    val formatVersion: Int,
    val sources: List<SourceConfig>,
    /** 4–8 digits, or null. */
    val parentalPin: String?,
    val preferences: BackupPreferences,
    /** Keyed by profile id (`default` and every added profile). */
    val profileData: Map<String, ProfileKept>,
    val channelPreferences: List<BackupChannel>,
    val channelLists: List<BackupList>,
    val channelListMembers: List<BackupMember>,
    val rules: List<BackupRule>,
    val aliases: List<BackupAlias>,
)

/** What a profile keeps (spec 71 §6.2 `profileData`); the allowed sets are group keys, empty = everything. */
data class ProfileKept(
    val favouriteEventIds: List<String> = emptyList(),
    val favouriteChannelIds: List<String> = emptyList(),
    /** Most recent first, at most 20. */
    val recentChannelIds: List<String> = emptyList(),
    val lastChannelId: String? = null,
    val lockedChannelIds: List<String> = emptyList(),
    val allowedLive: List<String> = emptyList(),
    val allowedMovies: List<String> = emptyList(),
    val allowedSeries: List<String> = emptyList(),
    /** The Home layout's stored text (spec 02 HOME-FR-91); null = the default. Beta 23 ignores it. */
    val homeLayout: String? = null,
)

/** One profile of `preferences.profiles`. */
data class BackupProfile(val id: String, val name: String, val color: Int)

/**
 * `preferences` (spec 71 §6.2, §6.4): the backed-up settings as their stored values. Enum values
 * are kept as their stored names; the reader has already checked them.
 */
data class BackupPreferences(
    /** Null when the TV's own zone is followed. */
    val timeZoneId: String?,
    val profiles: List<BackupProfile> = emptyList(),
    val activeProfileId: String = "default",
    val askProfileAtStart: Boolean = true,
    val lastGuideSourceId: String? = null,
    val startupScreen: String = "HOME",
    val remoteChannelKeyMode: String = "DPAD_AND_CHANNEL_KEYS",
    /** Null: the defaults the key mode implies. */
    val remoteMappings: List<String>? = null,
    val metadataLanguage: String? = null,
    val interfaceScale: String? = null,
    val colorTheme: String? = null,
    /** Sohva Sport's (M8), carried under beta 23's keys meanwhile; null = not in the file. */
    val followedSports: List<String>? = null,
    val followedCompetitionKeys: List<String>? = null,
    val sportsChannelPriority: List<String>? = null,
    val refreshInterval: String? = null,
    val bufferProfile: String? = null,
    val seekStep: String? = null,
    val subtitleSize: String? = null,
    val subtitleColor: String? = null,
    val subtitleBackground: String? = null,
    val reconnectPolicy: String? = null,
    val autoPlayNext: Boolean = true,
    val pictureInPicture: Boolean = false,
    /** New in the rebuild (BACKUP-FR-23); null keeps the TV's own. */
    val autoFrameRate: Boolean? = null,
    val editorsShowHidden: Boolean = true,
    val showChannelNumbers: Boolean = true,
    val preferredCopy: String? = null,
    val hiddenLive: List<String> = emptyList(),
    val hiddenMovies: List<String> = emptyList(),
    val hiddenSeries: List<String> = emptyList(),
    val audioPrimary: String? = null,
    val audioSecondary: String? = null,
    val subtitlesPrimary: String? = null,
    val subtitlesSecondary: String? = null,
    /** The JSON array text of `custom_catalogue_groups`, as the store keeps it. */
    val customGroupsJson: String? = null,
)

/** One `channelPreferences` entry: a channel's edits (beta 23 `channel_preferences`). */
data class BackupChannel(
    val channelId: String,
    val sourceId: String,
    val customName: String?,
    val customGroupTitle: String?,
    val hidden: Boolean,
    /** Beta 23's dense order index; the rebuild keeps sparse positions. */
    val sortOrder: Int?,
    val manualXmltvChannelId: String?,
    val updatedAt: Long,
    val customLogoUrl: String?,
    val channelNumber: Int?,
    /** A phone-sent logo's picture bytes (spec 71 BACKUP-06). */
    val customLogoData: ByteArray?,
)

data class BackupList(val listId: String, val name: String, val sortOrder: Int, val updatedAt: Long)

data class BackupMember(val listId: String, val channelId: String, val sortOrder: Int)

data class BackupRule(
    val room: String,
    val sourceId: String,
    val groupKey: String,
    val itemKey: String,
    val enabled: Boolean?,
    val sortMode: String?,
    val position: Long?,
)

/** Beta 23's film identity index: `alias` belongs to film `identity` (spec 71 §4.4). */
data class BackupAlias(val alias: String, val identity: String)
