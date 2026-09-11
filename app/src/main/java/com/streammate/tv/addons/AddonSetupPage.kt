package com.streammate.tv.addons

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons

/** The same fixed title/back row and spacing as the surrounding addon settings. */
@Composable
internal fun AddonSetupPage(
    title: String, onBack: () -> Unit, backLabel: String, backFocus: FocusRequester,
    screenTag: String, backTag: String, modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().padding(28.dp).testTag(screenTag)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(title, Modifier.weight(1f), fontSize = StreamMateThemeTokens.typography.display.fontSize,
                fontWeight = FontWeight.Black)
            TvActionButton(backLabel, onBack, icon = TvIcons.Back, compact = true,
                focusRequester = backFocus, testTag = backTag)
        }
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
internal fun AddonSetupNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = StreamMateThemeTokens.palette.textDim,
        fontSize = StreamMateThemeTokens.typography.label.fontSize,
        lineHeight = StreamMateThemeTokens.typography.label.lineHeight)
}
