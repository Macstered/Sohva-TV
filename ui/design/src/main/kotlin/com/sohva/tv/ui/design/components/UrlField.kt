package com.sohva.tv.ui.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** What kind of text a [TvUrlField] holds. */
@Immutable
data class FieldInput(
    val keyboard: KeyboardType = KeyboardType.Uri,
    /** Passwords and PINs show dots on the display box too. */
    val transformation: VisualTransformation = VisualTransformation.None,
    val compact: Boolean = false,
)

/**
 * Every text entry: addresses, keys, PINs, names (design/02 §9). The box is a focusable button
 * that shows the value (or [label] as a hint); OK opens the edit dialog with the keyboard. The
 * focus flip is instant, with no scale.
 */
@Composable
fun TvUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    input: FieldInput = FieldInput(),
    state: SurfaceState = SurfaceState(),
) {
    var editing by remember { mutableStateOf(false) }
    // Set only when the dialog closes, so focus returns to the box and never lands on it unasked.
    var returnFocus by remember { mutableStateOf(false) }
    val display = remember { FocusRequester() }
    val shape = if (input.compact) Sohva.shapes.small else Sohva.shapes.medium
    val style = SurfaceStyle(
        corner = shape,
        resting = Sohva.palette.surface,
        restingContent = Sohva.palette.textPrimary,
        focusScale = 1f,
        animateFill = false,
        padding = if (input.compact) PaddingValues(12.dp, 8.dp) else PaddingValues(16.dp, 14.dp),
    )
    TvSurface({ editing = true }, modifier.focusRequester(display), state, style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, size = 18.dp, tint = if (colors.focused) Sohva.palette.background.copy(alpha = 0.72f) else Sohva.palette.textMuted)
                Spacer(Modifier.width(if (input.compact) 8.dp else 12.dp))
            }
            val shown = if (value.isEmpty()) label else input.transformation.filter(AnnotatedString(value)).text.text
            val hintColor = if (colors.focused) colors.secondaryContent else Sohva.palette.textMuted
            Text(
                shown,
                style = if (input.compact) Sohva.typography.label.copy(fontWeight = FontWeight.Normal) else Sohva.typography.body,
                color = if (value.isEmpty()) hintColor else colors.content,
                maxLines = 1,
            )
        }
    }
    if (editing) {
        EditDialog(value, onValueChange, label, input) {
            editing = false
            returnFocus = true
        }
    }
    LaunchedEffect(returnFocus) {
        if (returnFocus) {
            display.requestFocusWhenAttached(FOCUS_RESTORE_ATTEMPTS)
            returnFocus = false
        }
    }
}

private const val FOCUS_RESTORE_ATTEMPTS = 6

/**
 * The platform dialog of design/02 §9: `scrim` α0.72, a `panel` card 0.62 of the width with large
 * corners and a 2 dp `focus` frame, the label as title, then the editor. Enter, Done or Back
 * closes it and hides the keyboard.
 */
@Composable
private fun EditDialog(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    input: FieldInput,
    onClose: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val editor = remember { FocusRequester() }
    val close = {
        keyboard?.hide()
        onClose()
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().roundFill(Sohva.palette.scrim.copy(alpha = 0.72f), 0.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.62f)
                    .border(2.dp, Sohva.palette.focus, RoundedCornerShape(Sohva.shapes.large))
                    .roundFill(Sohva.palette.panel, Sohva.shapes.large)
                    .padding(24.dp),
            ) {
                Text(label, style = Sohva.typography.headline, color = Sohva.palette.textPrimary)
                Spacer(Modifier.height(14.dp))
                val corner = if (input.compact) Sohva.shapes.small else Sohva.shapes.medium
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(editor)
                        .roundFill(Sohva.palette.surface, corner)
                        .padding(if (input.compact) PaddingValues(12.dp, 8.dp) else PaddingValues(16.dp, 14.dp)),
                    textStyle = Sohva.typography.body.copy(color = Sohva.palette.textPrimary),
                    cursorBrush = SolidColor(Sohva.palette.focus),
                    singleLine = true,
                    visualTransformation = input.transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = input.keyboard, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { close() }),
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        if (editor.requestFocusWhenAttached()) keyboard?.show()
    }
}
