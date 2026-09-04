package com.streammate.tv.testing

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.StreamMateApplication
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Resets the persisted state that the activity-backed tests assume.
 *
 * Several of these tests reach Settings through `guide-empty-settings`, a
 * control that only exists while no source is configured, and one of them adds
 * a source. Nothing reset that between tests, so the first test to add a source
 * broke every later test that expected an empty guide — including tests in
 * other classes, because the app data outlives the activity.
 *
 * Chain this outside the compose rule so it runs before the activity launches:
 *
 *     private val composeRule = createAndroidComposeRule<MainActivity>()
 *
 *     @get:Rule
 *     val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(composeRule)
 */
class ClearAppStateRule : TestWatcher() {

    override fun starting(description: Description) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(SECURE_SOURCES_PREFERENCES, ARTWORK_CACHE_PREFERENCES).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }

        val application = context.applicationContext as StreamMateApplication
        runBlocking { application.container.guideRepository.clear() }
    }

    private companion object {
        const val SECURE_SOURCES_PREFERENCES = "streammate_secure_sources"

        // Settings that have to be readable before the app is up live outside
        // DataStore, so they outlive a test the same way sources did.
        const val ARTWORK_CACHE_PREFERENCES = "streammate_artwork_cache"
    }
}
