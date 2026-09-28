package com.sohva.tv.core.data.backup

import com.squareup.moshi.JsonWriter
import com.sohva.tv.core.model.source.Beta23SourceCodec
import okio.Buffer
import okio.BufferedSink
import java.util.Base64

/**
 * Writes the payload as beta 23 writes it (spec 71 §7.2): compact UTF-8 JSON, the top-level keys
 * in order, straight to [sink] through a streaming writer (§9 NFR-02). What beta 23's reader
 * refuses is never written: no `null` for a boolean, an array, `profiles` or `color`; integers as
 * integer literals; `timeZoneId`, `startupScreen`, `remoteChannelKeyMode` and every top-level
 * array always present; `formatVersion` 2 (BACKUP-FR-23).
 */
object BackupWriter {
    const val FORMAT_VERSION: Int = 2

    /** [deviceZone] is written as `timeZoneId` when the TV's own zone is followed, as beta 23 does. */
    fun write(payload: BackupPayload, deviceZone: String, exportedAt: Long, sink: BufferedSink) {
        val w = JsonWriter.of(sink)
        w.serializeNulls = true
        w.beginObject()
        w.name("formatVersion").value(FORMAT_VERSION.toLong())
        w.name("exportedAtEpochMillis").value(exportedAt)
        w.name("sources").value(Beta23SourceCodec.encode(payload.sources))
        w.name("parentalPin").value(payload.parentalPin)
        w.name("preferences")
        preferences(w, payload.preferences, payload.profileData[payload.preferences.activeProfileId] ?: ProfileKept(), deviceZone)
        w.name("profileData").beginObject()
        for ((id, kept) in payload.profileData) {
            w.name(id)
            profile(w, kept)
        }
        w.endObject()
        w.name("channelPreferences").beginArray()
        for (c in payload.channelPreferences) channel(w, c)
        w.endArray()
        w.name("channelLists").beginArray()
        for (l in payload.channelLists) {
            w.beginObject().name("listId").value(l.listId).name("name").value(l.name)
                .name("sortOrder").value(l.sortOrder.toLong()).name("updatedAtEpochMillis").value(l.updatedAt).endObject()
        }
        w.endArray()
        w.name("channelListMembers").beginArray()
        for (m in payload.channelListMembers) {
            w.beginObject().name("listId").value(m.listId).name("channelId").value(m.channelId).name("sortOrder").value(m.sortOrder.toLong()).endObject()
        }
        w.endArray()
        w.name("organization").beginObject()
        w.name("rules").beginArray()
        for (r in payload.rules) {
            w.beginObject().name("room").value(r.room).name("sourceId").value(r.sourceId).name("groupKey").value(r.groupKey)
                .name("itemKey").value(r.itemKey).name("enabled").value(r.enabled).name("sortMode").value(r.sortMode)
            w.name("position")
            if (r.position == null) w.nullValue() else w.value(r.position)
            w.endObject()
        }
        w.endArray()
        w.name("aliases").beginArray()
        for (a in payload.aliases) w.beginObject().name("alias").value(a.alias).name("identity").value(a.identity).endObject()
        w.endArray()
        w.endObject()
        w.endObject()
        w.flush()
    }

    private fun preferences(w: JsonWriter, p: BackupPreferences, active: ProfileKept, deviceZone: String) {
        w.beginObject()
        w.name("timeZoneId").value(p.timeZoneId ?: deviceZone)
        w.name("timeZoneFollowsDevice").value(p.timeZoneId == null)
        strings(w, "favouriteEventIds", active.favouriteEventIds)
        strings(w, "favouriteChannelIds", active.favouriteChannelIds)
        strings(w, "recentChannelIds", active.recentChannelIds.take(RECENTS))
        w.name("profiles").beginArray()
        for (profile in p.profiles) {
            w.beginObject().name("id").value(profile.id).name("name").value(profile.name).name("color").value(profile.color.toLong()).endObject()
        }
        w.endArray()
        w.name("activeProfileId").value(p.activeProfileId)
        w.name("askProfileAtStart").value(p.askProfileAtStart)
        w.name("lastChannelId").value(active.lastChannelId)
        w.name("lastGuideSourceId").value(p.lastGuideSourceId)
        w.name("startupScreen").value(p.startupScreen)
        strings(w, "lockedChannelIds", active.lockedChannelIds)
        w.name("remoteChannelKeyMode").value(p.remoteChannelKeyMode)
        p.remoteMappings?.let { strings(w, "remoteMappings", it.sorted()) }
        optional(w, "metadataLanguage", p.metadataLanguage)
        optional(w, "interfaceScale", p.interfaceScale)
        optional(w, "colorTheme", p.colorTheme)
        p.followedSports?.let { strings(w, "followedSports", it) }
        p.followedCompetitionKeys?.let { strings(w, "followedCompetitionKeys", it) }
        p.sportsChannelPriority?.let { strings(w, "sportsChannelPriority", it) }
        optional(w, "playlistEpgRefreshInterval", p.refreshInterval)
        optional(w, "playbackBufferProfile", p.bufferProfile)
        optional(w, "playbackSeekStep", p.seekStep)
        optional(w, "subtitleTextSize", p.subtitleSize)
        optional(w, "subtitleTextColor", p.subtitleColor)
        optional(w, "subtitleBackground", p.subtitleBackground)
        optional(w, "playbackReconnectPolicy", p.reconnectPolicy)
        w.name("autoPlayNextEpisodeEnabled").value(p.autoPlayNext)
        w.name("pictureInPictureEnabled").value(p.pictureInPicture)
        p.autoFrameRate?.let { w.name("autoFrameRateEnabled").value(it) }
        w.name("editorsShowHidden").value(p.editorsShowHidden)
        w.name("showChannelNumbers").value(p.showChannelNumbers)
        optional(w, "preferredCatalogueCopy", p.preferredCopy)
        strings(w, "hiddenLiveCategories", p.hiddenLive)
        strings(w, "hiddenMovieCategories", p.hiddenMovies)
        strings(w, "hiddenSeriesCategories", p.hiddenSeries)
        w.name("preferredAudioLanguage").value(p.audioPrimary)
        w.name("secondaryAudioLanguage").value(p.audioSecondary)
        w.name("preferredSubtitleLanguage").value(p.subtitlesPrimary)
        w.name("secondarySubtitleLanguage").value(p.subtitlesSecondary)
        p.customGroupsJson?.takeIf { it.isNotBlank() }?.let { json ->
            w.name("customCatalogueGroups")
            w.jsonValue(com.squareup.moshi.JsonReader.of(Buffer().writeUtf8(json)).readJsonValue()?.let(::integral))
        }
        w.endObject()
    }

    private fun profile(w: JsonWriter, k: ProfileKept) {
        w.beginObject()
        strings(w, "favouriteEventIds", k.favouriteEventIds)
        strings(w, "favouriteChannelIds", k.favouriteChannelIds)
        strings(w, "recentChannelIds", k.recentChannelIds.take(RECENTS))
        w.name("lastChannelId").value(k.lastChannelId)
        strings(w, "lockedChannelIds", k.lockedChannelIds)
        strings(w, "allowedLiveGroupKeys", k.allowedLive)
        strings(w, "allowedMovieGroupKeys", k.allowedMovies)
        strings(w, "allowedSeriesGroupKeys", k.allowedSeries)
        // Only a changed layout is written; an optional key keeps format 2 (HOME-FR-91).
        k.homeLayout?.let { w.name("homeLayout").value(it) }
        w.endObject()
    }

    private fun channel(w: JsonWriter, c: BackupChannel) {
        w.beginObject()
        w.name("channelId").value(c.channelId)
        w.name("sourceId").value(c.sourceId)
        w.name("customName").value(c.customName)
        w.name("customGroupTitle").value(c.customGroupTitle)
        w.name("hidden").value(c.hidden)
        w.name("sortOrder").value(c.sortOrder?.toLong())
        w.name("manualXmltvChannelId").value(c.manualXmltvChannelId)
        w.name("updatedAtEpochMillis").value(c.updatedAt)
        w.name("customLogoUrl").value(c.customLogoUrl)
        w.name("channelNumber").value(c.channelNumber?.toLong())
        c.customLogoData?.let { w.name("customLogoData").value(Base64.getEncoder().encodeToString(it)) }
        w.endObject()
    }

    /** Beta 23 refuses non-blank-less elements: blanks are left out. */
    private fun strings(w: JsonWriter, name: String, values: List<String>) {
        w.name(name).beginArray()
        values.filter { it.isNotBlank() }.forEach { w.value(it) }
        w.endArray()
    }

    private fun optional(w: JsonWriter, name: String, value: String?) {
        if (value != null) w.name(name).value(value)
    }

    /** Moshi reads every number as a double; years and ids must go out as integer literals. */
    private fun integral(value: Any): Any = when (value) {
        is Double -> if (value == Math.floor(value) && !value.isInfinite()) value.toLong() else value
        is List<*> -> value.map { it?.let(::integral) }
        is Map<*, *> -> value.mapValues { (_, v) -> v?.let(::integral) }
        else -> value
    }

    private const val RECENTS = 20
}
