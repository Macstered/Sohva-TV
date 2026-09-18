package com.streammate.tv.feature.home

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StartupScreen
import com.streammate.tv.testing.ClearAppStateRule
import com.streammate.tv.testing.awaitFocused
import com.streammate.tv.testing.awaitTheEmptyGuide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Back finishes the activity while the process and cached Home state survive. */
class HomeReopenNavigationTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(compose)

    @Test fun backOutOfTheAppThenRelaunchKeepsFocusOnHomeContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = AppPreferencesRepository(context)
        val previousStartup = runBlocking { preferences.preferences.first().startupScreen }
        runBlocking { preferences.setStartupScreen(StartupScreen.HOME) }
        try {
            // Repeated launches reuse the real application/container, not a new fixture.
            repeat(3) {
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    setClass(context, MainActivity::class.java)
                    addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ActivityScenario.launch<MainActivity>(launcherIntent).use { scenario ->
                    compose.awaitFocused("home-hero-primary")
                    compose.onNodeWithTag("home-live").performClick()
                    compose.awaitTheEmptyGuide()

                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    compose.awaitFocused("home-hero-primary")

                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    compose.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
                }
            }
        } finally {
            runBlocking { preferences.setStartupScreen(previousStartup) }
        }
    }
}
