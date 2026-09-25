package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** A film copy as the standing-copy pass weighs it (spec 40 VOD-FR-26, -29). */
data class CopyRow(
    val id: Long,
    @ColumnInfo(name = "work_key") val workKey: String,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    @ColumnInfo(name = "sort_name") val sortName: String,
    @ColumnInfo(name = "claim_mask") val claimMask: Int,
    @ColumnInfo(name = "picture_rank") val pictureRank: Int,
    val visible: Boolean,
    @ColumnInfo(name = "primary_copy") val primaryCopy: Boolean,
    @ColumnInfo(name = "group_primary") val groupPrimary: Boolean,
)

/** A film's key and identity: what the passes walk in key order. */
data class FilmKey(val key: String, @ColumnInfo(name = "work_key") val workKey: String?)

/** The facts a match needs to re-key a film. */
data class FilmNameYear(val name: String, val year: Int?)

object LibrarySql {
    const val MATCHES_PAGE = "SELECT * FROM metadata_match WHERE content_key > :after AND content_key < :until ORDER BY content_key LIMIT :limit"
    const val FILM_KEYS_PAGE = "SELECT key, work_key FROM movie WHERE key > :after AND key < :until ORDER BY key LIMIT :limit"
    const val COPIES = "SELECT id, work_key, group_id, sort_name, claim_mask, picture_rank, visible, primary_copy, group_primary " +
        "FROM movie WHERE work_key IN (:workKeys)"
    const val RECOUNT_FILM_GROUPS = "UPDATE content_group SET item_count = (SELECT COUNT(*) FROM movie " +
        "WHERE movie.group_id = content_group.id AND movie.visible = 1 AND movie.group_primary = 1) WHERE source_id = :sourceId AND room = 'MOVIES'"
    const val FILM_GENRE_COUNT = "SELECT COUNT(*) FROM movie WHERE genre = :genre AND visible = 1 AND primary_copy = 1"
    const val FILM_UNSORTED_COUNT = "SELECT COUNT(*) FROM movie INDEXED BY index_movie_genre_visible_primary_copy_sort_name " +
        "WHERE genre IS NULL AND visible = 1 AND primary_copy = 1"
    const val SERIES_GENRE_COUNT = "SELECT COUNT(*) FROM series WHERE genre = :genre AND visible = 1 AND primary_copy = 1"
    const val SERIES_UNSORTED_COUNT = "SELECT COUNT(*) FROM series INDEXED BY index_series_genre_visible_primary_copy_sort_name " +
        "WHERE genre IS NULL AND visible = 1 AND primary_copy = 1"
}

@Dao
interface LibraryDao {
    @Query(LibrarySql.MATCHES_PAGE)
    fun matchesPage(after: String, until: String, limit: Int): List<MetadataMatchEntity>

    @Query(LibrarySql.FILM_KEYS_PAGE)
    fun filmKeysPage(after: String, until: String, limit: Int): List<FilmKey>

    @Query("SELECT name, year FROM movie WHERE key = :key")
    fun filmNameYear(key: String): FilmNameYear?

    @Query(
        "UPDATE movie SET replacement_title = :title, replacement_sort = :sort, replacement_poster = :poster, replace_poster = :replacePoster, " +
            "external_id = :externalId, genre = :genre, work_key = :workKey, replacement_key = :replacementKey WHERE key = :key",
    )
    fun applyFilm(key: String, title: String?, sort: String?, poster: String?, replacePoster: Boolean, externalId: String?, genre: String?, workKey: String?, replacementKey: String?)

    @Query(
        "UPDATE series SET replacement_title = :title, replacement_sort = :sort, replacement_poster = :poster, replace_poster = :replacePoster, " +
            "external_id = :externalId, genre = :genre, replacement_key = :replacementKey WHERE key = :key",
    )
    fun applySeries(key: String, title: String?, sort: String?, poster: String?, replacePoster: Boolean, externalId: String?, genre: String?, replacementKey: String?)

    @Query(LibrarySql.COPIES)
    fun copies(workKeys: List<String>): List<CopyRow>

    @Query("UPDATE movie SET primary_copy = :primary, group_primary = :groupPrimary WHERE id = :id")
    fun setPrimary(id: Long, primary: Boolean, groupPrimary: Boolean)

    @Query(LibrarySql.RECOUNT_FILM_GROUPS)
    fun recountFilmGroups(sourceId: String)

    @Query(LibrarySql.FILM_GENRE_COUNT)
    fun filmGenreCount(genre: String): Int

    @Query(LibrarySql.FILM_UNSORTED_COUNT)
    fun filmUnsortedCount(): Int

    @Query(LibrarySql.SERIES_GENRE_COUNT)
    fun seriesGenreCount(genre: String): Int

    @Query(LibrarySql.SERIES_UNSORTED_COUNT)
    fun seriesUnsortedCount(): Int

    @Upsert
    fun putCounts(rows: List<GenreCountEntity>)

    @Query("SELECT * FROM genre_count WHERE room = :room AND titles > 0")
    fun counts(room: String): List<GenreCountEntity>
}
