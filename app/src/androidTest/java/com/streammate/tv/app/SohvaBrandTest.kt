package com.streammate.tv.app

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.R
import com.streammate.tv.feature.common.SohvaSportBrand
import com.streammate.tv.feature.common.SohvaTvBrand
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SohvaBrandTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bothBrandsExposeTheirFullNamesAndSportKeepsItsAccent() {
        composeRule.setContent {
            StreamMateTheme {
                Column {
                    SohvaTvBrand(Modifier.testTag("tv-brand"))
                    SohvaSportBrand(Modifier.testTag("sport-brand"))
                }
            }
        }
        composeRule.onNodeWithTag("tv-brand").assertTextEquals("Sohva TV")
        composeRule.onNodeWithTag("sport-brand").assertTextEquals("Sohva Sport")

        val pixels = composeRule.onNodeWithTag("sport-brand").captureToImage().toPixelMap()
        var orange = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > 0.85f && color.green in 0.35f..0.65f && color.blue < 0.40f) orange++
            }
        }
        assertTrue("Sohva Sport should retain its orange accent", orange > 100)
    }

    @Test
    fun launcherAndBothLocalesUseTheNewNames() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Sohva TV", target.applicationInfo.loadLabel(target.packageManager).toString())
        for (language in listOf("en", "fi")) {
            val configuration = Configuration(target.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localized = target.createConfigurationContext(configuration)
            assertEquals("Sohva TV", localized.getString(R.string.app_name))
            assertEquals("Sohva Sport", localized.getString(R.string.home_sportmate))
            assertEquals("Sohva Sport", localized.getString(com.streammate.tv.iptv.R.string.settings_section_sport))
            assertTrue(localized.getString(R.string.about_noncommercial).startsWith("Sohva TV"))
        }
    }
}
