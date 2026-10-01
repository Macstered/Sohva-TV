package com.sohva.tv.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.text.TimeStyles
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 74 §11 "Instrumentation": the stored choice below Android 13 (supported languages),
 * texts outside an activity in the chosen language (L10N-FR-05), the JVM default back to the TV's
 * language on System default (L10N-FR-06), and the time styles Android gives each language.
 */
@RunWith(AndroidJUnit4::class)
class AppLocaleTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val store = LocaleStore(app)
    private val original = Locale.getDefault()

    @Before
    fun belowAndroid13() {
        assumeTrue("the platform holds the choice from Android 13", Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
    }

    @After
    fun restore() {
        store.setLanguageTag(null)
        Locale.setDefault(original)
    }

    @Test
    fun onlySupportedLanguagesAreAChoice() {
        store.setLanguageTag(null)
        assertNull(AppLocales.chosen(app))
        store.setLanguageTag("fi")
        assertEquals("fi", AppLocales.chosen(app))
        store.setLanguageTag("pt-BR")
        assertEquals("pt", AppLocales.chosen(app))
        for (tag in listOf("fr", "fr-FR", "fr-CA")) {
            store.setLanguageTag(tag)
            assertEquals("fr", AppLocales.chosen(app))
        }
        store.setLanguageTag("ja")
        assertNull(AppLocales.chosen(app))
    }

    @Test
    fun frenchTextsAndCountsFollowTheChoiceAndSwitchBack() {
        store.setLanguageTag("fr")
        val texts = AppLocales.texts(app)
        assertEquals("Erreur inconnue", texts.getString(R.string.error_unknown))
        assertEquals("Configurer depuis un téléphone", texts.getString(R.string.phone_setup_title))
        assertEquals("0 film", texts.resources.getQuantityString(R.plurals.catalogue_imported_movies, 0, 0))
        assertEquals("1 film", texts.resources.getQuantityString(R.plurals.catalogue_imported_movies, 1, 1))
        assertEquals("2 films", texts.resources.getQuantityString(R.plurals.catalogue_imported_movies, 2, 2))
        assertEquals("1000000 films", texts.resources.getQuantityString(R.plurals.catalogue_imported_movies, 1_000_000, 1_000_000))
        AppLocales.attach(app)
        assertEquals("fr", Locale.getDefault().language)
        store.setLanguageTag("fi")
        assertEquals("Tuntematon virhe", AppLocales.texts(app).getString(R.string.error_unknown))
        store.setLanguageTag(null)
        assertEquals("Unknown error", AppLocales.texts(app).getString(R.string.error_unknown))
    }

    @Test
    fun textsOutsideAnActivityFollowTheChoice() {
        store.setLanguageTag("fi")
        assertEquals("Tuntematon virhe", AppLocales.wrap(app).getString(R.string.error_unknown))
        assertEquals("Tuntematon virhe", AppLocales.texts(app).getString(R.string.error_unknown))
        store.setLanguageTag(null)
        // Back to the TV's language (English on the stand-in) without a restart.
        assertEquals("Unknown error", AppLocales.texts(app).getString(R.string.error_unknown))
    }

    @Test
    fun systemDefaultRestoresTheTvsLanguageForFormatting() {
        store.setLanguageTag("fi")
        AppLocales.attach(app)
        assertEquals("fi", Locale.getDefault().language)
        store.setLanguageTag(null)
        AppLocales.attach(app)
        assertEquals(android.content.res.Resources.getSystem().configuration.locales[0].language, Locale.getDefault().language)
    }

    @Test
    fun eachLanguageHasItsOwnClock() {
        val fi = TimeStyles.of(app, Locale.forLanguageTag("fi"))
        val en = TimeStyles.of(app, Locale.forLanguageTag("en-GB"))
        // Finnish with a dot, English with a colon; hours as the TV's 12/24-hour setting says.
        val twentyFour = android.text.format.DateFormat.is24HourFormat(app)
        assertEquals(if (twentyFour) "HH.mm" else "h.mm a", fi.clockPattern)
        assertEquals(if (twentyFour) "HH:mm" else "h:mm a", en.clockPattern)
    }
}
