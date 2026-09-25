package com.sohva.tv.measure.fixture

import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.SourceStatusEntity
import com.sohva.tv.core.model.text.SortNames
import com.sohva.tv.core.model.vod.CopyClaimReader
import kotlinx.coroutines.runBlocking

/**
 * The owner-scale film library of spec 40 §11 "Performance": 200,000 films in 800 groups, one of
 * them ("0 | Blockbusters", first in A–Z) holding 40,000. Fictional names with the claims and quality
 * tags real providers put in titles; reserved addresses, not playable. Written once, in
 * transactions of 2,000.
 */
internal object FilmFixture {
    const val SOURCE: String = "owner-vod"
    const val FILMS: Int = 200_000
    const val GROUPS: Int = 800
    const val BIG_GROUP: Int = 40_000

    fun seed(db: SohvaDatabase): Int {
        if (runBlocking { db.sources().get(SOURCE) } != null) return 0
        val now = System.currentTimeMillis()
        var index = 0
        for (g in 0 until GROUPS) {
            val size = if (g == 0) BIG_GROUP else (FILMS - BIG_GROUP) / (GROUPS - 1) + if (g - 1 < (FILMS - BIG_GROUP) % (GROUPS - 1)) 1 else 0
            val groupName = if (g == 0) "0 | Blockbusters" else "${PREFIXES[g % PREFIXES.size]} | Films ${"%03d".format(g)}"
            val groupId = db.groupImport().insert(
                ContentGroupEntity(
                    sourceId = SOURCE, room = "MOVIES", groupKey = "id:$g", name = groupName, providerOrder = g,
                    itemCount = size, shown = true, position = g, sortMode = null,
                ),
            )
            var written = 0
            while (written < size) {
                val batch = ArrayList<MovieEntity>(BATCH)
                while (written < size && batch.size < BATCH) {
                    val n = index++
                    val year = 1950 + n % 75
                    val name = "${LEAD[n % LEAD.size]}${WORDS[n % WORDS.size]} ${WORDS[(n / 7) % WORDS.size]} $n ($year)${TAIL[n % TAIL.size]}"
                    val claims = CopyClaimReader.read(name)
                    batch += MovieEntity(
                        key = "vod:movie:$SOURCE:$n", sourceId = SOURCE, providerId = "$n", groupId = groupId, name = name,
                        sortName = SortNames.of(name), year = year, rating = "${5 + n % 5}.${n % 10}", ratingX10 = 50 + n % 50,
                        posterUrl = null, streamUrlEnc = "not-playable", plot = null, providerOrder = written,
                        qualityMask = claims.qualityMask, claimMask = claims.languageMask, pictureRank = claims.pictureRank,
                        genre = null, workKey = null, primaryCopy = true, visible = true, itemPosition = null, contentHash = 1, generation = 1,
                    )
                    written++
                }
                db.movieImport().insert(batch)
            }
        }
        db.sourceStatus().upsert(SourceStatusEntity(SOURCE, "catalogue", "success", now, now, null, null, null, index, 0, 1, null, null))
        runBlocking { db.sources().upsert(SourceEntity(SOURCE, "Owner-scale films", "M3U", true, 0, 1, "VOD", 0, now, now)) }
        return index
    }

    private const val BATCH = 2_000
    private val PREFIXES = listOf("FI", "SE", "EN", "NORDIC", "DE", "4K", "MULTI", "KIDS")
    private val LEAD = listOf("", "", "FIN | ", "", "[FI] ", "", "NORDIC - ", "")
    private val WORDS = listOf("Harbour", "Silent", "Northern", "Glass", "River", "Cedar", "Willow", "Lantern", "Orbit", "Meridian", "Tide", "Ember")
    private val TAIL = listOf("", " 1080p", "", " 4K HDR10", "", " [MULTI-SUBS]", "", " 720p")
}
