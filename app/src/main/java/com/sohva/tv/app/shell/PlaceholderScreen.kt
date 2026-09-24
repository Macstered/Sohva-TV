package com.sohva.tv.app.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
 * A destination whose screen arrives in a later milestone: its title, "Coming in a later phase"
 * and a Back button that takes focus on entry. Uses only beta 23's own strings.
 */
@Composable
fun PlaceholderScreen(@StringRes title: Int, onBack: () -> Unit, tag: String) {
    val back = remember { FocusRequester() }
    ScreenBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = Sohva.spacing.safeHorizontal, vertical = Sohva.spacing.safeVertical).testTag(tag)) {
            Text(stringResource(title), style = Sohva.typography.display, color = Sohva.palette.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_coming_soon), style = Sohva.typography.bodyLarge, color = Sohva.palette.textMuted)
            Spacer(Modifier.height(24.dp))
            TvActionButton(
                label = stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier.focusRequester(back).testTag("placeholder-back"),
                icon = TvIcons.Back,
            )
        }
    }
    LaunchedEffect(Unit) { back.requestFocusWhenAttached() }
}
