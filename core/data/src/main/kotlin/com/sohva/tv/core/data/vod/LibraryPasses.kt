package com.sohva.tv.core.data.vod

import com.sohva.tv.core.data.database.CopyRow
import com.sohva.tv.core.data.database.GenreCountEntity
import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.MATCHED
import com.sohva.tv.core.data.database.MetadataMatchEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.model.metadata.TitleCleaner
import com.sohva.tv.core.model.metadata.WorkKeys
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.model.vod.PreferredCopy

/**
 * The library's derived columns (spec 40 §9.3, spec 41 §4.12 and §9.2–9.3), each pass paged in
 * key order, ≤ 2,000 rows a page, one short transaction per page, nothing catalogue-sized held:
 * - [applyMatches] writes the stored matches of a source back into its rows after an import;
 * - [refreshCopies] decides, per film identity, which copy stands for the film on walls that span
 *   groups ([com.sohva.tv.core.data.database.MovieEntity.primaryCopy]) and in each of its groups;
 * - [refreshSource] runs both for a whole source and recounts its groups;
 * - [recountGenres] refreshes the Genres rail's counts.
 * Blocking: callers run them on the bulk-write dispatcher.
 */
class LibraryPasses(private val db: SohvaDatabase) {
    private val dao get() = db.library()

    /** After an import of [sourceId]: matches back into the rows, identities and standing copies, group counts. */
    fun refreshSource(sourceId: String, preferred: PreferredCopy) {
        applyMatches(KeyRange.movies(sourceId))
        applyMatches(KeyRange.series(sourceId))
        val films = KeyRange.movies(sourceId)
        var after = films.from
        while (true) {
            val page = dao.filmKeysPage(after, films.until, PAGE)
            if (page.isEmpty()) break
            refreshCopies(page.mapNotNull { it.workKey }.distinct(), preferred)
            after = page.last().key
            if (page.size < PAGE) break
        }
        dao.recountFilmGroups(sourceId)
    }

    /** Writes the stored matches whose content keys fall in [range] into the film or series rows. */
    fun applyMatches(range: KeyRange) {
        var after = range.from
        while (true) {
            val page = dao.matchesPage(after, range.until, PAGE)
            if (page.isEmpty()) return
            db.runInTransaction { page.forEach(::apply) }
            after = page.last().contentKey
            if (page.size < PAGE) return
        }
    }

    /**
     * One title's match into its row (spec 41 META-FR-60): a match gives the replacement title and
     * poster, the external id (a film's work key becomes `tmdb:<id>`) and the genre; a real miss
     * leaves the provider's title and no genre.
     */
    fun apply(match: MetadataMatchEntity) {
        val matched = match.status == MATCHED
        val title = match.replacementTitle?.takeIf { matched }
        val sort = title?.let(SortNames::of)
        val replacementKey = title?.let(TitleCleaner::normalizeTitle)
        val genre = match.genre?.takeIf { matched && Genre.ofWire(it) != null }
        val externalId = match.externalId?.takeIf { matched }
        if (match.contentKey.startsWith(FILM)) {
            val facts = dao.filmNameYear(match.contentKey) ?: return
            val workKey = WorkKeys.of(facts.name, facts.year, externalId?.takeIf { match.provider == TMDB })
            dao.applyFilm(match.contentKey, title, sort, match.replacementPoster.takeIf { matched }, matched && match.replaceProviderPoster, externalId, genre, workKey, replacementKey)
        } else {
            dao.applySeries(match.contentKey, title, sort, match.replacementPoster.takeIf { matched }, matched && match.replaceProviderPoster, externalId, genre, replacementKey)
        }
    }

    /**
     * The standing copies of [workKeys] (spec 40 VOD-FR-26): the visible copy with the highest
     * preference score, ties to the first in wall order (sort name, then id); per group the same
     * among the group's copies. Only flags that change are written.
     */
    fun refreshCopies(workKeys: List<String>, preferred: PreferredCopy) {
        for (chunk in workKeys.chunked(IN_LIMIT)) {
            val copies = dao.copies(chunk)
            if (copies.isEmpty()) continue
            val changes = ArrayList<Triple<Long, Boolean, Boolean>>()
            for ((_, film) in copies.groupBy { it.workKey }) {
                val standing = standing(film, preferred)
                val perGroup = film.groupBy { it.groupId }.mapValues { (_, inGroup) -> standing(inGroup, preferred) }
                for (c in film) {
                    val primary = c.id == standing
                    val groupPrimary = c.id == perGroup[c.groupId]
                    if (primary != c.primaryCopy || groupPrimary != c.groupPrimary) changes += Triple(c.id, primary, groupPrimary)
                }
            }
            if (changes.isNotEmpty()) db.runInTransaction { for ((id, p, g) in changes) dao.setPrimary(id, p, g) }
        }
    }

    private fun standing(copies: List<CopyRow>, preferred: PreferredCopy): Long? = copies.asSequence()
        .filter { it.visible }
        .sortedWith(compareByDescending<CopyRow> { preferred.score(it.claimMask, it.pictureRank) }.thenBy { it.sortName }.thenBy { it.id })
        .firstOrNull()?.id

    /** Titles per genre and Unsorted, for both rooms (VOD-FR-04): 46 indexed counts. */
    fun recountGenres() {
        val rows = buildList {
            for (g in Genre.entries) {
                add(GenreCountEntity(MOVIES, g.wire, dao.filmGenreCount(g.wire)))
                add(GenreCountEntity(SERIES, g.wire, dao.seriesGenreCount(g.wire)))
            }
            add(GenreCountEntity(MOVIES, "", dao.filmUnsortedCount()))
            add(GenreCountEntity(SERIES, "", dao.seriesUnsortedCount()))
        }
        db.runInTransaction { dao.putCounts(rows) }
    }

    private companion object {
        const val PAGE = 2_000
        const val IN_LIMIT = 500
        const val FILM = "vod:movie:"
        const val TMDB = "tmdb"
        const val MOVIES = "MOVIES"
        const val SERIES = "SERIES"
    }
}
