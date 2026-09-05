package com.streammate.tv.app

import android.content.Context
import android.net.Uri
import com.streammate.tv.R
import com.streammate.tv.core.database.ChannelPreferenceEntity
import com.streammate.tv.core.database.CustomChannelListEntity
import com.streammate.tv.core.database.CustomChannelListMemberEntity
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.SportsFollowDefaults
import com.streammate.tv.core.security.IptvSourceConfigurationCodec
import com.streammate.tv.core.security.PortableBackupCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.repository.ChannelCustomizationSnapshot
import com.streammate.tv.iptv.repository.GuideRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.model.CatalogueGenre
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

data class BackupRestoreResult(
    val sourceCount: Int,
    val customizedChannelCount: Int,
    val customListCount: Int,
)

class StreamMateBackupManager(
    context: Context,
    private val secretSettingsStore: SecretSettingsStore,
    private val preferencesRepository: AppPreferencesRepository,
    private val guideRepository: GuideRepository,
) {
    private val applicationContext = context.applicationContext
    private val contentResolver = applicationContext.contentResolver

    suspend fun write(uri: Uri, passphrase: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val sources = secretSettingsStore.loadSources()
        val preferences = preferencesRepository.preferences.first()
        val customization = guideRepository.channelCustomizationSnapshot()
        val plainText = encodePayload(
            sources = IptvSourceConfigurationCodec.encode(sources),
            preferences = preferences,
            parentalPin = secretSettingsStore.parentalPinForEncryptedBackup(),
            customization = customization,
        ).toByteArray(Charsets.UTF_8)
        val encrypted = PortableBackupCipher.encrypt(plainText, passphrase.toCharArray())
        checkNotNull(contentResolver.openOutputStream(uri, "wt")) {
            applicationContext.getString(R.string.backup_error_open)
        }.use { output ->
            output.write(encrypted)
            output.flush()
        }
        BackupRestoreResult(sources.size, customization.preferences.size, customization.lists.size)
    }

    suspend fun restore(uri: Uri, passphrase: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val encrypted = checkNotNull(contentResolver.openInputStream(uri)) {
            applicationContext.getString(R.string.backup_error_open)
        }.use(::readBounded)
        val decoded = decodePayload(
            PortableBackupCipher.decrypt(encrypted, passphrase.toCharArray()).toString(Charsets.UTF_8),
        )

        val currentSourceIds = secretSettingsStore.loadSources().mapTo(mutableSetOf()) { it.id }
        val restoredSourceIds = decoded.sources.mapTo(mutableSetOf()) { it.id }
        (currentSourceIds - restoredSourceIds).forEach { guideRepository.clearSource(it) }
        secretSettingsStore.saveSources(decoded.sources)
        decoded.sources.forEach { guideRepository.upsertSourceState(it) }
        guideRepository.restoreChannelCustomization(decoded.customization)
        if (decoded.parentalPin != null) {
            secretSettingsStore.saveParentalPin(decoded.parentalPin)
        }
        preferencesRepository.restore(
            decoded.preferences.copy(
                parentalPinConfigured = decoded.parentalPin != null,
                lockedChannelIds = if (decoded.parentalPin == null) emptySet() else decoded.preferences.lockedChannelIds,
            ),
        )
        if (decoded.parentalPin == null && secretSettingsStore.hasParentalPin()) {
            secretSettingsStore.clearParentalPin()
        }
        guideRepository.organization?.migrateLegacy(decoded.preferences)
        BackupRestoreResult(
            decoded.sources.size,
            decoded.customization.preferences.size,
            decoded.customization.lists.size,
        )
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            require(total <= PortableBackupCipher.MAX_ENCRYPTED_BYTES) {
                applicationContext.getString(R.string.backup_error_too_large)
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun encodePayload(
        sources: String,
        preferences: AppPreferences,
        parentalPin: String?,
        customization: ChannelCustomizationSnapshot,
    ): String = buildJsonObject {
        put("formatVersion", FORMAT_VERSION)
        put("exportedAtEpochMillis", System.currentTimeMillis())
        put("sources", sources)
        put("parentalPin", parentalPin?.let(::JsonPrimitive) ?: JsonNull)
        put("preferences", preferences.toJson())
        put("channelPreferences", JsonArray(customization.preferences.map { it.toJson() }))
        put("channelLists", JsonArray(customization.lists.map { it.toJson() }))
        put("channelListMembers", JsonArray(customization.members.map { it.toJson() }))
        put("organization", customization.organization.toBackupJson())
    }.toString()

    private fun decodePayload(encoded: String): DecodedBackup {
        val root = Json.parseToJsonElement(encoded).jsonObject
        val version = root.requiredInt("formatVersion")
        require(version in 1..FORMAT_VERSION) {
            applicationContext.getString(R.string.backup_error_version)
        }
        val sources = IptvSourceConfigurationCodec.decode(root.requiredString("sources"))
        val parentalPin = root["parentalPin"]?.jsonPrimitive?.contentOrNull
        require(parentalPin == null || parentalPin.matches(Regex("\\d{4,8}"))) {
            applicationContext.getString(R.string.backup_error_pin)
        }
        val customization = ChannelCustomizationSnapshot(
            preferences = root.requiredArray("channelPreferences").map { it.jsonObject.toChannelPreference() },
            lists = root.requiredArray("channelLists").map { it.jsonObject.toChannelList() },
            members = root.requiredArray("channelListMembers").map { it.jsonObject.toChannelListMember() },
            organization = if (version >= 2) organizationFromBackupJson(root.requiredObject("organization"))
                else com.streammate.tv.core.database.OrganizationSnapshot(),
        )
        validateCustomization(customization, sources.mapTo(hashSetOf()) { it.id })
        return DecodedBackup(
            sources = sources,
            preferences = root.requiredObject("preferences").toPreferences(),
            parentalPin = parentalPin,
            customization = customization,
        )
    }

    private fun validateCustomization(snapshot: ChannelCustomizationSnapshot, sourceIds: Set<String>) {
        com.streammate.tv.core.database.validateOrganizationSnapshot(snapshot.organization)
        require(snapshot.preferences.size <= MAX_CUSTOMIZED_CHANNELS) {
            applicationContext.getString(R.string.backup_error_too_many_preferences)
        }
        require(snapshot.lists.size <= MAX_CUSTOM_CHANNEL_LISTS) {
            applicationContext.getString(R.string.backup_error_too_many_lists)
        }
        require(snapshot.members.size <= MAX_CUSTOM_LIST_MEMBERS) {
            applicationContext.getString(R.string.backup_error_too_many_members)
        }
        require(snapshot.preferences.map { it.channelId }.distinct().size == snapshot.preferences.size) {
            applicationContext.getString(R.string.backup_error_duplicate_preference)
        }
        require(snapshot.preferences.all { it.sourceId in sourceIds }) {
            applicationContext.getString(R.string.backup_error_missing_source)
        }
        val listIds = snapshot.lists.map { it.listId }
        require(listIds.distinct().size == listIds.size) {
            applicationContext.getString(R.string.backup_error_duplicate_list)
        }
        require(snapshot.members.all { it.listId in listIds }) {
            applicationContext.getString(R.string.backup_error_missing_list)
        }
        require(snapshot.members.map { it.listId to it.channelId }.distinct().size == snapshot.members.size) {
            applicationContext.getString(R.string.backup_error_duplicate_member)
        }
    }

    private fun AppPreferences.toJson(): JsonObject = buildJsonObject {
        put("timeZoneId", timeZoneId)
        put("favouriteEventIds", favouriteEventIds.toJsonArray())
        put("favouriteChannelIds", favouriteChannelIds.toJsonArray())
        put("recentChannelIds", recentChannelIds.toJsonArray())
        put("lastChannelId", lastChannelId?.let(::JsonPrimitive) ?: JsonNull)
        put("lastGuideSourceId", lastGuideSourceId?.let(::JsonPrimitive) ?: JsonNull)
        put("startupScreen", startupScreen.name)
        put("lockedChannelIds", lockedChannelIds.toJsonArray())
        put("remoteChannelKeyMode", remoteChannelKeyMode.name)
        put("remoteMappings", remoteMappings.encode().sorted().toJsonArray())
        put("metadataLanguage", metadataLanguage)
        put("followedSports", followedSports.map { it.name }.toSet().toJsonArray())
        put("followedCompetitionKeys", followedCompetitionKeys.toJsonArray())
        put("playlistEpgRefreshInterval", playlistEpgRefreshInterval.name)
        put("playbackBufferProfile", playbackBufferProfile.name)
        put("playbackReconnectPolicy", playbackReconnectPolicy.name)
        put("autoPlayNextEpisodeEnabled", autoPlayNextEpisodeEnabled)
        put("preferredCatalogueCopy", preferredCatalogueCopy.name)
        put("hiddenLiveCategories", hiddenLiveCategories.toJsonArray())
        put("hiddenMovieCategories", hiddenMovieCategories.toJsonArray())
        put("hiddenSeriesCategories", hiddenSeriesCategories.toJsonArray())
        put("preferredAudioLanguage", preferredAudioLanguage?.let(::JsonPrimitive) ?: JsonNull)
        put("secondaryAudioLanguage", secondaryAudioLanguage?.let(::JsonPrimitive) ?: JsonNull)
        put("preferredSubtitleLanguage", preferredSubtitleLanguage?.let(::JsonPrimitive) ?: JsonNull)
        put("secondarySubtitleLanguage", secondarySubtitleLanguage?.let(::JsonPrimitive) ?: JsonNull)
        // Written out in full rather than as an opaque string: a backup is a
        // document somebody may one day have to read, and a group is four
        // plain fields.
        put(
            "customCatalogueGroups",
            JsonArray(
                customCatalogueGroups.map { group ->
                    buildJsonObject {
                        put("id", group.id)
                        put("name", group.name)
                        put("genres", JsonArray(group.genres.map { JsonPrimitive(it.wireValue) }))
                        group.fromYear?.let { put("fromYear", JsonPrimitive(it)) }
                        group.toYear?.let { put("toYear", JsonPrimitive(it)) }
                        group.minRating?.let { put("minRating", JsonPrimitive(it)) }
                    }
                },
            ),
        )
    }

    private fun JsonObject.toPreferences(): AppPreferences = AppPreferences(
        timeZoneId = requiredString("timeZoneId").also { require(it.length <= 100) },
        favouriteEventIds = requiredStringSet("favouriteEventIds"),
        favouriteChannelIds = requiredStringSet("favouriteChannelIds"),
        recentChannelIds = requiredStringList("recentChannelIds").take(20),
        lastChannelId = this["lastChannelId"]?.jsonPrimitive?.contentOrNull,
        lastGuideSourceId = optionalString("lastGuideSourceId")?.also { require(it.length <= 128) },
        startupScreen = requiredString("startupScreen").let { stored ->
            StartupScreen.entries.firstOrNull { it.name == stored }
                ?: throw IllegalArgumentException(applicationContext.getString(R.string.backup_error_startup))
        },
        lockedChannelIds = requiredStringSet("lockedChannelIds"),
        remoteChannelKeyMode = requiredString("remoteChannelKeyMode").let { stored ->
            RemoteChannelKeyMode.entries.firstOrNull { it.name == stored }
                ?: throw IllegalArgumentException(applicationContext.getString(R.string.backup_error_remote))
        },
        // Absent from backups written before button mapping existed: those
        // restore to the defaults the legacy setting implies. Entries a newer
        // build wrote for buttons or actions this one lacks are dropped.
        metadataLanguage = optionalString("metadataLanguage")?.takeIf(MetadataLanguages::isSupported)
            ?: MetadataLanguages.defaultFor(AppLocale.stored(applicationContext)),
        remoteMappings = optionalStringList("remoteMappings")?.toSet()?.let(RemoteMappings::decode)
            ?: RemoteMappings.migrated(
                RemoteChannelKeyMode.entries.first { it.name == requiredString("remoteChannelKeyMode") },
            ),
        followedSports = optionalStringList("followedSports")
            ?.map { stored ->
                SportType.entries.firstOrNull { it.name == stored }
                    ?: throw IllegalArgumentException("Invalid followed sport")
            }
            ?.toSet()
            ?: SportsFollowDefaults.sports,
        followedCompetitionKeys = optionalStringList("followedCompetitionKeys")?.toSet()
            ?: SportsFollowDefaults.competitionKeys,
        playlistEpgRefreshInterval = optionalString("playlistEpgRefreshInterval")
            ?.let { stored ->
                PlaylistEpgRefreshInterval.entries.firstOrNull { it.name == stored }
                    ?: throw IllegalArgumentException("Invalid playlist and EPG refresh interval")
            }
            ?: PlaylistEpgRefreshInterval.DEFAULT,
        playbackBufferProfile = optionalString("playbackBufferProfile")
            ?.let { stored ->
                PlaybackBufferProfile.entries.firstOrNull { it.name == stored }
                    ?: throw IllegalArgumentException("Invalid playback buffer profile")
            }
            ?: PlaybackBufferProfile.DEFAULT,
        playbackReconnectPolicy = optionalString("playbackReconnectPolicy")
            ?.let { stored ->
                PlaybackReconnectPolicy.entries.firstOrNull { it.name == stored }
                    ?: throw IllegalArgumentException("Invalid playback reconnect policy")
            }
            ?: PlaybackReconnectPolicy.STANDARD,
        autoPlayNextEpisodeEnabled = optionalBoolean("autoPlayNextEpisodeEnabled") ?: true,
        preferredCatalogueCopy = optionalString("preferredCatalogueCopy")
            ?.let { stored ->
                CataloguePreferredCopy.entries.firstOrNull { it.name == stored }
                    ?: throw IllegalArgumentException("Invalid preferred catalogue copy")
            }
            ?: CataloguePreferredCopy.NONE,
        hiddenLiveCategories = optionalStringList("hiddenLiveCategories")?.toSet().orEmpty(),
        hiddenMovieCategories = optionalStringList("hiddenMovieCategories")?.toSet().orEmpty(),
        hiddenSeriesCategories = optionalStringList("hiddenSeriesCategories")?.toSet().orEmpty(),
        preferredAudioLanguage = optionalString("preferredAudioLanguage"),
        secondaryAudioLanguage = optionalString("secondaryAudioLanguage"),
        preferredSubtitleLanguage = optionalString("preferredSubtitleLanguage"),
        secondarySubtitleLanguage = optionalString("secondarySubtitleLanguage"),
        customCatalogueGroups = optionalCustomCatalogueGroups(),
    )

    private fun ChannelPreferenceEntity.toJson(): JsonObject = buildJsonObject {
        put("channelId", channelId)
        put("sourceId", sourceId)
        put("customName", customName?.let(::JsonPrimitive) ?: JsonNull)
        put("customGroupTitle", customGroupTitle?.let(::JsonPrimitive) ?: JsonNull)
        put("hidden", hidden)
        put("sortOrder", sortOrder?.let(::JsonPrimitive) ?: JsonNull)
        put("manualXmltvChannelId", manualXmltvChannelId?.let(::JsonPrimitive) ?: JsonNull)
        put("updatedAtEpochMillis", updatedAtEpochMillis)
    }

    private fun JsonObject.toChannelPreference(): ChannelPreferenceEntity = ChannelPreferenceEntity(
        channelId = requiredString("channelId"),
        sourceId = requiredString("sourceId"),
        customName = optionalString("customName"),
        customGroupTitle = optionalString("customGroupTitle"),
        hidden = requiredBoolean("hidden"),
        sortOrder = this["sortOrder"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
        manualXmltvChannelId = optionalString("manualXmltvChannelId"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun CustomChannelListEntity.toJson(): JsonObject = buildJsonObject {
        put("listId", listId)
        put("name", name)
        put("sortOrder", sortOrder)
        put("updatedAtEpochMillis", updatedAtEpochMillis)
    }

    private fun JsonObject.toChannelList(): CustomChannelListEntity = CustomChannelListEntity(
        listId = requiredString("listId"),
        name = requiredString("name"),
        sortOrder = requiredInt("sortOrder"),
        updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
    )

    private fun CustomChannelListMemberEntity.toJson(): JsonObject = buildJsonObject {
        put("listId", listId)
        put("channelId", channelId)
        put("sortOrder", sortOrder)
    }

    private fun JsonObject.toChannelListMember(): CustomChannelListMemberEntity = CustomChannelListMemberEntity(
        listId = requiredString("listId"),
        channelId = requiredString("channelId"),
        sortOrder = requiredInt("sortOrder"),
    )

    private fun Collection<String>.toJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))

    private fun JsonObject.requiredString(name: String): String =
        requireNotNull(this[name]?.jsonPrimitive?.contentOrNull) {
            applicationContext.getString(R.string.backup_error_missing_field, name)
        }
            .also {
                require(it.isNotBlank()) {
                    applicationContext.getString(R.string.backup_error_blank_field, name)
                }
                require(it.length <= MAX_STRING_LENGTH) {
                    applicationContext.getString(R.string.backup_error_long_field, name)
                }
            }

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull
            ?.also {
                require(it.length <= MAX_STRING_LENGTH) {
                    applicationContext.getString(R.string.backup_error_long_field, name)
                }
            }

    private fun JsonObject.requiredInt(name: String): Int =
        requireNotNull(this[name]) {
            applicationContext.getString(R.string.backup_error_missing_field, name)
        }.jsonPrimitive.int

    private fun JsonObject.requiredLong(name: String): Long =
        requireNotNull(this[name]) {
            applicationContext.getString(R.string.backup_error_missing_field, name)
        }.jsonPrimitive.long

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        requireNotNull(this[name]) {
            applicationContext.getString(R.string.backup_error_missing_field, name)
        }.jsonPrimitive.boolean

    private fun JsonObject.optionalBoolean(name: String): Boolean? =
        this[name]?.jsonPrimitive?.boolean

    private fun JsonObject.requiredObject(name: String): JsonObject =
        requireNotNull(this[name]) {
            applicationContext.getString(R.string.backup_error_missing_field, name)
        }.jsonObject

    private fun JsonObject.requiredArray(name: String): JsonArray =
        requireNotNull(this[name]) {
            applicationContext.getString(R.string.backup_error_missing_field, name)
        }.jsonArray

    private fun JsonObject.requiredStringList(name: String): List<String> = requiredArray(name).map { element ->
        element.jsonPrimitive.content.also {
            require(it.isNotBlank()) {
                applicationContext.getString(R.string.backup_error_blank_field, name)
            }
            require(it.length <= MAX_STRING_LENGTH) {
                applicationContext.getString(R.string.backup_error_long_field, name)
            }
        }
    }

    private fun JsonObject.requiredStringSet(name: String): Set<String> = requiredStringList(name).toSet()

    private fun JsonObject.optionalStringList(name: String): List<String>? =
        if (this[name] == null) null else requiredStringList(name)

    /**
     * A group that cannot be read is left out rather than failing the restore.
     * Losing one row of the rail is a smaller loss than refusing to bring back
     * a backup because of it.
     */
    private fun JsonObject.optionalCustomCatalogueGroups(): List<CatalogueCustomGroup> {
        val array = this["customCatalogueGroups"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val group = element as? JsonObject ?: return@mapNotNull null
            val id = (group["id"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val name = (group["name"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val genres = (group["genres"] as? JsonArray)
                .orEmpty()
                .mapNotNull { CatalogueGenre.fromWireValue((it as? JsonPrimitive)?.contentOrNull) }
                .toSet()
            CatalogueCustomGroup(
                id = id.take(MAX_STRING_LENGTH),
                name = name.take(MAX_STRING_LENGTH),
                genres = genres,
                fromYear = (group["fromYear"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
                toYear = (group["toYear"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
                minRating = (group["minRating"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull(),
            ).takeIf(CatalogueCustomGroup::isUsable)
        }
    }

    private data class DecodedBackup(
        val sources: List<com.streammate.tv.core.model.IptvSourceConfiguration>,
        val preferences: AppPreferences,
        val parentalPin: String?,
        val customization: ChannelCustomizationSnapshot,
    )

    private companion object {
        const val FORMAT_VERSION = 2
        const val MAX_STRING_LENGTH = 20_000
        const val MAX_CUSTOMIZED_CHANNELS = 100_000
        const val MAX_CUSTOM_CHANNEL_LISTS = 1_000
        const val MAX_CUSTOM_LIST_MEMBERS = 500_000
    }
}
