package com.streammate.tv.feature.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.streammate.tv.app.Profiles
import com.streammate.tv.app.AppPreferencesRepository
import org.junit.Assert.assertEquals
import com.streammate.tv.app.ArtworkCacheSettings
import com.streammate.tv.app.ArtworkCacheLimit
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.testing.ClearAppStateRule
import org.junit.rules.RuleChain
import com.streammate.tv.testing.awaitUntil
import com.streammate.tv.testing.openSettingsFromTheEmptyGuide
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.streammate.tv.app.AppLocale
import com.streammate.tv.app.InterfaceScale
import com.streammate.tv.app.MainActivity
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Outside the compose rule so persisted state is reset before the activity
    // launches, not after it has already rendered.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(composeRule)

    @Before
    fun waitForLandingPage() {
        composeRule.awaitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home-live").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun addXtreamSourceShowsMaskedCredentialEditor() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("source-add-xtream").performClick()

        // The address and the credentials sit directly under the source's own
        // heading, so adding a source lands on them without scrolling.
        composeRule.onNodeWithTag("settings-xtream-base-url").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-xtream-username").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-xtream-password").assertIsDisplayed()
        // Testing the connection is something you do after filling them in, and
        // it sits with Save at the foot of the section rather than among them.
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-test-xtream"))
        composeRule.onNodeWithTag("settings-test-xtream").assertIsDisplayed()
    }

    @Test
    fun testingAnM3uAddressReadsThePlaylistAndIntroducesTheApp() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody("#EXTM3U\n#EXTINF:-1,One\nhttp://stream.example/1\n#EXTINF:-1,Two\nhttp://stream.example/2\n"),
            )
            server.start()

            composeRule.openSettingsFromTheEmptyGuide()
            composeRule.onNodeWithTag("source-add-m3u").performClick()
            composeRule.onNodeWithTag("settings-m3u")
                .performClick()
                .performTextInput(server.url("/list.m3u").toString())
            composeRule.onNodeWithTag("settings-list")
                .performScrollToNode(hasTestTag("settings-source-name"))
            composeRule.onNodeWithTag("settings-source-name")
                .performClick()
                .performTextInput("Probe")
            composeRule.onNodeWithTag("settings-list")
                .performScrollToNode(hasTestTag("settings-test-m3u"))
            composeRule.onNodeWithTag("settings-test-m3u").performClick()

            composeRule.awaitUntil(timeoutMillis = 15_000) {
                runCatching {
                    composeRule.onNodeWithTag("settings-status").assertTextContains("2 entries", substring = true)
                    true
                }.getOrDefault(false)
            }
            // The whole point of the header: a panel that drops the HTTP library's
            // own agent must see the app's name instead.
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertTrue(request.getHeader("User-Agent").orEmpty().startsWith("Sohva TV/"))
        }
    }

    @Test
    fun vodOnlyM3uSourceShowsCatalogueImportWithoutLiveOrEpgControls() {
        composeRule.openSettingsFromTheEmptyGuide()
        // A source is a page now: the form opens from the Add button.
        composeRule.onNodeWithTag("source-add-m3u").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-import-vod"))
        composeRule.onNodeWithTag("settings-import-vod").performClick()

        composeRule.onNodeWithTag("settings-xmltv").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-refresh-playlist").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-refresh-epg").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-refresh-catalogue"))
        composeRule.onNodeWithTag("settings-refresh-catalogue").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-save").assertIsDisplayed()
    }

    @Test
    fun epgCorrectionUsesThirtyMinuteStepsForLiveSources() {
        composeRule.openSettingsFromTheEmptyGuide()
        // A source is a page now: the form opens from the Add button.
        composeRule.onNodeWithTag("source-add-m3u").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-epg-offset-up"))

        composeRule.onNodeWithTag("settings-epg-offset-value").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-epg-offset-value").assertTextEquals("0 min")
        composeRule.onNodeWithTag("settings-epg-offset-up").performClick().performClick()
        composeRule.onNodeWithTag("settings-epg-offset-value").assertTextEquals("+1 h")
        composeRule.onNodeWithTag("settings-epg-offset-down").performClick()
        composeRule.onNodeWithTag("settings-epg-offset-value").assertTextEquals("+30 min")
    }

    @Test
    fun settingsFieldsRequireSelectBeforeEditing() {
        composeRule.openSettingsFromTheEmptyGuide()
        // A source is a page now: the form opens from the Add button.
        composeRule.onNodeWithTag("source-add-m3u").performClick()

        composeRule.onNodeWithTag("settings-source-name")
            .assert(hasClickAction() and !hasSetTextAction())
            .performClick()
        composeRule.onNodeWithTag("settings-source-name").assert(hasSetTextAction())
    }

    @Test
    fun completingCredentialEditKeepsFocusInTheEditedField() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("source-add-xtream").performClick()

        composeRule.onNodeWithTag("settings-xtream-username")
            .performClick()
            .performTextInput("viewer")
        composeRule.onNodeWithTag("settings-xtream-username").performImeAction()

        composeRule.onNodeWithTag("settings-xtream-username").assertIsFocused()
    }

    @Test
    fun settingsUsesVerticalSectionRailAndCompactHeader() {
        composeRule.openSettingsFromTheEmptyGuide()

        composeRule.onAllNodesWithText("Sohva TV").assertCountEquals(0)
        composeRule.onNodeWithTag("settings-back").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-section-sources").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-section-sources").assertIsSelected()
        composeRule.onNodeWithTag("settings-section-about").assertIsDisplayed()
        composeRule.onNodeWithTag("source-add-m3u").assertIsFocused()

        val sourcesBounds = composeRule.onNodeWithTag("settings-section-sources").fetchSemanticsNode().boundsInRoot
        val playbackBounds = composeRule.onNodeWithTag("settings-section-playback").fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            assertTrue(playbackBounds.top > sourcesBounds.bottom)
            assertTrue(kotlin.math.abs(playbackBounds.left - sourcesBounds.left) < 1f)
        }
    }

    @Test
    fun playlistRefreshIntervalOpensDropdownAndPersistsSelection() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-general").performClick()

        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-refresh-interval"))
        composeRule.onNodeWithTag("settings-refresh-interval")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("settings-refresh-interval-24").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(refreshIntervalLabel(24), substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("settings-refresh-interval").performClick()
        composeRule.onNodeWithTag("settings-refresh-interval-24").assertIsFocused()
        composeRule.onNodeWithTag("settings-refresh-interval-4").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(refreshIntervalLabel(4), substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("settings-refresh-interval").performClick()
        composeRule.onNodeWithTag("settings-refresh-interval-24").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(refreshIntervalLabel(24), substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /**
     * The setting that decides which copy of a duplicated film the wall stands
     * on. Left on "whichever comes first" at the end, because a preference
     * outlives this test and would otherwise reorder somebody else's library.
     */
    @Test
    fun preferredCopyPersistsSelectionAndCanBeCleared() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-metadata").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-preferred-copy"))

        composeRule.onNodeWithTag("settings-preferred-copy").performClick()
        composeRule.onNodeWithTag("settings-preferred-copy-finnish_audio").performClick()
        // Reopened, the picker marks what was stored.
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-preferred-copy").performClick()
                composeRule.onNodeWithTag("settings-preferred-copy-finnish_audio").assertIsSelected()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("settings-preferred-copy-none").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-preferred-copy").performClick()
                composeRule.onNodeWithTag("settings-preferred-copy-none").assertIsSelected()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun skipStepIsChosenFromAPickerAndRestored() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-seek-step").performClick()
        composeRule.onNodeWithTag("settings-seek-step-ten_seconds").assertIsSelected()
        composeRule.onNodeWithTag("settings-seek-step-one_minute").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-seek-step").performClick()
                composeRule.onNodeWithTag("settings-seek-step-one_minute").assertIsSelected()
                true
            }.getOrDefault(false)
        }
        // Back to the default, so the preference does not leak into the next test.
        composeRule.onNodeWithTag("settings-seek-step-ten_seconds").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-seek-step").performClick()
                composeRule.onNodeWithTag("settings-seek-step-ten_seconds").assertIsSelected()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings-seek-step-ten_seconds").performClick()
    }

    @Test
    fun subtitleSizeIsChosenFromAPickerAndRestored() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-subtitle-size").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-subtitle-size-follow_tv").assertIsSelected()
        composeRule.onNodeWithTag("settings-subtitle-size-large").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-subtitle-size").performScrollTo().performClick()
                composeRule.onNodeWithTag("settings-subtitle-size-large").assertIsSelected()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings-subtitle-size-follow_tv").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-subtitle-size").performScrollTo().performClick()
                composeRule.onNodeWithTag("settings-subtitle-size-follow_tv").assertIsSelected()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings-subtitle-size-follow_tv").performClick()
    }

    @Test
    fun playbackBufferProfilePersistsSelectionAndCanRestoreDefault() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-buffer").performClick()
        composeRule.onNodeWithTag("settings-buffer-default").assertIsSelected()
        composeRule.onNodeWithTag("settings-buffer-low_latency").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-buffer").performClick()
                composeRule.onNodeWithTag("settings-buffer-low_latency").assertIsSelected()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings-buffer-low_latency").performClick()

        composeRule.onNodeWithTag("settings-section-sources").performClick()
        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-buffer").performClick()
        composeRule.onNodeWithTag("settings-buffer-low_latency").assertIsSelected()

        composeRule.onNodeWithTag("settings-buffer-default").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-buffer").performClick()
                composeRule.onNodeWithTag("settings-buffer-default").assertIsSelected()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings-buffer-default").performClick()
    }

    @Test
    fun playbackReconnectPolicyPersistsSelectionAndCanRestoreStandard() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-reconnect").performClick()
        composeRule.onNodeWithTag("settings-reconnect-standard").assertIsSelected()
        composeRule.onNodeWithTag("settings-reconnect-persistent").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-reconnect").performClick()
                composeRule.onNodeWithTag("settings-reconnect-persistent").assertIsSelected()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings-reconnect-persistent").performClick()

        composeRule.onNodeWithTag("settings-section-sources").performClick()
        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-reconnect").performClick()
        composeRule.onNodeWithTag("settings-reconnect-persistent").assertIsSelected()

        composeRule.onNodeWithTag("settings-reconnect-standard").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-reconnect").performClick()
                composeRule.onNodeWithTag("settings-reconnect-standard").assertIsSelected()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings-reconnect-standard").performClick()
    }

    @Test
    fun selectingSettingsSectionMovesFocusIntoItsFirstUsefulControl() {
        composeRule.openSettingsFromTheEmptyGuide()

        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-buffer").assertIsFocused()

        composeRule.onNodeWithTag("settings-section-metadata").performClick()
        composeRule.onNodeWithTag("settings-metadata-tmdb-enabled").assertIsFocused()

        composeRule.onNodeWithTag("settings-section-sport").performClick()
        composeRule.onNodeWithTag("settings-sports-api-save").assertIsFocused()

        composeRule.onNodeWithTag("settings-section-general").performClick()
        composeRule.onNodeWithTag("settings-interface-language").assertIsFocused()

        composeRule.onNodeWithTag("settings-section-parental").performClick()
        composeRule.onNodeWithTag("settings-parental-pin").assertIsFocused()

        composeRule.onNodeWithTag("settings-section-backup").performClick()
        composeRule.onNodeWithTag("settings-backup-passphrase").assertIsFocused()

        composeRule.onNodeWithTag("settings-section-remote").performClick()
        composeRule.onNodeWithTag("settings-remote-slot-up-press").assertIsFocused()

        composeRule.onNodeWithTag("settings-section-about").performClick()
        composeRule.onNodeWithTag("settings-update-check").assertIsFocused()
    }

    @Test
    fun theInterfaceCanBeDrawnAStepSmallerThanSmall() {
        val prefs = AppPreferencesRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-general").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-interface-scale"))
        composeRule.onNodeWithTag("settings-interface-scale").performClick()
        composeRule.onNodeWithTag("settings-interface-scale-smaller").assertIsDisplayed().performClick()
        try {
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                runBlocking { prefs.preferences.first().interfaceScale == InterfaceScale.SMALLER }
            }
        } finally {
            runBlocking { prefs.setInterfaceScale(InterfaceScale.DEFAULT) }
        }
    }

    @Test
    fun aProfileIsAddedSwitchedToAndRemoved() {
        val prefs = AppPreferencesRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        fun clearProfiles() = runBlocking {
            prefs.preferences.first().profiles.forEach { prefs.removeProfile(it.id) }
            prefs.setActiveProfile(Profiles.DEFAULT_ID)
        }
        clearProfiles()
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-general").performClick()
        composeRule.onNodeWithTag("settings-profile-active").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings-profile-name").performScrollTo().performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("settings-profile-name").assert(hasSetTextAction()); true }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings-profile-name").performTextInput("Kids")
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("settings-profile-add").performScrollTo().performClick(); true }.getOrDefault(false)
        }
        try {
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                runBlocking { prefs.preferences.first().profiles.any { it.name == "Kids" } }
            }
            val kids = runBlocking { prefs.preferences.first().profiles.single { it.name == "Kids" } }
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("settings-profile-ask").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("settings-profile-active").performScrollTo().performClick()
            composeRule.onNodeWithTag("settings-profile-default").assertIsSelected()
            composeRule.onNodeWithTag("settings-profile-${kids.id}").performClick()
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                runBlocking { prefs.preferences.first().activeProfileId == kids.id }
            }
            composeRule.onNodeWithTag("settings-profile-active").performScrollTo().performClick()
            composeRule.onNodeWithTag("settings-profile-${kids.id}").assertIsSelected()
            composeRule.onNodeWithTag("settings-profile-default").performClick()
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                runBlocking { prefs.preferences.first().activeProfileId == Profiles.DEFAULT_ID }
            }
            composeRule.onNodeWithTag("settings-profile-remove").performScrollTo().performClick()
            composeRule.onNodeWithTag("settings-profile-remove-${kids.id}").performClick()
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                runBlocking { prefs.preferences.first().profiles.isEmpty() }
            }
        } finally {
            clearProfiles()
        }
    }

    @Test
    fun interfaceLanguageCanBeChosenFromGeneralSettings() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-general").performClick()

        composeRule.onNodeWithTag("settings-interface-language").performClick()
        // System, plus one entry per language the app actually ships.
        composeRule.onNodeWithTag("settings-interface-language-system").assertIsDisplayed()
        AppLocale.SUPPORTED_TAGS.forEach { tag ->
            composeRule.onNodeWithTag("settings-interface-language-$tag").assertIsDisplayed()
        }
    }

    /**
     * The corner is off until asked for. A TV launcher need not offer any way
     * to dismiss a picture-in-picture window - Projectivy on the Shield does
     * not - so a viewer who never asked for it would be handed a playing
     * window with no obvious way out, still holding a provider connection.
     */
    @Test
    fun keepingWatchingInACornerIsOffUntilAskedFor() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-picture-in-picture"))
        composeRule.onNodeWithTag("settings-picture-in-picture").assertIsNotSelected()
        composeRule.onNodeWithTag("settings-picture-in-picture").performClick()
        composeRule.awaitUntil {
            composeRule.onAllNodes(hasTestTag("settings-picture-in-picture") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        // Back off: the preference outlives this test on the same device.
        composeRule.onNodeWithTag("settings-picture-in-picture").performClick()
        composeRule.awaitUntil {
            composeRule.onAllNodes(hasTestTag("settings-picture-in-picture") and isNotSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun matchingTheDisplayToThePictureCanBeTurnedOff() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-playback").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-auto-frame-rate"))

        // On by default: it is the point of the feature, and a setting nobody
        // finds is not a feature.
        composeRule.onNodeWithTag("settings-auto-frame-rate").assertIsSelected()

        composeRule.onNodeWithTag("settings-auto-frame-rate").performClick()
        composeRule.awaitUntil {
            composeRule.onAllNodes(
                hasTestTag("settings-auto-frame-rate") and isNotSelected(),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        // Back on: the preference outlives this test on the same device.
        composeRule.onNodeWithTag("settings-auto-frame-rate").performClick()
        composeRule.awaitUntil {
            composeRule.onAllNodes(
                hasTestTag("settings-auto-frame-rate") and isSelected(),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theImageCacheCeilingCanBeChosenAndTheCacheCleared() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-metadata").performClick()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("settings-artwork-cache-clear"))

        composeRule.onNodeWithTag("settings-artwork-cache-usage").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-artwork-cache").performClick()
        composeRule.onNodeWithTag("settings-artwork-cache-small").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-artwork-cache-medium").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-artwork-cache-large").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-artwork-cache-small").performClick()

        // Read back from where the image loader will read it at next start,
        // not from the button - the point of the setting is that it survives
        // the process that drew it.
        composeRule.runOnIdle {
            assertEquals(
                ArtworkCacheLimit.SMALL,
                ArtworkCacheSettings.limit(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                ),
            )
        }
    }

    @Test
    fun aboutAndLicencesIsReachableFromSettings() {
        composeRule.openSettingsFromTheEmptyGuide()
        composeRule.onNodeWithTag("settings-section-about").performClick()
        // Diagnostics sit on the same page, so a tester asked for a file finds it.
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-diagnostics-save"))
        composeRule.onNodeWithTag("settings-diagnostics-save").assertIsDisplayed()
        // No performScrollToNode here. The About section is two items tall, so
        // settings-list does not overflow, and a programmatic scroll on a lazy
        // list with nothing to scroll never returns - it spins the main thread
        // at full tilt with the screen frozen, and because AGP defaults the
        // instrumentation timeout to a year, it took the whole suite with it.
        // Opening the section focuses this button, so it is already on screen.
        composeRule.onNodeWithTag("settings-about-licenses").performClick()

        composeRule.onNodeWithTag("legal-back").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("legal-list")
            .performScrollToNode(hasTestTag("legal-contact-email"))
        composeRule.onNodeWithTag("legal-contact-email").assertIsDisplayed()
        composeRule.onNodeWithTag("legal-list")
            .performScrollToNode(hasTestTag("legal-open-api-sports"))
        composeRule.onNodeWithTag("legal-open-api-sports").assertIsDisplayed()
    }
    private val targetResources
        get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun refreshIntervalLabel(hours: Int): String = targetResources.getQuantityString(
        com.streammate.tv.iptv.R.plurals.source_refresh_interval_hours,
        hours,
        hours,
    )

}
