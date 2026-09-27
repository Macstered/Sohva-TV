package com.sohva.tv.app.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sohva.tv.app.R
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.theme.SohvaTheme
import com.sohva.tv.ui.design.R as DesignR

/**
 * The Compose launch screen (spec 01 §5.2): the window's launch picture plus the ground's two
 * washes, in the Original palette at device density, so the hand-over from the window moves
 * nothing. Until the ground is rendered it draws nothing and the window's picture shows.
 */
@Composable
fun LaunchScreen(updating: Boolean = false) {
    SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = true) {
        ScreenBackground(whileRendering = Color.Transparent) {
            Column(
                Modifier.fillMaxSize().testTag("launch-splash"),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(R.drawable.sohva_mark), null, Modifier.size(188.dp).testTag("launch-mark"))
                Image(
                    painterResource(R.drawable.sohva_wordmark),
                    stringResource(DesignR.string.brand_sohva_tv),
                    Modifier.width(273.dp).height(56.dp).testTag("launch-brand"),
                )
                // A first start over beta 23 that takes a while (plan/04 §17).
                if (updating) {
                    com.sohva.tv.ui.design.text.Text(
                        stringResource(DesignR.string.launch_updating), Modifier.testTag("launch-updating"),
                        style = com.sohva.tv.ui.design.theme.Sohva.typography.body, color = com.sohva.tv.ui.design.theme.Sohva.palette.textMuted,
                    )
                }
            }
        }
    }
}
