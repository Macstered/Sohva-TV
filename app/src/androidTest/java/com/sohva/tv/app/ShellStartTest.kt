package com.sohva.tv.app

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.shell.LaunchScreen
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Spec 01 §11: launch screen, launcher label, repeated launches, language before any text. */
@RunWith(AndroidJUnit4::class)
class ShellStartTest {
    private val clearState = ClearStateRule()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(clearState).around(compose)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun launchScreenShowsMarkAndWordmarkNamedSohvaTv() {
        compose.setContent {
            @Suppress("InjectDispatcher")
            CompositionLocalProvider(LocalRenderDispatcher provides Dispatchers.Default) { LaunchScreen() }
        }
        compose.onNodeWithTag("launch-splash").assertExists()
        compose.onNodeWithTag("launch-mark").assertExists()
        compose.onNodeWithContentDescription("Sohva TV").assertExists()
    }

    @Test
    fun launcherLabelIsSohvaTvInEnglishAndFinnish() {
        val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        assertEquals("Sohva TV", label)
        val fi = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("fi")) },
        )
        assertEquals("Sohva TV", fi.getString(com.sohva.tv.ui.design.R.string.app_name))
    }

    @Test
    fun threeLaunchesInOneProcessEachReachHome() {
        repeat(3) {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
                // Content focused, rail closed (spec 01 §11: three launches in one process).
                compose.waitUntil(10_000) {
                    compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-hero-primary") and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
    }

    @Test
    fun chosenLanguageAppliesBeforeAnyTextIsDrawn() {
        // Below Android 13 the app's own file holds the language; from 13 the platform does.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("fi")
        } else {
            LocaleStore(context).setLanguageTag("fi")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
            // The rail and the Welcome hero both say it.
            assertTrue(compose.onAllNodesWithTextExists("Suora TV"))
            compose.onNodeWithText("Asetukset").assertExists()
        }
    }

    @Test
    fun savedThemeIsUsedFromTheFirstAppFrame() {
        val graph = (context.applicationContext as SohvaApplication).graph
        runBlocking { graph.data.preferences.setTheme(ColorThemeId.KANAGAWA) }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
            // The Home marker is filled with textPrimary: Kanagawa's parchment, not Original's white.
            // Home focuses its content, so the marker is its collapsed 48 dp square: sample its left edge.
            val image = compose.onNodeWithTag("home-nav-home").captureToImage().asAndroidBitmap()
            val pixel = image.getPixel(image.height / 8, image.height / 2)
            assertEquals(Integer.toHexString(0xFFDCD7BA.toInt()), Integer.toHexString(pixel))
        }
    }

    @Test
    fun interfaceSizeScalesTheWholeInterface() {
        val graph = (context.applicationContext as SohvaApplication).graph
        runBlocking { graph.data.preferences.setScale(InterfaceScale.SMALLER) }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
            val height = compose.onNodeWithTag("home-nav-home").fetchSemanticsNode().size.height
            val expected = 48 * context.resources.displayMetrics.density * InterfaceScale.SMALLER.factor
            assertEquals(expected, height.toFloat(), 1.5f)
        }
    }
}
