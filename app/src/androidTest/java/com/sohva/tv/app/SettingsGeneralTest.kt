package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.prefs.ArtworkCacheStore
import com.sohva.tv.core.model.player.SkipStep
import com.sohva.tv.core.model.player.SubtitleSize
import com.sohva.tv.core.model.settings.ArtworkCacheLimit
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 70 §11 "UI": each section's OK lands on its first control; pickers open on the current
 * value, persist a choice and return focus to their row, Back leaves the value; the time-zone
 * dialog; VOD language pairing; the image cache limit.
 */
@RunWith(AndroidJUnit4::class)
class SettingsGeneralTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val prefs get() = graph.data.preferences

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(8_000) { focused(tag) }
        } catch (e: Throwable) {
            val now = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("waiting for $tag, focused: $now", e)
        }
    }

    private fun walkTo(tag: String, keyCode: Int = KeyEvent.KEYCODE_DPAD_DOWN, limit: Int = 30) {
        repeat(limit) {
            if (focused(tag)) return
            press(keyCode)
        }
        awaitFocus(tag)
    }

    private fun label(tag: String): String = runCatching {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun screenshot(name: String) {
        val image = instrumentation.uiAutomation.takeScreenshot()
        val file = java.io.File(graph.app.filesDir, "french-$name.png")
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    /** Settings opens on Playlists (SET-FR-05); Left to the rail, then OK on [section]. */
    private fun openSection(section: String, first: String) {
        compose.waitUntil(10_000) { exists(RailItem.SETTINGS.tag) }
        compose.onNodeWithTag(RailItem.SETTINGS.tag).performSemanticsAction(SemanticsActions.OnClick)
        selectSectionFromPlaylists(section, first)
    }

    private fun selectSectionFromPlaylists(section: String, first: String) {
        awaitFocus("source-add-m3u")
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("settings-section-sources")
        walkTo("settings-section-$section", if (section == "general") KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // OK on a rail row moves focus to the section's first control (SET-FR-12, -14).
        awaitFocus(first)
    }

    /** SET-FR-50, -21: System default and all languages, current choice focused, Back changes nothing. */
    @Test
    fun theLanguagePickerListsTheNineChoicesAndBackChangesNothing() {
        openSection("general", "settings-language")
        assertTrue(label("settings-language").contains("System default"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-language-0")
        for (i in 1..8) {
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            awaitFocus("settings-language-$i")
        }
        press(KeyEvent.KEYCODE_BACK)
        awaitFocus("settings-language")
        assertTrue(label("settings-language").contains("System default"))
    }

    @Test
    fun frenchCanBeChosenWithTheRemoteAndClearedAgain() {
        runBlocking {
            prefs.setVodLanguage(com.sohva.tv.core.model.settings.VodLanguageSlot.AUDIO, "fi")
            prefs.setVodLanguage(com.sohva.tv.core.model.settings.VodLanguageSlot.SUBTITLES, "en")
        }
        openSection("general", "settings-language")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-language-0")
        press(KeyEvent.KEYCODE_DPAD_DOWN, 8)
        awaitFocus("settings-language-8")
        assertTrue(label("settings-language-8"), label("settings-language-8").contains("Français (brouillon)"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // The Settings destination survives; its recreated model starts on Playlists.
        compose.waitUntil(10_000) { AppLocales.chosen(graph.app) == "fr" && label("source-add-m3u").contains("Ajouter") }
        selectSectionFromPlaylists("general", "settings-language")
        compose.waitUntil(5_000) { label("settings-language").contains("Français (brouillon)") }
        assertTrue(label("settings-language"), label("settings-language").contains("Français (brouillon)"))
        assertTrue(label("settings-language"), label("settings-language").contains("Langue"))
        screenshot("settings")
        assertTrue(runBlocking { prefs.playback() }.vodLanguages.let { it.audio == "fi" && it.subtitles == "en" })
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { exists("home-nav-home") }
        screenshot("home")
        openSection("general", "settings-language")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-language-8")
        screenshot("language")
        press(KeyEvent.KEYCODE_DPAD_UP, 8)
        awaitFocus("settings-language-0")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { AppLocales.chosen(graph.app) == null && label("source-add-m3u").contains("Add M3U") }
        selectSectionFromPlaylists("general", "settings-language")
        assertTrue(label("settings-language"), label("settings-language").contains("System default"))
    }

    /**
     * A tester reported that Down from Colour theme skipped Channel numbers and landed on Time zone,
     * so the numbers could not be turned off; the owner's boxes landed correctly. Focus search weighs
     * the distance down against the distance sideways: the switch sits at the far right of its row,
     * so on a wider pane (a smaller interface size or a lower screen density) the full-width Time zone
     * row below it won. Every interface size must stop on Channel numbers, both ways.
     */
    @Test
    fun downFromTheThemeStopsOnChannelNumbersAtEveryInterfaceSize() {
        for (scale in com.sohva.tv.core.model.settings.InterfaceScale.entries) {
            kotlinx.coroutines.runBlocking { prefs.setScale(scale) }
            if (!exists("settings-color-theme")) openSection("general", "settings-language")
            walkTo("settings-color-theme")
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            try {
                awaitFocus("settings-channel-numbers")
            } catch (e: AssertionError) {
                throw AssertionError("interface size $scale: ${e.message}", e)
            }
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            awaitFocus("settings-time-zone")
            press(KeyEvent.KEYCODE_DPAD_UP)
            awaitFocus("settings-channel-numbers")
            press(KeyEvent.KEYCODE_DPAD_UP)
            awaitFocus("settings-color-theme")
        }
    }

    /** SET-FR-52, -21: a theme applies at once and focus comes back to its row. */
    @Test
    fun aColourThemeAppliesAtOnceAndFocusReturnsToItsRow() {
        openSection("general", "settings-language")
        walkTo("settings-color-theme")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-color-theme-0")
        press(KeyEvent.KEYCODE_DPAD_DOWN, 6)
        awaitFocus("settings-color-theme-6")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-color-theme")
        compose.waitUntil(5_000) { runBlocking { prefs.theme.first() } == ColorThemeId.KANAGAWA }
        compose.waitUntil(5_000) { label("settings-color-theme").contains("Kanagawa") }
    }

    /** SET-FR-54, §4.6: the dialog opens on the TV's own zone; a search finds Helsinki; choosing it and back to the TV's own. */
    @Test
    fun theTimeZoneDialogSearchesAndChooses() {
        openSection("general", "settings-language")
        walkTo("settings-time-zone")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-time-zone-device")
        compose.onNodeWithTag("settings-time-zone-search").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("helsinki")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { exists("settings-time-zone-zone:Europe/Helsinki") && !exists("settings-time-zone-device") }
        compose.onNodeWithTag("settings-time-zone-zone:Europe/Helsinki").performSemanticsAction(SemanticsActions.OnClick)
        awaitFocus("settings-time-zone")
        compose.waitUntil(5_000) { runBlocking { prefs.timeZone.first() } == "Europe/Helsinki" }
        assertTrue(label("settings-time-zone"), label("settings-time-zone").contains("Helsinki · UTC"))
        // Opens on the chosen zone under Recent (SET-FR-64); the TV's own clears the choice.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-time-zone-recent:Europe/Helsinki")
        compose.onNodeWithTag("settings-time-zone-device").performSemanticsAction(SemanticsActions.OnClick)
        awaitFocus("settings-time-zone")
        compose.waitUntil(5_000) { runBlocking { prefs.timeZone.first() } == null }
    }

    /** SET-FR-70, -71: Playback opens on the buffer row; skip step and subtitle size persist. */
    @Test
    fun playbackPickersPersistTheirChoices() {
        openSection("playback", "settings-playback-buffer")
        walkTo("settings-playback-seek-step")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-playback-seek-step-0")
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-playback-seek-step")
        compose.waitUntil(5_000) { runBlocking { prefs.playback() }.skipStep == SkipStep.ONE_MINUTE }
        walkTo("settings-subtitle-size")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-subtitle-size-0")
        press(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-subtitle-size")
        compose.waitUntil(5_000) { runBlocking { prefs.playback() }.subtitleSize == SubtitleSize.LARGE }
        // Picture in picture is off by default and a switch (SET-FR-70 item 6).
        walkTo("settings-picture-in-picture", KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { runBlocking { prefs.playback() }.pictureInPicture }
    }

    /** SET-FR-72: choosing the partner row's language clears the partner. */
    @Test
    fun choosingThePartnersLanguageClearsIt() {
        runBlocking { prefs.setVodLanguage(com.sohva.tv.core.model.settings.VodLanguageSlot.AUDIO_SECOND, "en") }
        openSection("playback", "settings-playback-buffer")
        walkTo("settings-vod-language-audio")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-vod-language-audio-0")
        // Automatic, Finnish, English.
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-vod-language-audio")
        compose.waitUntil(5_000) { runBlocking { prefs.playback() }.vodLanguages.audio == "en" }
        assertNull(runBlocking { prefs.playback() }.vodLanguages.audioSecond)
        compose.waitUntil(5_000) { label("settings-vod-language-audio_second").contains("Automatic") }
    }

    /** SET-FR-80: the image cache limit is written for the next start. */
    @Test
    fun theImageCacheLimitIsSaved() {
        openSection("metadata", "settings-metadata-tmdb-enabled")
        walkTo("settings-artwork-limit")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-artwork-limit-1")
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitFocus("settings-artwork-limit")
        try {
            compose.waitUntil(5_000) { ArtworkCacheStore(graph.app).limit() == ArtworkCacheLimit.LARGE }
            compose.waitUntil(5_000) { label("settings-artwork-limit").contains("500 MB") }
        } finally {
            ArtworkCacheStore(graph.app).setLimit(ArtworkCacheLimit.MEDIUM)
        }
    }
}
