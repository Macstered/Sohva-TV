package com.streammate.tv.feature.catalogue

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodEpisodeEntity
import com.streammate.tv.core.database.VodSeriesEntity
import com.streammate.tv.core.security.MetadataSettings
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.VodSeries
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesSeasonFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val contentVisible = androidx.compose.runtime.mutableStateOf(true)
    private lateinit var database: StreamMateDatabase
    private lateinit var repository: CatalogueRepository
    private lateinit var metadataRepository: MetadataRepository

    @Before
    fun createSeries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        database.guideDao().upsertSourceState(
            IptvSourceStateEntity("source", "Xtream", "XTREAM", true, 1, 0, 1_000L),
        )
        database.catalogueDao().upsertSeries(
            listOf(
                VodSeriesEntity(
                    sourceId = "source",
                    snapshotId = "catalogue",
                    seriesId = "series",
                    name = "Seven Seasons",
                    normalizedName = "seven seasons",
                    categoryName = "Series",
                    posterUrl = null,
                    backdropUrl = null,
                    year = 2026,
                    rating = null,
                    plot = null,
                ),
            ),
        )
        database.catalogueDao().activateCatalogueSnapshot("source", "catalogue", 1, 1_000L)
        database.catalogueDao().replaceSeriesEpisodes(
            "source",
            "series",
            (1..7).map { season ->
                VodEpisodeEntity(
                    sourceId = "source",
                    seriesId = "series",
                    episodeId = "s$season-e1",
                    seasonNumber = season,
                    episodeNumber = 1,
                    name = "Episode 1",
                    encryptedStreamUrl = "encrypted-$season",
                    plot = null,
                    durationSeconds = 1_200,
                )
            },
        )
        repository = CatalogueRepository(database.catalogueDao())
        val settings = SecretSettingsStore(context, TestCipher).also {
            it.saveMetadataSettings(MetadataSettings())
        }
        metadataRepository = MetadataRepository(database.metadataDao(), settings, OkHttpClient())
    }

    @After
    fun closeDatabase() {
        // Dispose collectors before their Room connection is closed; JUnit @After precedes rule disposal.
        composeRule.runOnIdle { contentVisible.value = false }
        composeRule.waitForIdle()
        database.close()
    }

    @Test
    fun seventhSeasonHandsFocusToEpisodesAndCanReturnToEarlierSeasons() {
        composeRule.setContent {
            if (contentVisible.value) StreamMateTheme {
                SeriesDetailsScreen(
                    series = VodSeries(
                        sourceId = "source",
                        seriesId = "series",
                        name = "Seven Seasons",
                        categoryName = "Series",
                        posterUrl = null,
                        backdropUrl = null,
                        year = 2026,
                        rating = null,
                        plot = null,
                    ),
                    repository = repository,
                    metadataRepository = metadataRepository,
                    onRefreshEpisodes = { Result.success(0) },
                    onPlay = { _, _ -> },
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("series-season-6").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("series-season-6").performClick()
        awaitFocused("series-episode-6-1")
        pressFocused("series-episode-6-1", Key.DirectionUp)
        composeRule.onNodeWithTag("series-season-6").assertIsFocused()
        pressFocused("series-season-6", Key.DirectionRight)
        composeRule.onNodeWithTag("series-season-7").assertIsFocused()
        pressFocused("series-season-7", Key.Enter)
        awaitFocused("series-episode-7-1")

        pressFocused("series-episode-7-1", Key.DirectionUp)
        repeat(6) {
            val season = 7 - it
            pressFocused("series-season-$season", Key.DirectionLeft)
        }
        composeRule.onNodeWithTag("series-season-1").assertIsFocused()
        pressFocused("series-season-1", Key.Enter)
        awaitFocused("series-episode-1-1")
    }

    private fun pressFocused(tag: String, key: Key) {
        composeRule.onNodeWithTag(tag).performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun awaitFocused(tag: String) {
        composeRule.waitUntil(3_000) {
            runCatching { composeRule.onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
        composeRule.onNodeWithTag(tag).assertIsFocused()
    }

    private object TestCipher : SecretCipher {
        override fun encrypt(plainText: String): String = "test:$plainText"
        override fun decrypt(encoded: String): String = encoded.removePrefix("test:")
    }
}
