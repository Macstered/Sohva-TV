package com.sohva.tv.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.theme.Palettes
import com.sohva.tv.ui.design.theme.SohvaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Artwork must not cover a focus ring, in any theme; removing focus removes the ring. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-xhdpi")
class SurfaceFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theRingIsVisibleAboveOpaqueContentInEveryTheme() {
        var theme by mutableStateOf(ColorThemeId.ORIGINAL)
        var focused by mutableStateOf(false)
        val artwork = Color(0xFF203040)
        compose.setContent {
            SohvaTheme(theme, reducedMotion = true) {
                TvSurface(
                    onClick = {},
                    modifier = Modifier.size(230.dp, 130.dp).testTag("artwork"),
                    state = SurfaceState(showFocused = focused),
                    style = SurfaceStyle(focusRing = true, focusScale = 1f),
                ) {
                    Box(Modifier.fillMaxSize().background(artwork))
                }
            }
        }
        for (t in ColorThemeId.entries) {
            theme = t
            for (show in listOf(true, false)) {
                focused = show
                compose.waitForIdle()
                val image = compose.onNodeWithTag("artwork").captureToImage().asAndroidBitmap()
                val expected = if (show) Palettes.of(t).textPrimary.toArgb() else artwork.toArgb()
                assertEquals("$t focused=$show: left edge", expected, image.getPixel(2, image.height / 2))
                assertEquals("$t focused=$show: top edge", expected, image.getPixel(image.width / 2, 2))
                assertEquals("Artwork stays intact", artwork.toArgb(), image.getPixel(image.width / 2, image.height / 2))
            }
        }
    }
}
