package com.streammate.tv.core.error

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.R
import com.streammate.tv.core.network.GuideSourceException
import java.io.IOException
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun aStoredFailureComesBackInWordsWithItsArguments() {
        val http = GuideSourceException(R.string.error_source_http, listOf(403)).storedFailureMessage()
        val labelled = LocalizedException(
            R.string.error_source_url_invalid,
            listOf(ResourceArgument(R.string.error_source_label)),
        ).storedFailureMessage()

        assertEquals("The source responded with HTTP error 403", StoredFailureMessage.resolve(contextIn("en").resources, http))
        assertTrue(StoredFailureMessage.resolve(contextIn("fi").resources, http)!!.contains("403"))
        assertTrue(StoredFailureMessage.resolve(contextIn("fi").resources, labelled)!!.startsWith("Lähde-osoitteen"))
        assertEquals("Connection reset", StoredFailureMessage.resolve(context.resources, "Connection reset"))
        assertNull(StoredFailureMessage.resolve(context.resources, "  "))
    }

    @Test
    fun aStoredIdThatIsNotAnErrorResolvesToNothingRatherThanToTheWrongSentence() {
        // Resource ids are renumbered between builds; a refresh state written by
        // another beta must not put "Sohva TV" on the health row as a failure.
        assertNull(StoredFailureMessage.resolve(context.resources, "resource:${R.string.brand_sohva_tv}"))
        assertNull(StoredFailureMessage.resolve(context.resources, "resource:1"))
    }
}
