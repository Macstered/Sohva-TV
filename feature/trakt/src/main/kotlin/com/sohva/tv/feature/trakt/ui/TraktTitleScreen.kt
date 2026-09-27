package com.sohva.tv.feature.trakt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The Trakt title screen (spec 51 FR-30, §5.3): the title, "Looking for this title in your
 * sources…" while the addons are asked, then "not available" when none has it; Back to home
 * leaves. Replaced by the Discover title page when found.
 */
@Composable
fun TraktTitleScreen(title: String, missing: Boolean, back: () -> Unit) {
    val button = remember { FocusRequester() }
    BackHandler(onBack = back)
    ScreenBackground(Modifier.fillMaxSize().testTag("screen-trakt-title")) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = Sohva.typography.headline, color = Sohva.palette.textPrimary, maxLines = 2)
            Text(
                stringResource(if (missing) R.string.trakt_title_unavailable else R.string.trakt_title_loading),
                Modifier.testTag("trakt-title-status"), style = Sohva.typography.body, color = Sohva.palette.textMuted,
            )
            TvActionButton(stringResource(R.string.addon_back), back, Modifier.focusRequester(button).testTag("trakt-title-back"), TvIcons.Back)
        }
    }
    LaunchedEffect(Unit) { button.requestFocusWhenAttached() }
}
