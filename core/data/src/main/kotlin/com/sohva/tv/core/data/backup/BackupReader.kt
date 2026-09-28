package com.sohva.tv.core.data.backup

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.sohva.tv.core.data.metadata.MetadataConfig
import com.sohva.tv.core.data.prefs.CustomGroupCodec
import com.sohva.tv.core.model.backup.BackupException
import com.sohva.tv.core.model.backup.BackupProblem
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgSort
import com.sohva.tv.core.model.player.BufferProfile
import com.sohva.tv.core.model.player.ReconnectPolicy
import com.sohva.tv.core.model.player.SkipStep
import com.sohva.tv.core.model.player.SubtitleBackground
import com.sohva.tv.core.model.player.SubtitleColor
import com.sohva.tv.core.model.player.SubtitleSize
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.core.model.source.Beta23SourceCodec
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.vod.PreferredCopy
import java.io.IOException
import java.util.Base64
import okio.Buffer
import okio.BufferedSource

/**
 * Reads and checks a payload (spec 71 §7.2, §7.3, §6.4) with a pull parser, never a tree
 * (§9 NFR-03): everything is validated here, before the restore changes anything
 * (BACKUP-FR-18 step 3). Reads what beta 23 reads (formats 1 and 2); every fault is a
 * [BackupException] whose problem has its own sentence (§4.6).
 */
object BackupReader {
    fun read(source: BufferedSource): BackupPayload = try {
        payload(JsonReader.of(source))
    } catch (e: BackupException) {
        throw e
    } catch (e: JsonDataException) {
        throw BackupException(BackupProblem.STRUCTURE, cause = e)
    } catch (e: JsonEncodingException) {
        throw BackupException(BackupProblem.STRUCTURE, cause = e)
    } catch (e: IOException) {
        throw BackupException(BackupProblem.STRUCTURE, cause = e)
    }

    private class Top {
        var version: Int? = null
        var sources: List<SourceConfig>? = null
        var pin: String? = null
        var preferences: Prefs? = null
        var profileData: Map<String, ProfileKept> = emptyMap()
        var channels: List<BackupChannel>? = null
        var lists: List<BackupList>? = null
        var members: List<BackupMember>? = null
        var rules: List<BackupRule>? = null
        var aliases: List<BackupAlias>? = null
    }

    /** `preferences` and the active profile's lists that sit beside them. */
    private class Prefs(val preferences: BackupPreferences, val active: ProfileKept)

    private fun payload(r: JsonReader): BackupPayload {
        val top = Top()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "formatVersion" -> top.version = int(r, "formatVersion")
                "sources" -> top.sources = sources(r)
                "parentalPin" -> top.pin = optionalString(r, "parentalPin")?.also {
                    if (!PIN.matches(it)) throw BackupException(BackupProblem.PIN)
                }
                "preferences" -> top.preferences = preferences(r)
                "profileData" -> top.profileData = profileData(r)
                "channelPreferences" -> top.channels = array(r) { channel(it) }
                "channelLists" -> top.lists = array(r) { list(it) }
                "channelListMembers" -> top.members = array(r) { member(it) }
                "organization" -> organization(r, top)
                else -> r.skipValue()
            }
        }
        r.endObject()
        val version = top.version ?: throw BackupException(BackupProblem.MISSING_FIELD, "formatVersion")
        if (version !in 1..BackupWriter.FORMAT_VERSION) throw BackupException(BackupProblem.FORMAT_VERSION)
        val sources = top.sources ?: throw BackupException(BackupProblem.MISSING_FIELD, "sources")
        val prefs = top.preferences ?: throw BackupException(BackupProblem.MISSING_FIELD, "preferences")
        val channels = top.channels ?: throw BackupException(BackupProblem.MISSING_FIELD, "channelPreferences")
        val lists = top.lists ?: throw BackupException(BackupProblem.MISSING_FIELD, "channelLists")
        val members = top.members ?: throw BackupException(BackupProblem.MISSING_FIELD, "channelListMembers")
        if (version >= 2 && (top.rules == null || top.aliases == null)) throw BackupException(BackupProblem.MISSING_FIELD, "organization")
        checkCustomisation(sources, channels, lists, members)
        val rules = top.rules.orEmpty()
        checkOrganisation(rules, top.aliases.orEmpty())
        // The lists beside `preferences` are the active profile's; `profileData` overrides them (beta 23's order).
        val active = prefs.preferences.activeProfileId
        val profileData = if (active in top.profileData) top.profileData else top.profileData + (active to prefs.active)
        return BackupPayload(
            version, sources, top.pin, prefs.preferences, profileData, channels, lists, members, rules, top.aliases.orEmpty(),
        )
    }

    private fun sources(r: JsonReader): List<SourceConfig> {
        // Not capped at 20,000 characters: the codec's own bounds (100 sources) decide (spec 71 §8).
        val text = if (r.peek() == JsonReader.Token.NULL) {
            r.nextNull<Unit>()
            throw BackupException(BackupProblem.MISSING_FIELD, "sources")
        } else {
            r.nextString()
        }
        if (text.isBlank()) throw BackupException(BackupProblem.BLANK_FIELD, "sources")
        return try {
            Beta23SourceCodec.decode(text)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupProblem.STRUCTURE, "sources", e)
        }
    }

    private fun preferences(r: JsonReader): Prefs {
        val values = HashMap<String, Any?>()
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            values[name] = when (name) {
                "timeZoneId" -> string(r, name).also { if (it.length > ZONE_MAX) throw BackupException(BackupProblem.LONG_FIELD, name) }
                "favouriteEventIds", "favouriteChannelIds", "lockedChannelIds", "recentChannelIds",
                "remoteMappings", "followedSports", "followedCompetitionKeys", "sportsChannelPriority",
                "hiddenLiveCategories", "hiddenMovieCategories", "hiddenSeriesCategories",
                -> strings(r, name)
                "profiles" -> profiles(r)
                "timeZoneFollowsDevice", "askProfileAtStart", "autoPlayNextEpisodeEnabled", "pictureInPictureEnabled",
                "autoFrameRateEnabled", "editorsShowHidden", "showChannelNumbers",
                -> bool(r, name)
                "customCatalogueGroups" -> customGroups(r)
                else -> if (r.peek() == JsonReader.Token.STRING || r.peek() == JsonReader.Token.NULL) optionalString(r, name) else r.skipValue().let { null }
            }
        }
        r.endObject()
        fun list(name: String, required: Boolean = false): List<String>? {
            if (name !in values) return if (required) throw BackupException(BackupProblem.MISSING_FIELD, name) else null
            @Suppress("UNCHECKED_CAST")
            return values[name] as List<String>
        }
        fun text(name: String): String? = values[name] as? String
        fun flag(name: String, default: Boolean): Boolean = values[name] as? Boolean ?: default
        val zone = text("timeZoneId") ?: throw BackupException(BackupProblem.MISSING_FIELD, "timeZoneId")
        val startup = text("startupScreen") ?: throw BackupException(BackupProblem.MISSING_FIELD, "startupScreen")
        if (StartupScreen.entries.none { it.name == startup }) throw BackupException(BackupProblem.STARTUP)
        val keyMode = text("remoteChannelKeyMode") ?: throw BackupException(BackupProblem.MISSING_FIELD, "remoteChannelKeyMode")
        if (keyMode !in KEY_MODES) throw BackupException(BackupProblem.REMOTE)
        val sports = list("followedSports")
        if (sports != null && sports.any { it !in SPORTS }) throw BackupException(BackupProblem.STRUCTURE, "followedSports")
        val active = text("activeProfileId")?.also { if (it.length > ID_MAX) throw BackupException(BackupProblem.LONG_FIELD, "activeProfileId") }
        val guideSource = text("lastGuideSourceId")?.also { if (it.length > GUIDE_SOURCE_MAX) throw BackupException(BackupProblem.LONG_FIELD, "lastGuideSourceId") }
        @Suppress("UNCHECKED_CAST")
        val preferences = BackupPreferences(
            timeZoneId = if (flag("timeZoneFollowsDevice", false)) null else zone,
            profiles = (values["profiles"] as? List<BackupProfile>).orEmpty(),
            activeProfileId = active ?: "default",
            askProfileAtStart = flag("askProfileAtStart", true),
            lastGuideSourceId = guideSource,
            startupScreen = startup,
            remoteChannelKeyMode = keyMode,
            remoteMappings = list("remoteMappings"),
            metadataLanguage = text("metadataLanguage")?.takeIf { it in MetadataConfig.LANGUAGES },
            interfaceScale = text("interfaceScale")?.takeIf { v -> InterfaceScale.entries.any { it.name == v } },
            colorTheme = text("colorTheme")?.takeIf { v -> ColorThemeId.entries.any { it.id == v } },
            followedSports = sports,
            followedCompetitionKeys = list("followedCompetitionKeys"),
            sportsChannelPriority = list("sportsChannelPriority")?.distinct()?.take(PRIORITY_MAX),
            refreshInterval = strict(text("playlistEpgRefreshInterval"), "playlistEpgRefreshInterval", RefreshInterval.entries.map { it.name }),
            bufferProfile = strict(text("playbackBufferProfile"), "playbackBufferProfile", BufferProfile.entries.map { it.name }),
            seekStep = strict(text("playbackSeekStep"), "playbackSeekStep", SkipStep.entries.map { it.name }),
            subtitleSize = strict(text("subtitleTextSize"), "subtitleTextSize", SubtitleSize.entries.map { it.name }),
            subtitleColor = strict(text("subtitleTextColor"), "subtitleTextColor", SubtitleColor.entries.map { it.name }),
            subtitleBackground = strict(text("subtitleBackground"), "subtitleBackground", SubtitleBackground.entries.map { it.name }),
            reconnectPolicy = strict(text("playbackReconnectPolicy"), "playbackReconnectPolicy", ReconnectPolicy.entries.map { it.name }),
            autoPlayNext = flag("autoPlayNextEpisodeEnabled", true),
            pictureInPicture = flag("pictureInPictureEnabled", false),
            autoFrameRate = values["autoFrameRateEnabled"] as? Boolean,
            editorsShowHidden = flag("editorsShowHidden", true),
            showChannelNumbers = flag("showChannelNumbers", true),
            preferredCopy = strict(text("preferredCatalogueCopy"), "preferredCatalogueCopy", PreferredCopy.entries.map { it.name }),
            hiddenLive = list("hiddenLiveCategories").orEmpty(),
            hiddenMovies = list("hiddenMovieCategories").orEmpty(),
            hiddenSeries = list("hiddenSeriesCategories").orEmpty(),
            audioPrimary = text("preferredAudioLanguage"),
            audioSecondary = text("secondaryAudioLanguage"),
            subtitlesPrimary = text("preferredSubtitleLanguage"),
            subtitlesSecondary = text("secondarySubtitleLanguage"),
            customGroupsJson = values["customCatalogueGroups"] as? String,
        )
        val kept = ProfileKept(
            favouriteEventIds = list("favouriteEventIds", required = true)!!,
            favouriteChannelIds = list("favouriteChannelIds", required = true)!!,
            recentChannelIds = list("recentChannelIds", required = true)!!.take(RECENTS),
            lastChannelId = text("lastChannelId"),
            lockedChannelIds = list("lockedChannelIds", required = true)!!,
        )
        return Prefs(preferences, kept)
    }

    /** An enum written by name: absent is the default (null), anything unknown refuses the file (§6.4). */
    private fun strict(value: String?, field: String, known: List<String>): String? {
        if (value == null) return null
        if (value !in known) throw BackupException(BackupProblem.STRUCTURE, field)
        return value
    }

    /** Ids ≤ 64 and non-blank names cut to 24; colours clamped; duplicates dropped; the first six (§6.4). */
    private fun profiles(r: JsonReader): List<BackupProfile> {
        val out = ArrayList<BackupProfile>()
        r.beginArray()
        while (r.hasNext()) {
            var id: String? = null
            var name: String? = null
            var color = 0
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "id" -> id = string(r, "profiles.id").also { if (it.length > ID_MAX) throw BackupException(BackupProblem.LONG_FIELD, "profiles.id") }
                    "name" -> name = string(r, "profiles.name").take(NAME_MAX)
                    "color" -> color = int(r, "profiles.color").coerceIn(0, COLOURS - 1)
                    else -> r.skipValue()
                }
            }
            r.endObject()
            out += BackupProfile(
                id ?: throw BackupException(BackupProblem.MISSING_FIELD, "profiles.id"),
                name ?: throw BackupException(BackupProblem.MISSING_FIELD, "profiles.name"),
                color,
            )
        }
        r.endArray()
        return out.distinctBy { it.id }.take(PROFILES_MAX)
    }

    /** Lenient, as beta 23: unusable groups are dropped, the first 24 kept, stored as the JSON the preferences keep. */
    private fun customGroups(r: JsonReader): String? {
        val value = r.readJsonValue() ?: return null
        val text = Buffer().also { JsonWriter.of(it).jsonValue(value) }.readUtf8()
        val groups = CustomGroupCodec.decode(text).take(GROUPS_MAX)
        return if (groups.isEmpty()) null else CustomGroupCodec.encode(groups)
    }

    private fun profileData(r: JsonReader): Map<String, ProfileKept> {
        val out = LinkedHashMap<String, ProfileKept>()
        r.beginObject()
        while (r.hasNext()) {
            val id = r.nextName()
            val lists = HashMap<String, List<String>>()
            var last: String? = null
            var layout: String? = null
            r.beginObject()
            while (r.hasNext()) {
                when (val name = r.nextName()) {
                    "favouriteEventIds", "favouriteChannelIds", "recentChannelIds", "lockedChannelIds",
                    "allowedLiveGroupKeys", "allowedMovieGroupKeys", "allowedSeriesGroupKeys",
                    -> lists[name] = strings(r, "profileData.$name")
                    "lastChannelId" -> last = optionalString(r, "profileData.lastChannelId")
                    "homeLayout" -> layout = optionalString(r, "profileData.homeLayout")
                    else -> r.skipValue()
                }
            }
            r.endObject()
            out[id] = ProfileKept(
                favouriteEventIds = lists["favouriteEventIds"].orEmpty(),
                favouriteChannelIds = lists["favouriteChannelIds"].orEmpty(),
                recentChannelIds = lists["recentChannelIds"].orEmpty().take(RECENTS),
                lastChannelId = last,
                lockedChannelIds = lists["lockedChannelIds"].orEmpty(),
                allowedLive = lists["allowedLiveGroupKeys"].orEmpty(),
                allowedMovies = lists["allowedMovieGroupKeys"].orEmpty(),
                allowedSeries = lists["allowedSeriesGroupKeys"].orEmpty(),
                homeLayout = layout,
            )
        }
        r.endObject()
        return out
    }

    private fun channel(r: JsonReader): BackupChannel {
        var id: String? = null
        var source: String? = null
        var name: String? = null
        var group: String? = null
        var hidden: Boolean? = null
        var sortOrder: Int? = null
        var epg: String? = null
        var updated: Long? = null
        var logo: String? = null
        var number: Int? = null
        var data: ByteArray? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "channelId" -> id = string(r, "channelId")
                "sourceId" -> source = string(r, "sourceId")
                "customName" -> name = optionalString(r, "customName")
                "customGroupTitle" -> group = optionalString(r, "customGroupTitle")
                "hidden" -> hidden = bool(r, "hidden")
                "sortOrder" -> sortOrder = lenientInt(r)
                "manualXmltvChannelId" -> epg = optionalString(r, "manualXmltvChannelId")
                "updatedAtEpochMillis" -> updated = long(r, "updatedAtEpochMillis")
                "customLogoUrl" -> logo = optionalString(r, "customLogoUrl")
                "channelNumber" -> number = lenientInt(r)
                "customLogoData" -> data = optionalLongString(r)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return BackupChannel(
            id ?: throw BackupException(BackupProblem.MISSING_FIELD, "channelId"),
            source ?: throw BackupException(BackupProblem.MISSING_FIELD, "sourceId"),
            name, group,
            hidden ?: throw BackupException(BackupProblem.MISSING_FIELD, "hidden"),
            sortOrder, epg,
            updated ?: throw BackupException(BackupProblem.MISSING_FIELD, "updatedAtEpochMillis"),
            logo, number, data,
        )
    }

    private fun list(r: JsonReader): BackupList {
        var id: String? = null
        var name: String? = null
        var order: Int? = null
        var updated: Long? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "listId" -> id = string(r, "listId")
                "name" -> name = string(r, "name")
                "sortOrder" -> order = int(r, "sortOrder")
                "updatedAtEpochMillis" -> updated = long(r, "updatedAtEpochMillis")
                else -> r.skipValue()
            }
        }
        r.endObject()
        return BackupList(
            id ?: throw BackupException(BackupProblem.MISSING_FIELD, "listId"),
            name ?: throw BackupException(BackupProblem.MISSING_FIELD, "name"),
            order ?: throw BackupException(BackupProblem.MISSING_FIELD, "sortOrder"),
            updated ?: throw BackupException(BackupProblem.MISSING_FIELD, "updatedAtEpochMillis"),
        )
    }

    private fun member(r: JsonReader): BackupMember {
        var list: String? = null
        var channel: String? = null
        var order: Int? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "listId" -> list = string(r, "listId")
                "channelId" -> channel = string(r, "channelId")
                "sortOrder" -> order = int(r, "sortOrder")
                else -> r.skipValue()
            }
        }
        r.endObject()
        return BackupMember(
            list ?: throw BackupException(BackupProblem.MISSING_FIELD, "listId"),
            channel ?: throw BackupException(BackupProblem.MISSING_FIELD, "channelId"),
            order ?: throw BackupException(BackupProblem.MISSING_FIELD, "sortOrder"),
        )
    }

    private fun organization(r: JsonReader, top: Top) {
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "rules" -> top.rules = array(r) { rule(it) }
                "aliases" -> top.aliases = array(r) { alias(it) }
                else -> r.skipValue()
            }
        }
        r.endObject()
    }

    private fun rule(r: JsonReader): BackupRule {
        val keys = HashMap<String, String>()
        var enabled: Boolean? = null
        var sort: String? = null
        var position: Long? = null
        r.beginObject()
        while (r.hasNext()) {
            when (val name = r.nextName()) {
                "room", "sourceId", "groupKey", "itemKey" -> keys[name] = key(r, name)
                "enabled" -> enabled = if (r.peek() == JsonReader.Token.NULL) r.nextNull() else r.nextBoolean()
                "sortMode" -> sort = optionalString(r, "sortMode")
                "position" -> position = if (r.peek() == JsonReader.Token.NULL) r.nextNull() else r.nextLong()
                else -> r.skipValue()
            }
        }
        r.endObject()
        fun required(name: String) = keys[name] ?: throw BackupException(BackupProblem.MISSING_FIELD, "organization.$name")
        return BackupRule(required("room"), required("sourceId"), required("groupKey"), required("itemKey"), enabled, sort, position)
    }

    private fun alias(r: JsonReader): BackupAlias {
        var alias: String? = null
        var identity: String? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "alias" -> alias = key(r, "organization.alias")
                "identity" -> identity = key(r, "organization.identity")
                else -> r.skipValue()
            }
        }
        r.endObject()
        val a = alias ?: throw BackupException(BackupProblem.MISSING_FIELD, "organization.alias")
        val i = identity ?: throw BackupException(BackupProblem.MISSING_FIELD, "organization.identity")
        if (a.isBlank() || i.isBlank()) throw BackupException(BackupProblem.BLANK_FIELD, "organization.alias")
        return BackupAlias(a, i)
    }

    /** §7.3: limits, unique ids, every preference's source in the backup, every member's list. */
    private fun checkCustomisation(sources: List<SourceConfig>, channels: List<BackupChannel>, lists: List<BackupList>, members: List<BackupMember>) {
        if (channels.size > MAX_PREFERENCES) throw BackupException(BackupProblem.TOO_MANY_PREFERENCES)
        if (lists.size > MAX_LISTS) throw BackupException(BackupProblem.TOO_MANY_LISTS)
        if (members.size > MAX_MEMBERS) throw BackupException(BackupProblem.TOO_MANY_MEMBERS)
        val sourceIds = sources.mapTo(HashSet()) { it.source.id }
        val seen = HashSet<String>()
        for (c in channels) {
            if (!seen.add(c.channelId)) throw BackupException(BackupProblem.DUPLICATE_PREFERENCE)
            if (c.sourceId !in sourceIds) throw BackupException(BackupProblem.MISSING_SOURCE)
        }
        val listIds = HashSet<String>()
        for (l in lists) if (!listIds.add(l.listId)) throw BackupException(BackupProblem.DUPLICATE_LIST)
        val pairs = HashSet<Pair<String, String>>()
        for (m in members) {
            if (m.listId !in listIds) throw BackupException(BackupProblem.MISSING_LIST)
            if (!pairs.add(m.listId to m.channelId)) throw BackupException(BackupProblem.DUPLICATE_MEMBER)
        }
    }

    /** §7.3: rooms, sort names, positions ≥ 0, no duplicate rule, unique aliases. */
    private fun checkOrganisation(rules: List<BackupRule>, aliases: List<BackupAlias>) {
        if (rules.size > MAX_RULES || aliases.size > MAX_ALIASES) throw BackupException(BackupProblem.TOO_LARGE)
        val seen = HashSet<List<String>>()
        for (rule in rules) {
            if (OrgRoom.of(rule.room) == null) throw BackupException(BackupProblem.STRUCTURE, "organization.room")
            if (rule.sortMode != null && OrgSort.of(rule.sortMode) == null) throw BackupException(BackupProblem.STRUCTURE, "organization.sortMode")
            if (rule.position != null && rule.position < 0) throw BackupException(BackupProblem.STRUCTURE, "organization.position")
            if (!seen.add(listOf(rule.room, rule.sourceId, rule.groupKey, rule.itemKey))) throw BackupException(BackupProblem.STRUCTURE, "organization.rules")
        }
        val names = HashSet<String>()
        for (a in aliases) if (!names.add(a.alias)) throw BackupException(BackupProblem.STRUCTURE, "organization.aliases")
    }

    // ---- Field readers (beta 23's rules: §7.3 "Required strings: present, non-blank, ≤ 20,000") ----

    private fun <T> array(r: JsonReader, item: (JsonReader) -> T): List<T> {
        val out = ArrayList<T>()
        r.beginArray()
        while (r.hasNext()) out += item(r)
        r.endArray()
        return out
    }

    private fun string(r: JsonReader, field: String): String {
        if (r.peek() == JsonReader.Token.NULL) throw BackupException(BackupProblem.MISSING_FIELD, field)
        val value = r.nextString()
        if (value.isBlank()) throw BackupException(BackupProblem.BLANK_FIELD, field)
        if (value.length > STRING_MAX) throw BackupException(BackupProblem.LONG_FIELD, field)
        return value
    }

    private fun optionalString(r: JsonReader, field: String): String? {
        if (r.peek() == JsonReader.Token.NULL) return r.nextNull()
        val value = r.nextString()
        if (value.length > STRING_MAX) throw BackupException(BackupProblem.LONG_FIELD, field)
        return value
    }

    /** A logo's picture: up to the payload's own limits, not the 20,000-character string limit. */
    private fun optionalLongString(r: JsonReader): String? = if (r.peek() == JsonReader.Token.NULL) r.nextNull() else r.nextString()

    /** Organisation keys: present, empty allowed, ≤ 2,048. */
    private fun key(r: JsonReader, field: String): String {
        if (r.peek() == JsonReader.Token.NULL) throw BackupException(BackupProblem.MISSING_FIELD, field)
        val value = r.nextString()
        if (value.length > KEY_MAX) throw BackupException(BackupProblem.LONG_FIELD, field)
        return value
    }

    private fun strings(r: JsonReader, field: String): List<String> {
        if (r.peek() == JsonReader.Token.NULL) throw BackupException(BackupProblem.STRUCTURE, field)
        return array(r) { string(it, field) }
    }

    private fun bool(r: JsonReader, field: String): Boolean {
        if (r.peek() == JsonReader.Token.NULL) throw BackupException(BackupProblem.STRUCTURE, field)
        return r.nextBoolean()
    }

    private fun int(r: JsonReader, field: String): Int {
        if (r.peek() == JsonReader.Token.NULL) throw BackupException(BackupProblem.MISSING_FIELD, field)
        return r.nextInt()
    }

    private fun long(r: JsonReader, field: String): Long {
        if (r.peek() == JsonReader.Token.NULL) throw BackupException(BackupProblem.MISSING_FIELD, field)
        return r.nextLong()
    }

    /** `sortOrder`, `channelNumber`: a non-integer becomes null, as in beta 23. */
    private fun lenientInt(r: JsonReader): Int? {
        if (r.peek() == JsonReader.Token.NULL) return r.nextNull()
        return runCatching { r.nextInt() }.getOrElse { r.skipValue(); null }
    }

    private val PIN = Regex("\\d{4,8}")
    private val KEY_MODES = setOf("DPAD_AND_CHANNEL_KEYS", "CHANNEL_KEYS_ONLY")

    /** Beta 23's sport names (spec 60); an unknown one refuses the file, as beta 23 does. */
    val SPORTS: Set<String> = setOf(
        "FOOTBALL", "ICE_HOCKEY", "AUSTRALIAN_FOOTBALL", "BASKETBALL", "BASEBALL", "HANDBALL", "RUGBY", "VOLLEYBALL",
        "AMERICAN_FOOTBALL", "MMA", "FORMULA_1", "NBA",
    )

    private const val STRING_MAX = 20_000
    private const val KEY_MAX = 2_048
    private const val ZONE_MAX = 100
    private const val ID_MAX = 64
    private const val GUIDE_SOURCE_MAX = 128
    private const val NAME_MAX = 24
    private const val COLOURS = 6
    private const val PROFILES_MAX = 6
    private const val RECENTS = 20
    private const val PRIORITY_MAX = 8
    private const val GROUPS_MAX = 24
    private const val MAX_PREFERENCES = 100_000
    private const val MAX_LISTS = 1_000
    private const val MAX_MEMBERS = 500_000
    private const val MAX_RULES = 300_000
    private const val MAX_ALIASES = 500_000
}
