package com.sohva.tv.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import com.sohva.tv.ui.design.components.PickerChoice
import com.sohva.tv.ui.design.components.SettingsSwitch
import com.sohva.tv.ui.design.components.SettingsRow
import com.sohva.tv.ui.design.components.SettingsValueRow
import com.sohva.tv.ui.design.components.SinglePickerDialog
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached

/**
 * A value row with its own single-choice picker (spec 70 SET-FR-20, -21, -25): the value is the
 * current choice's label (the first choice's when the value is unknown); the picker opens on the
 * current value; a choice closes it, returns focus to the row and is written only when it differs
 * from the current one. The picker's options are tagged `<tag>-<index>`.
 */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    value: T,
    choices: List<PickerChoice<T>>,
    onChoose: (T) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    divider: Boolean = false,
) {
    var picking by remember { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    val row = remember { FocusRequester() }
    SettingsValueRow(
        title = title,
        value = (choices.firstOrNull { it.value == value } ?: choices.firstOrNull())?.label.orEmpty(),
        onClick = { picking = true },
        modifier = modifier.focusRequester(row).testTag(tag),
        icon = icon,
        subtitle = subtitle,
        divider = divider,
    )
    if (picking) {
        val close = {
            picking = false
            returnFocus = true
        }
        SinglePickerDialog(
            title = title,
            choices = choices.mapIndexed { i, c -> c.copy(tag = c.tag ?: "$tag-$i") },
            current = value,
            onChoose = {
                close()
                if (it != value) onChoose(it)
            },
            onDismiss = close,
            tag = "settings-picker",
        )
    }
    LaunchedEffect(returnFocus) {
        if (returnFocus) {
            row.requestFocusWhenAttached(RETURN_ATTEMPTS)
            returnFocus = false
        }
    }
}

/** A switch row (SET-FR-23): the switch is the focus target; OK flips it and writes at once. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    tag: String,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
) {
    SettingsRow(title, icon = icon, subtitle = subtitle) {
        SettingsSwitch(checked, onToggle, Modifier.testTag(tag))
    }
}

private const val RETURN_ATTEMPTS = 6
