package com.sohva.tv.app.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Once, on the first start that brought beta 23's data across (decision "Upgrade notice"): what
 * came across, and that the guide and libraries refill from the providers for a few minutes, so an
 * emptier guide right after the update is not read as lost data. OK or Back closes it.
 */
@Composable
fun UpgradeNotice(onClose: () -> Unit) {
    val ok = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(620.dp).roundFill(Sohva.palette.surface, Sohva.shapes.large).padding(28.dp).testTag("upgrade-notice"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.upgrade_notice_title),
                    style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary,
                )
                Text(stringResource(R.string.upgrade_notice_kept), style = Sohva.typography.body, color = Sohva.palette.textMuted)
                Text(stringResource(R.string.upgrade_notice_reload), style = Sohva.typography.body, color = Sohva.palette.textMuted)
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    TvActionButton(stringResource(R.string.upgrade_notice_ok), onClose, Modifier.focusRequester(ok).testTag("upgrade-notice-ok"))
                }
            }
        }
        LaunchedEffect(Unit) { ok.requestFocusWhenAttached() }
    }
}
