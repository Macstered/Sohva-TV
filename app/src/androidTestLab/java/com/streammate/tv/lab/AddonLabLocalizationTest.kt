package com.streammate.tv.lab

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.sohva.tv.addons.*
import com.streammate.tv.R
import com.streammate.tv.addons.*
import com.streammate.tv.app.*
import com.streammate.tv.feature.common.StreamMateScreenBackground
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Changes only the emulator Lab locale; restores it and removes synthetic fixtures afterward. */
class AddonLabLocalizationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun app(): StreamMateApplication {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        return (compose.activity.application as StreamMateApplication).also { check(it.packageName == "com.streammate.tv.lab") }
    }
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }

    @Test fun allLocalesFormatCountsNamesAndErrorsWithoutChangingProviderTypes() {
        app()
        for (tag in AppLocale.SUPPORTED_TAGS) {
            val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val resources = compose.activity.createConfigurationContext(config).resources
            val labels = AddonStrings(resources)
            assertEquals(tag, labels.locale.language)
            assertEquals("custom.type", labels.mediaType("custom.type"))
            assertFalse(labels.mediaType("movie").isBlank())
            assertFalse(labels.languageName("fin").isBlank())
            assertEquals(resources.getString(R.string.addon_ui_unknown_language), labels.languageName(""))
            for (count in listOf(0, 1, 2, 32)) {
                val rendered = labels.count(R.plurals.addon_ui_preview_count, count)
                assertTrue(rendered.contains(count.toString()))
                assertFalse(rendered.contains("%1"))
            }
            assertTrue(labels(R.string.addon_ui_preferred_languages_value, "LANG_A", "LANG_B").let { it.contains("LANG_A") && it.contains("LANG_B") })
            assertFalse(labels(AddonFailure.NETWORK.messageResource()).isBlank())
        }
    }

    @Test fun savedInterfaceLanguageReachesAddonSettingsImportHomeAndSearch(): Unit = runBlocking {
        val app = app()
        val host = AddonHost.get(app, app.container)
        val preferences = app.container.preferencesRepository.preferences.first()
        val oldLanguage = AppLocale.stored(compose.activity)
        val oldDefault = Locale.getDefault()
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse().setBody("""{"metas":[]}""")
            }
            val installed = host.store.install(preferences.activeProfileId,
                AddonEndpoint.parse(server.url("/manifest.json").toString(), true),
                """{"id":"test.localization","version":"1","name":"Provider name unchanged","types":["lab.localization"],"resources":["catalog"],"catalogs":[{"type":"lab.localization","id":"home","name":"Catalog name unchanged"}]}""")
            try {
                for (tag in listOf("fi", "de", "es", "pt", "sv", "it", "en")) {
                    compose.runOnUiThread { AppLocale.apply(compose.activity, tag) }
                    compose.activityRule.scenario.recreate()
                    assertEquals(tag, AppLocale.stored(compose.activity))
                    assertEquals(tag, compose.activity.resources.configuration.primaryLocale().language)
                    compose.runOnUiThread { compose.activity.setContent { StreamMateTheme {
                        StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
                            AddonDiscoverScreen(host, preferences, {}, modifier, initiallyManage = true, loadInstallations = { listOf(installed) })
                        }
                    } } }
                    waitFor("addon-manager-list")
                    val labels = AddonStrings(compose.activity.resources)
                    compose.onNodeWithText(labels(R.string.addon_ui_addons_setup)).assertIsDisplayed()
                    compose.onNodeWithText("Provider name unchanged").assertIsDisplayed()
                    screenshot("$tag-settings")
                    compose.onNodeWithTag("addon-settings-subtitles").performClick()
                    compose.onNodeWithText(labels(R.string.addon_ui_preferred_languages)).assertIsDisplayed()
                    compose.onNodeWithTag("addon-settings-setup").performClick()
                    compose.onNodeWithTag("addon-import").performClick()
                    waitFor("addon-import-screen")
                    compose.onNodeWithText(labels(R.string.addon_ui_import_methods), ignoreCase = true).assertIsDisplayed()
                    compose.onNodeWithTag("addon-import-section-files").performClick()
                    compose.onNodeWithText(labels(R.string.addon_ui_choose_file_on_phone)).assertIsDisplayed()
                    screenshot("$tag-import")
                    back(); waitFor("addon-manager-list")
                    back(); waitFor("addon-discover")
                    compose.onNodeWithTag("addon-search").assertContentDescriptionEquals(labels(R.string.home_search))
                    compose.onNodeWithTag("addon-search").performClick()
                    waitFor("addon-search-page")
                    compose.onNodeWithText(labels(R.string.addon_ui_search_movies_and_series)).assertExists()
                    back(); waitFor("addon-discover")
                }
                assertEquals(installed.revision, host.manager.list(preferences.activeProfileId).first { it.installationId == installed.installationId }.revision)
            } finally {
                compose.runOnUiThread { compose.activity.setContent {}; AppLocale.apply(compose.activity, oldLanguage) }
                compose.activityRule.scenario.recreate()
                Locale.setDefault(oldDefault)
                host.store.remove(preferences.activeProfileId, installed.installationId)
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // System window transitions can outlive Compose idleness after Activity recreation.
        android.os.SystemClock.sleep(400)
        AddonLabScreenshots.capture(compose.activity, "localization-$name.png")
    }
}
