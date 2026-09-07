package com.streammate.tv.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.streammate.tv.app.ArtworkCacheSettings
import com.streammate.tv.app.ArtworkCacheLimit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streammate.tv.iptv.R
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.requestFocusWhenAttached
import kotlinx.coroutines.launch

/** The group frame and heading every settings page is built from, and the image-cache group. */

/**
 * Settings groups read as sections of one list, separated by a hairline, rather
 * than as a stack of filled panels. The only filled surface on the screen is
 * whatever currently has focus.
 */
/**
 * The artwork cache: how much it may take, how much it has taken, and a way to
 * take it back.
 *
 * A library of a few thousand titles will fill whatever ceiling it is given, and
 * on a set-top box that disk is shared with recordings and every other app. It
 * was a flat gigabyte before, chosen by nobody.
 */
@Composable
internal fun ArtworkCacheGroup(onCleared: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var limit by remember { mutableStateOf(ArtworkCacheSettings.limit(context)) }
    var usageBytes by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }

    suspend fun refreshUsage() {
        usageBytes = ArtworkCache.usageBytes(context)
    }
    LaunchedEffect(Unit) { refreshUsage() }

    SettingsGroup {
        SettingsValueRow(
            title = stringResource(R.string.artwork_cache_title),
            subtitle = stringResource(R.string.artwork_cache_help),
            value = stringResource(R.string.artwork_cache_limit, limit.megabytes),
            icon = TvIcons.Save,
            onClick = { pickerOpen = true },
            focusRequester = rowFocus,
            divider = false,
            testTag = "settings-artwork-cache",
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SETTINGS_ROW_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = usageBytes?.let {
                    stringResource(R.string.artwork_cache_usage, ArtworkCache.formatBytes(it))
                }.orEmpty(),
                color = palette.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f).testTag("settings-artwork-cache-usage"),
            )
            TvActionButton(
                label = stringResource(R.string.artwork_cache_clear),
                icon = TvIcons.Delete,
                enabled = !clearing && (usageBytes ?: 0L) > 0L,
                onClick = {
                    scope.launch {
                        clearing = true
                        ArtworkCache.clear(context)
                        refreshUsage()
                        clearing = false
                        onCleared()
                    }
                },
                compact = true,
                testTag = "settings-artwork-cache-clear",
            )
        }
    }
    if (pickerOpen) {
        SettingsPickerDialog(
            title = stringResource(R.string.artwork_cache_title),
            options = ArtworkCacheLimit.entries.map { option ->
                SettingsPickerOption(
                    option, stringResource(R.string.artwork_cache_limit, option.megabytes),
                    testTag = "settings-artwork-cache-${option.name.lowercase()}",
                )
            },
            selected = limit,
            onSelect = { option ->
                limit = option
                ArtworkCacheSettings.setLimit(context, option)
                pickerOpen = false
                scope.launch { rowFocus.requestFocusWhenAttached() }
            },
            onDismiss = { pickerOpen = false; scope.launch { rowFocus.requestFocusWhenAttached() } },
        )
    }
}

/** The uppercase label naming a group, inside the group's own margin. */
@Composable
internal fun SettingsGroupHeading(text: String) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Text(
        text = text.uppercase(),
        color = palette.textDim,
        fontSize = typography.overline.fontSize,
        lineHeight = typography.overline.lineHeight,
        fontWeight = FontWeight.Bold,
        letterSpacing = typography.overline.letterSpacing,
    )
}

/**
 * One group of settings.
 *
 * A hairline above it and the pane's own left margin down the side: no panel,
 * no outline, no second background. The screen reads as one list whose only
 * filled surface is whatever currently has focus.
 */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val palette = StreamMateThemeTokens.palette
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SETTINGS_ROW_PADDING)
                .height(1.dp)
                .background(palette.divider),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SETTINGS_ROW_PADDING, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
