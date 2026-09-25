package com.sohva.tv.app

import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.EpisodeEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SeriesEntity
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.metadata.TitleCleaner
import com.sohva.tv.core.model.text.SortNames
import kotlinx.coroutines.runBlocking

/**
 * A fictional film and series library written straight into the app's database: one source
 * `vod-0` with provider groups of [perGroup] films each. Film n of group g is "<Group> <number>",
 * named so A–Z order is the numbering order within a group. [stream] gives each title's address.
 */
object LibraryFixture {
    fun seed(
        graph: AppGraph,
        groups: List<String> = listOf("Drama", "Comedy"),
        perGroup: Int = 30,
        posters: Boolean = false,
        stream: (id: Int) -> String = { "http://192.0.2.20/movie/$it.mp4" },
        series: Int = 0,
        episodes: Int = 3,
    ) {
        val db = graph.data.database
        val now = System.currentTimeMillis()
        var index = 0
        for ((g, groupName) in groups.withIndex()) {
            val groupId = db.groupImport().insert(
                ContentGroupEntity(
                    sourceId = SOURCE, room = "MOVIES", groupKey = "id:$g", name = groupName, providerOrder = g,
                    itemCount = perGroup, shown = true, position = g, sortMode = null,
                ),
            )
            val films = (0 until perGroup).map { n ->
                val name = "$groupName %04d".format(n)
                val id = index++
                MovieEntity(
                    key = key(id), sourceId = SOURCE, providerId = "$id", groupId = groupId, name = name,
                    sortName = SortNames.of(name), year = 2000 + n % 25, rating = "7.${n % 10}", ratingX10 = 70 + n % 10,
                    posterUrl = if (posters) "http://192.0.2.20/poster/$id.jpg" else null,
                    streamUrlEnc = graph.data.cipher.encrypt(stream(id)), plot = "A fictional film.",
                    providerOrder = n, genre = null, workKey = null, primaryCopy = true, visible = true, itemPosition = null,
                    contentHash = 1, generation = 1, similarKey = TitleCleaner.normalizeTitle(name),
                )
            }
            films.chunked(2_000).forEach { db.movieImport().insert(it) }
        }
        if (series > 0) seedSeries(graph, series, episodes, stream)
        db.sourceStatus().upsert(SourceStatusEntity(SOURCE, "catalogue", "success", now, now, null, null, null, index, 0, 1, null, null))
        runBlocking { db.sources().upsert(SourceEntity(SOURCE, "Fixture VOD", "M3U", true, 0, 1, "VOD", 0, now, now)) }
    }

    /** [count] series "Northern Line n" in the group "Crime", each with [episodes] episodes of season 1. */
    private fun seedSeries(graph: AppGraph, count: Int, episodes: Int, stream: (id: Int) -> String) {
        val db = graph.data.database
        val groupId = db.groupImport().insert(
            ContentGroupEntity(sourceId = SOURCE, room = "SERIES", groupKey = "id:s", name = "Crime", providerOrder = 0, itemCount = count, shown = true, position = 0, sortMode = null),
        )
        for (s in 0 until count) {
            val name = "Northern Line $s"
            val seriesId = db.seriesImport().insert(
                listOf(
                    SeriesEntity(
                        key = seriesKey(s), sourceId = SOURCE, providerId = "s$s", groupId = groupId, name = name, sortName = SortNames.of(name),
                        year = 2020, rating = "8.1", ratingX10 = 81, posterUrl = null, backdropUrl = null, plot = "A fictional series.",
                        providerOrder = s, genre = null, workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
                    ),
                ),
            ).single()
            db.episodeImport().insert(
                (1..episodes).map { n ->
                    EpisodeEntity(
                        key = episodeKey(s, n), seriesId = seriesId, sourceId = SOURCE, providerId = "s$s-e$n", season = 1, number = n,
                        name = "Chapter $n", streamUrlEnc = graph.data.cipher.encrypt(stream(10_000 + s * 100 + n)), plot = null,
                        durationSeconds = 20, thumbnailUrl = null, contentHash = 1, generation = 1,
                    )
                },
            )
        }
    }

    const val SOURCE: String = "vod-0"

    fun key(id: Int): String = "vod:movie:$SOURCE:$id"

    fun seriesKey(s: Int): String = "series:$SOURCE:s$s"

    fun episodeKey(s: Int, n: Int): String = "vod:episode:$SOURCE:s$s-e$n"
}
