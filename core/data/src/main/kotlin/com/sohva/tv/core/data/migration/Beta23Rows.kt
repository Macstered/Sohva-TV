package com.sohva.tv.core.data.migration

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sohva.tv.core.data.database.CONTENT_EPISODE
import com.sohva.tv.core.data.database.CONTENT_MOVIE
import com.sohva.tv.core.data.database.EventChannelDecisionEntity
import com.sohva.tv.core.data.database.MetadataMatchEntity
import com.sohva.tv.core.data.database.ReminderEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.TeamAliasEntity
import com.sohva.tv.core.data.database.WatchProgressEntity
import com.sohva.tv.core.model.vod.Genre

/** The importer's writes: inserts that never replace what the rebuild already holds. */
@Dao
interface Beta23ImportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun progress(rows: List<WatchProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun reminders(rows: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun decisions(rows: List<EventChannelDecisionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun teamAliases(rows: List<TeamAliasEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun matches(rows: List<MetadataMatchEntity>)

    /**
     * Imported positions carry no film identity or series yet (the catalogue is imported again
     * from the providers): once the titles exist, each row takes its title's. Only rows still
     * missing them are touched, reached through the (content_key) index.
     */
    @Query(
        "UPDATE watch_progress SET work_key = (SELECT m.work_key FROM movie m WHERE m.key = watch_progress.content_key) " +
            "WHERE content_type = 'MOVIE' AND work_key IS NULL AND content_key IN (SELECT key FROM movie WHERE key IN (:keys))",
    )
    fun linkFilms(keys: List<String>)

    @Query(
        "UPDATE watch_progress SET series_key = (SELECT s.key FROM episode e CROSS JOIN series s ON s.id = e.series_id WHERE e.key = watch_progress.content_key) " +
            "WHERE content_type = 'EPISODE' AND series_key IS NULL AND content_key IN (SELECT key FROM episode WHERE key IN (:keys))",
    )
    fun linkEpisodes(keys: List<String>)

    @Query(
        "SELECT content_key FROM watch_progress WHERE content_key > :after AND content_type = :type " +
            "AND ((:type = 'MOVIE' AND work_key IS NULL) OR (:type = 'EPISODE' AND series_key IS NULL)) ORDER BY content_key LIMIT :limit",
    )
    fun unlinked(type: String, after: String, limit: Int): List<String>
}

/**
 * Beta 23's rows that its backups never held (plan/04 §17): positions, reminders, sport channel
 * decisions, team aliases and metadata fixes. Copied as they are; keys follow plan/04 §6 and are
 * the same strings in both apps.
 */
object Beta23Rows {
    data class Counts(val progress: Int, val reminders: Int, val decisions: Int, val aliases: Int, val matches: Int)

    fun copy(old: Beta23Database, db: SohvaDatabase): Counts {
        val dao = db.beta23Import()
        val progress = old.progress().mapNotNull { p ->
            val type = when (p.contentType.uppercase()) {
                CONTENT_MOVIE -> CONTENT_MOVIE
                CONTENT_EPISODE -> CONTENT_EPISODE
                else -> return@mapNotNull null
            }
            // Film identity and series come from the titles once they are imported again (linkFilms, linkEpisodes).
            WatchProgressEntity(p.profileId, p.contentKey, p.sourceId, type, null, null, p.positionMs, p.durationMs, p.completed, p.updatedAt)
        }
        val reminders = old.reminders().map { ReminderEntity(it.id, it.kind, it.eventId, it.channelId, it.title, it.subtitle, it.startAt, it.createdAt) }
        val decisions = old.decisions().map { EventChannelDecisionEntity(it.eventId, it.channelId, it.decision, it.updatedAt) }
        val aliases = old.teamAliases().map { TeamAliasEntity(it.sport, it.canonical, it.alias) }
        val matches = matches(old)
        db.runInTransaction {
            progress.chunked(BATCH).forEach(dao::progress)
            reminders.chunked(BATCH).forEach(dao::reminders)
            decisions.chunked(BATCH).forEach(dao::decisions)
            aliases.chunked(BATCH).forEach(dao::teamAliases)
            matches.chunked(BATCH).forEach(dao::matches)
        }
        return Counts(progress.size, reminders.size, decisions.size, aliases.size, matches.size)
    }

    /**
     * Metadata fixes and matches (`catalogue_metadata_overrides`, `catalogue_genres`). Films are
     * TMDB's (TVmaze has none); a series' id is taken only when beta 23's cache saw it from exactly
     * one provider, because TVmaze and TMDB ids look alike (lesson 5). Anything else is left for
     * the enrichment to find again.
     */
    private fun matches(old: Beta23Database): List<MetadataMatchEntity> {
        val seriesProviders by lazy { old.providersOf("SERIES") + old.providersOf("series") }
        return old.overrides().mapNotNull { o ->
            val (type, provider) = when {
                o.contentKey.startsWith("vod:movie:") -> "movie" to "tmdb"
                o.contentKey.startsWith("series:") -> "series" to (seriesProviders[o.externalId]?.singleOrNull() ?: return@mapNotNull null)
                else -> return@mapNotNull null
            }
            val id = o.externalId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val genre = o.genres.firstNotNullOfOrNull(Genre::ofWire)
            MetadataMatchEntity(
                o.contentKey, type, "matched", provider, id, genre?.wire, if (genre != null) o.genresVersion else 0,
                o.replacementTitle, o.replacementPoster, o.replaceProviderPoster, o.updatedAt,
            )
        }
    }

    /** After a catalogue import: imported positions take their titles' film identity and series, a page at a time. */
    fun link(db: SohvaDatabase) {
        val dao = db.beta23Import()
        for (type in listOf(CONTENT_MOVIE, CONTENT_EPISODE)) {
            var after = ""
            while (true) {
                // Keyset pages over the (small) positions table; titles that never come back stay unlinked.
                val keys = dao.unlinked(type, after, LINK_PAGE)
                if (keys.isEmpty()) break
                if (type == CONTENT_MOVIE) dao.linkFilms(keys) else dao.linkEpisodes(keys)
                after = keys.last()
            }
        }
    }

    private const val BATCH = 500
    private const val LINK_PAGE = 500
}
