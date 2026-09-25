package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.EpisodeEntity
import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SeriesEntity
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.source.ImportRoute
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.text.Keys
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.text.StableIds
import com.sohva.tv.core.model.vod.CopyClaimReader
import com.sohva.tv.core.model.vod.QualityChips
import com.sohva.tv.core.model.vod.VodText
import com.sohva.tv.core.net.http.ProviderRequest
import com.sohva.tv.core.net.m3u.M3uEntry
import com.sohva.tv.core.net.m3u.M3uKind
import com.sohva.tv.core.net.m3u.M3uReader
import com.sohva.tv.core.net.xtream.XtreamClient
import com.sohva.tv.core.net.xtream.XtreamFilm
import com.sohva.tv.core.net.xtream.XtreamList
import com.sohva.tv.core.net.xtream.XtreamSeries
import com.sohva.tv.core.net.xtream.XtreamUrls
import com.sohva.tv.core.sync.diff.ContentHash
import com.sohva.tv.core.sync.diff.GroupRef
import com.sohva.tv.core.sync.diff.GroupResolver
import com.sohva.tv.core.sync.diff.KeyedDiff
import com.sohva.tv.core.sync.diff.Room
import com.sohva.tv.core.sync.diff.Row
import com.sohva.tv.core.sync.diff.Tables

import kotlinx.coroutines.withContext

/**
 * The catalogue import (spec 10 §4.17): films and series (and for M3U their episodes), each table
 * written as a diff. Nothing proportional to the catalogue is held: M3U episodes are written as they
 * are parsed and only series headers (key → id) stay in memory (SRC-L-04, SRC-L-05).
 */
internal class CatalogueImport(private val env: ImportEnvironment) {
    /** An episode waiting for its series' row id, which only the writer knows. */
    private class EpisodeDraft(val seriesKey: String, val row: (seriesId: Long) -> Row<EpisodeEntity>)

    private class Batch(val films: List<Row<MovieEntity>>, val series: List<Row<SeriesEntity>>, val episodes: List<EpisodeDraft>)

    private inner class Target(sourceId: String) {
        val filmGroups = GroupResolver(env.db.groupImport(), sourceId, Room.MOVIES)
        val seriesGroups = GroupResolver(env.db.groupImport(), sourceId, Room.SERIES)
        val films = KeyedDiff(Tables.movies(env.db, sourceId), filmGroups)
        val series = KeyedDiff(Tables.series(env.db, sourceId), seriesGroups)
        val episodes = KeyedDiff(Tables.episodes(env.db, sourceId), null)
        val seriesIds = HashMap<String, Long>()
        val storedBefore = KeyRange.movies(sourceId).let { env.db.movieImport().count(it.from, it.until) } +
            KeyRange.series(sourceId).let { env.db.seriesImport().count(it.from, it.until) }
    }

    suspend fun m3u(route: ImportRoute.M3u, scope: ImportScope, job: ImportJob): Int {
        val target = open(job.sourceId)
        var parsed = 0
        pipeline<Batch>(env.dispatchers, produce = { send ->
            env.http.get(route.playlistUrl, ProviderRequest.SOURCE) { body ->
                val reader = M3uReader(body)
                val parser = M3uCatalogue(job, env)
                while (true) {
                    val entry = reader.next() ?: break
                    parsed++
                    // Scope BOTH leaves live entries to the playlist import; VOD keeps every entry (SRC-FR-90).
                    if (scope == ImportScope.BOTH && entry.kind == M3uKind.LIVE) continue
                    parser.add(entry)
                    if (parser.size >= LiveImport.BATCH) send(parser.take())
                }
                if (parser.size > 0) send(parser.take())
            }
        }, consume = { batch -> write(target, batch, job) })
        if (parsed == 0) throw AppException(AppError.CatalogueEmpty)
        return finish(target, job.sourceId, sweepFilms = true, sweepSeries = true, sweepEpisodes = true)
    }

    suspend fun xtream(route: ImportRoute.Xtream, job: ImportJob): Int {
        val client = XtreamClient(env.http, route.account)
        val urls = XtreamUrls(route.account)
        client.accountInfo()
        val filmCategories = client.categories(XtreamList.FILMS).associate { it.id to it.name }
        val seriesCategories = client.categories(XtreamList.SERIES).associate { it.id to it.name }
        val target = open(job.sourceId)
        var films = 0
        var series = 0
        pipeline<Batch>(env.dispatchers, produce = { send ->
            var batch = ArrayList<Row<MovieEntity>>(LiveImport.BATCH)
            client.films { film ->
                batch += xtreamFilm(job, film, films++, filmCategories, urls)
                if (batch.size == LiveImport.BATCH) {
                    send(Batch(batch, emptyList(), emptyList()))
                    batch = ArrayList(LiveImport.BATCH)
                }
            }
            if (batch.isNotEmpty()) send(Batch(batch, emptyList(), emptyList()))
            var seriesBatch = ArrayList<Row<SeriesEntity>>(LiveImport.BATCH)
            client.series { item ->
                seriesBatch += xtreamSeries(job, item, series++, seriesCategories)
                if (seriesBatch.size == LiveImport.BATCH) {
                    send(Batch(emptyList(), seriesBatch, emptyList()))
                    seriesBatch = ArrayList(LiveImport.BATCH)
                }
            }
            if (seriesBatch.isNotEmpty()) send(Batch(emptyList(), seriesBatch, emptyList()))
        }, consume = { batch -> write(target, batch, job) })
        // An empty list never empties what the source has (SRC-FR-80 rebuild rule); each list on its own.
        if (films == 0 && series == 0 && target.storedBefore > 0) throw AppException(AppError.CatalogueEmpty)
        // Xtream episodes arrive with the series page; those of a removed series go with it.
        return finish(target, job.sourceId, sweepFilms = films > 0, sweepSeries = series > 0, sweepEpisodes = false)
    }

    private suspend fun open(sourceId: String): Target = withContext(env.dispatchers.bulkWrite) { Target(sourceId) }

    private suspend fun write(target: Target, batch: Batch, job: ImportJob) {
        env.pauseGate.awaitTurn(WorkOrigin.VIEWER)
        env.db.runInTransaction {
            target.films.write(batch.films)
            if (batch.series.isNotEmpty()) {
                target.series.write(batch.series)
                val missing = batch.series.map { it.key }.filter { it !in target.seriesIds }
                if (missing.isNotEmpty()) env.db.seriesImport().hashes(missing).forEach { target.seriesIds[it.key] = it.id }
            }
            if (batch.episodes.isNotEmpty()) {
                target.episodes.write(batch.episodes.mapNotNull { draft -> target.seriesIds[draft.seriesKey]?.let(draft.row) })
            }
        }
        job.advance(batch.films.size + batch.series.size + batch.episodes.size)
    }

    private suspend fun finish(target: Target, sourceId: String, sweepFilms: Boolean, sweepSeries: Boolean, sweepEpisodes: Boolean): Int =
        withContext(env.dispatchers.bulkWrite) {
            if (sweepFilms) target.films.sweep(env.db)
            if (sweepSeries) target.series.sweep(env.db)
            if (sweepEpisodes) target.episodes.sweep(env.db)
            val episodes = KeyRange.episodes(sourceId)
            do {
                val deleted = env.db.runInTransaction<Int> {
                    env.db.episodeImport().deleteOrphans(episodes.from, episodes.until, KeyedDiff.DELETE_CHUNK)
                }
            } while (deleted > 0)
            env.db.runInTransaction {
                target.filmGroups.finish(complete = sweepFilms)
                target.seriesGroups.finish(complete = sweepSeries)
            }
            KeyRange.movies(sourceId).let { env.db.movieImport().count(it.from, it.until) } +
                KeyRange.series(sourceId).let { env.db.seriesImport().count(it.from, it.until) }
        }

    private fun xtreamFilm(job: ImportJob, f: XtreamFilm, index: Int, categories: Map<String, String>, urls: XtreamUrls): Row<MovieEntity> {
        val key = Keys.movieKey(job.sourceId, f.streamId)
        val group = f.categoryId?.let { id -> categories[id]?.let { GroupRef(Keys.groupKey(id, it), it) } }
        val address = urls.film(f.streamId, f.extension)
        val hash = ContentHash().add(KEYS_VERSION).add(f.name).add(group?.key).add(group?.name).add(f.year).add(f.rating).add(f.posterUrl)
            .add(address).add(f.plot).add(index).value()
        return Row(key, hash, group) { id, groupId ->
            val claims = CopyClaimReader.read(f.name)
            MovieEntity(
                id = id, key = key, sourceId = job.sourceId, providerId = f.streamId, groupId = groupId, name = f.name,
                sortName = SortNames.of(f.name), year = VodText.year(f.year, f.name), rating = f.rating,
                ratingX10 = VodText.ratingTenths(f.rating), posterUrl = f.posterUrl, streamUrlEnc = env.sealer.seal(address),
                plot = f.plot, providerOrder = index, qualityMask = claims.qualityMask, claimMask = claims.languageMask,
                pictureRank = claims.pictureRank, genre = null, workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = hash,
                generation = job.generation,
            )
        }
    }

    private fun xtreamSeries(job: ImportJob, s: XtreamSeries, index: Int, categories: Map<String, String>): Row<SeriesEntity> {
        val key = Keys.seriesKey(job.sourceId, s.seriesId)
        val group = s.categoryId?.let { id -> categories[id]?.let { GroupRef(Keys.groupKey(id, it), it) } }
        val hash = ContentHash().add(KEYS_VERSION).add(s.name).add(group?.key).add(group?.name).add(s.year).add(s.rating).add(s.coverUrl)
            .add(s.backdropUrl).add(s.plot).add(index).value()
        return Row(key, hash, group) { id, groupId ->
            SeriesEntity(
                id = id, key = key, sourceId = job.sourceId, providerId = s.seriesId, groupId = groupId, name = s.name,
                sortName = SortNames.of(s.name), year = VodText.year(s.year, s.name), rating = s.rating,
                ratingX10 = VodText.ratingTenths(s.rating), posterUrl = s.coverUrl, backdropUrl = s.backdropUrl, plot = s.plot,
                providerOrder = index, qualityMask = QualityChips.mask(s.name), genre = null,
                workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = hash,
                generation = job.generation,
            )
        }
    }

    /**
     * The M3U side of SRC-FR-90 for one parse: films, series headers on first sight, episodes.
     * Holds only the current batch and the set of series keys already announced.
     */
    private class M3uCatalogue(private val job: ImportJob, private val env: ImportEnvironment) {
        private val ids = StableIds()
        private val announced = HashSet<String>()
        private var films = ArrayList<Row<MovieEntity>>()
        private var series = ArrayList<Row<SeriesEntity>>()
        private var episodes = ArrayList<EpisodeDraft>()

        val size: Int get() = films.size + series.size + episodes.size

        fun take(): Batch = Batch(films, series, episodes).also {
            films = ArrayList()
            series = ArrayList()
            episodes = ArrayList()
        }

        fun add(e: M3uEntry) {
            val name = e.name ?: env.names.channel(e.index + 1)
            val marker = EpisodeNames.parse(name)
            if (marker == null) films += film(e, name) else episode(e, marker)
        }

        private fun film(e: M3uEntry, name: String): Row<MovieEntity> {
            val key = Keys.movieKey(job.sourceId, e.id)
            val group = e.group?.let { GroupRef(Keys.groupKey(null, it), it) }
            val year = EpisodeNames.year(name)
            val hash = ContentHash().add(KEYS_VERSION).add(name).add(group?.key).add(year).add(e.logoUrl).add(e.streamUrl).add(e.index).value()
            return Row(key, hash, group) { id, groupId ->
                val claims = CopyClaimReader.read(name)
                MovieEntity(
                    id = id, key = key, sourceId = job.sourceId, providerId = e.id, groupId = groupId, name = name,
                    sortName = SortNames.of(name), year = year, rating = null, ratingX10 = null, posterUrl = e.logoUrl,
                    streamUrlEnc = env.sealer.seal(e.streamUrl), plot = null, providerOrder = e.index,
                    qualityMask = claims.qualityMask, claimMask = claims.languageMask, pictureRank = claims.pictureRank, genre = null,
                    workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = hash,
                    generation = job.generation,
                )
            }
        }

        private fun episode(e: M3uEntry, marker: EpisodeNames.Marker) {
            val providerId = ids.sha256Hex("${marker.series}|${e.group.orEmpty()}")
            val seriesKey = Keys.seriesKey(job.sourceId, providerId)
            if (announced.add(seriesKey)) series += seriesHeader(e, marker, seriesKey, providerId)
            val key = Keys.episodeKey(job.sourceId, e.id)
            val name = marker.title ?: "S%02dE%02d".format(marker.season, marker.episode)
            val hash = ContentHash().add(seriesKey).add(marker.season).add(marker.episode).add(name).add(e.streamUrl).add(e.logoUrl).value()
            episodes += EpisodeDraft(seriesKey) { seriesId ->
                Row(key, hash, null) { id, _ ->
                    EpisodeEntity(
                        id = id, key = key, seriesId = seriesId, sourceId = job.sourceId, providerId = e.id, season = marker.season,
                        number = marker.episode, name = name, streamUrlEnc = env.sealer.seal(e.streamUrl), plot = null,
                        durationSeconds = e.durationSeconds?.takeIf { it > 0 }, thumbnailUrl = e.logoUrl, contentHash = hash,
                        generation = job.generation,
                    )
                }
            }
        }

        private fun seriesHeader(e: M3uEntry, marker: EpisodeNames.Marker, key: String, providerId: String): Row<SeriesEntity> {
            val group = e.group?.let { GroupRef(Keys.groupKey(null, it), it) }
            val year = EpisodeNames.year(marker.series)
            val hash = ContentHash().add(KEYS_VERSION).add(marker.series).add(group?.key).add(year).add(e.logoUrl).add(announced.size).value()
            return Row(key, hash, group) { id, groupId ->
                SeriesEntity(
                    id = id, key = key, sourceId = job.sourceId, providerId = providerId, groupId = groupId, name = marker.series,
                    sortName = SortNames.of(marker.series), year = year, rating = null, ratingX10 = null, posterUrl = e.logoUrl,
                    backdropUrl = null, plot = null, providerOrder = announced.size - 1, qualityMask = QualityChips.mask(marker.series),
                    genre = null, workKey = null,
                    primaryCopy = true, visible = true, itemPosition = null, contentHash = hash, generation = job.generation,
                )
            }
        }
    }

    private companion object {
        /**
         * Part of every film and series hash: bumped when the columns an import derives change
         * (5 = the claim columns of schema v5), so the next import rewrites every row once.
         */
        const val KEYS_VERSION = 5
    }
}

/** The M3U episode names of SRC-FR-90 and the film year rule. */
internal object EpisodeNames {
    class Marker(val series: String, val season: Int, val episode: Int, val title: String?)

    private val seasonEpisode = Regex("""(?i)^(.+?)\s+[Ss](\d{1,3})\s*[Ee](\d{1,4})(?:\s*[-–—:.]\s*(.*))?$""")
    private val crossed = Regex("""(?i)^(.+?)\s+(\d{1,3})x(\d{1,4})(?:\s*[-–—:.]\s*(.*))?$""")
    private val year = Regex("""(?<!\d)(?:19|20)\d\d(?!\d)""")

    fun parse(name: String): Marker? {
        val match = seasonEpisode.matchEntire(name) ?: crossed.matchEntire(name) ?: return null
        val (series, season, episode, title) = match.destructured
        return Marker(series.trim(), season.toInt(), episode.toInt(), title.trim().ifEmpty { null })
    }

    fun year(name: String): Int? = year.find(name)?.value?.toInt()
}
