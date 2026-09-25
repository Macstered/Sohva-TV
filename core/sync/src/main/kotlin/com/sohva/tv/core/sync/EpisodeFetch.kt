package com.sohva.tv.core.sync

import com.sohva.tv.core.data.database.EpisodeEntity
import com.sohva.tv.core.data.source.SourceStore
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.ImportRoute
import com.sohva.tv.core.model.text.Keys
import com.sohva.tv.core.net.xtream.XtreamClient
import com.sohva.tv.core.net.xtream.XtreamUrls
import com.sohva.tv.core.sync.diff.ContentHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * "Refresh episodes" and a series page's first open (spec 40 VOD-FR-75): Xtream sources (and M3U
 * addresses that derive an Xtream account) are asked for `get_series_info`; a plain M3U playlist
 * already named its episodes, so there is nothing to fetch. The series' stored episodes are
 * replaced in one transaction; addresses are sealed before they are stored.
 */
class EpisodeFetch(private val env: ImportEnvironment, private val sources: SourceStore) {
    suspend fun fetch(seriesKey: String): Outcome<Unit> = withContext(env.dispatchers.io) {
        try {
            val series = env.db.titles().series(seriesKey) ?: return@withContext Outcome.Failed(AppError.SourceDisabled)
            val config = when (val loaded = sources.load(series.sourceId)) {
                is Outcome.Ok -> loaded.value
                is Outcome.Failed -> return@withContext loaded
            }
            if (config == null || !config.source.enabled) return@withContext Outcome.Failed(AppError.SourceDisabled)
            val route = ImportRoute.of(config) as? ImportRoute.Xtream ?: return@withContext Outcome.Ok(Unit)
            val episodes = XtreamClient(env.http, route.account).episodes(series.providerId)
            val urls = XtreamUrls(route.account)
            val rows = episodes.sortedWith(compareBy({ it.season }, { it.episode })).map { e ->
                val key = Keys.episodeKey(series.sourceId, e.id)
                val address = urls.episode(e.id, e.extension)
                EpisodeEntity(
                    key = key, seriesId = series.id, sourceId = series.sourceId, providerId = e.id, season = e.season, number = e.episode,
                    name = e.title, streamUrlEnc = env.sealer.seal(address), plot = e.plot, durationSeconds = e.durationSeconds,
                    thumbnailUrl = e.imageUrl,
                    contentHash = ContentHash().add(e.title).add(e.season).add(e.episode).add(address).add(e.imageUrl).value(), generation = 0,
                )
            }
            withContext(env.dispatchers.bulkWrite) {
                env.db.runInTransaction {
                    env.db.titles().deleteEpisodes(series.id)
                    // The same key may already belong to another series row: a provider that moved an episode.
                    rows.chunked(CHUNK).forEach { chunk -> env.db.episodeImport().deleteKeys(chunk.map { it.key }) }
                    rows.chunked(CHUNK).forEach(env.db.episodeImport()::insert)
                }
            }
            Outcome.Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppException) {
            Outcome.Failed(e.error)
        }
    }

    private companion object {
        /** Under SQLite's 999 variables for the key deletes. */
        const val CHUNK = 500
    }
}
