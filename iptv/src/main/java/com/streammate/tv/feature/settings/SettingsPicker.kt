package com.streammate.tv.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.iptv.R
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvSurface
import com.streammate.tv.feature.common.requestFocusWhenAttached

/**
 * The two row shapes every setting is drawn with from phase 2 of the
 * settings restructure on, plus the picker a value row opens.
 *
 * A setting is a row: what it is on the left, its value or switch on the
 * right, one line of help beneath the title where the title is not enough.
 * A choice among several values opens [SettingsPickerDialog] rather than
 * spreading its options across the page; a yes/no setting is a
 * [SettingsSwitchRow] and applies at once.
 */

/** A setting that is on or off. The switch is the focus target; the whole row reads as one. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    focusRequester: FocusRequester? = null,
    testTag: String? = null,
) {
    SettingsRow(title = title, modifier = modifier, subtitle = subtitle, icon = icon, divider = divider) {
        SettingsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            focusRequester = focusRequester,
            testTag = testTag,
        )
    }
}

/** One choice in a picker: its value, the label shown, and an optional line saying what it does. */
internal data class SettingsPickerOption<T>(
    val value: T,
    val label: String,
    val description: String? = null,
    val testTag: String? = null,
)

/**
 * A list of choices over the page, the current one marked and focused.
 * Selecting closes it; Back closes it without a change. The caller returns
 * focus to the row that opened it.
 */
@Composable
internal fun <T> SettingsPickerDialog(
    title: String,
    options: List<SettingsPickerOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    testTag: String = "settings-picker",
) {
    val palette = StreamMateThemeTokens.palette
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val matchIndex = options.indexOfFirst { it.value == selected }
    val selectedIndex = matchIndex.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        listState.scrollToItem(selectedIndex)
        firstFocus.requestFocusWhenAttached()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(PICKER_WIDTH)
                .heightIn(max = PICKER_MAX_HEIGHT)
                .background(palette.panel, StreamMateThemeTokens.shapes.medium)
                .border(1.dp, palette.outline, StreamMateThemeTokens.shapes.medium)
                .padding(18.dp)
                .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEachIndexed { index, option ->
                    item(key = option.testTag ?: index) {
                        PickerRow(
                            option = option,
                            selected = matchIndex >= 0 && index == matchIndex,
                            onClick = { onSelect(option.value) },
                            focusRequester = if (index == selectedIndex) firstFocus else null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Several choices at once: a row toggles, a tick marks the chosen ones, and
 * Done closes. Back closes too and keeps what was toggled, since every
 * toggle is applied as it happens.
 */
@Composable
internal fun SettingsMultiPickerDialog(
    title: String,
    options: List<SettingsPickerOption<String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    testTag: String = "settings-multi-picker",
    /** Shown in place of the rows when there is nothing to choose from. */
    emptyText: String? = null,
) {
    val palette = StreamMateThemeTokens.palette
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWhenAttached() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(PICKER_WIDTH)
                .heightIn(max = PICKER_MAX_HEIGHT)
                .background(palette.panel, StreamMateThemeTokens.shapes.medium)
                .border(1.dp, palette.outline, StreamMateThemeTokens.shapes.medium)
                .padding(18.dp)
                .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
            )
            if (options.isEmpty() && emptyText != null) {
                Text(
                    text = emptyText,
                    color = palette.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                options.forEachIndexed { index, option ->
                    item(key = option.testTag ?: index) {
                        PickerRow(
                            option = option,
                            selected = option.value in selected,
                            onClick = { onToggle(option.value) },
                            focusRequester = if (index == 0) firstFocus else null,
                        )
                    }
                }
            }
            TvActionButton(
                label = stringResource(R.string.category_edit_done),
                onClick = onDismiss,
                icon = TvIcons.Check,
                compact = true,
                focusRequester = if (options.isEmpty()) firstFocus else null,
                testTag = "$testTag-done",
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun <T> PickerRow(
    option: SettingsPickerOption<T>,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = StreamMateThemeTokens.shapes.medium,
        selected = selected,
        resting = Color.Transparent,
        restingContent = palette.textPrimary,
        focusScale = 1f,
        focusRequester = focusRequester,
        testTag = option.testTag,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) { colors ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    color = colors.content,
                    fontSize = typography.body.fontSize,
                    lineHeight = typography.body.lineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                option.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 2.dp),
                        color = colors.secondaryContent,
                        fontSize = typography.label.fontSize,
                        lineHeight = typography.label.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.Image(
                    painter = painterResource(TvIcons.Check),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.content),
                    modifier = Modifier.size(18.dp).clearAndSetSemantics { },
                )
            }
        }
    }
}

private val PICKER_WIDTH = 560.dp
private val PICKER_MAX_HEIGHT = 620.dp
