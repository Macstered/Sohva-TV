package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * What metadata settled for one title (plan/04 §15.7, spec 41 META-FR-60): matched with a record,
 * or a real miss. Keyed by content key, so it survives re-imports; the import and every write copy
 * its effect (replacement title and poster, external id, genre) into the `movie`/`series` row.
 */
@Entity(tableName = "metadata_match", primaryKeys = ["content_key"], indices = [Index(value = ["external_id"])])
data class MetadataMatchEntity(
    @ColumnInfo(name = "content_key") val contentKey: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
    /** `matched` or `no_match`. */
    val status: String,
    val provider: String?,
    @ColumnInfo(name = "external_id") val externalId: String?,
    val genre: String?,
    @ColumnInfo(name = "genres_version") val genresVersion: Int,
    @ColumnInfo(name = "replacement_title") val replacementTitle: String?,
    /** A TMDB image path or an https address. */
    @ColumnInfo(name = "replacement_poster") val replacementPoster: String?,
    @ColumnInfo(name = "replace_provider_poster") val replaceProviderPoster: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** The record's backdrop, a TMDB image path or an https address: Continue watching's landscape card (decision "Library card art"). */
    val backdrop: String? = null,
)

/**
 * Lookup answers per (lookup key, provider) (spec 41 META-FR-31): positive TMDB 30 days, TVmaze
 * 24 h, negative 7 days. The record travels as JSON in [payload]. Capped at 50,000 rows by the
 * daily maintenance (§9.6).
 */
@Entity(
    tableName = "metadata_cache",
    primaryKeys = ["lookup_key", "provider"],
    indices = [Index(value = ["expires_at"]), Index(value = ["external_id"])],
)
data class MetadataCacheEntity(
    @ColumnInfo(name = "lookup_key") val lookupKey: String,
    val provider: String,
    /** `positive` or `negative`. */
    val status: String,
    @ColumnInfo(name = "external_id") val externalId: String?,
    val payload: String?,
    @ColumnInfo(name = "genres_version") val genresVersion: Int,
    @ColumnInfo(name = "details_loaded") val detailsLoaded: Boolean,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
)

/**
 * The viewer's own choice of record for a title (spec 41 META-FR-75, -78): kept apart from the
 * cache so it survives language changes, cache clears and key saves, applied to every copy with
 * the same film identity ([workKey]), and fetched from its own provider.
 */
@Entity(tableName = "metadata_pin", primaryKeys = ["content_key"], indices = [Index(value = ["work_key"])])
data class MetadataPinEntity(
    @ColumnInfo(name = "content_key") val contentKey: String,
    @ColumnInfo(name = "work_key") val workKey: String?,
    val provider: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "pinned_at") val pinnedAt: Long,
)

/**
 * The background enrichment's durable queue (spec 41 §4.11): one row per film and series of the
 * enabled sources, stamp-and-sweep synchronised, taken in priority order (watched titles first,
 * hidden ones last, META-FR-65).
 */
@Entity(
    tableName = "metadata_queue",
    primaryKeys = ["content_key"],
    indices = [Index(value = ["state", "priority", "content_key"]), Index(value = ["stamp"])],
)
data class MetadataQueueEntity(
    @ColumnInfo(name = "content_key") val contentKey: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
    val title: String,
    val year: Int?,
    @ColumnInfo(name = "target_version") val targetVersion: Int,
    /** `pending`, `retry`, `complete` or `no_match`. */
    val state: String,
    val attempts: Int,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long,
    /** 0 watched, 1 visible, 2 hidden. */
    val priority: Int,
    val stamp: Long,
)

/** Titles per genre and Unsorted for the Genres rail (spec 40 §9.3), recomputed in the background. */
@Entity(tableName = "genre_count", primaryKeys = ["room", "genre"])
data class GenreCountEntity(
    val room: String,
    /** A genre's wire value, or `""` for Unsorted. */
    val genre: String,
    val titles: Int,
)

const val MATCHED: String = "matched"
const val NO_MATCH: String = "no_match"
