package com.streammate.tv.testing

import com.streammate.tv.app.Profiles
import kotlinx.coroutines.flow.first
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
        listOf(SECURE_SOURCES_PREFERENCES, ARTWORK_CACHE_PREFERENCES, LOCALE_PREFERENCES).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }

        val application = context.applicationContext as StreamMateApplication
        runBlocking {
            application.container.guideRepository.clear()
            // A household of one again: a leftover second profile would put
            // the who-is-watching screen in front of every test that follows.
            val preferences = application.container.preferencesRepository
            val current = preferences.preferences.first()
            current.profiles.forEach { preferences.removeProfile(it.id) }
            if (current.activeProfileId != Profiles.DEFAULT_ID) preferences.setActiveProfile(Profiles.DEFAULT_ID)
            // A switch a previous test left on is indistinguishable from the
            // shipped default once it is written, so the test that asserts the
            // default would pass or fail on the order tests happened to run in.
            if (current.pictureInPictureEnabled) preferences.setPictureInPictureEnabled(false)
        }
    }

    private companion object {
        const val SECURE_SOURCES_PREFERENCES = "streammate_secure_sources"

        // Settings that have to be readable before the app is up live outside
        // DataStore, so they outlive a test the same way sources did.
        const val ARTWORK_CACHE_PREFERENCES = "streammate_artwork_cache"

        // The interface language, which a screenshot review can leave set;
        // every test here reads its labels in English.
        const val LOCALE_PREFERENCES = "streammate_locale"
    }
}
