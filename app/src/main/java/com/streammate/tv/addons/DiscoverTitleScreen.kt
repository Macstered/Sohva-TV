package com.streammate.tv.addons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.sohva.tv.addons.AddonMedia
import com.sohva.tv.addons.AddonWatchProgress
import com.sohva.tv.addons.InstalledAddon
import com.streammate.tv.R
import com.streammate.tv.app.StreamMateContainer
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton

/**
 * A part-watched Discover title opened from Home: straight to its details
 * page, on the episode that was being watched, where Continue is offered.
 */
@Composable
internal fun DiscoverTitleScreen(container: StreamMateContainer, profileId: String, progress: AddonWatchProgress, onBack: () -> Unit) {
    val context = LocalContext.current
    val host = remember(container) { AddonHost.get(context, container) }
    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
        AddonHistoryDetailsScreen(host, profileId, progress, onBack, modifier)
    }
}

/** Home and Discover history share title details and episode-completion behavior. */
@Composable
internal fun AddonHistoryDetailsScreen(host: AddonHost, profileId: String, progress: AddonWatchProgress,
    onBack: () -> Unit, modifier: Modifier) {
    var installation by remember(progress) { mutableStateOf<InstalledAddon?>(null) }
    var missing by remember(progress) { mutableStateOf(false) }
    LaunchedEffect(progress, profileId) {
        installation = runCatching {
            host.manager.list(profileId).firstOrNull { it.installationId == progress.identity.metadataInstallationId && it.enabled }
        }.getOrNull()
        missing = installation == null
    }
    BackHandler(onBack = onBack)
    val current = installation
    if (current != null) {
        val artwork = progress.artwork
        val preview = AddonMedia(progress.identity.media, artwork?.name ?: progress.title, artwork?.poster, "poster", artwork?.background, null, null, emptyList())
        AddonDetailsScreen(host, profileId, current, preview, onBack, modifier, initialVideo = progress.identity.video)
    } else Column(modifier) {
        Text(stringResource(if (missing) R.string.addon_access_denied else R.string.addon_loading))
        TvActionButton(stringResource(R.string.addon_back), onBack)
    }
}
