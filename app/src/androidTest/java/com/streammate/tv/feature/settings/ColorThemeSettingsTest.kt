package com.streammate.tv.feature.settings

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.ColorTheme
import com.streammate.tv.app.MainActivity
import com.streammate.tv.testing.ClearAppStateRule
import com.streammate.tv.testing.awaitUntil
import com.streammate.tv.testing.openSettingsFromTheEmptyGuide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ColorThemeSettingsTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(compose)

    private val preferences get() = AppPreferencesRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun selectingThemesRecolorsTheAppReturnsFocusAndSurvivesRecreation() {
        openGeneral()
        ColorTheme.entries.filter { it != ColorTheme.ORIGINAL }.forEach { theme ->
            compose.onNodeWithTag("settings-color-theme").performClick()
            compose.onNodeWithTag("settings-color-theme-${theme.storedValue}").performClick()
            compose.awaitUntil {
                runBlocking { preferences.preferences.first().colorTheme == theme } &&
                    compose.onAllNodes(hasTestTag("settings-color-theme") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            assertScreenColor(theme)
        }
        compose.activityRule.scenario.recreate()
        openGeneral()
        assertScreenColor(ColorTheme.CYBER_PLUM)
        compose.onNodeWithTag("settings-color-theme").performClick()
        compose.onNodeWithTag("settings-color-theme-cyber_plum").assertIsFocused()
        saveReviewImage(compose.onNodeWithTag("settings-picker").captureToImage(), "picker")
        compose.onNodeWithTag("settings-color-theme-original").performClick()
        compose.awaitUntil { runBlocking { preferences.preferences.first().colorTheme == ColorTheme.DEFAULT } }
    }

    @Test
    fun backingOutOfThePickerLeavesTheThemeAndFocusIntact() {
        openGeneral()
        compose.onNodeWithTag("settings-color-theme").performClick()
        compose.awaitUntil {
            compose.onAllNodes(hasTestTag("settings-color-theme-original") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        // Back belongs to the Android dialog window, not the Compose key dispatcher.
        pressBack()
        compose.awaitUntil {
            compose.onAllNodes(hasTestTag("settings-color-theme") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settings-picker").assertDoesNotExist()
        assertEquals(ColorTheme.DEFAULT, runBlocking { preferences.preferences.first().colorTheme })
    }

    private fun openGeneral() {
        compose.awaitUntil(timeoutMillis = 15_000) { compose.onAllNodesWithTag("home-live").fetchSemanticsNodes().isNotEmpty() }
        compose.openSettingsFromTheEmptyGuide()
        compose.onNodeWithTag("settings-section-general").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-color-theme"))
    }

    private fun assertScreenColor(theme: ColorTheme) {
        val image = compose.onRoot().captureToImage()
        saveReviewImage(image, theme.storedValue)
        val pixels = image.toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height - 2]
        val expected = theme.palette.backgroundBottom
        assertEquals("$theme red", expected.red, actual.red, 0.02f)
        assertEquals("$theme green", expected.green, actual.green, 0.02f)
        assertEquals("$theme blue", expected.blue, actual.blue, 0.02f)
    }

    private fun saveReviewImage(image: ImageBitmap, name: String) {
        if (InstrumentationRegistry.getArguments().getString("themeScreenshots") != "true") return
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "color-theme-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
