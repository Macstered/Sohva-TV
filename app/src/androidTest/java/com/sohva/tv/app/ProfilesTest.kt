package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Profile
import com.sohva.tv.core.model.profile.Profiles
import com.sohva.tv.feature.home.RailItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 04 §11 "Instrumentation": the start-time picker, the PIN screen, the gates in front of a
 * locked channel, Settings and an unrestricted profile, a restricted profile's guide, and the rail
 * item. The household is written before the app starts, as a TV that already has profiles.
 */
@RunWith(AndroidJUnit4::class)
class ProfilesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph

    private val seed: ExternalResource = object : ExternalResource() {
        override fun before() = GuideFixture.seed(graph, groups = 3, perGroup = 4)
    }
    private val compose = createComposeRule()

    /** The household as a TV that already has it, then a cold start of the app (a fresh start state). */
    private fun start(household: suspend () -> Unit = {}, body: () -> Unit) {
        runBlocking {
            household()
            val stored = graph.data.preferences.household.first()
            graph.data.profiles.seed(stored)
            // Once seeded, the store follows the preferences on its own: wait until it has.
            kotlinx.coroutines.withTimeout(5_000) { graph.data.profiles.household.first { it == stored } }
        }
        ActivityScenario.launch(MainActivity::class.java).use { body() }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(seed).around(compose)

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(10_000) { focused(tag) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val now = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            throw AssertionError("$tag not focused; focused: $now", e)
        }
    }

    private fun click(tag: String) {
        compose.waitUntil(10_000) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        click(tag)
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
    }

    private fun text(tag: String): String = runCatching {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    /** A button's words: they sit on its children, merged into the button's node. */
    private fun label(tag: String): String = runCatching {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    }.getOrDefault("")

    private fun textAnywhere(value: String) = compose.onAllNodesWithTextExists(value, substring = true)

    private suspend fun twoProfiles(active: String = Profiles.DEFAULT_ID, ask: Boolean = false) {
        graph.data.preferences.editHousehold { it.copy(stored = listOf(Profile(KIDS, "Kids", 1)), activeId = active, askAtStart = ask) }
    }

    private suspend fun kidsSee(groupKey: String) = graph.data.profiles.setAllowed(KIDS, OrgRoom.LIVE, groupKey, true)

    private fun awaitHome() = compose.waitUntil(10_000) { exists(RailItem.LIVE_TV.tag) }

    private fun openGuide() {
        awaitHome()
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("guide-row-0") }
        awaitFocus("guide-row-0")
    }

    private fun enterPin(pin: String) {
        compose.waitUntil(10_000) { exists("screen-pin") }
        awaitFocus("parental-pin")
        type("parental-pin", pin)
        click("parental-unlock")
    }

    /** PROF-FR-10, -12, -13: asked at start with the last active profile focused; the choice opens its Home. */
    @Test
    fun theStartPickerFocusesTheLastProfileAndAChoiceOpensHome() = start({ twoProfiles(active = KIDS, ask = true) }) {
        compose.waitUntil(10_000) { exists("screen-profiles") }
        awaitFocus("profile-tile-$KIDS")
        assertTrue(exists("profile-tile-default"))
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("profile-tile-default")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        awaitHome()
        assertEquals(Profiles.DEFAULT_ID, graph.data.profiles.activeId)
        // Two profiles: Who is watching is on the rail (PROF-FR-16).
        assertTrue(exists(RailItem.PROFILES.tag))
    }

    /** PROF-FR-16: a profile added while the app runs puts Who is watching on the rail. */
    @Test
    fun aProfileAddedWhileRunningPutsWhoIsWatchingOnTheRail() = start {
        awaitHome()
        assertFalse(exists(RailItem.PROFILES.tag))
        runBlocking { twoProfiles() }
        try {
            compose.waitUntil(10_000) { exists(RailItem.PROFILES.tag) }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("store: ${graph.data.profiles.household.value}; prefs: ${runBlocking { graph.data.preferences.household.first() }}", e)
        }
    }

    /** PROF-FR-19, -22, SHELL-FR-20, -23: a locked channel meets the PIN; a wrong PIN says so, the right one plays. */
    @Test
    fun aLockedChannelMeetsThePinAndTheRightPinPlaysIt() = start({
        graph.data.profiles.setPin("2468")
        graph.data.profiles.setLocked("fixture-0:c0", true)
    }) {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-pin") }
        assertFalse(exists("screen-player"))
        enterPin("1357")
        compose.waitUntil(5_000) { text("parental-message") == "Incorrect PIN code" }
        // The field was cleared and has focus again (PROF-FR-53).
        awaitFocus("parental-pin")
        enterPin("2468")
        compose.waitUntil(10_000) { exists("screen-player") }
        assertFalse(exists("screen-pin"))
    }

    /** SHELL-FR-22, -23: zapping onto a locked channel asks for the PIN over the player; unlocking replaces both. */
    @Test
    fun zappingOntoALockedChannelMeetsThePin() = start({
        graph.data.profiles.setPin("2468")
        graph.data.profiles.setLocked("fixture-0:c1", true)
    }) {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-player") }
        // CH− steps to the next channel (REMOTE-FR-13): the locked one.
        press(KeyEvent.KEYCODE_CHANNEL_DOWN)
        enterPin("2468")
        compose.waitUntil(10_000) { exists("screen-player") && !exists("screen-pin") }
        // Back leaves to the guide on the channel just unlocked (SHELL-FR-23 rememberForGuide).
        repeat(3) { if (!exists("screen-guide")) press(KeyEvent.KEYCODE_BACK) }
        compose.waitUntil(10_000) { exists("screen-guide") }
        awaitFocus("guide-row-1")
    }

    /** PROF-FR-20, -21, SHELL-FR-32, -33: a restricted profile meets the PIN for Settings and for leaving; entering it does not. */
    @Test
    fun aRestrictedProfileMeetsThePinForSettingsAndForLeaving() = start({
        twoProfiles(active = KIDS)
        kidsSee("g1")
        graph.data.profiles.setPin("2468")
    }) {
        awaitHome()
        compose.focusRail(RailItem.SETTINGS)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-pin") }
        assertTrue(textAnywhere("Settings are locked for this profile"))
        click("parental-back")
        awaitHome()
        assertFalse(exists("screen-settings"))
        // Leaving for the unrestricted first viewer asks for the PIN.
        compose.focusRail(RailItem.PROFILES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-profiles") }
        awaitFocus("profile-tile-$KIDS")
        click("profile-tile-default")
        enterPin("2468")
        compose.waitUntil(10_000) { graph.data.profiles.activeId == Profiles.DEFAULT_ID }
        awaitHome()
        // Entering the restricted profile never asks.
        compose.focusRail(RailItem.PROFILES)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("screen-profiles") }
        click("profile-tile-$KIDS")
        compose.waitUntil(10_000) { graph.data.profiles.activeId == KIDS }
        assertFalse(exists("screen-pin"))
    }

    /** PROF-FR-12, -24, spec 04 §11 "Guide": a restricted profile sees only its groups; gone groups are explained. */
    @Test
    fun aRestrictedProfilesGuideHoldsOnlyItsGroups() = start({
        twoProfiles(active = KIDS)
        kidsSee("g1")
    }) {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("guide-rail-group:g1")
        assertFalse(exists("guide-rail-group:g0"))
        assertFalse(exists("guide-rail-group:g2"))
        // Only groups no source has: the empty list says why.
        runBlocking {
            graph.data.profiles.setAllowed(KIDS, OrgRoom.LIVE, "g1", false)
            kidsSee("gone")
        }
        // Back closes the rail, then leaves the guide; never past Home, which would close the app.
        repeat(3) { if (!exists("screen-home")) press(KeyEvent.KEYCODE_BACK) }
        compose.waitUntil(10_000) { exists("screen-home") }
        compose.focusRail(RailItem.LIVE_TV)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { text("guide-list-empty").startsWith("This profile's groups are not in the current sources") }
    }

    /** GUIDE-49: a change to the profile's groups applies while the guide is open. */
    @Test
    fun aRestrictionChangeAppliesWhileTheGuideIsOpen() = start({
        twoProfiles(active = KIDS)
        kidsSee("g1")
    }) {
        openGuide()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        awaitFocus("guide-rail-group:g1")
        assertFalse(exists("guide-rail-group:g2"))
        runBlocking { graph.data.profiles.setAllowed(KIDS, OrgRoom.LIVE, "g2", true) }
        compose.waitUntil(10_000) { exists("guide-rail-group:g2") }
        assertFalse(exists("guide-rail-group:g0"))
    }

    /** Spec 04 §11 "Settings": a profile is added, switched to, limited to chosen groups and removed with what it kept. */
    @Test
    fun settingsAddsSwitchesLimitsAndRemovesAProfile() = start {
        awaitHome()
        compose.focusRail(RailItem.SETTINGS)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // Settings opens on Playlists (SET-FR-02); the profiles are General's last group.
        click("settings-section-general")
        compose.waitUntil(10_000) { exists("settings-profile-name") }
        type("settings-profile-name", "Aino")
        click("settings-profile-add")
        compose.waitUntil(10_000) { exists("settings-profile-remove") }
        val aino = graph.data.profiles.household.value.shown[1].id
        // Who is watching: switch to Aino; Settings stays open (unrestricted).
        click("settings-profile-active")
        click("settings-profile-$aino")
        compose.waitUntil(10_000) { graph.data.profiles.activeId == aino }
        compose.waitUntil(10_000) { text("settings-profile-active").contains("Aino") || textAnywhere("Aino") }
        // Limit Aino to one live group: the value reads "1 group" and the note asks for a PIN.
        click("settings-profile-groups-live")
        compose.waitUntil(10_000) { exists("settings-profile-group-g0") }
        click("settings-profile-group-g0")
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { textAnywhere("1 group") }
        compose.waitUntil(10_000) { exists("settings-profile-content-pin") }
        assertTrue(runBlocking { graph.data.profiles.restriction(aino).live } == setOf("g0"))
        // Remove Aino: asked once, then gone with its groups; the first viewer is active again.
        click("settings-profile-remove")
        click("settings-profile-remove-$aino")
        compose.waitUntil(10_000) { exists("settings-profile-remove-confirm") }
        click("settings-profile-remove-yes")
        compose.waitUntil(10_000) { !exists("settings-profile-remove") }
        assertEquals(Profiles.DEFAULT_ID, graph.data.profiles.activeId)
        assertFalse(runBlocking { graph.data.profiles.restriction(aino).restricted })
    }

    /** PROF-FR-33, -40: a PIN is enabled in Parental controls; then Channel management locks a channel. */
    @Test
    fun parentalControlsEnableThePinAndChannelManagementLocksWithIt() = start {
        awaitHome()
        compose.focusRail(RailItem.SETTINGS)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(10_000) { exists("settings-section-parental") }
        click("settings-section-parental")
        compose.waitUntil(10_000) { exists("settings-parental-pin") }
        type("settings-parental-pin", "2468")
        click("settings-parental-save")
        compose.waitUntil(10_000) { graph.data.profiles.household.value.pinConfigured }
        compose.waitUntil(10_000) { textAnywhere("Parental control PIN saved securely") }
        assertTrue(runBlocking { graph.data.profiles.verifyPin("2468") })
        // Channel management now locks the first channel for this profile, without asking (PROF-FR-40).
        repeat(3) { if (!exists("screen-home")) press(KeyEvent.KEYCODE_BACK) }
        openGuide()
        press(KeyEvent.KEYCODE_MENU)
        click("guide-options-channels")
        compose.waitUntil(10_000) { exists("screen-channels") }
        awaitFocus("channels-row-fixture-0:c0")
        compose.waitUntil(5_000) { label("channels-lock") == "Lock with PIN" }
        click("channels-lock")
        compose.waitUntil(5_000) { text("channels-status") == "Channel locked with PIN" }
        compose.waitUntil(5_000) { label("channels-lock") == "Remove PIN lock" }
        assertTrue(runBlocking { graph.data.profiles.isLocked("fixture-0:c0") })
    }

    private companion object {
        const val KIDS = "p1790000000000"
    }
}
