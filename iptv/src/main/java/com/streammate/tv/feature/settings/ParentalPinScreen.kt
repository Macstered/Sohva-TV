package com.streammate.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.iptv.R
import kotlinx.coroutines.launch

@Composable
fun ParentalPinScreen(
    channelName: String,
    pinConfigured: Boolean,
    onVerify: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val initialMessage = if (pinConfigured) {
        stringResource(R.string.pin_prompt)
    } else {
        stringResource(R.string.pin_not_configured)
    }
    val wrongPinMessage = stringResource(R.string.pin_wrong)
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember(pinConfigured, initialMessage) {
        mutableStateOf(initialMessage)
    }
    val scope = rememberCoroutineScope()

    StreamMateScreenBackground { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SohvaTvBrand(modifier = Modifier.align(Alignment.Start))
            Text(
                text = stringResource(R.string.pin_locked_channel),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
            )
            Text(text = channelName, color = palette.focus, fontSize = 22.sp)
            Text(text = message, color = palette.textMuted, fontSize = 16.sp)
            TvUrlField(
                value = pin,
                onValueChange = { value -> pin = value.filter(Char::isDigit).take(MAX_PIN_LENGTH) },
                label = stringResource(R.string.pin_code),
                modifier = Modifier.width(360.dp),
                testTag = "parental-pin",
                leadingIcon = "●",
                keyboardType = KeyboardType.NumberPassword,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TvActionButton(
                    label = stringResource(R.string.action_back),
                    icon = TvIcons.Back,
                    onClick = onBack,
                    testTag = "parental-back",
                )
                TvActionButton(
                    label = if (busy) {
                        stringResource(R.string.pin_checking)
                    } else {
                        stringResource(R.string.pin_unlock)
                    },
                    icon = TvIcons.Check,
                    enabled = pinConfigured && pin.length >= MIN_PIN_LENGTH && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            if (onVerify(pin)) {
                                onUnlocked()
                            } else {
                                pin = ""
                                message = wrongPinMessage
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.padding(start = 12.dp),
                    testTag = "parental-unlock",
                )
            }
        }
    }
}

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 8
