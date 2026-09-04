package com.streammate.tv.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLocaleTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // wrap() sets the process-wide default locale on purpose, so that date and
    // number formatting follows the interface rather than the system. That
    // makes it a shared mutation, and leaving it set would quietly change what
    // every test after this one formats.
    private val systemLocale: Locale = Locale.getDefault()

    @After
    fun clearChoice() {
        AppLocale.apply(context, null)
        Locale.setDefault(systemLocale)
    }

    @Test
    fun defaultsToFollowingTheSystem() {
        AppLocale.apply(context, null)

        assertNull(AppLocale.stored(context))
    }

    @Test
    fun remembersASupportedChoice() {
        AppLocale.apply(context, "fi")

        assertEquals("fi", AppLocale.stored(context))
    }

    @Test
    fun ignoresALanguageTheInterfaceIsNotTranslatedInto() {
        // Storing an unsupported tag would leave the app resolving to the
        // default set while claiming to be in another language.
        AppLocale.apply(context, "de")

        assertNull(AppLocale.stored(context))
    }

    @Test
    fun wrappingResolvesStringsInTheChosenLanguage() {
        AppLocale.apply(context, "fi")

        val wrapped = AppLocale.wrap(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The framework applies the locale to the context itself from
            // Tiramisu, so wrap() deliberately hands the same one back.
            assertEquals(context, wrapped)
        } else {
            assertEquals("Tuntematon virhe", wrapped.getString(R.string.error_unknown))
        }
    }

    @Test
    fun everyOfferedLanguageIsOneTheAppActuallyShips() {
        AppLocale.SUPPORTED_TAGS.forEach { tag ->
            AppLocale.apply(context, tag)
            assertTrue(tag, AppLocale.stored(context) == tag)
        }
    }
}
