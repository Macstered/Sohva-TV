package com.sohva.tv.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.gallery.GallerySection
import com.sohva.tv.ui.design.gallery.GallerySectionContent
import com.sohva.tv.ui.design.ground.GroundCache
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.theme.Palettes
import com.sohva.tv.ui.design.theme.Sohva
import com.sohva.tv.ui.design.theme.SohvaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of every gallery page in every theme, on the shared ground, at a 1080p TV
 * (960 × 540 dp at xhdpi). Record with `recordRoborazziDebug`, check with `verifyRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-xhdpi")
class GalleryScreenshotTest {
    // A small tolerance, so goldens recorded on one machine verify on another.
    private val compare = RoborazziOptions.CompareOptions(changeThreshold = 0.001f)
    private val fullSize = RoborazziOptions(compareOptions = compare)
    private val halfSize = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5), compareOptions = compare)

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everySectionInEveryTheme() {
        var theme by mutableStateOf(ColorThemeId.ORIGINAL)
        var section by mutableStateOf(GallerySection.TOKENS)
        compose.setContent {
            CompositionLocalProvider(LocalRenderDispatcher provides Dispatchers.Unconfined) {
                SohvaTheme(theme, reducedMotion = true) {
                    ScreenBackground {
                        // The options sheet places itself in the safe area; the other pages are padded here.
                        val inset = if (section == GallerySection.SHEET) Modifier else Modifier.padding(
                            horizontal = Sohva.spacing.safeHorizontal,
                            vertical = Sohva.spacing.safeVertical,
                        )
                        Box(Modifier.fillMaxSize().then(inset)) { GallerySectionContent(section) }
                    }
                }
            }
        }
        for (t in ColorThemeId.entries) {
            // The ground is rendered before the frame, as the start-up sequence does.
            runBlocking { GroundCache.prepare(Palettes.of(t), IntSize(1920, 1080), Dispatchers.Unconfined) }
            for (s in GallerySection.entries) {
                theme = t
                section = s
                compose.waitForIdle()
                // Original at full 1080p, to compare with beta 23's screenshots; the others at half size.
                val options = if (t == ColorThemeId.ORIGINAL) fullSize else halfSize
                compose.onRoot().captureRoboImage("src/test/screenshots/${s.name.lowercase()}-${t.id}.png", options)
            }
        }
    }
}
