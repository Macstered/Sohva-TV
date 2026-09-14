package com.streammate.tv.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.streammate.tv.R
import com.streammate.tv.feature.common.StreamMateScreenBackground

@Composable
fun StreamMateLaunchScreen() {
    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxSize().testTag("launch-splash"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.sohva_mark),
                contentDescription = null,
                modifier = Modifier.size(188.dp).testTag("launch-mark"),
            )
            Spacer(Modifier.height(18.dp))
            // The logo's own lettering, so this screen matches the window
            // background drawn before Compose is up and nothing jumps.
            Image(
                painter = painterResource(R.drawable.sohva_wordmark),
                contentDescription = stringResource(com.streammate.tv.core.R.string.brand_sohva_tv),
                modifier = Modifier.height(56.dp).testTag("launch-brand"),
            )
        }
    }
}
