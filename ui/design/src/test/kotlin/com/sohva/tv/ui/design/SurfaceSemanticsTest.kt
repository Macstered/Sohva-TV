package com.sohva.tv.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.components.DialogTitle
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsSwitch
import com.sohva.tv.ui.design.components.SettingsSwitchRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.theme.SohvaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a screen reader is told: only a selected control says "selected" (every button used to
 * announce "not selected"), a switch is a switch that is on or off, and titles are headings.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-xhdpi")
class SurfaceSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun config(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config

    @Test
    fun onlyASelectedControlReportsSelection() {
        compose.setContent {
            SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = true) {
                Column {
                    TvActionButton("Plain", {}, Modifier.testTag("plain"))
                    TvActionButton("Chosen", {}, Modifier.testTag("chosen"), state = SurfaceState(selected = true))
                }
            }
        }
        assertNull(config("plain").getOrNull(SemanticsProperties.Selected))
        assertEquals(true, config("chosen").getOrNull(SemanticsProperties.Selected))
        assertEquals(Role.Button, config("plain").getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun aSwitchIsASwitchThatIsOnOrOff() {
        compose.setContent {
            SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = true) {
                Column {
                    SettingsSwitch(true, {}, Modifier.testTag("on"))
                    SettingsSwitchRow("Channel numbers", false, {}, Modifier.testTag("off"))
                }
            }
        }
        assertEquals(Role.Switch, config("on").getOrNull(SemanticsProperties.Role))
        assertEquals(ToggleableState.On, config("on").getOrNull(SemanticsProperties.ToggleableState))
        assertEquals(Role.Switch, config("off").getOrNull(SemanticsProperties.Role))
        assertEquals(ToggleableState.Off, config("off").getOrNull(SemanticsProperties.ToggleableState))
        assertNull(config("on").getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun sectionAndDialogTitlesAreHeadings() {
        compose.setContent {
            SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = true) {
                Column {
                    SettingsOverline("Sources", Modifier.testTag("overline"))
                    DialogTitle("Remove source", Modifier.testTag("title"))
                }
            }
        }
        assertEquals(Unit, config("overline").getOrNull(SemanticsProperties.Heading))
        assertEquals(Unit, config("title").getOrNull(SemanticsProperties.Heading))
    }
}
