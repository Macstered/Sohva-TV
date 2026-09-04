package com.streammate.tv

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.R as CoreR
import com.streammate.tv.iptv.R as IptvR
import com.streammate.tv.sportmate.R as SportMateR
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationResourcesTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun englishIsTheFallbackAndFinnishIsExplicitlyComplete() {
        assertEquals("Haku", localized("fi", R.string.search_title))
        assertEquals("Search", localized("en", R.string.search_title))

        assertEquals("Kanavaopas", localized("fi", IptvR.string.guide_title))
        assertEquals("TV guide", localized("en", IptvR.string.guide_title))
        assertEquals("Asetukset", localized("fi", IptvR.string.settings_title))
        assertEquals("Settings", localized("en", IptvR.string.settings_title))

        val finnishSports = localized("fi", SportMateR.string.today_headline)
        val englishSports = localized("en", SportMateR.string.today_headline)
        assertNotEquals(finnishSports, englishSports)
        assertEquals("TODAY’S SPORTS", englishSports)

        assertEquals(
            "About, privacy and licences",
            localized("en", R.string.about_title),
        )
        assertEquals(
            "Tietoja, tietosuoja ja lisenssit",
            localized("fi", R.string.about_title),
        )
        assertEquals("Send email", localized("en", R.string.about_contact_email))
        assertEquals("Lähetä sähköpostia", localized("fi", R.string.about_contact_email))
        assertEquals(
            "About, privacy and licences",
            localized("en", IptvR.string.settings_about_licenses),
        )
    }

    /**
     * A device set to anything the app is not translated into has to land on
     * English. It used to land on Finnish, because Finnish was the default
     * resource set - correct for a Finland-first sideload, wrong for anyone
     * else on earth.
     */
    @Test
    fun anUntranslatedLocaleFallsBackToEnglish() {
        listOf("sv", "da", "de", "ja").forEach { language ->
            assertEquals(language, "Search", localized(language, R.string.search_title))
            assertEquals(language, "TV guide", localized(language, IptvR.string.guide_title))
            assertEquals(
                language,
                "TODAY’S SPORTS",
                localized(language, SportMateR.string.today_headline),
            )
            assertEquals(
                language,
                "Unknown error",
                localized(language, CoreR.string.error_unknown),
            )
        }
    }

    /**
     * Messages raised below the UI layer are resources like any other, so they
     * have to translate too - that was the whole point of moving them there.
     */
    @Test
    fun belowTheUiMessagesAreTranslated() {
        assertEquals(
            "The Xtream username is missing",
            localized("en", CoreR.string.error_xtream_username_missing),
        )
        assertEquals(
            "Xtream-käyttäjänimi puuttuu",
            localized("fi", CoreR.string.error_xtream_username_missing),
        )
    }

    @Test
    fun formattedResourcesWorkInBothLanguages() {
        assertEquals("1 tulos", localizedQuantity("fi", R.plurals.search_result_count, 1, 1))
        assertEquals("3 tulosta", localizedQuantity("fi", R.plurals.search_result_count, 3, 3))
        assertEquals("1 result", localizedQuantity("en", R.plurals.search_result_count, 1, 1))
        assertEquals("3 results", localizedQuantity("en", R.plurals.search_result_count, 3, 3))
        assertEquals(
            "Imported 4 movies and 2 series",
            localized(
                "en",
                R.string.catalogue_imported,
                localizedQuantity("en", R.plurals.catalogue_imported_movies, 4, 4),
                localizedQuantity("en", R.plurals.catalogue_imported_series, 2, 2),
            ),
        )
        assertEquals(
            "Source: Provider",
            localized("en", IptvR.string.channels_source, "Provider"),
        )
    }

    private fun localized(language: String, resourceId: Int, vararg arguments: Any): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return context.createConfigurationContext(configuration).getString(resourceId, *arguments)
    }

    private fun localizedQuantity(
        language: String,
        resourceId: Int,
        quantity: Int,
        vararg arguments: Any,
    ): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return context.createConfigurationContext(configuration).resources
            .getQuantityString(resourceId, quantity, *arguments)
    }
}
