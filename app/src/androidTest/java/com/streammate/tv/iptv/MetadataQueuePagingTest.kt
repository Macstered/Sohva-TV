package com.streammate.tv.iptv

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.CatalogueMetadataWorkEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.database.VodSeriesEntity
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.repository.CatalogueRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The metadata queue is rebuilt from the catalogue a page at a time. Pages
 * must cover every title once, keep the rows that need no change, and sweep
 * the titles that left the catalogue.
 */
@RunWith(AndroidJUnit4::class)
class MetadataQueuePagingTest {

    @Test
    fun pagesCoverEveryTitleAndSweepTheOnesThatLeft() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        try {
            db.guideDao().upsertSourceState(IptvSourceStateEntity("one", "one", "xtream", true, 1, 0, 1))
            db.catalogueDao().upsertMovies((1..5).map { movie("first", "m$it", "Film $it") })
            db.catalogueDao().upsertSeries(listOf(series("first", "s1", "Show 1"), series("first", "s2", "Show 2")))
            db.catalogueDao().activateCatalogueSnapshot("one", "first", 5, 2)
            val catalogue = CatalogueRepository(db.catalogueDao())
            val metadata = MetadataRepository(db.metadataDao(), SecretSettingsStore(context, TestCipher), OkHttpClient())

            val pages = catalogue.catalogueMetadataCandidatePages(pageSize = 2).toList()
            assertEquals(listOf(2, 2, 1, 2), pages.map { it.size })

            val pending = metadata.synchronizeCatalogueMetadataWork(catalogue.catalogueMetadataCandidatePages(pageSize = 2))
            assertEquals(7, pending)
            assertEquals(7, db.metadataDao().catalogueMetadataWorkCount())

            // A title looked up and left in retry keeps its place through the next rebuild.
            val retry = db.metadataDao().catalogueMetadataWorkFor(listOf("vod:movie:one:m3")).single()
                .copy(state = CatalogueMetadataWorkEntity.STATE_RETRY, attemptCount = 2, nextAttemptAtEpochMillis = 99L)
            db.metadataDao().upsertCatalogueMetadataWork(listOf(retry))

            // The next import drops two films and one show.
            db.catalogueDao().upsertMovies(listOf("m1", "m3", "m5").map { movie("second", it, "Film ${it.drop(1)}") })
            db.catalogueDao().upsertSeries(listOf(series("second", "s2", "Show 2")))
            db.catalogueDao().activateCatalogueSnapshot("one", "second", 3, 1)
            metadata.synchronizeCatalogueMetadataWork(catalogue.catalogueMetadataCandidatePages(pageSize = 2))

            val rows = db.metadataDao().catalogueMetadataWork().associateBy { it.contentKey }
            assertEquals(
                setOf("vod:movie:one:m1", "vod:movie:one:m3", "vod:movie:one:m5", "series:one:s2"),
                rows.keys,
            )
            val kept = rows.getValue("vod:movie:one:m3")
            assertEquals(CatalogueMetadataWorkEntity.STATE_RETRY, kept.state)
            assertEquals(2, kept.attemptCount)
            assertEquals(99L, kept.nextAttemptAtEpochMillis)
        } finally {
            db.close()
        }
    }

    private fun movie(snapshot: String, id: String, name: String) = VodMovieEntity(
        "one", snapshot, id, name, name.lowercase(), "Films", posterUrl = null, encryptedStreamUrl = "encrypted",
        year = 2001, rating = null, plot = null, organizationGroupKey = "id:$id",
    )

    private fun series(snapshot: String, id: String, name: String) = VodSeriesEntity(
        "one", snapshot, id, name, name.lowercase(), "Shows", posterUrl = null, backdropUrl = null,
        year = 2001, rating = null, plot = null,
    )

    private object TestCipher : SecretCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
    }
}
