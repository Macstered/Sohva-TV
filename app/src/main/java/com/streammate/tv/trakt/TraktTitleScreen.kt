package com.streammate.tv.trakt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.sohva.tv.addons.AddonException
import com.sohva.tv.addons.AddonMedia
import com.sohva.tv.addons.AddonMediaKey
import com.sohva.tv.addons.InstalledAddon
import com.streammate.tv.R
import com.streammate.tv.addons.AddonDetailsScreen
import com.streammate.tv.addons.AddonHost
import com.streammate.tv.app.StreamMateContainer
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import kotlinx.coroutines.CancellationException

/**
 * A Trakt title with no library copy: asked of the Discover addons by id,
 * IMDb first, then TMDB, and opened on its details page, on the episode when
 * one was named. Nothing is imported; the page is the ordinary one.
 */
@Composable
internal fun TraktTitleScreen(container: StreamMateContainer, profileId: String, title: TraktHomeTitle, onBack: () -> Unit) {
    val context = LocalContext.current
    val host = remember(container) { AddonHost.get(context, container) }
    var found by remember(title) { mutableStateOf<Triple<InstalledAddon, AddonMedia, AddonMediaKey?>?>(null) }
    var loading by remember(title) { mutableStateOf(true) }
    BackHandler(onBack = onBack)
    LaunchedEffect(title, profileId) {
        try {
            val type = if (title.kind == "movie") "movie" else "series"
            val keys = listOfNotNull(title.ids.imdb?.let { AddonMediaKey(type, it) }, title.ids.tmdb?.let { AddonMediaKey(type, "tmdb:$it") })
            val installations = host.manager.list(profileId).filter { it.enabled }
            for (key in keys) for (installation in installations) {
                if (!installation.manifest.supports("meta", key.type, key.id)) continue
                val media = try { host.browser.details(profileId, installation.installationId, key).value }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: AddonException) { continue }
                val video = if (title.season != null && title.number != null) {
                    media.videos.firstOrNull { it.season == title.season && it.episode == title.number }?.let { AddonMediaKey(media.key.type, it.id) }
                } else null
                found = Triple(installation, media, video)
                return@LaunchedEffect
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        finally { loading = false }
    }
    StreamMateScreenBackground(contentPadding = PaddingValues(0.dp)) { modifier ->
        val current = found
        if (current != null) {
            AddonDetailsScreen(host, profileId, current.first, current.second, onBack, modifier, initialVideo = current.third)
        } else Column(modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title.title, fontSize = StreamMateThemeTokens.typography.headline.fontSize)
            Text(stringResource(if (loading) R.string.trakt_title_loading else R.string.trakt_title_unavailable))
            TvActionButton(stringResource(R.string.addon_back), onBack)
        }
    }
}
