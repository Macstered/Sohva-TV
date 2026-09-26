package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.QrCodeImage
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** Opens links and remembers which control asked, so focus returns there when the dialog closes. */
@Stable
internal class LinkOpener(private val about: AboutSettings) {
    var from: FocusRequester? = null
        private set

    fun open(url: String, from: FocusRequester) {
        this.from = from
        about.openLink(url)
    }
}

@Composable
internal fun rememberLinkOpener(about: AboutSettings): LinkOpener = remember(about) { LinkOpener(about) }

/** A compact button that opens [url] (spec 72 §5.2's legal buttons). */
@Composable
internal fun LinkButton(label: String, url: String, tag: String, links: LinkOpener, self: FocusRequester = remember { FocusRequester() }) {
    TvActionButton(label, { links.open(url, self) }, Modifier.focusRequester(self).testTag(tag), compact = true)
}

/**
 * A TV with no browser or mail app (ABOUT-FR-23, rebuild): the address as text and as a code to
 * scan, like phone setup. Focus goes back to the link's control before the dialog hides.
 */
@Composable
internal fun LinkDialog(about: AboutSettings, links: LinkOpener) {
    val shown by about.link.collectAsStateWithLifecycle()
    val link = shown ?: return
    val close = remember { FocusRequester() }
    fun dismiss() {
        links.from?.requestFocus()
        about.closeLink()
    }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DialogCard(Modifier.width(620.dp).testTag("link-dialog"), border = Sohva.palette.outline) {
                Text(stringResource(R.string.link_open_elsewhere), style = Sohva.typography.body, color = Sohva.palette.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    link.qr?.let { QrCodeImage(it, link.url) }
                    Text(link.url, Modifier.testTag("link-dialog-url"), style = Sohva.typography.bodyLarge, color = Sohva.palette.focus)
                }
                TvActionButton(stringResource(R.string.action_close), ::dismiss, Modifier.focusRequester(close).testTag("link-dialog-close"))
            }
        }
    }
    LaunchedEffect(link) { close.requestFocusWhenAttached() }
}
