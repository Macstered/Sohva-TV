package com.sohva.tv.app

import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.text.SortNames
import kotlinx.coroutines.runBlocking

/**
 * A fictional film library written straight into the app's database: one Xtream-like source
 * `vod-0` with provider groups of [perGroup] films each. Film n of group g is "<Word> <number>",
 * named so A–Z order is the numbering order within a group.
 */
object LibraryFixture {
    fun seed(graph: AppGraph, groups: List<String> = listOf("Drama", "Comedy"), perGroup: Int = 30, posters: Boolean = false) {
        val db = graph.data.database
        val now = System.currentTimeMillis()
        val sourceId = "vod-0"
        var index = 0
        for ((g, groupName) in groups.withIndex()) {
            val groupId = db.groupImport().insert(
                ContentGroupEntity(
                    sourceId = sourceId, room = "MOVIES", groupKey = "id:$g", name = groupName, providerOrder = g,
                    itemCount = perGroup, shown = true, position = g, sortMode = null,
                ),
            )
            val films = (0 until perGroup).map { n ->
                val name = "$groupName %04d".format(n)
                val id = index++
                MovieEntity(
                    key = "vod:movie:$sourceId:$id", sourceId = sourceId, providerId = "$id", groupId = groupId, name = name,
                    sortName = SortNames.of(name), year = 2000 + n % 25, rating = "7.${n % 10}", ratingX10 = 70 + n % 10,
                    posterUrl = if (posters) "http://192.0.2.20/poster/$id.jpg" else null,
                    streamUrlEnc = graph.data.cipher.encrypt("http://192.0.2.20/movie/$id.mp4"), plot = "A fictional film.",
                    providerOrder = n, genre = null, workKey = null, primaryCopy = true, visible = true, itemPosition = null,
                    contentHash = 1, generation = 1,
                )
            }
            films.chunked(2_000).forEach { db.movieImport().insert(it) }
        }
        db.sourceStatus().upsert(SourceStatusEntity(sourceId, "catalogue", "success", now, now, null, null, null, index, 0, 1, null, null))
        runBlocking { db.sources().upsert(SourceEntity(sourceId, "Fixture VOD", "XTREAM", true, 0, 1, "VOD", 0, now, now)) }
    }

    fun key(id: Int): String = "vod:movie:vod-0:$id"
}
