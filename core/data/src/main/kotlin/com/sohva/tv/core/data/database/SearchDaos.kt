package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/** A film or series found by Search (spec 03 SEARCH-FR-12, -13). */
data class TitleHit(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val name: String,
    val year: Int?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "group_name") val groupName: String?,
)

/** An episode found by its own name or its series' name (SEARCH-FR-14). */
data class EpisodeHit(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val season: Int,
    val number: Int,
    val name: String?,
    @ColumnInfo(name = "series_name") val seriesName: String,
    @ColumnInfo(name = "series_sort") val seriesSort: String,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
)

/** A channel found by its shown name (SEARCH-FR-11). */
data class ChannelHit(
    val id: Long,
    val key: String,
    val name: String,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    @ColumnInfo(name = "group_name") val groupName: String?,
    @ColumnInfo(name = "source_name") val sourceName: String,
)

/** A programme of a visible channel in its source's active guide (SEARCH-FR-11). */
data class ProgrammeHit(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "offset_minutes") val offsetMinutes: Int,
    @ColumnInfo(name = "channel_id") val channelId: Long,
    @ColumnInfo(name = "channel_key") val channelKey: String,
    @ColumnInfo(name = "channel_name") val channelName: String,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
)

/**
 * Search's queries (spec 03 §9): each starts from its full-text index (`MATCH`), reaches rows by
 * id, and applies visibility and enabled sources before the limit, so the work is bounded by the
 * matches, never by the catalogue. The joins are pinned with `CROSS JOIN` in that order.
 */
object SearchSql {
    const val FILMS = "SELECT m.id, m.key, m.source_id, m.name, m.year, m.poster_url, g.name AS group_name " +
        "FROM movie_search f CROSS JOIN movie m ON m.id = f.rowid LEFT JOIN content_group g ON g.id = m.group_id " +
        "WHERE movie_search MATCH :match AND m.visible = 1 AND m.source_id IN (:sources) ORDER BY m.sort_name, m.id LIMIT :limit"

    const val SERIES = "SELECT s.id, s.key, s.source_id, s.name, s.year, s.poster_url, g.name AS group_name " +
        "FROM series_search f CROSS JOIN series s ON s.id = f.rowid LEFT JOIN content_group g ON g.id = s.group_id " +
        "WHERE series_search MATCH :match AND s.visible = 1 AND s.source_id IN (:sources) ORDER BY s.sort_name, s.id LIMIT :limit"

    // By the episode's own name, and every episode of a series whose name matches.
    const val EPISODES = "SELECT e.id, e.key, e.source_id, e.season, e.number, e.name, s.name AS series_name, s.sort_name AS series_sort, s.poster_url " +
        "FROM episode_search f CROSS JOIN episode e ON e.id = f.rowid CROSS JOIN series s ON s.id = e.series_id " +
        "WHERE episode_search MATCH :match AND s.visible = 1 AND s.source_id IN (:sources) " +
        "UNION " +
        "SELECT e.id, e.key, e.source_id, e.season, e.number, e.name, s.name AS series_name, s.sort_name AS series_sort, s.poster_url " +
        "FROM series_search f CROSS JOIN series s ON s.id = f.rowid CROSS JOIN episode e ON e.series_id = s.id " +
        "WHERE series_search MATCH :match AND s.visible = 1 AND s.source_id IN (:sources) " +
        // A compound select orders by position: series sort name, season, number, id.
        "ORDER BY 8, 4, 5, 1 LIMIT :limit"

    const val CHANNELS = "SELECT c.id, c.key, c.name, c.logo_url, g.name AS group_name, src.name AS source_name " +
        "FROM channel_search f CROSS JOIN channel c ON c.id = f.rowid CROSS JOIN source src ON src.id = c.source_id " +
        "LEFT JOIN content_group g ON g.id = c.group_id " +
        "WHERE channel_search MATCH :match AND c.visible = 1 AND src.enabled = 1 ORDER BY c.sort_name, c.id LIMIT :limit"

    // The programme's channel through (source_id, epg_id), in the source's active guide snapshot.
    const val PROGRAMMES = "SELECT p.id, p.title, p.start_at, src.epg_offset_minutes AS offset_minutes, c.id AS channel_id, c.key AS channel_key, " +
        "c.name AS channel_name, c.logo_url " +
        "FROM programme_search f CROSS JOIN programme p ON p.id = f.rowid " +
        "CROSS JOIN source_status st ON st.source_id = p.source_id AND st.kind = 'epg' AND st.epg_snapshot = p.snapshot " +
        "CROSS JOIN source src ON src.id = p.source_id " +
        // Pinned: with `visible` in the filter the planner picks (source_id, visible), every channel of the source per match.
        "CROSS JOIN channel c INDEXED BY index_channel_source_id_epg_id ON c.source_id = p.source_id AND c.epg_id = p.epg_id " +
        "WHERE programme_search MATCH :match AND src.enabled = 1 AND c.visible = 1 ORDER BY p.title, p.start_at, p.id, c.id LIMIT :limit"

    const val ENABLED_SOURCES = "SELECT id FROM source WHERE enabled = 1"
}

@Dao
interface SearchDao {
    @Query(SearchSql.ENABLED_SOURCES)
    suspend fun enabledSources(): List<String>

    @Query(SearchSql.FILMS)
    suspend fun films(match: String, sources: List<String>, limit: Int): List<TitleHit>

    @Query(SearchSql.SERIES)
    suspend fun series(match: String, sources: List<String>, limit: Int): List<TitleHit>

    @Query(SearchSql.EPISODES)
    suspend fun episodes(match: String, sources: List<String>, limit: Int): List<EpisodeHit>

    @Query(SearchSql.CHANNELS)
    suspend fun channels(match: String, limit: Int): List<ChannelHit>

    @Query(SearchSql.PROGRAMMES)
    suspend fun programmes(match: String, limit: Int): List<ProgrammeHit>
}
