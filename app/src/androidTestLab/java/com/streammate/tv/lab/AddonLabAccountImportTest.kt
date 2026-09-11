package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.sohva.tv.addons.*
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** No Stremio service or real account is contacted. Fake copy flow, real TV focus/lifecycle. */
class AddonLabAccountImportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun guard() {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        check(compose.activity.packageName == "com.streammate.tv.lab")
    }

    @Test fun accountCopyRequiresStartAndThenSeparateManifestPreview() {
        guard()
        val requests = AtomicInteger()
        val previews = AtomicInteger()
        compose.runOnUiThread {
            compose.activity.setContent {
                StreamMateTheme {
                    AddonImportScreen({ previews.incrementAndGet(); error("Preview must not run in this test") },
                        { error("Install must not run in this test") }, {}, Modifier, stremioImport = {
                            flow {
                                requests.incrementAndGet()
                                emit(StremioCopyEvent.Ready(AddonCopyExport.nuvio("""[{"url":"https://example.invalid/PrivateFixture/manifest.json"}]""")))
                            }
                        })
                }
            }
        }
        compose.onNodeWithTag("addon-import-stremio").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0) }
        assertEquals(0, requests.get())
        compose.onNodeWithTag("addon-stremio-start").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("addon-import-screen")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("addon-import-count").assertTextEquals("1 addon ready for preview")
        compose.onNodeWithTag("addon-import-preview").assertIsDisplayed().assertIsEnabled()
        assertEquals(1, requests.get())
        assertEquals(0, previews.get())
        compose.runOnIdle { assertEquals(0, compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        compose.onAllNodes(hasText("PrivateFixture", substring = true)).assertCountEquals(0)
    }

    @Test fun backgroundDropsQrAndCancelsPollingWithoutAutomaticRestart() {
        guard()
        val starts = AtomicInteger()
        val cancels = AtomicInteger()
        compose.runOnUiThread {
            compose.activity.setContent {
                StreamMateTheme {
                    AddonStremioImportScreen({ flow {
                        starts.incrementAndGet()
                        try {
                            emit(StremioCopyEvent.Waiting(StremioImportLink("TEST", "https://link.stremio.com/TEST")))
                            awaitCancellation()
                        } finally { cancels.incrementAndGet() }
                    } }, { error("No account should be returned") }, {}, Modifier)
                }
            }
        }
        compose.onNodeWithTag("addon-stremio-start").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { starts.get() == 1 }
        compose.onNodeWithTag("addon-stremio-qr").assertExists()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5000) { cancels.get() == 1 }
        compose.onNodeWithTag("addon-stremio-qr").assertDoesNotExist()
        compose.onNodeWithTag("addon-stremio-start").assertIsDisplayed().assertIsEnabled()
        assertEquals(1, starts.get())
    }

    @Test fun leavingCancelsAuthorizationAndReturnsToImport() {
        guard()
        val cancels = AtomicInteger()
        compose.runOnUiThread {
            compose.activity.setContent {
                StreamMateTheme {
                    AddonImportScreen({ error("No preview") }, { error("No install") }, {}, Modifier,
                        stremioImport = { flow {
                            try {
                                emit(StremioCopyEvent.Waiting(StremioImportLink("TEST", "https://link.stremio.com/TEST")))
                                awaitCancellation()
                            } finally { cancels.incrementAndGet() }
                        } })
                }
            }
        }
        compose.onNodeWithTag("addon-import-stremio").performScrollTo().performClick()
        compose.onNodeWithTag("addon-stremio-start").performClick()
        compose.onNodeWithTag("addon-stremio-back").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { cancels.get() == 1 }
        compose.onNodeWithTag("addon-import-screen").assertExists()
        compose.onNodeWithTag("addon-import-count").assertTextEquals("0 addons ready for preview")
    }
}
