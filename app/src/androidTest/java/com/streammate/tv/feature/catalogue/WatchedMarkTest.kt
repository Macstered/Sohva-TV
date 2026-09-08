package com.streammate.tv.feature.catalogue

import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The film page's watched mark: set by hand, shown, and taken back. */
@RunWith(AndroidJUnit4::class)
class WatchedMarkTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase

    @After
    fun closeLibrary() = database.close()

    @Test
    fun theFilmPageMarksAndUnmarks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository: CatalogueRepository
        val movie = runBlocking {
            database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
            database.guideDao().upsertSourceState(IptvSourceStateEntity("xtream", "Xtream", "XTREAM", true, 1, 0, 1L))
            database.catalogueDao().upsertMovies(listOf(
                VodMovieEntity("xtream", "snap", "1", "The Matrix (1999)", "the matrix", "Films", posterUrl = null,
                    encryptedStreamUrl = "encrypted", year = 1999, rating = null, plot = null, organizationGroupKey = "id:1"),
            ))
            database.catalogueDao().activateCatalogueSnapshot("xtream", "snap", 1, 1L)
            repository = CatalogueRepository(database.catalogueDao())
            repository.observeMovies().first().single()
        }
        composeRule.setContent {
            StreamMateTheme {
                MovieDetailsScreen(
                    movie = movie,
                    repository = repository,
                    metadataRepository = MetadataRepository(database.metadataDao(), SecretSettingsStore(context, TestCipher), OkHttpClient()),
                    onPlay = { _, _ -> },
                    onOpenMovie = {},
                    onBack = {},
                )
            }
        }
        composeRule.awaitUntil(timeoutMillis = 10_000) { texts("Mark as watched") == 1 }
        composeRule.onNodeWithTag("movie-details-mark-watched").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) { texts("Mark as unwatched") == 1 }
        runBlocking { assertTrue(requireNotNull(repository.progress(movie.contentKey)).completed) }

        composeRule.onNodeWithTag("movie-details-mark-watched").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) { texts("Mark as watched") == 1 }
        runBlocking { assertNull(repository.progress(movie.contentKey)) }
        assertNotNull(database)
    }

    private fun texts(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private object TestCipher : SecretCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
    }
}
