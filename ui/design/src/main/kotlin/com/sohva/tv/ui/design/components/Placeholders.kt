package com.sohva.tv.ui.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.text.Initials
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Up to two initials on a surface tile where artwork is missing (design/04 §3). The initials sit
 * under any logo or crest drawn on top, so a missing image costs nothing extra.
 */
@Composable
fun InitialsTile(name: String, modifier: Modifier = Modifier, corner: Dp = 8.dp, fontSize: TextUnit = 18.sp) {
    val initials = remember(name) { Initials.of(name) }
    Box(modifier.roundFill(Sohva.palette.surfaceRaised, corner), contentAlignment = Alignment.Center) {
        Text(
            initials,
            style = Sohva.typography.label.copy(fontSize = fontSize, fontWeight = FontWeight.Black),
            color = Sohva.palette.focus,
            maxLines = 1,
        )
    }
}

/** A section's message: loading, empty or failed, in plain language (plan/03 §4.11). */
@Composable
fun SectionMessage(text: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = Sohva.typography.bodyLarge.copy(textAlign = TextAlign.Center), color = Sohva.palette.textMuted)
        if (detail != null) {
            Text(detail, Modifier.padding(top = 6.dp), style = Sohva.typography.label, color = Sohva.palette.textDim)
        }
    }
}

/** The one sentence the viewer reads for an [AppError]. Features add their codes with their strings. */
@Composable
fun errorMessage(error: AppError): String = when (error) {
    is AppError.TransportFailed ->
        if (error.detail.isNullOrBlank()) {
            stringResource(R.string.error_transport_failed)
        } else {
            stringResource(R.string.error_transport_failed_detail, error.detail.orEmpty())
        }
    is AppError.HttpStatus -> stringResource(R.string.error_source_http, error.status)
    // SecretsUnreadable gets its own sentence with spec 73 in M1 (docs/decisions.md).
    AppError.Unknown, AppError.SecretsUnreadable -> stringResource(R.string.error_unknown)
}

/** Fills its parent with a centred [SectionMessage]; for screens that have nothing else yet. */
@Composable
fun FullScreenMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SectionMessage(text) }
}
