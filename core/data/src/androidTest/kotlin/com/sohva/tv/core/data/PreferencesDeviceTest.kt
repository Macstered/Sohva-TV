package com.sohva.tv.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sohva.tv.core.data.prefs.AppPreferences
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.core.model.settings.ColorThemeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Persistence that the JVM tests cannot show: the DataStore file and the synchronous locale file. */
@RunWith(AndroidJUnit4::class)
class PreferencesDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fileName = "sohva_test_preferences"

    @After
    fun cleanUp() {
        context.preferencesDataStoreFile(fileName).delete()
        LocaleStore(context).setLanguageTag(null)
    }

    @Test
    fun savedThemeIsOnDisk() = runBlocking {
        @Suppress("InjectDispatcher")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val prefs = AppPreferences(
                PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile(fileName) },
            )
            prefs.setTheme(ColorThemeId.EVERFOREST)
            assertEquals(ColorThemeId.EVERFOREST, prefs.startSnapshot().theme)
            assertTrue(context.preferencesDataStoreFile(fileName).length() > 0)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun localeTagIsCommittedSynchronously() {
        val store = LocaleStore(context)
        assertTrue(store.setLanguageTag("fi"))
        assertEquals("fi", LocaleStore(context).languageTag())
        store.setLanguageTag(null)
        assertNull(LocaleStore(context).languageTag())
    }
}
