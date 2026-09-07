package com.streammate.tv.feature.settings

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.MainActivity
import com.streammate.tv.testing.ClearAppStateRule
import com.streammate.tv.testing.awaitUntil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

/**
 * Not a test of anything: writes one screenshot per settings section to the
 * app's private files directory, for a design review of the screen. Pull
 * them with `adb exec-out run-as <package> tar -C files -cf - settings-review`.
 */
class SettingsScreenshotDumpTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(composeRule)

    @Test
    fun dumpEverySection() {
        composeRule.awaitUntil(timeoutMillis = 15_000) {
            runCatching { composeRule.onAllNodesWithTag("home-live").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "settings-review").apply { mkdirs() }
        fun shot(name: String) {
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        composeRule.onNodeWithTag("home-live").performClick()
        composeRule.onNodeWithTag("guide-empty-settings").performClick()
        shot("01-sources")
        composeRule.onNodeWithTag("source-add-m3u").performClick()
        shot("02-sources-add-m3u")
        listOf("general", "playback", "remote", "metadata", "sport", "parental", "backup", "about").forEachIndexed { index, section ->
            composeRule.onNodeWithTag("settings-section-$section").performClick()
            shot("${(index + 3).toString().padStart(2, '0')}-$section")
        }
    }
}
