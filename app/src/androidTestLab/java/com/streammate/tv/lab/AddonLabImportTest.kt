package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.AddonEndpoint
import com.sohva.tv.addons.AddonCopyExport
import com.sohva.tv.addons.StremioCopyEvent
import kotlinx.coroutines.flow.flow
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateApplication
import com.streammate.tv.app.StreamMateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.provider.MediaStore
import java.util.concurrent.atomic.AtomicBoolean

/** Synthetic, emulator-only. Never touches the owner's configured addon endpoints. */
class AddonLabImportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsSectionsAreRemoteReachableAndKeepPreviewVisible() {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        check(compose.activity.packageName == "com.streammate.tv.lab")
        compose.runOnUiThread { compose.activity.setContent {
            StreamMateTheme { com.streammate.tv.feature.common.StreamMateScreenBackground(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { modifier ->
                AddonImportScreen({ error("No provider preview") }, { error("No installation") }, {}, modifier,
                    stremioImport = { error("Do not authorize during visual review") })
            } }
        } }
        compose.onNodeWithTag("addon-import-stremio").assertIsDisplayed()
        compose.onNodeWithTag("addon-import-back").assertIsDisplayed()
        compose.onNodeWithTag("addon-import-preview").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("addon-import-section-transfer").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("addon-import-section-files").assertIsFocused()
        compose.onNodeWithTag("addon-import-file").assertIsDisplayed()
        compose.onNodeWithTag("addon-import-phone-file").assertIsDisplayed()
        compose.onNodeWithTag("addon-import-nuvio").performScrollTo().assertIsDisplayed()
        AddonLabScreenshots.capture(compose.activity, "addon-import-settings-files.png")
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("addon-import-section-manual").assertIsFocused()
        compose.onNodeWithTag("addon-import-url").assertIsDisplayed()
        compose.onNodeWithTag("addon-import-preview").assertIsDisplayed()
        AddonLabScreenshots.capture(compose.activity, "addon-import-settings-manual.png")
        compose.onNodeWithTag("addon-import-section-transfer").performClick()
        compose.onNodeWithTag("addon-import-stremio").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        AddonLabScreenshots.capture(compose.activity, "addon-import-settings-main.png")
    }

    @Test fun longReadOnlyReviewCanBeNavigatedWithRemoteAndKeepsActionsVisible(): Unit = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val copied = AddonCopyExport.nuvio((1..32).joinToString(",", "[", "]") { """{"url":"invalid-fixture-$it"}""" })
        compose.runOnUiThread { compose.activity.setContent {
            StreamMateTheme { com.streammate.tv.feature.common.StreamMateScreenBackground(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { modifier ->
                AddonImportScreen({ host.batchImport.preview(profile, it) }, { error("Nothing can be installed") }, {}, modifier,
                    stremioImport = { flow { emit(StremioCopyEvent.Ready(copied)) } })
            } }
        } }
        compose.onNodeWithTag("addon-import-stremio").performScrollTo().performClick()
        compose.onNodeWithTag("addon-stremio-start").performClick()
        compose.onNodeWithTag("addon-import-preview").performClick()
        waitFor("addon-import-entry-1")
        compose.onNodeWithTag("addon-import-status-1").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        repeat(31) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onNodeWithTag("addon-import-status-32").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("addon-import-back").assertIsDisplayed()
        compose.onNodeWithTag("addon-import-confirm").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("addon-import-reset").assertIsDisplayed()
        AddonLabScreenshots.capture(compose.activity, "addon-import-settings-review-bottom.png")
    }

    @Test fun nuvioJsonPickerCopiesOnlyAddonUrlsAndDoesNotContactProviders() {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        check(compose.activity.packageName == "com.streammate.tv.lab")
        val resolver = compose.activity.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "sohva-synthetic-nuvio-${java.util.UUID.randomUUID()}.json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
        }))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*")
        }, Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        val contacted = AtomicBoolean(false)
        try {
            checkNotNull(resolver.openOutputStream(uri)).use {
                it.write("""{"addons":[{"url":"https://example.invalid/PrivateFixture/manifest.json","name":"Ignored"}],"authKey":"NeverCopy","collections":[]}""".toByteArray())
            }
            compose.runOnUiThread {
                compose.activity.setContent {
                    StreamMateTheme {
                        AddonImportScreen({ contacted.set(true); error("No preview in this test") },
                            { error("No installation in this test") }, {}, Modifier)
                    }
                }
            }
            compose.onNodeWithTag("addon-import-section-files").performClick()
            compose.onNodeWithTag("addon-import-nuvio").performScrollTo().performClick()
            assertEquals(1, monitor.hits)
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("1 addon ready for preview")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("addon-import-preview").assertIsDisplayed().assertIsEnabled()
            assertFalse(contacted.get())
            compose.onAllNodes(hasText("NeverCopy", substring = true)).assertCountEquals(0)
            compose.onAllNodes(hasText("PrivateFixture", substring = true)).assertCountEquals(0)
        } finally {
            instrumentation.removeMonitor(monitor)
            resolver.delete(uri, null, null) // Only the synthetic document created by this test.
        }
    }

    @Test fun selectedTextDocumentIsReadWithoutInstallingOrContactingAnAddon(): Unit = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val resolver = app.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "sohva-synthetic-import-${java.util.UUID.randomUUID()}.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Return a synthetic document from the system picker contract; no real files/accounts.
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("*/*")
        },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        val contacted = AtomicBoolean(false)
        try {
            checkNotNull(resolver.openOutputStream(uri)).use {
                it.write("\uFEFFhttps://example.invalid/first\r\n\r\nhttps://example.invalid/second\r\n".toByteArray())
            }
            compose.runOnUiThread {
                compose.activity.setContent {
                    StreamMateTheme {
                        AddonImportScreen({ contacted.set(true); host.batchImport.preview(profile, it) },
                            { host.batchImport.commit(it) }, {}, Modifier)
                    }
                }
            }
            compose.onNodeWithTag("addon-import-section-files").performClick()
            compose.onNodeWithTag("addon-import-file").performScrollTo().performClick()
            assertEquals("Synthetic document picker must intercept this request", 1, monitor.hits)
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("2 addons ready for preview")).fetchSemanticsNodes().isNotEmpty()
            }
            assertFalse(contacted.get())
            compose.onNodeWithTag("addon-import-preview").assertIsEnabled()
            compose.onNodeWithText("Clear list").performClick()
            compose.onNodeWithTag("addon-import-count").assertTextEquals("0 addons ready for preview")
        } finally {
            instrumentation.removeMonitor(monitor)
            resolver.delete(uri, null, null) // Only this test-created document.
        }
    }

    @Test fun leavingCancelsPendingPreview(): Unit = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val started = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        var show by mutableStateOf(true)
        compose.runOnUiThread {
            compose.activity.setContent {
                StreamMateTheme {
                    if (show) AddonImportScreen({ text ->
                        started.set(true)
                        try { delay(60_000); host.batchImport.preview(profile, text) }
                        finally { cancelled.set(true) }
                    }, { host.batchImport.commit(it) }, { show = false }, Modifier)
                    else androidx.tv.material3.Text("Import closed")
                }
            }
        }
        add("https://example.invalid/cancellation")
        compose.onNodeWithTag("addon-import-preview").performClick()
        compose.waitUntil(5_000) { started.get() }
        compose.onNodeWithTag("addon-import-back").performClick()
        compose.waitUntil(5_000) { cancelled.get() }
        compose.onNodeWithText("Import closed").assertIsDisplayed()
    }

    @Test fun previewConfirmDuplicatesPartialFailuresAndLeavingClearSecrets(): Unit = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        val app = compose.activity.application as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(MANIFEST))
            val url = server.url("/ImportFixture/manifest.json").toString()
            val fingerprint = AddonEndpoint.parse(url, true).fingerprint
            var show by mutableStateOf(true)
            compose.runOnUiThread {
                compose.activity.setContent {
                    StreamMateTheme {
                        com.streammate.tv.feature.common.StreamMateScreenBackground(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { modifier ->
                            if (show) AddonImportScreen(
                                { host.batchImport.preview(profile, it, allowInsecureHttp = true) },
                                { host.batchImport.commit(it) }, { show = false }, modifier,
                            ) else com.streammate.tv.feature.common.TvActionButton("Reopen import", { show = true })
                        }
                    }
                }
            }
            try {
                add(url)
                add(url)
                add("not-a-url")
                assertEquals(0, server.requestCount)
                compose.onNodeWithTag("addon-import-preview").performClick()
                waitFor("addon-import-entry-1")
                compose.onNodeWithTag("addon-import-select-1").assertTextContains("Ready to install")
                compose.onNodeWithTag("addon-import-status-2").assertTextContains("Duplicate in this list — skipped")
                compose.onNodeWithTag("addon-import-select-1").performScrollTo().performClick()
                compose.onNodeWithTag("addon-import-confirm").assertIsDisplayed().assertIsNotEnabled()
                compose.onNodeWithTag("addon-import-select-1").performScrollTo().performClick()
                compose.onNodeWithTag("addon-import-confirm").assertIsDisplayed()
                screenshot()
                assertTrue(host.store.list(profile).none { it.endpoint.fingerprint == fingerprint })
                compose.onNodeWithTag("addon-import-confirm").performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodes(hasText("Installed")).fetchSemanticsNodes().isNotEmpty()
                }
                val installed = host.store.list(profile).single { it.endpoint.fingerprint == fingerprint }
                host.store.setEnabled(profile, installed.installationId, false)
                compose.onNodeWithTag("addon-import-reset").performClick()
                add(url)
                compose.onNodeWithTag("addon-import-preview").performClick()
                waitFor("addon-import-entry-1")
                compose.onNodeWithTag("addon-import-status-1").assertTextContains("Already installed — unchanged")
                compose.onNodeWithTag("addon-import-confirm").assertIsNotEnabled()
                assertEquals(1, server.requestCount)
                assertFalse(host.store.list(profile).single { it.installationId == installed.installationId }.enabled)
                // Disposing the import page must not keep a previous credential-bearing preview.
                compose.onNodeWithTag("addon-import-back").performClick()
                compose.onNodeWithText("Reopen import").performClick()
                compose.onNodeWithTag("addon-import-count").assertTextEquals("0 addons ready for preview")
                compose.onNodeWithTag("addon-import-preview").assertIsNotEnabled()
            } finally {
                host.store.list(profile).filter { it.endpoint.fingerprint == fingerprint }
                    .forEach { host.store.remove(profile, it.installationId) }
            }
        }
    }

    private fun add(value: String) {
        compose.onNodeWithTag("addon-import-section-manual").performClick()
        compose.onNodeWithTag("addon-import-url").performScrollTo().performClick()
        compose.onNodeWithTag("addon-import-url").performTextReplacement(value)
        compose.onNodeWithTag("addon-import-url").performImeAction()
        compose.onNodeWithTag("addon-import-add").performScrollTo().performClick()
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot() {
        AddonLabScreenshots.capture(compose.activity, "addon-lab-import-preview.png")
    }

    private companion object {
        const val MANIFEST = """{"id":"test.lab.import","version":"1","name":"Synthetic import","types":["lab.import"],"resources":["meta"],"catalogs":[]}"""
    }
}
