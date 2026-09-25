package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * One card of a movie or series wall (spec 40 VOD-FR-21): only what the card draws and what the
 * pager keys on. No stream address, plot or cast crosses into a wall.
 */
data class WallRow(
    val id: Long,
    val key: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val name: String,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    @ColumnInfo(name = "quality_mask") val qualityMask: Int,
    @ColumnInfo(name = "work_key") val workKey: String?,
    /** The metadata override (VOD-FR-21): the title shown instead of [name] when present (VOD-FR-22). */
    @ColumnInfo(name = "replacement_title") val replacementTitle: String? = null,
    /** A TMDB image path or an https address. */
    @ColumnInfo(name = "replacement_poster") val replacementPoster: String? = null,
    @ColumnInfo(name = "replace_poster") val replacePoster: Boolean = false,
    /** Sort keys of a group's other orders (spec 42 ORG-07), for the pager's cursor. */
    @ColumnInfo(name = "rating_x10") val ratingX10: Int? = null,
    @ColumnInfo(name = "item_position") val itemPosition: Int? = null,
) {
    /** VOD-FR-22. */
    val displayTitle: String get() = replacementTitle?.takeIf { it.isNotBlank() } ?: name
}

/**
 * A copy of a page's film, for the folded card (VOD-FR-27): what it may fill in, and where it
 * stands, so a card counts only the copies of its own wall.
 */
data class CopyFacts(
    val key: String,
    @ColumnInfo(name = "work_key") val workKey: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    val genre: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    @ColumnInfo(name = "quality_mask") val qualityMask: Int,
    @ColumnInfo(name = "replacement_title") val replacementTitle: String?,
    @ColumnInfo(name = "replacement_poster") val replacementPoster: String?,
    @ColumnInfo(name = "replace_poster") val replacePoster: Boolean,
)

/** A provider group of one source in a wall room: the rail merges them by name across sources. */
data class WallGroupRow(
    val id: Long,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val name: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    /** The resolved manual place (spec 42 ORG-FR-19); `Int.MAX_VALUE` when none. */
    val position: Int = Int.MAX_VALUE,
    /** The group's resolved content order (ORG-FR-20). */
    @ColumnInfo(name = "sort_mode") val sortMode: String? = null,
)

/** A History row: the progress row's time and key for the keyset, and the title it points at. */
data class HistoryRow(
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "content_key") val contentKey: String,
    @Embedded val title: WallRow,
)

/**
 * The walls' pages (spec 40 §9.3, plan/04 §15.11). Every page is a keyset range over an index that
 * ends in `sort_name` and the row id, forwards (`after`) or backwards (`before`), so no page sorts
 * and no page offsets. `:search` is a [com.sohva.tv.core.model.text.SortNames] form or null; it is
 * a filter on rows read in index order, so a common word answers at once and a rare one walks the
 * destination once, cancellably.
 */
object WallSql {
    const val COLUMNS: String = "id, key, source_id, name, sort_name, poster_url, year, rating, quality_mask, work_key, " +
        "replacement_title, replacement_poster, replace_poster, rating_x10, item_position"

    // The provider title or the replacement title (VOD-FR-42), both in their sort form.
    const val FILTERS: String = "AND source_id IN (:sources) AND (:search IS NULL OR instr(sort_name, :search) > 0 " +
        "OR instr(COALESCE(replacement_sort, ''), :search) > 0)"
    private const val AFTER = "AND (sort_name > :name OR (sort_name = :name AND id > :id)) $FILTERS ORDER BY sort_name, id LIMIT :limit"
    private const val BEFORE = "AND (sort_name < :name OR (sort_name = :name AND id < :id)) $FILTERS ORDER BY sort_name DESC, id DESC LIMIT :limit"

    private const val FILM_GROUP = "SELECT $COLUMNS FROM movie WHERE group_id = :groupId AND visible = 1 AND group_primary = 1"
    private const val FILM_ALL = "SELECT $COLUMNS FROM movie WHERE visible = 1 AND primary_copy = 1"
    private const val FILM_GENRE = "SELECT $COLUMNS FROM movie WHERE genre = :genre AND visible = 1 AND primary_copy = 1"
    // Pinned: the planner prefers the all-titles index and filters `genre IS NULL` row by row, which
    // walks the whole library once most titles have a genre.
    private const val FILM_UNSORTED = "SELECT $COLUMNS FROM movie INDEXED BY index_movie_genre_visible_primary_copy_sort_name " +
        "WHERE genre IS NULL AND visible = 1 AND primary_copy = 1"
    private const val SERIES_GROUP = "SELECT $COLUMNS FROM series WHERE group_id = :groupId AND visible = 1 AND primary_copy = 1"
    private const val SERIES_ALL = "SELECT $COLUMNS FROM series WHERE visible = 1 AND primary_copy = 1"
    private const val SERIES_GENRE = "SELECT $COLUMNS FROM series WHERE genre = :genre AND visible = 1 AND primary_copy = 1"
    private const val SERIES_UNSORTED = "SELECT $COLUMNS FROM series INDEXED BY index_series_genre_visible_primary_copy_sort_name " +
        "WHERE genre IS NULL AND visible = 1 AND primary_copy = 1"

    const val FILM_GROUP_AFTER = "$FILM_GROUP $AFTER"
    const val FILM_GROUP_BEFORE = "$FILM_GROUP $BEFORE"
    const val FILM_ALL_AFTER = "$FILM_ALL $AFTER"
    const val FILM_ALL_BEFORE = "$FILM_ALL $BEFORE"
    const val FILM_GENRE_AFTER = "$FILM_GENRE $AFTER"
    const val FILM_GENRE_BEFORE = "$FILM_GENRE $BEFORE"
    const val FILM_UNSORTED_AFTER = "$FILM_UNSORTED $AFTER"
    const val FILM_UNSORTED_BEFORE = "$FILM_UNSORTED $BEFORE"
    const val SERIES_GROUP_AFTER = "$SERIES_GROUP $AFTER"
    const val SERIES_GROUP_BEFORE = "$SERIES_GROUP $BEFORE"
    const val SERIES_ALL_AFTER = "$SERIES_ALL $AFTER"
    const val SERIES_ALL_BEFORE = "$SERIES_ALL $BEFORE"
    const val SERIES_GENRE_AFTER = "$SERIES_GENRE $AFTER"
    const val SERIES_GENRE_BEFORE = "$SERIES_GENRE $BEFORE"
    const val SERIES_UNSORTED_AFTER = "$SERIES_UNSORTED $AFTER"
    const val SERIES_UNSORTED_BEFORE = "$SERIES_UNSORTED $BEFORE"

    /** The rooms' groups of the enabled sources: a few hundred rows per source, merged in memory. */
    const val GROUPS = "SELECT id, source_id, name, item_count, position, sort_mode FROM content_group " +
        "WHERE room = :room AND source_id IN (:sources) AND shown = 1 AND item_count > 0"

    /**
     * History, newest first (spec 40 VOD-FR-93): the profile's progress rows in index order, each
     * kept only when it is the newest of its film (copies fold by work key) and its title is on a
     * wall. The progress table leads the join (`CROSS JOIN`) so titles are reached by their key.
     * "Older" pages continue down the list; "newer" pages come back up it.
     */
    private const val FILM_HISTORY = "SELECT w.updated_at, w.content_key, m.id, m.key, m.source_id, m.name, m.sort_name, " +
        "m.poster_url, m.year, m.rating, m.quality_mask, m.work_key, m.replacement_title, m.replacement_poster, m.replace_poster, " +
        "m.rating_x10, m.item_position " +
        "FROM watch_progress w CROSS JOIN movie m ON m.key = w.content_key WHERE w.profile_id = :profile AND w.content_type = 'MOVIE' " +
        "AND m.visible = 1 AND m.source_id IN (:sources) " +
        "AND (:search IS NULL OR instr(m.sort_name, :search) > 0 OR instr(COALESCE(m.replacement_sort, ''), :search) > 0) " +
        "AND (w.work_key IS NULL OR NOT EXISTS (SELECT 1 FROM watch_progress n WHERE n.profile_id = w.profile_id " +
        "AND n.work_key = w.work_key AND (n.updated_at > w.updated_at OR (n.updated_at = w.updated_at AND n.content_key > w.content_key))))"
    private const val SERIES_HISTORY = "SELECT w.updated_at, w.content_key, s.id, s.key, s.source_id, s.name, s.sort_name, " +
        "s.poster_url, s.year, s.rating, s.quality_mask, s.work_key, s.replacement_title, s.replacement_poster, s.replace_poster, " +
        "s.rating_x10, s.item_position " +
        "FROM watch_progress w CROSS JOIN series s ON s.key = w.series_key WHERE w.profile_id = :profile AND w.content_type = 'EPISODE' " +
        "AND s.visible = 1 AND s.source_id IN (:sources) " +
        "AND (:search IS NULL OR instr(s.sort_name, :search) > 0 OR instr(COALESCE(s.replacement_sort, ''), :search) > 0) " +
        "AND NOT EXISTS (SELECT 1 FROM watch_progress n WHERE n.profile_id = w.profile_id AND n.series_key = w.series_key " +
        "AND (n.updated_at > w.updated_at OR (n.updated_at = w.updated_at AND n.content_key > w.content_key)))"
    private const val OLDER = "AND (w.updated_at < :at OR (w.updated_at = :at AND w.content_key < :contentKey)) " +
        "ORDER BY w.updated_at DESC, w.content_key DESC LIMIT :limit"
    private const val NEWER = "AND (w.updated_at > :at OR (w.updated_at = :at AND w.content_key > :contentKey)) " +
        "ORDER BY w.updated_at, w.content_key LIMIT :limit"

    const val FILM_HISTORY_OLDER = "$FILM_HISTORY $OLDER"
    const val FILM_HISTORY_NEWER = "$FILM_HISTORY $NEWER"
    const val SERIES_HISTORY_OLDER = "$SERIES_HISTORY $OLDER"
    const val SERIES_HISTORY_NEWER = "$SERIES_HISTORY $NEWER"

    /** Sources whose films and series the walls show. */
    const val ENABLED_SOURCES = "SELECT id FROM source WHERE enabled = 1 AND import_scope <> 'LIVE_TV'"

    /** Copies of the page's films (VOD-FR-27 fill-ins and "×N"): ≤ 120 work keys per call. */
    const val FILM_COPIES = "SELECT key, work_key, group_id, genre, poster_url, year, rating, quality_mask, replacement_title, " +
        "replacement_poster, replace_poster FROM movie WHERE work_key IN (:workKeys) AND visible = 1 AND source_id IN (:sources)"
}

@Dao
interface WallDao {
    @Query(WallSql.FILM_GROUP_AFTER)
    fun filmGroupAfter(groupId: Long, name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.FILM_GROUP_BEFORE)
    fun filmGroupBefore(groupId: Long, name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.FILM_ALL_AFTER)
    fun filmAllAfter(name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.FILM_ALL_BEFORE)
    fun filmAllBefore(name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.FILM_GENRE_AFTER)
    fun filmGenreAfter(genre: String, name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.FILM_GENRE_BEFORE)
    fun filmGenreBefore(genre: String, name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.FILM_UNSORTED_AFTER)
    fun filmUnsortedAfter(name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.FILM_UNSORTED_BEFORE)
    fun filmUnsortedBefore(name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.SERIES_GROUP_AFTER)
    fun seriesGroupAfter(groupId: Long, name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.SERIES_GROUP_BEFORE)
    fun seriesGroupBefore(groupId: Long, name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.SERIES_ALL_AFTER)
    fun seriesAllAfter(name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.SERIES_ALL_BEFORE)
    fun seriesAllBefore(name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.SERIES_GENRE_AFTER)
    fun seriesGenreAfter(genre: String, name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.SERIES_GENRE_BEFORE)
    fun seriesGenreBefore(genre: String, name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.SERIES_UNSORTED_AFTER)
    fun seriesUnsortedAfter(name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.SERIES_UNSORTED_BEFORE)
    fun seriesUnsortedBefore(name: String, id: Long, sources: List<String>, search: String?, limit: Int): List<WallRow>

    @Query(WallSql.GROUPS)
    fun groups(room: String, sources: List<String>): List<WallGroupRow>

    @Query(WallSql.FILM_HISTORY_OLDER)
    fun filmHistoryOlder(profile: String, at: Long, contentKey: String, sources: List<String>, search: String?, limit: Int): List<HistoryRow>

    @Query(WallSql.FILM_HISTORY_NEWER)
    fun filmHistoryNewer(profile: String, at: Long, contentKey: String, sources: List<String>, search: String?, limit: Int): List<HistoryRow>

    @Query(WallSql.SERIES_HISTORY_OLDER)
    fun seriesHistoryOlder(profile: String, at: Long, contentKey: String, sources: List<String>, search: String?, limit: Int): List<HistoryRow>

    @Query(WallSql.SERIES_HISTORY_NEWER)
    fun seriesHistoryNewer(profile: String, at: Long, contentKey: String, sources: List<String>, search: String?, limit: Int): List<HistoryRow>

    @Query(WallSql.ENABLED_SOURCES)
    fun enabledSources(): List<String>

    @Query(WallSql.FILM_COPIES)
    fun filmCopies(workKeys: List<String>, sources: List<String>): List<CopyFacts>

    /** A group wall in one of its other orders: SQL built by [com.sohva.tv.core.data.vod.GroupOrderPages], index-served. */
    @RawQuery(observedEntities = [MovieEntity::class, SeriesEntity::class])
    fun rows(query: SupportSQLiteQuery): List<WallRow>
}
