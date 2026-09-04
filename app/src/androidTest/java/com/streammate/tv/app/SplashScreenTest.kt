package com.streammate.tv.app

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sohva TV uses the same accessible white/cyan wordmark at launch and in headers. */
@RunWith(AndroidJUnit4::class)
class SplashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theLaunchScreenDrawsTheLogoAndItsWordmarkColours() {
        composeRule.setContent { StreamMateTheme { StreamMateLaunchScreen() } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("launch-brand").assertTextEquals("Sohva TV")

        val wordmark = composeRule.onNodeWithTag("launch-brand").captureToImage().toPixelMap()
        var white = 0
        var cyan = 0
        for (y in 0 until wordmark.height) {
            for (x in 0 until wordmark.width) {
                val c = wordmark[x, y]
                val r = c.red
                val g = c.green
                val b = c.blue
                if (r > 0.90f && g > 0.90f && b > 0.90f) white++
                if (r < 0.35f && g > 0.70f && b > 0.70f) cyan++
            }
        }
        assertTrue("no white lettering found in the wordmark", white > 200)
        assertTrue("no cyan TV lettering found in the wordmark", cyan > 200)

        // Inspect the mark independently so the wordmark cannot satisfy this assertion.
        val splash = composeRule.onNodeWithTag("launch-mark").captureToImage().toPixelMap()
        var blue = 0
        for (y in 0 until splash.height step 2) {
            for (x in 0 until splash.width step 2) {
                val c = splash[x, y]
                if (c.blue > 0.55f && c.blue - c.red > 0.20f) blue++
            }
        }
        assertTrue("the logo mark does not appear to be drawn", blue > 400)
    }
}
