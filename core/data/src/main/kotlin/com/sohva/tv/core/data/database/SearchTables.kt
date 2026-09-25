package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Full-text indexes for Search (spec 03 §9): one FTS4 table per searched table, keyed by the
 * searched row's id. `unicode61` folds case over all of Unicode and folds accents, like the walls'
 * search through `sort_name`; matching is by word prefix (decision "Search matching").
 *
 * New rows are indexed in bulk when an import has written them ([SearchIndex.catchUp]); renames and
 * deletes follow through [SearchIndex]'s triggers, which fire only when the searched text changes.
 * Room's own content-sync triggers fire on every update of a row (films are updated in bulk by the
 * organisation pass, the copy flips and the metadata passes), and an insert trigger costs about 28 s
 * per 200,000 films against 0.5 s for one bulk insert (decision "Search index").
 */

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "movie_search")
data class MovieSearchEntity(@PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long, val name: String)

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "series_search")
data class SeriesSearchEntity(@PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long, val name: String)

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "episode_search")
data class EpisodeSearchEntity(@PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long, val name: String?)

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "channel_search")
data class ChannelSearchEntity(@PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long, val name: String)

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "programme_search")
data class ProgrammeSearchEntity(@PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long, val title: String, val subtitle: String?)

/** The searched tables (spec 03 §4.2). */
enum class SearchTable(internal val table: String, internal val index: String, internal val columns: List<String>) {
    MOVIE("movie", "movie_search", listOf("name")),
    SERIES("series", "series_search", listOf("name")),
    EPISODE("episode", "episode_search", listOf("name")),
    CHANNEL("channel", "channel_search", listOf("name")),
    PROGRAMME("programme", "programme_search", listOf("title", "subtitle")),
}

/** Keeping the search tables current: bulk catch-up after imports, triggers for renames and deletes. */
object SearchIndex {
    private fun triggers(i: SearchTable): List<String> {
        val cols = i.columns.joinToString(", ")
        val changed = i.columns.joinToString(" OR ") { "old.$it IS NOT new.$it" }
        val set = i.columns.joinToString(", ") { "$it = new.$it" }
        return listOf(
            "CREATE TRIGGER IF NOT EXISTS ${i.index}_update AFTER UPDATE OF $cols ON ${i.table} WHEN $changed " +
                "BEGIN UPDATE ${i.index} SET $set WHERE rowid = old.id; END",
            "CREATE TRIGGER IF NOT EXISTS ${i.index}_delete AFTER DELETE ON ${i.table} " +
                "BEGIN DELETE FROM ${i.index} WHERE rowid = old.id; END",
        )
    }

    fun installTriggers(db: SupportSQLiteDatabase) {
        for (i in SearchTable.entries) triggers(i).forEach(db::execSQL)
    }

    /**
     * Indexes the rows of [table] the index does not hold yet: one statement, a lookup per row by id.
     * Runs in the caller's write transaction when an import has written its rows.
     */
    fun catchUp(db: SupportSQLiteDatabase, table: SearchTable) {
        val cols = table.columns.joinToString(", ")
        val from = table.columns.joinToString(", ") { "t.$it" }
        db.execSQL(
            "INSERT INTO ${table.index}(rowid, $cols) SELECT t.id, $from FROM ${table.table} t " +
                "WHERE NOT EXISTS (SELECT 1 FROM ${table.index} s WHERE s.rowid = t.id)",
        )
    }

    fun catchUp(db: SohvaDatabase, vararg tables: SearchTable) {
        db.runInTransaction { tables.forEach { catchUp(db.openHelper.writableDatabase, it) } }
    }

    /** Indexes every existing row (right after the tables were created). */
    fun fill(db: SupportSQLiteDatabase) = SearchTable.entries.forEach { catchUp(db, it) }

    /** Installs the triggers on a new database; migrations install them with their tables. */
    object Callback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = installTriggers(db)
    }
}
