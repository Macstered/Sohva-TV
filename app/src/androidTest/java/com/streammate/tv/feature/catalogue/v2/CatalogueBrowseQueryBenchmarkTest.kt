package com.streammate.tv.feature.catalogue.v2

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.OrganizationChange
import com.streammate.tv.core.model.*
import com.streammate.tv.iptv.repository.OrganizationRepository
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.app.CataloguePreferredCopy
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.iptv.repository.CatalogueRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Device evidence for deciding whether V2 needs a separate paged read model. */
@RunWith(AndroidJUnit4::class)
class CatalogueBrowseQueryBenchmarkTest {
    private lateinit var database: StreamMateDatabase

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity(
                sourceId = SOURCE_ID,
                name = "Benchmark provider",
                type = "xtream",
                enabled = true,
                connectionLimit = 1,
                priority = 0,
                updatedAtEpochMillis = 1,
            ),
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun indexedEightThousandTitleProviderGroupHasBoundedDeliveryTime() = runBlocking {
        val dao = database.catalogueDao()
        dao.upsertMovies(
            List(TOTAL_TITLES) { index ->
                val targetGroup = index < TARGET_GROUP_TITLES
                VodMovieEntity(
                    sourceId = SOURCE_ID,
                    snapshotId = SNAPSHOT_ID,
                    movieId = index.toString(),
                    name = "Movie ${index.toString().padStart(5, '0')}",
                    normalizedName = "movie ${index.toString().padStart(5, '0')}",
                    categoryName = if (targetGroup) TARGET_GROUP else "Other",
                    posterUrl = "https://example.invalid/$index.jpg",
                    encryptedStreamUrl = "encrypted-$index",
                    year = 2000 + index % 25,
                    rating = "7.5",
                    plot = null,
                )
            },
        )
        dao.activateCatalogueSnapshot(SOURCE_ID, SNAPSHOT_ID, TOTAL_TITLES, 2)
        val organization = OrganizationRepository(database.organizationDao())
        val groupKey = organizationGroupKey(TARGET_GROUP)
        organization.change(listOf(
            OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, SOURCE_ID, groupKey), sort = LibrarySort.MANUAL, changeSort = true),
            OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, SOURCE_ID, organizationGroupKey("Other")), enabled = false, changeEnabled = true),
        ) + List(TARGET_GROUP_TITLES) { index ->
            OrganizationChange(OrganizationKey(LibraryRoom.MOVIES, SOURCE_ID, groupKey, "vod:movie:$SOURCE_ID:$index"),
                enabled = index % 10 != 0, changeEnabled = true, position = (TARGET_GROUP_TITLES - index).toLong(), changePosition = true)
        })
        val repository = CatalogueRepository(dao, organization = organization)

        val request = CatalogueBrowseRequest(
            CatalogueMode.MOVIES,
            CatalogueBrowsePartition.PlaylistGroup(TARGET_GROUP),
        )
        val dataSource = RepositoryCatalogueBrowseDataSource(repository)
        val started = SystemClock.elapsedRealtimeNanos()
        val entries = dataSource.observeEntries(request).first()
        val queryMillis = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
        val deriver = CatalogueBrowseDeriver(CataloguePreferredCopy.NONE)
        val deriveStarted = SystemClock.elapsedRealtimeNanos()
        val derived = deriver.derive(request, entries)
        val deriveMillis = (SystemClock.elapsedRealtimeNanos() - deriveStarted) / 1_000_000
        val warmStarted = SystemClock.elapsedRealtimeNanos()
        deriver.derive(request, entries)
        val warmDeriveMillis = (SystemClock.elapsedRealtimeNanos() - warmStarted) / 1_000_000
        val elapsedMillis = queryMillis + deriveMillis

        Log.i(
            BENCHMARK_TAG,
            "query_items=${entries.size} query_ms=$queryMillis derive_ms=$deriveMillis " +
                "warm_derive_ms=$warmDeriveMillis total_ms=$elapsedMillis",
        )
        assertEquals(TARGET_GROUP_TITLES * 9 / 10, entries.size)
        assertEquals(TARGET_GROUP_TITLES * 9 / 10, derived.entries.size)
        // This is a regression tripwire, not the final product budget. The
        // measured value decides whether the next phase keeps full slices.
        assertTrue("Indexed V2 slice took ${elapsedMillis}ms", elapsedMillis < 5_000)

        // Saved filters use a selected database query, not a whole-library
        // in-memory projection for every custom row in the rail. This is a
        // different query on the same warm DB, including all organization rules.
        val custom = CatalogueCustomGroup("rated", "Rated", fromYear = 2000, toYear = 2024, minRating = 7.5)
        dataSource.setCustomGroups(listOf(custom))
        val customRequest = CatalogueBrowseRequest(CatalogueMode.MOVIES, CatalogueBrowsePartition.CustomGroup(custom.id))
        val customStarted = SystemClock.elapsedRealtimeNanos()
        val customEntries = dataSource.observeEntries(customRequest).first()
        val customDerived = deriver.derive(customRequest, customEntries)
        val customMillis = (SystemClock.elapsedRealtimeNanos() - customStarted) / 1_000_000
        Log.i(BENCHMARK_TAG, "custom_items=${customEntries.size} custom_query_and_derive_ms=$customMillis")
        assertEquals(TARGET_GROUP_TITLES * 9 / 10, customEntries.size)
        assertEquals(TARGET_GROUP_TITLES * 9 / 10, customDerived.entries.size)
        assertTrue("Selected custom slice took ${customMillis}ms", customMillis < 5_000)
    }

    private companion object {
        const val BENCHMARK_TAG = "CatalogueV2Bench"
        const val SOURCE_ID = "benchmark"
        const val SNAPSHOT_ID = "snapshot"
        const val TARGET_GROUP = "Large group"
        const val TOTAL_TITLES = 12_000
        const val TARGET_GROUP_TITLES = 8_000
    }
}
