package com.streammate.tv.feature.catalogue

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.testing.awaitUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The wall shows one card for a film two playlists carry. This is where the
 * other copy is offered rather than hidden - which copy is better varies by
 * title, and the app is in no position to be certain.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueCopyChoiceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase
    private val played = mutableListOf<Pair<String, Long>>()

    @After
    fun closeLibrary() = database.close()

    @Test
    fun bothCopiesAreOfferedAndSayWhatTheyClaim() {
        showFilm(carriedTwice = true)

        assertEquals(1, textCount("Xtream"))
        assertEquals(1, textCount("M3U"))
        // What each copy claims, asserted on the terms rather than on the
        // translated language words, so this holds whichever language the
        // device is set to. Substring, because a card says its language and its
        // picture in one line; twice for 4K UHD, since the facts row above
        // already carries it for the copy the page was opened as.
        assertEquals(2, claimCount("4K UHD"))
        assertEquals(1, claimCount("1080p"))
    }

    @Test
    fun choosingACopyPlaysThatCopy() {
        showFilm(carriedTwice = true)

        composeRule.onNodeWithTag("movie-details-version-m3u").performClick()

        assertEquals(listOf(SECOND_COPY), played.map { it.first })
    }

    /**
     * A film carried once is not a film with versions, and a row saying so
     * would read as a fault rather than as information.
     */
    @Test
    fun aFilmCarriedOnceOffersNothingToChooseBetween() {
        showFilm(carriedTwice = false)

        assertTrue(
            composeRule.onAllNodesWithTag("movie-details-version-xtream")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    private fun showFilm(carriedTwice: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val movie = runBlocking {
            database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
            database.guideDao().upsertSourceState(
                IptvSourceStateEntity("xtream", "Xtream", "XTREAM", true, 1, 0, NOW),
            )
            database.catalogueDao().upsertMovies(
                listOf(movie("xtream", "1", "FIN | The Matrix (1999) 4K")),
            )
            database.catalogueDao().activateCatalogueSnapshot("xtream", "catalogue-xtream", 1, NOW)
            if (carriedTwice) {
                database.guideDao().upsertSourceState(
                    IptvSourceStateEntity("m3u", "M3U", "M3U", true, 1, 1, NOW),
                )
                database.catalogueDao().upsertMovies(
                    listOf(movie("m3u", "2", "The Matrix 1999 [MULTI-SUBS] 1080p")),
                )
                database.catalogueDao().activateCatalogueSnapshot("m3u", "catalogue-m3u", 1, NOW)
            }
            CatalogueRepository(database.catalogueDao())
                .observeMovies()
                .first()
                .first { it.sourceId == "xtream" }
        }
        composeRule.setContent {
            StreamMateTheme {
                MovieDetailsScreen(
                    movie = movie,
                    repository = CatalogueRepository(database.catalogueDao()),
                    metadataRepository = MetadataRepository(
                        database.metadataDao(),
                        SecretSettingsStore(context, TestCipher),
                        OkHttpClient(),
                    ),
                    onPlay = { contentKey, position -> played += contentKey to position },
                    onOpenMovie = {},
                    onBack = {},
                )
            }
        }
        composeRule.awaitUntil { textCount("The Matrix") > 0 }
        if (carriedTwice) {
            composeRule.awaitUntil {
                composeRule.onAllNodesWithTag("movie-details-version-m3u")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    private fun movie(sourceId: String, movieId: String, name: String) = VodMovieEntity(
        sourceId = sourceId,
        snapshotId = "catalogue-$sourceId",
        movieId = movieId,
        name = name,
        normalizedName = name.lowercase(),
        categoryName = "Drama",
        posterUrl = null,
        encryptedStreamUrl = "encrypted-$sourceId-$movieId",
        year = 1999,
        rating = null,
        plot = null,
    )

    /** A card says its language and its picture in one line. */
    private fun claimCount(text: String) = composeRule
        .onAllNodesWithText(text, substring = true, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size

    private fun textCount(text: String) = composeRule
        .onAllNodesWithText(text, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size

    private object TestCipher : SecretCipher {
        override fun encrypt(value: String) = value

        override fun decrypt(value: String) = value
    }

    private companion object {
        const val NOW = 1_000L
        const val SECOND_COPY = "vod:movie:m3u:2"
    }
}
