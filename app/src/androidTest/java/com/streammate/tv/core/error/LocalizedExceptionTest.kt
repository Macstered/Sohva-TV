package com.streammate.tv.core.error

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.R
import java.io.IOException
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedExceptionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun contextIn(language: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale(language)) },
    )

    @Test
    fun theSameFailureReadsInWhicheverLanguageTheInterfaceIsIn() {
        val failure = LocalizedException(R.string.error_xtream_username_missing)

        assertEquals("The Xtream username is missing", failure.userMessage(contextIn("en")))
        assertEquals("Xtream-käyttäjänimi puuttuu", failure.userMessage(contextIn("fi")))
    }

    @Test
    fun argumentsSurviveTranslation() {
        val failure = LocalizedException(R.string.error_source_http, listOf(503))

        assertTrue(failure.userMessage(contextIn("en")).contains("503"))
        assertTrue(failure.userMessage(contextIn("fi")).contains("503"))
    }

    @Test
    fun anArgumentThatIsItselfALabelIsTranslatedWithTheMessage() {
        val failure = LocalizedException(
            R.string.error_source_url_invalid,
            listOf(ResourceArgument(R.string.error_source_label)),
        )

        assertTrue(failure.userMessage(contextIn("en")).startsWith("The Source"))
        assertTrue(failure.userMessage(contextIn("fi")).startsWith("Lähde-osoitteen"))
    }

    @Test
    fun aFailureFromOutsideOurCodeIsRedactedBeforeItIsShown() {
        // The whole reason redaction lives in userMessage: an OkHttp or Java
        // message routinely quotes the URL it failed on, credentials included.
        val failure = IOException(
            "Failed to connect to https://viewer:hunter2@provider.example/live?token=topsecret",
        )

        val shown = failure.userMessage(context)

        assertFalse(shown, shown.contains("hunter2"))
        assertFalse(shown, shown.contains("topsecret"))
        assertTrue(shown, shown.contains("provider.example"))
    }

    @Test
    fun aFailureWithNothingToSayStillRendersSomething() {
        assertEquals("Unknown error", IOException().userMessage(contextIn("en")))
        assertEquals("Tuntematon virhe", IOException().userMessage(contextIn("fi")))
    }

    @Test
    fun theLogMessageNeverNeedsAContext() {
        // Crash reports are read without a device locale in hand.
        val failure = LocalizedException(
            R.string.error_transport_failed_detail,
            listOf("connection refused"),
            logMessage = "connection refused",
        )

        assertEquals("connection refused", failure.message)
    }
}
