package com.streammate.tv.feature.home

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.GuideRepository
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hero carries artwork, and its title, metadata and buttons sit on top of
 * it.
 *
 * Checks the one thing that can be read off pixels: that the side of the hero
 * the text lives on stays dark enough for white lettering, whatever the
 * artwork behind it happens to be. Whether the balance looks good is a
 * judgement no assertion makes - that part still needs eyes on a television.
 */
@RunWith(AndroidJUnit4::class)
class HomeTileBackdropTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StreamMateDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun theHeroKeepsAReadableGroundUnderItsText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            StreamMateTheme {
                HomeScreen(
                    guideRepository = GuideRepository(database.guideDao()),
                    catalogueRepository = CatalogueRepository(database.catalogueDao()),
                    preferencesRepository = AppPreferencesRepository(context),
                    sportsEvents = emptyList(),
                    onLiveTv = {},
                    onSportMate = {},
                    onMovies = {},
                    onSeries = {},
                    onSearch = {},
                    onSettings = {},
                    onPlayChannel = {},
                    onPlayVod = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()

        val image = composeRule.onRoot().captureToImage()

        // Sample across the band the hero's metadata and description sit in,
        // and only across the column they occupy: the artwork is deliberately
        // left visible on the far side, where no lettering goes.
        val pixels = image.toPixelMap()
        val textRow = (pixels.height * TEXT_BAND).toInt().coerceIn(0, pixels.height - 1)
        val textEdge = (pixels.width * TEXT_COLUMN).toInt().coerceIn(1, pixels.width)
        // Median, not maximum. The text itself is white, so the brightest pixel
        // in this band is always the lettering; what matters is the ground it
        // sits on, which is what most of the row is.
        val ground = (0 until textEdge step 4)
            .map { x -> pixels[x, textRow] }
            .map { maxOf(it.red, it.green, it.blue) }
            .sorted()
        val median = ground[ground.size / 2]

        assertTrue(
            "the ground under the hero text sits at ${(median * 100).toInt()}% brightness, " +
                "which is too light for white lettering",
            median < 0.55f,
        )
    }

    private companion object {
        /** Roughly where the hero's metadata sits, as a fraction of screen height. */
        const val TEXT_BAND = 0.34f

        /** How far across the screen the hero's text column reaches. */
        const val TEXT_COLUMN = 0.56f
    }
}
