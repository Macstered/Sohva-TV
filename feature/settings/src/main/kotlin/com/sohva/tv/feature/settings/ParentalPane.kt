package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.profile.ParentalPin
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Settings › Parental controls (spec 04 PROF-FR-33, §5.3): without a PIN, a new PIN and Enable;
 * with one, the current PIN, an optional new PIN and "Remove / change PIN" (decision "Spec 04 open
 * questions", Q7). The PIN field takes the section's focus.
 */
@Composable
internal fun ParentalPane(profiles: ProfileSettings, start: FocusRequester) {
    val household by profiles.household.collectAsStateWithLifecycle()
    val state by profiles.state.collectAsStateWithLifecycle()
    val configured = household.pinConfigured
    SettingsOverline(stringResource(R.string.settings_section_parental))
    SettingsGroup {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.parental_title),
                style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold),
                color = Sohva.palette.textMuted,
            )
            TvUrlField(
                value = state.pin,
                onValueChange = profiles::typePin,
                label = stringResource(if (configured) R.string.parental_current_pin else R.string.parental_new_pin),
                modifier = Modifier.width(330.dp).focusRequester(start).testTag("settings-parental-pin"),
                icon = TvIcons.Key,
                input = PIN_FIELD,
            )
            if (configured) {
                TvUrlField(
                    value = state.newPin,
                    onValueChange = profiles::typeNewPin,
                    label = stringResource(R.string.parental_new_pin),
                    modifier = Modifier.width(330.dp).testTag("settings-parental-new-pin"),
                    icon = TvIcons.Key,
                    input = PIN_FIELD,
                )
            }
            val ready = ParentalPin.valid(state.pin) && (state.newPin.isEmpty() || ParentalPin.valid(state.newPin)) && !state.busy
            if (configured) {
                TvActionButton(
                    stringResource(R.string.parental_remove_change),
                    profiles::removeOrChangePin,
                    Modifier.testTag("settings-parental-clear"),
                    state = SurfaceState(enabled = ready, danger = true, keepsFocus = state.busy),
                    compact = true,
                )
            } else {
                TvActionButton(
                    stringResource(R.string.parental_enable),
                    profiles::enablePin,
                    Modifier.testTag("settings-parental-save"),
                    state = SurfaceState(enabled = ready, keepsFocus = state.busy),
                    compact = true,
                )
            }
        }
    }
    StatusGroup(listOfNotNull(state.status?.resolve()), securityNote = false)
}

private val PIN_FIELD = FieldInput(keyboard = KeyboardType.NumberPassword, transformation = PasswordVisualTransformation(), compact = true)
