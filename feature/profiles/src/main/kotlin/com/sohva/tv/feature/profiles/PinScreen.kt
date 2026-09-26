package com.sohva.tv.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.model.profile.ParentalPin
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a PIN screen guards (spec 04 PROF-FR-50): its heading. */
enum class PinGateKind { LOCKED_CHANNEL, SWITCH_PROFILE, SETTINGS }

/** The PIN field's state: digits typed, a check running, the last check wrong; [focusTurn] moves on after a wrong PIN. */
data class PinState(val pin: String = "", val checking: Boolean = false, val wrong: Boolean = false, val focusTurn: Int = 0)

/**
 * One PIN screen's state (PROF-FR-51): [verify] runs off the main thread in the store; a wrong PIN
 * clears the field and says so, with no limit on tries (decision "Spec 04 open questions", Q1).
 */
class PinModel(private val verify: suspend (String) -> Boolean, val pinExists: Boolean, private val onUnlock: () -> Unit) : ViewModel() {
    private val _state = MutableStateFlow(PinState())
    val state: StateFlow<PinState> = _state.asStateFlow()

    fun type(input: String) = _state.update { it.copy(pin = ParentalPin.cut(input)) }

    fun canUnlock(s: PinState): Boolean = pinExists && s.pin.length >= ParentalPin.MIN && !s.checking

    fun unlock() {
        val s = _state.value
        if (!canUnlock(s)) return
        _state.value = s.copy(checking = true)
        viewModelScope.launch {
            if (verify(s.pin)) {
                _state.update { it.copy(checking = false) }
                onUnlock()
            } else {
                _state.update { PinState(wrong = true, focusTurn = it.focusTurn + 1) }
            }
        }
    }
}

/**
 * The PIN screen of the three gates and the start-time switch (spec 04 §4.6, §5.2): brand,
 * heading, what is locked, the prompt, the masked field, Back and Unlock. The field takes focus on
 * entry and again after a wrong PIN (PROF-FR-53).
 */
@Composable
fun PinScreen(model: PinModel, kind: PinGateKind, subject: String, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val field = remember { FocusRequester() }
    ScreenBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = Sohva.spacing.safeHorizontal, vertical = Sohva.spacing.safeVertical).testTag("screen-pin"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SohvaTvBrand(Modifier.align(Alignment.Start))
            Text(stringResource(heading(kind)), style = Sohva.typography.display, color = Sohva.palette.textPrimary)
            Text(subject, style = Sohva.typography.headline.copy(fontSize = 22.sp), color = Sohva.palette.focus, maxLines = 1)
            val message = when {
                !model.pinExists -> R.string.pin_not_configured
                state.wrong -> R.string.pin_wrong
                else -> R.string.pin_prompt
            }
            Text(stringResource(message), style = Sohva.typography.body, color = Sohva.palette.textMuted, modifier = Modifier.testTag("parental-message"))
            TvUrlField(
                value = state.pin,
                onValueChange = model::type,
                label = stringResource(R.string.pin_code),
                modifier = Modifier.width(360.dp).focusRequester(field).testTag("parental-pin"),
                input = PIN_INPUT,
                hint = "●  " + stringResource(R.string.pin_code),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                TvActionButton(stringResource(R.string.action_back), onBack, Modifier.testTag("parental-back"), icon = TvIcons.Back)
                TvActionButton(
                    stringResource(if (state.checking) R.string.pin_checking else R.string.pin_unlock),
                    model::unlock,
                    Modifier.testTag("parental-unlock"),
                    icon = TvIcons.Check,
                    state = SurfaceState(enabled = model.canUnlock(state), keepsFocus = state.checking),
                )
            }
        }
    }
    LaunchedEffect(state.focusTurn) { field.requestFocusWhenAttached() }
}

private fun heading(kind: PinGateKind): Int = when (kind) {
    PinGateKind.LOCKED_CHANNEL -> R.string.pin_locked_channel
    PinGateKind.SWITCH_PROFILE -> R.string.pin_profile_gate
    PinGateKind.SETTINGS -> R.string.pin_settings_gate
}

private val PIN_INPUT = FieldInput(keyboard = KeyboardType.NumberPassword, transformation = PasswordVisualTransformation())
