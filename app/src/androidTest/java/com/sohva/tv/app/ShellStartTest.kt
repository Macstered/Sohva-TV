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
                compose.onNodeWithTag(RailItem.LIVE_TV.tag).assertIsFocused()
            }
        }
    }

    @Test
    fun chosenLanguageAppliesBeforeAnyTextIsDrawn() {
        // Below Android 13 the app's own file holds the language (the stand-in is API 30).
        LocaleStore(context).setLanguageTag("fi")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
            compose.onNodeWithText("Suora TV").assertExists()
            compose.onNodeWithText("Asetukset").assertExists()
        }
    }
}
