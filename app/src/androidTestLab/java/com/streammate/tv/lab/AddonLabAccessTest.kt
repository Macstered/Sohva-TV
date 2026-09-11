package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.AddonException
import com.sohva.tv.addons.AddonFailure
import com.streammate.tv.app.StreamMateApplication
import com.streammate.tv.app.activeRestriction
import com.streammate.tv.core.model.LibraryRoom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddonLabAccessTest {
    @Test fun liveProfileRestrictionDeniesManagerAndBrowser(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val preferences = app.container.preferencesRepository
        val original = preferences.preferences.first()
        val host = AddonHost.get(app, app.container)
        try {
            preferences.setAllowedGroups(original.activeProfileId, LibraryRoom.MOVIES, setOf("fixture-only-group"))
            try { host.manager.list(original.activeProfileId); fail("Restricted manager access") }
            catch (error: AddonException) { assertEquals(AddonFailure.ACCESS_DENIED, error.failure) }
            try { host.browser.catalog(original.activeProfileId, "missing", "movie", "fixture"); fail("Restricted browser access") }
            catch (error: AddonException) { assertEquals(AddonFailure.ACCESS_DENIED, error.failure) }
            assertFalse(host.access.allowed("another-profile"))
        } finally {
            preferences.setAllowedGroups(original.activeProfileId, LibraryRoom.MOVIES, original.activeRestriction.movies)
        }
    }
}
