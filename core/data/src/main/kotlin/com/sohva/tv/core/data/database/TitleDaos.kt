package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/** A film as its page opens it (spec 40 VOD-FR-59): everything but the stream address. */
data class FilmRecord(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val name: String,
    @ColumnInfo(name = "group_name") val groupName: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    val plot: String?,
    @ColumnInfo(name = "quality_mask") val qualityMask: Int,
    @ColumnInfo(name = "work_key") val workKey: String?,
    /** The metadata title, once the background enrichment matched the film (spec 41 META-FR-60). */
    @ColumnInfo(name = "replacement_title") val replacementTitle: String? = null,
)

/** A copy on the Versions row (VOD-FR-68): its claims are read off [name] off the main thread. */
data class VersionRow(
    val key: String,
    val name: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
)

/** A film that may stand for one of TMDB's similar titles (VOD-FR-71). */
data class SimilarRow(
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val year: Int?,
    @ColumnInfo(name = "similar_key") val similarKey: String?,
    @ColumnInfo(name = "replacement_key") val replacementKey: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
)

/** A series as its page opens it (VOD-FR-73), with the provider's backdrop. */
data class SeriesRecord(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    val name: String,
    @ColumnInfo(name = "group_name") val groupName: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "backdrop_url") val backdropUrl: String?,
    val year: Int?,
    val rating: String?,
    val plot: String?,
    @ColumnInfo(name = "quality_mask") val qualityMask: Int,
    /** The metadata title, once the background enrichment matched the series (spec 41 META-FR-60). */
    @ColumnInfo(name = "replacement_title") val replacementTitle: String? = null,
)

/** An episode card's facts (VOD-FR-84); [name] null reads as the translated "Episode n". */
data class EpisodeRecord(
    val key: String,
    val season: Int,
    val number: Int,
    val name: String?,
    @ColumnInfo(name = "duration_s") val durationSeconds: Int?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    val plot: String?,
)

/** What the player needs to open a film or an episode (VOD-FR-102); the address stays sealed. */
data class PlayableTitle(
    val key: String,
    val title: String,
    @ColumnInfo(name = "series_name") val seriesName: String?,
    val season: Int?,
    val number: Int?,
    @ColumnInfo(name = "stream_url_enc") val streamUrlEnc: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "connection_limit") val connectionLimit: Int,
)

object TitleSql {
    const val FILM = "SELECT m.id, m.key, m.source_id, m.name, g.name AS group_name, m.poster_url, m.year, m.rating, m.plot, " +
        "m.quality_mask, m.work_key, m.replacement_title FROM movie m LEFT JOIN content_group g ON g.id = m.group_id WHERE m.key = :key"

    /**
     * The copies of one film identity (VOD-FR-68), by source priority, source name and title. The
     * work key index bounds the read to the film's few copies, so the small sort is fine here.
     */
    const val VERSIONS = "SELECT m.key, m.name, src.name AS source_name FROM movie m CROSS JOIN source src ON src.id = m.source_id " +
        "WHERE m.work_key = :workKey AND m.visible = 1 AND src.enabled = 1 ORDER BY src.priority, src.name, m.sort_name LIMIT 50"

    /** Films whose provider or replacement comparison key is one of [keys] (VOD-FR-71): two index lookups. */
    const val SIMILAR = "SELECT m.key, m.source_id, m.year, m.similar_key, m.replacement_key, m.poster_url FROM movie m " +
        "CROSS JOIN source src ON src.id = m.source_id WHERE m.similar_key IN (:keys) AND m.visible = 1 AND src.enabled = 1 " +
        "UNION ALL SELECT m.key, m.source_id, m.year, m.similar_key, m.replacement_key, m.poster_url FROM movie m " +
        "CROSS JOIN source src ON src.id = m.source_id WHERE m.replacement_key IN (:keys) AND m.visible = 1 AND src.enabled = 1 LIMIT 400"
    const val VISIBLE = "SELECT key FROM movie WHERE key IN (:keys) AND visible = 1"

    /** A missing poster repaired from the details record (spec 41 META-FR-71); a no-op when one exists. */
    const val REPAIR_FILM_POSTER = "UPDATE movie SET replacement_poster = :poster, replace_poster = 1 WHERE key = :key " +
        "AND (poster_url IS NULL OR poster_url = '') AND (replacement_poster IS NULL OR replacement_poster = '')"
    const val REPAIR_SERIES_POSTER = "UPDATE series SET replacement_poster = :poster, replace_poster = 1 WHERE key = :key " +
        "AND (poster_url IS NULL OR poster_url = '') AND (replacement_poster IS NULL OR replacement_poster = '')"
    const val SERIES = "SELECT s.id, s.key, s.source_id, s.provider_id, s.name, g.name AS group_name, s.poster_url, s.backdrop_url, " +
        "s.year, s.rating, s.plot, s.quality_mask, s.replacement_title FROM series s LEFT JOIN content_group g ON g.id = s.group_id WHERE s.key = :key"
    const val EPISODES = "SELECT e.key, e.season, e.number, e.name, e.duration_s, e.thumbnail_url, e.plot " +
        "FROM series s CROSS JOIN episode e ON e.series_id = s.id WHERE s.key = :seriesKey ORDER BY e.season, e.number"
    const val PLAYABLE_FILM = "SELECT m.key, m.name AS title, NULL AS series_name, NULL AS season, NULL AS number, m.stream_url_enc, " +
        "m.source_id, src.name AS source_name, src.connection_limit FROM movie m CROSS JOIN source src ON src.id = m.source_id " +
        "WHERE m.key = :key AND src.enabled = 1"
    const val PLAYABLE_EPISODE = "SELECT e.key, COALESCE(e.name, '') AS title, s.name AS series_name, e.season, e.number, e.stream_url_enc, " +
        "e.source_id, src.name AS source_name, src.connection_limit FROM episode e CROSS JOIN series s ON s.id = e.series_id " +
        "CROSS JOIN source src ON src.id = e.source_id WHERE e.key = :key AND src.enabled = 1"

    /** The next (season, episode) of the same series, across seasons (VOD-FR-101). */
    const val NEXT_EPISODE = "SELECT n.key FROM episode e CROSS JOIN episode n ON n.series_id = e.series_id WHERE e.key = :key " +
        "AND (n.season > e.season OR (n.season = e.season AND n.number > e.number)) ORDER BY n.season, n.number LIMIT 1"
    const val SERIES_OF_EPISODE = "SELECT s.key FROM episode e CROSS JOIN series s ON s.id = e.series_id WHERE e.key = :key"
    const val DELETE_EPISODES = "DELETE FROM episode WHERE series_id = :seriesId"
}

@Dao
interface TitleDao {
    @Query(TitleSql.FILM)
    fun film(key: String): FilmRecord?

    @Query(TitleSql.SERIES)
    fun series(key: String): SeriesRecord?

    @Query(TitleSql.EPISODES)
    fun episodes(seriesKey: String): List<EpisodeRecord>

    @Query(TitleSql.PLAYABLE_FILM)
    fun playableFilm(key: String): PlayableTitle?

    @Query(TitleSql.PLAYABLE_EPISODE)
    fun playableEpisode(key: String): PlayableTitle?

    @Query(TitleSql.NEXT_EPISODE)
    fun nextEpisode(key: String): String?

    @Query(TitleSql.SERIES_OF_EPISODE)
    fun seriesOfEpisode(key: String): String?

    @Query(TitleSql.DELETE_EPISODES)
    fun deleteEpisodes(seriesId: Long)

    @Query(TitleSql.VERSIONS)
    fun versions(workKey: String): List<VersionRow>

    @Query(TitleSql.SIMILAR)
    fun similar(keys: List<String>): List<SimilarRow>

    @Query(TitleSql.VISIBLE)
    fun visible(keys: List<String>): List<String>

    @Query(TitleSql.REPAIR_FILM_POSTER)
    fun repairFilmPoster(key: String, poster: String): Int

    @Query(TitleSql.REPAIR_SERIES_POSTER)
    fun repairSeriesPoster(key: String, poster: String): Int
}
