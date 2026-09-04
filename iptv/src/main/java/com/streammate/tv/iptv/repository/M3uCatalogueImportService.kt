package com.streammate.tv.iptv.repository

import com.streammate.tv.core.error.localizedTransportFailure
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import androidx.annotation.StringRes
import com.streammate.tv.core.database.CatalogueDao
import com.streammate.tv.core.database.VodEpisodeEntity
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.database.VodSeriesEntity
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.network.GuideSource
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretRedactor
import com.streammate.tv.iptv.m3u.ChannelNameNormalizer
import com.streammate.tv.iptv.m3u.M3uContentKind
import com.streammate.tv.iptv.m3u.M3uParser
import com.streammate.tv.iptv.m3u.ParsedIptvChannel
import java.security.MessageDigest
import java.util.UUID

class M3uCatalogueImportService(
    private val sourceClient: GuideSource,
    private val parser: M3uParser,
    private val dao: CatalogueDao,
    private val secretCipher: SecretCipher,
    private val organization: OrganizationRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun refresh(source: IptvSourceConfiguration): CatalogueImportSummary {
        if (source.type != IptvSourceType.M3U) {
            throw LocalizedException(CoreR.string.error_source_not_m3u)
        }
        if (!source.importScope.importsVod) {
            throw LocalizedException(CoreR.string.error_source_no_vod)
        }
        val url = source.m3uUrl?.takeIf(String::isNotBlank)
            ?: throw GuideImportException(CoreR.string.error_m3u_url_missing)
        val snapshotId = UUID.randomUUID().toString()
        dao.markCatalogueRefreshStarted(source.id, clock())
        return try {
            val movies = mutableListOf<VodMovieEntity>()
            var movieCount = 0
            val series = linkedMapOf<String, SeriesAccumulator>()
            sourceClient.withSource(url) { input ->
                for (entry in parser.records(input)) {
                    if (source.importScope == IptvImportScope.BOTH && entry.contentKind == M3uContentKind.LIVE) {
                        continue
                    }
                    val episode = parseEpisode(entry.name)
                    if (episode == null) {
                        movies += entry.toMovie(source.id, snapshotId)
                        movieCount += 1
                        if (movies.size >= BATCH_SIZE) {
                            dao.upsertMovies(movies.toList())
                            movies.clear()
                        }
                    } else {
                        val seriesId = stableId("${episode.seriesName}|${entry.groupTitle.orEmpty()}")
                        val accumulator = series.getOrPut(seriesId) {
                            SeriesAccumulator(
                                entity = VodSeriesEntity(
                                    sourceId = source.id,
                                    snapshotId = snapshotId,
                                    seriesId = seriesId,
                                    name = episode.seriesName,
                                    normalizedName = ChannelNameNormalizer.normalize(episode.seriesName),
                                    categoryName = entry.groupTitle,
                                    posterUrl = entry.logoUrl,
                                    backdropUrl = null,
                                    year = extractYear(episode.seriesName),
                                    rating = null,
                                    plot = null,
                                ),
                            )
                        }
                        accumulator.episodes += VodEpisodeEntity(
                            sourceId = source.id,
                            seriesId = seriesId,
                            episodeId = entry.id,
                            seasonNumber = episode.season,
                            episodeNumber = episode.episode,
                            name = episode.episodeName,
                            encryptedStreamUrl = secretCipher.encrypt(entry.streamUrl),
                            plot = null,
                            durationSeconds = null,
                            thumbnailUrl = entry.logoUrl,
                        )
                    }
                }
            }
            if (movies.isNotEmpty()) dao.upsertMovies(movies)
            for (batch in series.values.map(SeriesAccumulator::entity).chunked(BATCH_SIZE)) {
                dao.upsertSeries(batch)
            }
            series.values.forEach { item ->
                dao.replaceSeriesEpisodes(source.id, item.entity.seriesId, item.episodes)
            }
            val seriesCount = series.size
            organization?.registerImportedSnapshot(source.id, snapshotId)
            dao.activateCatalogueSnapshot(
                sourceId = source.id,
                snapshotId = snapshotId,
                itemCount = movieCount + seriesCount,
                now = clock(),
            )
            CatalogueImportSummary(movieCount, seriesCount)
        } catch (error: Throwable) {
            dao.deleteMovieSnapshot(source.id, snapshotId)
            dao.deleteSeriesSnapshot(source.id, snapshotId)
            val redacted = SecretRedactor.redact(error.message)
            runCatching { dao.markCatalogueRefreshFailed(source.id, clock(), redacted) }
            throw localizedTransportFailure(error, ::GuideImportException)
        }
    }

    private fun ParsedIptvChannel.toMovie(sourceId: String, snapshotId: String) = VodMovieEntity(
        sourceId = sourceId,
        snapshotId = snapshotId,
        movieId = id,
        name = name,
        normalizedName = normalizedName,
        categoryName = groupTitle,
        posterUrl = logoUrl,
        encryptedStreamUrl = secretCipher.encrypt(streamUrl),
        year = extractYear(name),
        rating = null,
        plot = null,
    )

    private data class SeriesAccumulator(
        val entity: VodSeriesEntity,
        val episodes: MutableList<VodEpisodeEntity> = mutableListOf(),
    )

    private data class EpisodeDetails(
        val seriesName: String,
        val season: Int,
        val episode: Int,
        val episodeName: String,
    )

    private companion object {
        const val BATCH_SIZE = 250
        val EPISODE_PATTERN = Regex(
            """(?i)^(.+?)\s+[Ss](\d{1,3})\s*[Ee](\d{1,4})(?:\s*[-–—:.]\s*(.*))?$""",
        )
        val ALTERNATE_EPISODE_PATTERN = Regex(
            """(?i)^(.+?)\s+(\d{1,3})x(\d{1,4})(?:\s*[-–—:.]\s*(.*))?$""",
        )
        val YEAR_PATTERN = Regex("""(?:^|\D)((?:19|20)\d{2})(?:\D|$)""")

        fun parseEpisode(name: String): EpisodeDetails? {
            val match = EPISODE_PATTERN.matchEntire(name.trim())
                ?: ALTERNATE_EPISODE_PATTERN.matchEntire(name.trim())
                ?: return null
            val seriesName = match.groupValues[1].trim().takeIf(String::isNotBlank) ?: return null
            val season = match.groupValues[2].toIntOrNull() ?: return null
            val episode = match.groupValues[3].toIntOrNull() ?: return null
            val episodeName = match.groupValues.getOrNull(4)?.trim().takeIf { !it.isNullOrBlank() }
                ?: "S%02dE%02d".format(season, episode)
            return EpisodeDetails(seriesName, season, episode, episodeName)
        }

        fun extractYear(name: String): Int? = YEAR_PATTERN.find(name)?.groupValues?.get(1)?.toIntOrNull()

        fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
