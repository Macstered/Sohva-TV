package com.streammate.tv.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import java.util.Locale
import com.streammate.tv.core.model.organizationGroupKey

@Entity(tableName = "iptv_source_state", primaryKeys = ["sourceId"])
data class IptvSourceStateEntity(
    val sourceId: String,
    val name: String,
    val type: String,
    val enabled: Boolean,
    val connectionLimit: Int,
    val priority: Int,
    val updatedAtEpochMillis: Long,
    val epgOffsetMinutes: Int = 0,
)

@Entity(
    tableName = "channel_preferences",
    primaryKeys = ["channelId"],
    indices = [Index("sourceId")],
)
data class ChannelPreferenceEntity(
    val channelId: String,
    val sourceId: String,
    val customName: String?,
    val customGroupTitle: String?,
    val hidden: Boolean,
    val sortOrder: Int?,
    val manualXmltvChannelId: String?,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "NULL") val customOrganizationGroupKey: String? = customGroupTitle?.takeIf(String::isNotBlank)?.let { organizationGroupKey(it) },
)

@Entity(tableName = "channel_lists", primaryKeys = ["listId"])
data class CustomChannelListEntity(
    val listId: String,
    val name: String,
    val sortOrder: Int,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "channel_list_members",
    primaryKeys = ["listId", "channelId"],
    indices = [Index("channelId")],
)
data class CustomChannelListMemberEntity(
    val listId: String,
    val channelId: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "iptv_channels",
    primaryKeys = ["sourceId", "snapshotId", "channelId"],
    indices = [
        Index(value = ["sourceId", "snapshotId"]),
        Index(value = ["sourceId", "tvgId"]),
        // getActiveChannel filters on channelId alone, so neither the primary
        // key nor the indices above apply and every channel launch was a scan.
        Index(value = ["channelId"]),
    ],
)
data class IptvChannelEntity(
    val sourceId: String,
    val snapshotId: String,
    val channelId: String,
    val tvgId: String?,
    val name: String,
    val normalizedName: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val encryptedStreamUrl: String,
    val userAgent: String?,
    val referrer: String?,
    val lastSeenEpochMillis: Long,
    val playlistOrder: Int = Int.MAX_VALUE,
    val catchupType: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    val xtreamStreamId: String? = null,
    val catchupTimeZone: String? = null,
    @ColumnInfo(defaultValue = "''") val organizationGroupKey: String = organizationGroupKey(groupTitle),
    @ColumnInfo(defaultValue = "''") val organizationNameKey: String = organizationGroupKey(groupTitle),
)

@Entity(
    tableName = "xmltv_channels",
    primaryKeys = ["sourceId", "snapshotId", "xmltvChannelId"],
    indices = [Index(value = ["sourceId", "snapshotId"])],
)
data class XmlTvChannelEntity(
    val sourceId: String,
    val snapshotId: String,
    val xmltvChannelId: String,
    val displayName: String?,
    val iconUrl: String?,
)

@Entity(
    tableName = "tv_programmes",
    primaryKeys = ["sourceId", "snapshotId", "programmeId"],
    indices = [
        Index(value = ["sourceId", "snapshotId"]),
        Index(value = ["sourceId", "xmltvChannelId", "startEpochMillis", "stopEpochMillis"]),
    ],
)
data class TvProgrammeEntity(
    val sourceId: String,
    val snapshotId: String,
    val programmeId: String,
    val xmltvChannelId: String,
    val startEpochMillis: Long,
    val stopEpochMillis: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val categories: String,
)

@Entity(
    tableName = "vod_movies",
    primaryKeys = ["sourceId", "snapshotId", "movieId"],
    indices = [
        Index(value = ["sourceId", "snapshotId"]),
        Index(value = ["normalizedName"]),
        Index(value = ["categoryKey"]),
        // Resume looks up (sourceId, movieId); snapshotId sits between them in
        // the primary key, so the key prefix cannot serve that query.
        Index(value = ["sourceId", "movieId"]),
    ],
)
data class VodMovieEntity(
    val sourceId: String,
    val snapshotId: String,
    val movieId: String,
    val name: String,
    val normalizedName: String,
    val categoryName: String?,
    val categoryKey: String? = catalogueCategoryKey(categoryName),
    val posterUrl: String?,
    val encryptedStreamUrl: String,
    val year: Int?,
    val rating: String?,
    val plot: String?,
    @ColumnInfo(defaultValue = "''") val organizationGroupKey: String = organizationGroupKey(categoryName),
    @ColumnInfo(defaultValue = "''") val organizationNameKey: String = organizationGroupKey(categoryName),
)

data class AvailableMovieMatchRow(
    @Embedded val movie: VodMovieEntity,
    val replacementTitle: String?,
)

/** One copy of a film, with the playlist it came from and what it matched. */
data class CatalogueCopyRow(
    @Embedded val movie: VodMovieEntity,
    val sourceName: String,
    val externalId: String?,
)

/** The narrow active-catalogue projection used to seed background metadata work. */
data class CatalogueMetadataCandidateRow(
    val contentKey: String,
    val mediaType: String,
    val title: String,
    val year: Int?,
    val providerPosterUrl: String?,
    val genresVersion: Int,
)

/**
 * The movie wall's database projection. It deliberately excludes the encrypted
 * stream URL and plot; those are fetched for one title when its details open.
 */
data class VodMovieCardRow(
    val sourceId: String,
    val movieId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    val replacementPosterUrl: String?,
    val replaceProviderPoster: Boolean?,
    val replacementTitle: String?,
    val externalId: String?,
    val metadataGenresVersion: Int?,
    val genresCsv: String?,
    val organizationGroupKey: String = organizationGroupKey(categoryName),
)

@Entity(
    tableName = "vod_series",
    primaryKeys = ["sourceId", "snapshotId", "seriesId"],
    indices = [
        Index(value = ["sourceId", "snapshotId"]),
        Index(value = ["normalizedName"]),
        Index(value = ["categoryKey"]),
    ],
)
data class VodSeriesEntity(
    val sourceId: String,
    val snapshotId: String,
    val seriesId: String,
    val name: String,
    val normalizedName: String,
    val categoryName: String?,
    val categoryKey: String? = catalogueCategoryKey(categoryName),
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: String?,
    val plot: String?,
    @ColumnInfo(defaultValue = "''") val organizationGroupKey: String = organizationGroupKey(categoryName),
    @ColumnInfo(defaultValue = "''") val organizationNameKey: String = organizationGroupKey(categoryName),
)

/** The series equivalent of [VodMovieCardRow], without detail-only payload. */
data class VodSeriesCardRow(
    val sourceId: String,
    val seriesId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    val replacementPosterUrl: String?,
    val replaceProviderPoster: Boolean?,
    val replacementTitle: String?,
    val externalId: String?,
    val metadataGenresVersion: Int?,
    val genresCsv: String?,
    val organizationGroupKey: String = organizationGroupKey(categoryName),
)

/** Minimal whole-library row used only when custom genre groups exist. */
data class CatalogueGroupFacetRow(
    val contentKey: String,
    val year: Int?,
    val rating: String?,
    val genresCsv: String?,
)

/** Stable key used by indexed category-wall queries. */
fun catalogueCategoryKey(value: String?): String? = value
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.lowercase(Locale.ROOT)

@Entity(
    tableName = "vod_episodes",
    primaryKeys = ["sourceId", "seriesId", "episodeId"],
    indices = [
        Index(value = ["sourceId", "seriesId", "seasonNumber", "episodeNumber"]),
        // Same shape as vod_movies: resume filters on (sourceId, episodeId) and
        // seriesId sits between them in the primary key.
        Index(value = ["sourceId", "episodeId"]),
    ],
)
data class VodEpisodeEntity(
    val sourceId: String,
    val seriesId: String,
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val encryptedStreamUrl: String,
    val plot: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String? = null,
)

@Entity(
    tableName = "playback_progress",
    primaryKeys = ["contentKey"],
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["contentType"]),
        Index(value = ["lastWatchedEpochMillis"]),
        Index(value = ["workKey"]),
    ],
)
data class PlaybackProgressEntity(
    val contentKey: String,
    val sourceId: String,
    val contentType: String,
    val itemId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val completed: Boolean,
    val lastWatchedEpochMillis: Long,
    /**
     * Which film this position is in, where that is known.
     *
     * A position is recorded against a copy, but forty minutes into one copy is
     * forty minutes into the film however many playlists carry it. Rows written
     * before this column existed carry null and stand only for themselves; each
     * gets a work key the next time it is written. Episodes carry null too -
     * matching those across playlists is a harder problem left for later.
     */
    val workKey: String? = null,
)

@Entity(
    tableName = "metadata_cache",
    primaryKeys = ["lookupKey", "provider"],
    indices = [Index("expiresAtEpochMillis"), Index("externalId")],
)
data class MetadataCacheEntity(
    val lookupKey: String,
    val provider: String,
    val status: String,
    val externalId: String?,
    val mediaType: String,
    val matchedTitle: String?,
    val displayTitle: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val runtimeMinutes: Int? = null,
    val rating: String? = null,
    val castJson: String? = null,
    val genresJson: String? = null,
    /** Which version of the genre vocabulary [genresJson] was written under. */
    @ColumnInfo(defaultValue = "0")
    val genresVersion: Int = 0,
    /**
     * Set when a person chose this match rather than the matcher finding it.
     *
     * A pinned row does not expire and is never replaced by a search, so the
     * choice holds until it is cleared. It also displaces whatever was there
     * before, which is how the "no match" recorded against a title is undone.
     */
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val detailsLoaded: Boolean = false,
    val similarMoviesJson: String? = null,
    val attributionName: String,
    val attributionUrl: String,
    val confidence: Double,
    val cachedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

/**
 * One row per genre a title belongs to.
 *
 * A row rather than a column because a title carries several genres and appears
 * under every one of them, so the rail's contents and its counts are a query
 * over this table instead of a scan that unpacks a list on every row of the
 * library.
 *
 * This outlives [MetadataCacheEntity], which is swept once it expires. What a
 * film is does not stop being true after thirty days, and rediscovering it would
 * mean asking the provider about the whole library again.
 */
@Entity(
    tableName = "catalogue_genres",
    primaryKeys = ["contentKey", "genre"],
    indices = [Index("genre")],
)
data class CatalogueGenreEntity(
    val contentKey: String,
    val genre: String,
)

/**
 * Durable metadata work, kept separate from the catalogue rows themselves.
 *
 * The worker reads this table a bounded page at a time. In particular it never
 * reloads every movie, series and override merely to discover the next sixty
 * titles, which is what made a large library quadratic in practice.
 */
@Entity(
    tableName = "catalogue_metadata_work",
    primaryKeys = ["contentKey"],
    indices = [Index(value = ["state", "nextAttemptAtEpochMillis", "contentKey"])],
)
data class CatalogueMetadataWorkEntity(
    val contentKey: String,
    val mediaType: String,
    val title: String,
    val year: Int?,
    val providerPosterUrl: String?,
    val targetGenresVersion: Int,
    val state: String,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_RETRY = "retry"
        const val STATE_COMPLETE = "complete"
        const val STATE_NO_MATCH = "no_match"
    }
}

@Entity(tableName = "catalogue_metadata_overrides", primaryKeys = ["contentKey"])
data class CatalogueMetadataOverrideEntity(
    val contentKey: String,
    val providerPosterUrl: String?,
    val replacementPosterUrl: String?,
    val replaceProviderPoster: Boolean,
    val replacementTitle: String,
    /**
     * The record this title turned out to be, where anything matched it.
     *
     * Two copies of one film from two playlists resolve to the same record even
     * when their names disagree about the year, so this is what lets them be
     * recognised as the same film.
     */
    val externalId: String? = null,
    /** Which version of the genre vocabulary this title was sorted under. */
    @ColumnInfo(defaultValue = "0")
    val genresVersion: Int = 0,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "sports_api_cache",
    primaryKeys = ["cacheKey"],
    indices = [Index("staleUntilEpochMillis")],
)
data class SportsApiCacheEntity(
    val cacheKey: String,
    val sport: String,
    val kind: String,
    val payload: String,
    val source: String,
    val quotaRemaining: Int?,
    val fetchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val staleUntilEpochMillis: Long,
)

@Entity(tableName = "import_state", primaryKeys = ["sourceId", "kind"])
data class ImportStateEntity(
    val sourceId: String,
    val kind: String,
    val activeSnapshotId: String,
    val updatedAtEpochMillis: Long,
    val itemCount: Int,
)

/**
 * Stable, narrow projection used to invalidate catalogue browse state when an
 * enabled provider atomically activates a new import snapshot.
 */
data class CatalogueGenerationRow(
    val sourceId: String,
    val activeSnapshotId: String,
    val updatedAtEpochMillis: Long,
    val itemCount: Int,
)

@Entity(tableName = "source_refresh_state", primaryKeys = ["sourceId", "kind"])
data class SourceRefreshStateEntity(
    val sourceId: String,
    val kind: String,
    val status: String,
    val lastAttemptAtEpochMillis: Long,
    val lastSuccessAtEpochMillis: Long?,
    val lastFailureAtEpochMillis: Long?,
    val lastError: String?,
    val itemCount: Int,
    val consecutiveFailures: Int,
)

data class GuideChannelRow(
    val sourceId: String,
    val sourceName: String,
    val sourcePriority: Int,
    val channelId: String,
    val name: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val playlistOrder: Int,
    val currentProgrammeTitle: String?,
    val currentProgrammeSubtitle: String?,
    val programmeStartEpochMillis: Long?,
    val programmeStopEpochMillis: Long?,
    val organizationGroupKey: String = organizationGroupKey(groupTitle),
    val legacyPosition: Long? = null,
)

data class GuideTimelineRow(
    val sourceId: String,
    val sourceName: String,
    val sourcePriority: Int,
    val channelId: String,
    val channelName: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val playlistOrder: Int,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val programmeId: String?,
    val programmeTitle: String?,
    val programmeSubtitle: String?,
    val programmeDescription: String?,
    val programmeCategories: String?,
    val programmeStartEpochMillis: Long?,
    val programmeStopEpochMillis: Long?,
    val organizationGroupKey: String = organizationGroupKey(groupTitle),
    val legacyPosition: Long? = null,
)

data class GuideSearchResultRow(
    val resultType: String,
    val sourceId: String,
    val channelId: String,
    val title: String,
    val subtitle: String?,
    val logoUrl: String?,
    val startEpochMillis: Long?,
    val stopEpochMillis: Long?,
)

data class VodEpisodeSearchRow(
    val sourceId: String,
    val seriesId: String,
    val seriesName: String,
    val seriesPosterUrl: String?,
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeName: String,
)

data class CatalogueCategoryRow(
    val categoryName: String,
    val itemCount: Int,
)

data class ContinueWatchingRow(
    val contentKey: String,
    val contentType: String,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val positionMillis: Long,
    val durationMillis: Long,
    val completed: Boolean,
    val lastWatchedEpochMillis: Long,
)

data class EditableChannelRow(
    val sourceId: String,
    val sourceName: String,
    val channelId: String,
    val originalName: String,
    val originalGroupTitle: String?,
    val logoUrl: String?,
    val tvgId: String?,
    val playlistOrder: Int,
    val customName: String?,
    val customGroupTitle: String?,
    val hidden: Boolean,
    val sortOrder: Int?,
    val manualXmltvChannelId: String?,
    val organizationGroupKey: String = organizationGroupKey(originalGroupTitle),
    val sourceEnabled: Boolean = true,
)

/** One group of one source with its channel count: what the guide's rail is drawn from. */
data class GuideRailRow(
    val sourceId: String,
    val sourceName: String,
    val sourcePriority: Int,
    val groupTitle: String?,
    val organizationGroupKey: String,
    val channelCount: Int,
)

/** See [GuideDao.stagedEpgMatch]. */
data class StagedEpgMatchRow(
    val matchedProgrammes: Int,
    val mappableChannels: Int,
)

data class XmlTvChannelOptionRow(
    val sourceId: String,
    val xmltvChannelId: String,
    val displayName: String?,
)

data class ProgrammeCandidateRow(
    val sourceId: String,
    val channelId: String,
    val channelName: String,
    val programmeId: String,
    val programmeTitle: String,
    val programmeSubtitle: String?,
    val programmeDescription: String?,
    val programmeStartEpochMillis: Long,
    val programmeStopEpochMillis: Long,
)

data class ChannelNameCandidateRow(
    val sourceId: String,
    val channelId: String,
    val channelName: String,
)

@Entity(
    tableName = "team_aliases",
    primaryKeys = ["sport", "normalizedCanonicalName", "normalizedAlias"],
    indices = [Index("sport")],
)
data class TeamAliasEntity(
    val sport: String,
    val normalizedCanonicalName: String,
    val normalizedAlias: String,
)

@Entity(
    tableName = "event_channel_decisions",
    primaryKeys = ["eventId", "channelId"],
    indices = [Index("eventId")],
)
data class EventChannelDecisionEntity(
    val eventId: String,
    val channelId: String,
    val decision: String,
    val updatedAtEpochMillis: Long,
)
