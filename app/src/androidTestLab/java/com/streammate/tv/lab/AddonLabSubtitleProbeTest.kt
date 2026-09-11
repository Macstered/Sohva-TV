package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.AddonSubtitlePolicy
import com.streammate.tv.app.StreamMateApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in preference-only diagnostic. No URLs, titles, playback, imports or preference writes. */
class AddonLabSubtitleProbeTest {
    @Test fun inspectLanguagePreferences(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("addonSubtitleProbe") == "true")
        val app = ApplicationProvider.getApplicationContext<StreamMateApplication>()
        check(app.packageName == "com.streammate.tv.lab")
        val emulator = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu")
        check(emulator || (android.os.Build.MODEL == "SHIELD Android TV" && arguments.getString("addonPhysicalDevice") == "true"))
        val preferences = app.container.preferencesRepository.preferences.first()
        fun code(value: String?) = AddonSubtitlePolicy.language(value)?.takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "unset"
        println("Subtitle preferences: primary=${code(preferences.preferredSubtitleLanguage)}, secondary=${code(preferences.secondarySubtitleLanguage)}, primaryAudio=${code(preferences.preferredAudioLanguage)}, secondaryAudio=${code(preferences.secondaryAudioLanguage)}")
    }
}
