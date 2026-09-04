package com.streammate.tv.feature.catalogue

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.CataloguePreferredCopy
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.CatalogueMetadataOverrideEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowseDeriver
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowsePartition
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowserStore
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowserV2Screen
import com.streammate.tv.feature.catalogue.v2.RepositoryCatalogueBrowseDataSource
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.testing.awaitUntil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Repeated real metadata writes must never replace a loaded wall with an empty message. */
class CatalogueWallReloadStabilityTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var database: StreamMateDatabase
    private val attached = mutableStateOf(true)
    private val derivationExecutor = Executors.newSingleThreadExecutor()
    private val derivationDispatcher = derivationExecutor.asCoroutineDispatcher()
    private val derivations = AtomicInteger()

    @Before
    fun createLibrary() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("source", "Xtream", "XTREAM", true, 1, 0, NOW),
        )
        database.catalogueDao().upsertMovies(listOf(VodMovieEntity(
            sourceId = "source", snapshotId = "catalogue-1", movieId = "1",
            name = ORIGINAL_TITLE, normalizedName = ORIGINAL_TITLE.lowercase(),
            categoryName = "Action", posterUrl = null, encryptedStreamUrl = "encrypted-1",
            year = 2024, rating = "7.0", plot = null,
        )))
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue-1", 1, NOW)
    }

    @After
    fun closeLibrary() {
        composeRule.runOnIdle { attached.value = false }
        composeRule.waitForIdle()
        derivationDispatcher.close()
        database.close()
    }

    @Test
    fun metadataInvalidationKeepsTheLastCompleteWallVisible() {
        composeRule.setContent {
            if (attached.value) StreamMateTheme {
                val scope = rememberCoroutineScope()
                val store = remember {
                    val dataSource = RepositoryCatalogueBrowseDataSource(CatalogueRepository(database.catalogueDao()))
                    val deriver = CatalogueBrowseDeriver(CataloguePreferredCopy.NONE, derivationDispatcher)
                    CatalogueBrowserStore(
                        CatalogueMode.MOVIES, dataSource, scope,
                        initialPartition = CatalogueBrowsePartition.PlaylistGroup("Action"),
                        deriveEntries = { request, entries ->
                            derivations.incrementAndGet()
                            deriver.derive(request, entries)
                        },
                    )
                }
                val state by store.state.collectAsState()
                CatalogueBrowserV2Screen(state, onSelectPartition = store::selectPartition, onOpenEntry = {}, onBack = {})
            }
        }
        composeRule.awaitUntil { tagShowing("catalogue-v2-wall-current") && textShowing(ORIGINAL_TITLE) }

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        derivationExecutor.execute {
            entered.countDown()
            release.await(15, TimeUnit.SECONDS)
        }
        assertTrue("derivation dispatcher did not pause", entered.await(5, TimeUnit.SECONDS))
        val initialDerivations = derivations.get()
        try {
            repeat(3) { index ->
                runBlocking {
                    database.metadataDao().upsertCatalogueMetadataOverride(
                        CatalogueMetadataOverrideEntity(
                            contentKey = CONTENT_KEY, providerPosterUrl = null,
                            replacementPosterUrl = null, replaceProviderPoster = false,
                            replacementTitle = if (index == 2) REPLACEMENT_TITLE else "Intermediate " + index,
                            externalId = "tmdb:1", genresVersion = CatalogueGenre.VERSION,
                            updatedAtEpochMillis = NOW + index + 1,
                        ),
                    )
                }
                if (index == 0) composeRule.awaitUntil { derivations.get() > initialDerivations }
                composeRule.waitForIdle()
                assertTrue("the previous wall disappeared during derivation", textShowing(ORIGINAL_TITLE))
                assertFalse(textShowing("Intermediate " + index))
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                assertFalse(textShowing(context.getString(com.streammate.tv.iptv.R.string.catalogue_v2_empty_group)))
            }
        } finally {
            release.countDown()
        }
        composeRule.awaitUntil { tagShowing("catalogue-v2-wall-current") && textShowing(REPLACEMENT_TITLE) }
    }

    private fun tagShowing(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun textShowing(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val NOW = 1_000L
        const val CONTENT_KEY = "vod:movie:source:1"
        const val ORIGINAL_TITLE = "Original Action Film"
        const val REPLACEMENT_TITLE = "Enriched Action Film"
    }
}
