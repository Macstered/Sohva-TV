package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.RefreshState
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.errorMessage
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * A section's status group (spec 10 SRC-FR-37): the message and, on a source page, the health
 * summary joined by " · " in `focus`; under it, on Playlists, the security note. No spinner:
 * progress is text (settings.md §9).
 */
@Composable
internal fun StatusGroup(parts: List<String>, securityNote: Boolean) {
    val line = parts.filter { it.isNotBlank() }.joinToString(" · ")
    SettingsGroup {
        if (line.isNotEmpty()) {
            Text(line, Modifier.testTag("settings-status"), style = Sohva.typography.label, color = Sohva.palette.focus)
        }
        if (securityNote) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.settings_security_subtitle), style = Sohva.typography.caption, color = Sohva.palette.textDim)
        }
    }
}

/**
 * One source's health (SRC-FR-39): a part per kind that has a state, in kind-name order
 * (catalogue, guide, playlist), each "Kind: updating", "Kind: N items" or "Kind: reason".
 */
@Composable
internal fun healthSummary(health: List<SourceHealth>): String {
    val parts = health.filter { it.state != RefreshState.IDLE }.sortedBy { it.kind.id }.map { h ->
        val kind = stringResource(
            when (h.kind) {
                RefreshKind.PLAYLIST -> R.string.health_playlist
                RefreshKind.EPG -> R.string.health_epg
                RefreshKind.CATALOGUE -> R.string.health_catalogue
            },
        )
        when (h.state) {
            RefreshState.RUNNING -> stringResource(R.string.health_updating, kind)
            RefreshState.SUCCESS -> pluralStringResource(R.plurals.health_success, h.itemCount, kind, h.itemCount)
            RefreshState.FAILED -> {
                val error = h.error
                if (error == null || error == AppError.Unknown) {
                    stringResource(R.string.health_failed, kind, h.consecutiveFailures)
                } else {
                    stringResource(R.string.health_failed_detail, kind, errorMessage(error))
                }
            }
            RefreshState.IDLE -> ""
        }
    }
    return parts.joinToString(" · ")
}
