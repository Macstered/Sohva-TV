package com.sohva.tv.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.RefreshState
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.errorMessage
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * No channels yet (GUIDE-FR-101, guide.md §10): per enabled source its playlist and guide health,
 * "Sync now" (first focus, only with sources) and "Open settings".
 */
@Composable
internal fun EmptyLibrary(model: GuideModel, navigation: GuideNavigation) {
    val saved by model.savedSources.collectAsStateWithLifecycle()
    val health by model.health.collectAsStateWithLifecycle()
    val enabled = saved.filter { it.enabled }
    val first = remember { FocusRequester() }
    val palette = Sohva.palette
    Column(
        Modifier.widthIn(max = 720.dp).roundFill(palette.surface, Sohva.shapes.large).padding(28.dp).testTag("guide-empty"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.guide_empty_title), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Black), color = palette.textPrimary)
        Text(
            stringResource(if (enabled.isNotEmpty()) R.string.guide_empty_sources_description else R.string.guide_empty_description),
            style = Sohva.typography.body,
            color = palette.textMuted,
        )
        for (source in enabled) {
            Text(source.name, Modifier.padding(top = 6.dp), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = palette.textPrimary)
            for (kind in listOf(RefreshKind.PLAYLIST, RefreshKind.EPG)) {
                val h = health.firstOrNull { it.sourceId == source.id && it.kind == kind }
                Text(
                    healthLine(kind, h),
                    style = Sohva.typography.label.copy(fontSize = 13.sp),
                    color = if (h?.state == RefreshState.FAILED) palette.danger else palette.textMuted,
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (enabled.isNotEmpty()) {
                TvActionButton(stringResource(R.string.guide_empty_sync), { model.syncAll() }, Modifier.focusRequester(first).testTag("guide-empty-sync"), icon = TvIcons.Refresh)
            }
            TvActionButton(
                stringResource(R.string.guide_open_settings),
                { navigation.openSettings() },
                (if (enabled.isEmpty()) Modifier.focusRequester(first) else Modifier).testTag("guide-empty-settings"),
                icon = TvIcons.Settings,
            )
        }
        if (enabled.isNotEmpty()) {
            Text(stringResource(R.string.guide_empty_sync_hint), style = Sohva.typography.label.copy(fontSize = 13.sp), color = palette.textDim)
        }
    }
    LaunchedEffect(enabled.isEmpty()) { first.requestFocusWhenAttached() }
}

@Composable
private fun healthLine(kind: RefreshKind, health: SourceHealth?): String {
    val label = stringResource(if (kind == RefreshKind.PLAYLIST) R.string.health_playlist else R.string.health_epg)
    return when (health?.state) {
        null, RefreshState.IDLE -> stringResource(R.string.health_never, label)
        RefreshState.RUNNING -> stringResource(R.string.health_updating, label)
        RefreshState.SUCCESS -> pluralStringResource(R.plurals.health_success, health.itemCount, label, health.itemCount)
        RefreshState.FAILED -> {
            val error = health.error
            if (error == null || error == AppError.Unknown) {
                stringResource(R.string.health_failed, label, health.consecutiveFailures)
            } else {
                stringResource(R.string.health_failed_detail, label, errorMessage(error))
            }
        }
    }
}
